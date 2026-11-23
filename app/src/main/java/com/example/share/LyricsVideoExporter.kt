package com.example.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.Surface
import androidx.core.content.FileProvider
import com.example.data.repository.AudioTagParsers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Native Android exporter for sharing 9:16 synchronized lyric/audio clips (.mp4)
 * and high-resolution share cards (.png) with zero heavy third-party dependencies.
 */
object LyricsVideoExporter {
    private const val SHARED_CLIPS_DIR = "shared_clips"
    private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val VIDEO_FPS = 30
    private const val FRAME_DURATION_US = 33_333L
    private const val FRAME_DURATION_NS = 33_333_333L
    private const val VIDEO_BITRATE = 3_500_000
    private const val AUDIO_BITRATE = 192_000
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun getSharedClipsCacheDir(context: Context): File {
        return File(context.cacheDir, SHARED_CLIPS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    fun cleanupSharedClipsCache(context: Context) {
        val dir = File(context.cacheDir, SHARED_CLIPS_DIR)
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }

    fun buildOutputFileName(spec: LyricsShareClipSpec): String {
        val base = AudioTagParsers.buildSafeLrcFileName(spec.track.artist, spec.track.title)
            .removeSuffix(".lrc")
            .ifBlank { "davoo_clip" }
        val suffix = System.currentTimeMillis().toString().takeLast(5)
        return "${base}_${suffix}.${spec.format.extension}"
    }

    fun resolveFileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun exportClip(
        context: Context,
        spec: LyricsShareClipSpec,
        onProgress: (progress: Float, stageLabel: String) -> Unit
    ): ShareExportUiState.Ready = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        cleanupSharedClipsCache(context)
        val outDir = getSharedClipsCacheDir(context)
        val fileName = buildOutputFileName(spec)
        val outFile = File(outDir, fileName)

        try {
            if (spec.format == ShareOutputFormat.IMAGE_PNG) {
                exportStaticPngInternal(context, spec, outFile, onProgress)
            } else {
                exportVideoMp4Internal(context, spec, outFile, onProgress)
            }
            currentCoroutineContext().ensureActive()
            val uri = resolveFileProviderUri(context, outFile)
            ShareExportUiState.Ready(
                uri = uri,
                mimeType = spec.format.mimeType,
                format = spec.format,
                fileName = fileName
            )
        } catch (cancel: CancellationException) {
            runCatching { outFile.delete() }
            throw cancel
        } catch (err: Throwable) {
            runCatching { outFile.delete() }
            throw err
        }
    }

    private suspend fun exportStaticPngInternal(
        context: Context,
        spec: LyricsShareClipSpec,
        outFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.20f, "Cargando carátula…")
        currentCoroutineContext().ensureActive()
        val artwork = withContext(Dispatchers.IO) {
            LyricsShareCardRenderer.loadArtworkBitmap(context, spec.track, maxSizePx = 900)
        }
        var blurred: Bitmap? = null
        val canvasBitmap = Bitmap.createBitmap(
            LyricsShareCardRenderer.PNG_WIDTH,
            LyricsShareCardRenderer.PNG_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        try {
            if (spec.style == ShareCardStyle.BLURRED_ARTWORK && artwork != null) {
                blurred = LyricsShareCardRenderer.createBlurredBackgroundBitmap(
                    source = artwork,
                    targetWidth = LyricsShareCardRenderer.PNG_WIDTH,
                    targetHeight = LyricsShareCardRenderer.PNG_HEIGHT
                )
            }
            onProgress(0.55f, "Renderizando tarjeta…")
            currentCoroutineContext().ensureActive()
            val canvas = Canvas(canvasBitmap)
            LyricsShareCardRenderer.drawShareCardFrame(
                canvas = canvas,
                width = LyricsShareCardRenderer.PNG_WIDTH,
                height = LyricsShareCardRenderer.PNG_HEIGHT,
                spec = spec,
                artworkBitmap = artwork,
                blurredBgBitmap = blurred,
                frameElapsedMs = 0L,
                isStaticImage = true
            )
            onProgress(0.85f, "Guardando imagen PNG…")
            withContext(Dispatchers.IO) {
                FileOutputStream(outFile).use { fos ->
                    check(canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                        "No se pudo comprimir la imagen PNG"
                    }
                }
            }
            onProgress(1f, "Listo para compartir")
        } finally {
            if (!canvasBitmap.isRecycled) canvasBitmap.recycle()
            if (blurred != null && !blurred.isRecycled) blurred.recycle()
            if (artwork != null && !artwork.isRecycled) artwork.recycle()
        }
    }

    private data class DecodedAudioSegment(
        val pcmShorts: ShortArray,
        val sampleRateHz: Int,
        val channelCount: Int
    )

    private data class EncodedAudioPacket(
        val bytes: ByteArray,
        val ptsUs: Long,
        val flags: Int
    )

    private suspend fun exportVideoMp4Internal(
        context: Context,
        spec: LyricsShareClipSpec,
        outFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.05f, "Decodificando audio del fragmento…")
        currentCoroutineContext().ensureActive()

        val audioSegment = withContext(Dispatchers.IO) {
            extractAndNormalizeAudioSegment(
                context = context,
                audioUri = spec.track.contentUri,
                startMs = spec.startMs,
                endMs = spec.endMs
            )
        }
        onProgress(0.32f, "Codificando audio AAC…")
        currentCoroutineContext().ensureActive()

        val (audioFormat, audioPackets) = encodePcmToAacPackets(audioSegment)

        onProgress(0.45f, "Preparando lienzo y codificador H.264…")
        currentCoroutineContext().ensureActive()

        val artwork = withContext(Dispatchers.IO) {
            LyricsShareCardRenderer.loadArtworkBitmap(context, spec.track, maxSizePx = 720)
        }
        var blurred: Bitmap? = null
        if (spec.style == ShareCardStyle.BLURRED_ARTWORK && artwork != null) {
            blurred = LyricsShareCardRenderer.createBlurredBackgroundBitmap(
                source = artwork,
                targetWidth = LyricsShareCardRenderer.VIDEO_WIDTH,
                targetHeight = LyricsShareCardRenderer.VIDEO_HEIGHT
            )
        }

        val frameBitmap = Bitmap.createBitmap(
            LyricsShareCardRenderer.VIDEO_WIDTH,
            LyricsShareCardRenderer.VIDEO_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val frameCanvas = Canvas(frameBitmap)

        var videoEncoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var eglRenderer: EglSurfaceBitmapRenderer? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            val videoFormat = MediaFormat.createVideoFormat(
                VIDEO_MIME,
                LyricsShareCardRenderer.VIDEO_WIDTH,
                LyricsShareCardRenderer.VIDEO_HEIGHT
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            inputSurface = videoEncoder.createInputSurface()
            eglRenderer = EglSurfaceBitmapRenderer(
                surface = inputSurface,
                width = LyricsShareCardRenderer.VIDEO_WIDTH,
                height = LyricsShareCardRenderer.VIDEO_HEIGHT
            )
            videoEncoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val totalFrames = ((spec.clipDurationMs * VIDEO_FPS.toLong()) / 1000L).toInt().coerceAtLeast(VIDEO_FPS)
            val bufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var nextAudioPacketIdx = 0
            var lastVideoPtsUs = -1L

            fun ensureMuxerStartedIfReady(encoderFormat: MediaFormat) {
                if (!muxerStarted) {
                    videoTrackIndex = muxer!!.addTrack(encoderFormat)
                    audioTrackIndex = muxer!!.addTrack(audioFormat)
                    muxer!!.start()
                    muxerStarted = true
                }
            }

            fun flushAudioUpTo(limitPtsUs: Long) {
                if (!muxerStarted || audioTrackIndex < 0) return
                while (nextAudioPacketIdx < audioPackets.size) {
                    val pkt = audioPackets[nextAudioPacketIdx]
                    if (pkt.ptsUs > limitPtsUs) break
                    val bb = ByteBuffer.wrap(pkt.bytes)
                    val info = MediaCodec.BufferInfo().apply {
                        offset = 0
                        size = pkt.bytes.size
                        presentationTimeUs = pkt.ptsUs
                        flags = pkt.flags
                    }
                    muxer!!.writeSampleData(audioTrackIndex, bb, info)
                    nextAudioPacketIdx++
                }
            }

            fun drainVideoEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    videoEncoder!!.signalEndOfInputStream()
                }
                var eosIdleTries = 0
                while (true) {
                    val outIdx = videoEncoder!!.dequeueOutputBuffer(
                        bufferInfo,
                        if (endOfStream) DEQUEUE_TIMEOUT_US else 0L
                    )
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                            eosIdleTries++
                            if (eosIdleTries > 400) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            eosIdleTries = 0
                            ensureMuxerStartedIfReady(videoEncoder!!.outputFormat)
                        }
                        outIdx >= 0 -> {
                            eosIdleTries = 0
                            val encodedBuf = videoEncoder!!.getOutputBuffer(outIdx)
                            if (encodedBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                                if (!muxerStarted) {
                                    ensureMuxerStartedIfReady(videoEncoder!!.outputFormat)
                                }
                                encodedBuf.position(bufferInfo.offset)
                                encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val safePts = if (bufferInfo.presentationTimeUs <= lastVideoPtsUs) {
                                    lastVideoPtsUs + 1L
                                } else {
                                    bufferInfo.presentationTimeUs
                                }
                                lastVideoPtsUs = safePts
                                bufferInfo.presentationTimeUs = safePts
                                muxer!!.writeSampleData(videoTrackIndex, encodedBuf, bufferInfo)
                                flushAudioUpTo(safePts)
                            }
                            videoEncoder!!.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            for (frameIdx in 0 until totalFrames) {
                currentCoroutineContext().ensureActive()
                val elapsedMs = ((frameIdx.toLong() * 1000L) / VIDEO_FPS.toLong())
                    .coerceIn(0L, spec.clipDurationMs)

                LyricsShareCardRenderer.drawShareCardFrame(
                    canvas = frameCanvas,
                    width = LyricsShareCardRenderer.VIDEO_WIDTH,
                    height = LyricsShareCardRenderer.VIDEO_HEIGHT,
                    spec = spec,
                    artworkBitmap = artwork,
                    blurredBgBitmap = blurred,
                    frameElapsedMs = elapsedMs,
                    isStaticImage = false
                )

                val ptsNs = frameIdx.toLong() * FRAME_DURATION_NS
                eglRenderer.drawBitmap(frameBitmap, ptsNs)
                drainVideoEncoder(endOfStream = false)

                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    val frac = 0.45f + 0.50f * ((frameIdx + 1).toFloat() / totalFrames.toFloat())
                    onProgress(frac.coerceIn(0.45f, 0.96f), "Renderizando video (${frameIdx + 1}/$totalFrames)…")
                }
            }

            onProgress(0.97f, "Finalizando archivo MP4…")
            drainVideoEncoder(endOfStream = true)
            flushAudioUpTo(Long.MAX_VALUE)

            check(muxerStarted && outFile.exists() && outFile.length() > 0L) {
                "No se pudo completar el archivo de video MP4"
            }
            onProgress(1f, "Listo para compartir")
        } finally {
            runCatching { eglRenderer?.release() }
            runCatching { inputSurface?.release() }
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            if (!frameBitmap.isRecycled) frameBitmap.recycle()
            if (blurred != null && !blurred.isRecycled) blurred.recycle()
            if (artwork != null && !artwork.isRecycled) artwork.recycle()
        }
    }

    private suspend fun extractAndNormalizeAudioSegment(
        context: Context,
        audioUri: String,
        startMs: Long,
        endMs: Long
    ): DecodedAudioSegment {
        val extractor = MediaExtractor()
        var pfd: ParcelFileDescriptor? = null
        var decoder: MediaCodec? = null
        try {
            if (audioUri.startsWith("http://", ignoreCase = true) ||
                audioUri.startsWith("https://", ignoreCase = true)
            ) {
                extractor.setDataSource(audioUri)
            } else {
                val parsed = Uri.parse(audioUri)
                pfd = runCatching { context.contentResolver.openFileDescriptor(parsed, "r") }.getOrNull()
                if (pfd != null) {
                    extractor.setDataSource(pfd.fileDescriptor)
                } else {
                    extractor.setDataSource(context, parsed, null)
                }
            }

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            check(audioTrackIndex >= 0 && inputFormat != null) {
                "El archivo no contiene una pista de audio compatible"
            }

            extractor.selectTrack(audioTrackIndex)
            val startUs = (startMs * 1000L).coerceAtLeast(0L)
            val endUs = (endMs * 1000L).coerceAtLeast(startUs + 1_000_000L)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            var decodedSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44_100
            var decodedChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            var decodedPcmEncoding = if (inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                LyricsShareAudioProcessor.PCM_ENCODING_16BIT
            }

            val accumulatedShorts = ShortArrayAccumulator()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0

            while (!outputDone) {
                currentCoroutineContext().ensureActive()

                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (sampleTimeUs >= 0L && sampleTimeUs > endUs + 500_000L)) {
                                decoder.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIdx,
                                    0,
                                    sampleSize,
                                    sampleTimeUs.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleLoops++
                        if (inputDone && idleLoops > 250) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = decoder.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            decodedSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            decodedChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            decodedPcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    outIdx >= 0 -> {
                        idleLoops = 0
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunkBytes = ByteArray(bufferInfo.size)
                            outBuf.get(chunkBytes)

                            val rawShorts = LyricsShareAudioProcessor.decodePcmBytesToShorts(
                                bytes = chunkBytes,
                                pcmEncoding = decodedPcmEncoding
                            )
                            val safeChannels = decodedChannels.coerceAtLeast(1)
                            val framesInChunk = rawShorts.size / safeChannels
                            val chunkStartUs = bufferInfo.presentationTimeUs
                            val chunkDurUs = (framesInChunk.toLong() * 1_000_000L) / decodedSampleRate.coerceAtLeast(1)
                            val chunkEndUs = chunkStartUs + chunkDurUs

                            if (chunkEndUs > startUs && chunkStartUs < endUs) {
                                val skipFrames = if (chunkStartUs < startUs) {
                                    (((startUs - chunkStartUs) * decodedSampleRate) / 1_000_000L)
                                        .toInt()
                                        .coerceIn(0, framesInChunk)
                                } else 0

                                val keepUntilFrame = if (chunkEndUs > endUs) {
                                    (((endUs - chunkStartUs) * decodedSampleRate) / 1_000_000L)
                                        .toInt()
                                        .coerceIn(skipFrames, framesInChunk)
                                } else framesInChunk

                                val startSample = skipFrames * safeChannels
                                val endSample = keepUntilFrame * safeChannels
                                if (endSample > startSample) {
                                    accumulatedShorts.append(rawShorts, startSample, endSample - startSample)
                                }
                            }
                            if (chunkStartUs >= endUs) {
                                outputDone = true
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val rawSegment = accumulatedShorts.toShortArray()
            check(rawSegment.isNotEmpty()) {
                "No se pudo extraer audio del fragmento seleccionado"
            }

            val (channelNorm, finalChannels) = LyricsShareAudioProcessor.normalizeChannels(
                samples = rawSegment,
                srcChannels = decodedChannels
            )
            val targetSampleRate = LyricsShareAudioProcessor.selectTargetAacSampleRate(decodedSampleRate)
            val resampled = LyricsShareAudioProcessor.linearResample(
                samples = channelNorm,
                channels = finalChannels,
                srcSampleRate = decodedSampleRate,
                dstSampleRate = targetSampleRate
            )

            // Exact clip length matching video frames + 300ms fade-in / 500ms fade-out
            val targetFrames = (((endMs - startMs) * targetSampleRate) / 1000L).toInt().coerceAtLeast(1)
            val targetTotalSamples = targetFrames * finalChannels
            val trimmed = if (resampled.size > targetTotalSamples) {
                resampled.copyOf(targetTotalSamples)
            } else {
                resampled
            }
            LyricsShareAudioProcessor.applyFadeInOut(
                samples = trimmed,
                sampleRate = targetSampleRate,
                channels = finalChannels,
                fadeInMs = 300,
                fadeOutMs = 500
            )
            return DecodedAudioSegment(
                pcmShorts = trimmed,
                sampleRateHz = targetSampleRate,
                channelCount = finalChannels
            )
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
            runCatching { pfd?.close() }
        }
    }

    private suspend fun encodePcmToAacPackets(
        segment: DecodedAudioSegment
    ): Pair<MediaFormat, List<EncodedAudioPacket>> {
        val packets = ArrayList<EncodedAudioPacket>(256)
        var outFormat: MediaFormat? = null
        var encoder: MediaCodec? = null

        try {
            val encodeFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME,
                segment.sampleRateHz,
                segment.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }

            encoder = MediaCodec.createEncoderByType(AUDIO_MIME).apply {
                configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            val pcmBytes = ByteArray(segment.pcmShorts.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(segment.pcmShorts)

            val bytesPerFrame = segment.channelCount * 2
            var byteOffset = 0
            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            var lastAudioPtsUs = -1L

            while (!outputDone) {
                currentCoroutineContext().ensureActive()

                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            if (byteOffset >= pcmBytes.size) {
                                val eosPtsUs = ((byteOffset / bytesPerFrame).toLong() * 1_000_000L) /
                                    segment.sampleRateHz.toLong()
                                encoder.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    eosPtsUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val maxWrite = min(inBuf.remaining(), pcmBytes.size - byteOffset)
                                val alignedWrite = (maxWrite / bytesPerFrame) * bytesPerFrame
                                val actualWrite = if (alignedWrite > 0) alignedWrite else maxWrite
                                val ptsUs = ((byteOffset / bytesPerFrame).toLong() * 1_000_000L) /
                                    segment.sampleRateHz.toLong()
                                inBuf.put(pcmBytes, byteOffset, actualWrite)
                                encoder.queueInputBuffer(inIdx, 0, actualWrite, ptsUs, 0)
                                byteOffset += actualWrite
                            }
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outFormat = encoder.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val payload = ByteArray(bufferInfo.size)
                            outBuf.get(payload)
                            val safePts = if (bufferInfo.presentationTimeUs <= lastAudioPtsUs) {
                                lastAudioPtsUs + 1L
                            } else {
                                bufferInfo.presentationTimeUs
                            }
                            lastAudioPtsUs = safePts
                            packets += EncodedAudioPacket(
                                bytes = payload,
                                ptsUs = safePts,
                                flags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                            )
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val resolvedFormat = outFormat ?: encodeFormat
            return resolvedFormat to packets
        } finally {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
        }
    }

    private class ShortArrayAccumulator(initialCapacity: Int = 65_536) {
        private var buffer = ShortArray(initialCapacity)
        private var size = 0

        fun append(src: ShortArray, srcOffset: Int, length: Int) {
            if (length <= 0) return
            if (size + length > buffer.size) {
                var newCap = buffer.size * 2
                while (newCap < size + length) newCap *= 2
                buffer = buffer.copyOf(newCap)
            }
            System.arraycopy(src, srcOffset, buffer, size, length)
            size += length
        }

        fun toShortArray(): ShortArray = buffer.copyOf(size)
    }

    /**
     * Minimal EGL14 + GLES20 textured quad renderer so Canvas Bitmaps are uploaded directly to
     * [MediaCodec.createInputSurface] with exact nanosecond presentation timestamps.
     */
    private class EglSurfaceBitmapRenderer(
        surface: Surface,
        private val width: Int,
        private val height: Int
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var programId: Int = 0
        private var textureId: Int = 0
        private var textureInitialized = false
        private var posHandle = -1
        private var uvHandle = -1
        private var samplerHandle = -1

        private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        -1f, -1f,
                        1f, -1f,
                        -1f, 1f,
                        1f, 1f
                    )
                )
                position(0)
            }

        private val uvBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        0f, 1f,
                        1f, 1f,
                        0f, 0f,
                        1f, 0f
                    )
                )
                position(0)
            }

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "No se pudo iniciar EGLDisplay" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                "No se pudo inicializar EGL14"
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] <= 0 || configs[0] == null
            ) {
                val fallbackAttribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )
                check(EGL14.eglChooseConfig(eglDisplay, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                    numConfigs[0] > 0 && configs[0] != null
                ) {
                    "No se encontró configuración EGL compatible para video"
                }
            }
            val chosenConfig = configs[0]!!

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "No se pudo crear contexto EGL" }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, chosenConfig, surface, surfaceAttribs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "No se pudo crear superficie EGL" }

            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                "No se pudo activar superficie EGL"
            }

            setupGlPipeline()
        }

        private fun setupGlPipeline() {
            val vertexSrc = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """.trimIndent()
            val fragmentSrc = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()

            val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
            val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            programId = GLES20.glCreateProgram().also { prog ->
                GLES20.glAttachShader(prog, vShader)
                GLES20.glAttachShader(prog, fShader)
                GLES20.glLinkProgram(prog)
            }
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)

            posHandle = GLES20.glGetAttribLocation(programId, "aPosition")
            uvHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
            samplerHandle = GLES20.glGetUniformLocation(programId, "uTexture")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }

        fun drawBitmap(bitmap: Bitmap, ptsNs: Long) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            if (!textureInitialized) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                textureInitialized = true
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            }
            GLES20.glUniform1i(samplerHandle, 0)

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            uvBuffer.position(0)
            GLES20.glEnableVertexAttribArray(uvHandle)
            GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 0, uvBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(posHandle)
            GLES20.glDisableVertexAttribArray(uvHandle)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                runCatching {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                }
                if (textureId != 0) {
                    runCatching { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0) }
                    textureId = 0
                }
                if (programId != 0) {
                    runCatching { GLES20.glDeleteProgram(programId) }
                    programId = 0
                }
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                runCatching { EGL14.eglTerminate(eglDisplay) }
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
        }
    }
}
