package com.example.playback

import android.content.Context
import android.media.AudioFormat as AndroidAudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight, zero-dependency loudness / ReplayGain scanner.
 * Samples representative audio windows using streaming MediaExtractor + MediaCodec
 * so RAM stays tiny (~64 KB) and low-end devices scan rapidly without battery drain.
 */
object ReplayGainScanner {

    private const val TARGET_REFERENCE_DBFS = -18.0
    private const val MIN_GAIN_DB = -12.0f
    private const val MAX_GAIN_DB = 12.0f
    private const val WINDOW_DURATION_US = 2_400_000L
    private const val DECODE_TIMEOUT_US = 6_000L

    fun computeReplayGainDbFromRms(
        rmsNormalized: Double,
        targetDbfs: Double = TARGET_REFERENCE_DBFS
    ): Float? {
        if (!rmsNormalized.isFinite() || rmsNormalized <= 1e-6) return null
        val dbfs = 20.0 * log10(rmsNormalized)
        val rawGain = (targetDbfs - dbfs).toFloat()
        if (!rawGain.isFinite()) return null
        val clamped = rawGain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        return (clamped * 100f).roundToInt() / 100f
    }

    suspend fun scanTrackReplayGainDb(
        context: Context,
        audioUriString: String
    ): Float? = withContext(Dispatchers.IO) {
        if (audioUriString.isBlank() ||
            audioUriString.startsWith("http://", ignoreCase = true) ||
            audioUriString.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext null
        }

        val extractor = MediaExtractor()
        var pfd: ParcelFileDescriptor? = null
        var decoder: MediaCodec? = null

        try {
            val uri = Uri.parse(audioUriString)
            pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (pfd != null) {
                extractor.setDataSource(pfd.fileDescriptor)
            } else {
                extractor.setDataSource(context, uri, null)
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
            if (audioTrackIndex < 0 || inputFormat == null) return@withContext null

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L)
            } else 0L

            extractor.selectTrack(audioTrackIndex)
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            var pcmEncoding = AndroidAudioFormat.ENCODING_PCM_16BIT
            val windowStartsUs: List<Long> = if (durationUs > 22_000_000L) {
                listOf(0.14, 0.32, 0.50, 0.68, 0.84).map { frac ->
                    (durationUs * frac).toLong().coerceIn(0L, (durationUs - WINDOW_DURATION_US).coerceAtLeast(0L))
                }
            } else {
                listOf(0L)
            }
            val singleContinuousPass = windowStartsUs.size == 1

            var sumSquares = 0.0
            var totalSamples = 0L
            val bufferInfo = MediaCodec.BufferInfo()

            for (windowStartUs in windowStartsUs) {
                currentCoroutineContext().ensureActive()
                if (!singleContinuousPass) {
                    runCatching { decoder.flush() }
                    extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }

                val windowLimitUs = if (singleContinuousPass) {
                    Long.MAX_VALUE
                } else {
                    windowStartUs + WINDOW_DURATION_US
                }

                var inputDone = false
                var outputDone = false
                var idleIterations = 0

                while (!outputDone) {
                    currentCoroutineContext().ensureActive()

                    if (!inputDone) {
                        val inIdx = decoder.dequeueInputBuffer(DECODE_TIMEOUT_US)
                        if (inIdx >= 0) {
                            val inBuf = decoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val readSize = extractor.readSampleData(inBuf, 0)
                                val sampleTimeUs = extractor.sampleTime
                                if (readSize < 0 || (!singleContinuousPass && sampleTimeUs > windowLimitUs)) {
                                    decoder.queueInputBuffer(
                                        inIdx,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(inIdx, 0, readSize, sampleTimeUs.coerceAtLeast(0L), 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, DECODE_TIMEOUT_US)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = decoder.outputFormat
                            if (BuildChecks.hasPcmEncoding(outFmt)) {
                                pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        outIdx >= 0 -> {
                            idleIterations = 0
                            val outBuf = decoder.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val (chunkSq, chunkCount) = accumulateNormalizedSquares(outBuf, bufferInfo.size, pcmEncoding)
                                sumSquares += chunkSq
                                totalSamples += chunkCount
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            idleIterations++
                            if (inputDone && idleIterations > 40) {
                                outputDone = true
                            }
                        }
                    }
                }
            }

            if (totalSamples <= 0L) return@withContext null
            val rms = sqrt(sumSquares / totalSamples.toDouble())
            computeReplayGainDbFromRms(rms)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
            runCatching { pfd?.close() }
        }
    }

    private fun accumulateNormalizedSquares(
        buffer: ByteBuffer,
        byteCount: Int,
        pcmEncoding: Int
    ): Pair<Double, Long> {
        val dup = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var count = 0L

        when (pcmEncoding) {
            AndroidAudioFormat.ENCODING_PCM_FLOAT -> {
                val floatCount = byteCount / 4
                for (i in 0 until floatCount) {
                    val v = dup.float.toDouble().coerceIn(-1.0, 1.0)
                    sumSq += v * v
                    count++
                }
            }
            AndroidAudioFormat.ENCODING_PCM_8BIT -> {
                for (i in 0 until byteCount) {
                    val unsigned = dup.get().toInt() and 0xFF
                    val norm = (unsigned - 128) / 128.0
                    sumSq += norm * norm
                    count++
                }
            }
            AndroidAudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val sampleCount = byteCount / 3
                for (i in 0 until sampleCount) {
                    val b0 = dup.get().toInt() and 0xFF
                    val b1 = dup.get().toInt() and 0xFF
                    val b2 = dup.get().toInt() // signed MSB
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    val norm = (sample24.toDouble() / 8_388_608.0).coerceIn(-1.0, 1.0)
                    sumSq += norm * norm
                    count++
                }
            }
            AndroidAudioFormat.ENCODING_PCM_32BIT -> {
                val intCount = byteCount / 4
                for (i in 0 until intCount) {
                    val sample32 = dup.int
                    val norm = (sample32.toDouble() / 2_147_483_648.0).coerceIn(-1.0, 1.0)
                    sumSq += norm * norm
                    count++
                }
            }
            else -> {
                // Default 16-bit signed little-endian PCM
                val shortCount = byteCount / 2
                for (i in 0 until shortCount) {
                    val sample16 = dup.short.toDouble() / 32_768.0
                    sumSq += sample16 * sample16
                    count++
                }
            }
        }
        return sumSq to count
    }

    private object BuildChecks {
        fun hasPcmEncoding(format: MediaFormat): Boolean {
            return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                format.containsKey(MediaFormat.KEY_PCM_ENCODING)
        }
    }
}
