package com.example.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.core.graphics.ColorUtils
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.LyricsData
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ShareOutputFormat(
    val label: String,
    val shortLabel: String,
    val mimeType: String,
    val extension: String
) {
    VIDEO_MP4(
        label = "Video con audio (.mp4)",
        shortLabel = "Video .mp4",
        mimeType = "video/mp4",
        extension = "mp4"
    ),
    IMAGE_PNG(
        label = "Imagen estática (.png)",
        shortLabel = "Imagen .png",
        mimeType = "image/png",
        extension = "png"
    )
}

enum class ShareCardStyle(
    val label: String,
    val description: String
) {
    DYNAMIC_ARTWORK(
        label = "Color dinámico",
        description = "Degradado extraído de la carátula"
    ),
    COPPER_AMBER_DARK(
        label = "Oscuro ámbar",
        description = "Fondo elegante con acento CopperAmber"
    ),
    BLURRED_ARTWORK(
        label = "Carátula difuminada",
        description = "Arte del álbum suavizado al fondo"
    )
}

@Immutable
data class ShareLyricVerse(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val secondaryText: String? = null
)

@Immutable
data class LyricsShareClipSpec(
    val track: AudioTrack,
    val startMs: Long,
    val endMs: Long,
    val isSyncedLyrics: Boolean,
    val verses: List<ShareLyricVerse>,
    val plainExcerpt: String? = null,
    val includeTranslation: Boolean = false,
    val style: ShareCardStyle = ShareCardStyle.DYNAMIC_ARTWORK,
    val format: ShareOutputFormat = ShareOutputFormat.VIDEO_MP4,
    val dynamicAccentColorInt: Int = 0xFFD98A3A.toInt()
) {
    val clipDurationMs: Long get() = (endMs - startMs).coerceAtLeast(1_000L)
}

@Immutable
sealed interface ShareExportUiState {
    data object Idle : ShareExportUiState
    data object Preparing : ShareExportUiState
    data class Exporting(
        val progress: Float,
        val stageLabel: String
    ) : ShareExportUiState

    data class Ready(
        val uri: Uri,
        val mimeType: String,
        val format: ShareOutputFormat,
        val fileName: String
    ) : ShareExportUiState

    data class Error(
        val message: String
    ) : ShareExportUiState
}

/**
 * Pure Kotlin clip calculation logic for both synced (.lrc) 1-6 line selection
 * and unsynced 15s-30s time window selection.
 */
object LyricsShareClipCalculator {
    const val MAX_CLIP_DURATION_MS = 30_000L
    const val MIN_UNSYNCED_DURATION_MS = 15_000L
    const val DEFAULT_UNSYNCED_DURATION_MS = 20_000L
    const val MAX_SELECTED_LINES = 6
    const val EOF_FALLBACK_LINE_MS = 5_000L

    fun effectiveTimestampMs(rawTimestampMs: Long, offsetMs: Long): Long {
        return (rawTimestampMs - offsetMs).coerceAtLeast(0L)
    }

    fun initialSyncedSelectionRange(
        lyrics: LyricsData,
        currentPositionMs: Long
    ): IntRange {
        val lines = lyrics.lines
        if (lines.isEmpty()) return 0..0
        val offset = lyrics.offsetMs
        var anchorIdx = -1
        for (i in lines.indices) {
            val ts = lines[i].timestampMs ?: continue
            val audioPos = effectiveTimestampMs(ts, offset)
            if (audioPos <= currentPositionMs) {
                anchorIdx = i
            } else {
                break
            }
        }
        val firstVocal = lines.indexOfFirst {
            it.timestampMs != null && !it.isInstrumental && it.text.isNotBlank()
        }.coerceAtLeast(0)
        val start = if (anchorIdx >= 0 && !lines[anchorIdx].isInstrumental && lines[anchorIdx].text.isNotBlank()) {
            anchorIdx
        } else {
            val nextVocal = (anchorIdx.coerceAtLeast(0) until lines.size).firstOrNull { idx ->
                val l = lines[idx]
                l.timestampMs != null && !l.isInstrumental && l.text.isNotBlank()
            }
            nextVocal ?: firstVocal
        }
        // Auto-expand up to 3 lines while keeping <= 22s for an inviting default preview
        var end = start
        val startAudioMs = lines[start].timestampMs?.let { effectiveTimestampMs(it, offset) } ?: 0L
        for (candidate in (start + 1) until minOf(lines.size, start + 3)) {
            val line = lines[candidate]
            if (line.timestampMs == null || line.isInstrumental || line.text.isBlank()) break
            val nextBoundary = findNextLineEndMs(lyrics, candidate, Long.MAX_VALUE)
            if ((nextBoundary - startAudioMs) <= 22_000L) {
                end = candidate
            } else {
                break
            }
        }
        return start..end
    }

    fun updateSyncedSelection(
        currentRange: IntRange,
        tappedIndex: Int,
        lyrics: LyricsData,
        trackDurationMs: Long
    ): IntRange {
        val lines = lyrics.lines
        if (tappedIndex !in lines.indices) return currentRange
        if (lines[tappedIndex].timestampMs == null) return currentRange

        val proposed = when {
            currentRange.isEmpty() -> tappedIndex..tappedIndex
            tappedIndex == currentRange.first && currentRange.first == currentRange.last -> tappedIndex..tappedIndex
            tappedIndex == currentRange.first -> (tappedIndex + 1)..currentRange.last
            tappedIndex == currentRange.last -> currentRange.first..(tappedIndex - 1)
            tappedIndex in currentRange -> tappedIndex..tappedIndex
            tappedIndex < currentRange.first -> tappedIndex..currentRange.last
            else -> currentRange.first..tappedIndex
        }

        val count = (proposed.last - proposed.first + 1)
        if (count in 1..MAX_SELECTED_LINES) {
            val hasOnlySynced = (proposed.first..proposed.last).all { lines[it].timestampMs != null }
            if (hasOnlySynced) {
                val window = computeSyncedClipWindow(
                    lyrics = lyrics,
                    translatedLyrics = null,
                    selectedRange = proposed,
                    trackDurationMs = trackDurationMs,
                    includeTranslation = false
                )
                if ((window.endMs - window.startMs) <= MAX_CLIP_DURATION_MS) {
                    return proposed
                }
            }
        }
        return tappedIndex..tappedIndex
    }

    data class SyncedClipWindow(
        val startMs: Long,
        val endMs: Long,
        val verses: List<ShareLyricVerse>,
        val rawSpanMs: Long,
        val wasClampedTo30s: Boolean
    )

    fun computeSyncedClipWindow(
        lyrics: LyricsData,
        translatedLyrics: LyricsData?,
        selectedRange: IntRange,
        trackDurationMs: Long,
        includeTranslation: Boolean
    ): SyncedClipWindow {
        val lines = lyrics.lines
        if (lines.isEmpty()) {
            return SyncedClipWindow(0L, 5_000L, emptyList(), 5_000L, false)
        }
        val safeFirst = selectedRange.first.coerceIn(0, lines.lastIndex)
        val safeLast = selectedRange.last.coerceIn(safeFirst, (safeFirst + MAX_SELECTED_LINES - 1).coerceAtMost(lines.lastIndex))
        val offset = lyrics.offsetMs
        val effectiveDurationMs = trackDurationMs.takeIf { it > 0L } ?: Long.MAX_VALUE

        val firstStartMs = lines[safeFirst].timestampMs
            ?.let { effectiveTimestampMs(it, offset) }
            ?: 0L
        val lastLineStartMs = lines[safeLast].timestampMs
            ?.let { effectiveTimestampMs(it, offset) }
            ?: firstStartMs

        val nextStartMs = findNextLineEndMs(
            lyrics = lyrics,
            lastSelectedIndex = safeLast,
            trackDurationMs = effectiveDurationMs
        )

        val unClampedEnd = nextStartMs.coerceAtLeast(firstStartMs + 1_500L)
            .let { if (trackDurationMs > 0L) it.coerceAtMost(trackDurationMs) else it }
            .coerceAtLeast(firstStartMs + 1_000L)
        val rawSpan = (unClampedEnd - firstStartMs).coerceAtLeast(1_000L)
        val finalEnd = minOf(unClampedEnd, firstStartMs + MAX_CLIP_DURATION_MS)
        val clamped = unClampedEnd > finalEnd

        val transLines = translatedLyrics?.lines
        val verses = (safeFirst..safeLast).mapIndexed { idxOffset, lineIdx ->
            val line = lines[lineIdx]
            val lineStart = line.timestampMs
                ?.let { effectiveTimestampMs(it, offset) }
                ?: firstStartMs
            val nextLineTs = (lineIdx + 1..safeLast)
                .asSequence()
                .mapNotNull { lines[it].timestampMs?.let { ts -> effectiveTimestampMs(ts, offset) } }
                .firstOrNull()
                ?: finalEnd
            val secondary = if (includeTranslation) {
                (transLines?.getOrNull(lineIdx)?.text ?: line.secondaryText)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals(line.text.trim(), ignoreCase = true) }
            } else null
            ShareLyricVerse(
                startMs = lineStart.coerceIn(firstStartMs, finalEnd),
                endMs = nextLineTs.coerceIn(lineStart.coerceIn(firstStartMs, finalEnd), finalEnd),
                text = line.text.trim(),
                secondaryText = secondary
            )
        }.filter { it.text.isNotEmpty() }

        return SyncedClipWindow(
            startMs = firstStartMs,
            endMs = finalEnd.coerceAtLeast(firstStartMs + 1_000L),
            verses = verses,
            rawSpanMs = rawSpan,
            wasClampedTo30s = clamped
        )
    }

    private fun findNextLineEndMs(
        lyrics: LyricsData,
        lastSelectedIndex: Int,
        trackDurationMs: Long
    ): Long {
        val lines = lyrics.lines
        val offset = lyrics.offsetMs
        val lastLineStart = lines[lastSelectedIndex].timestampMs
            ?.let { effectiveTimestampMs(it, offset) }
            ?: 0L
        for (i in (lastSelectedIndex + 1) until lines.size) {
            val ts = lines[i].timestampMs ?: continue
            val candidateEnd = effectiveTimestampMs(ts, offset)
            if (candidateEnd > lastLineStart) {
                return if (trackDurationMs < Long.MAX_VALUE) {
                    minOf(trackDurationMs, candidateEnd)
                } else {
                    candidateEnd
                }
            }
        }
        return if (trackDurationMs < Long.MAX_VALUE) {
            minOf(trackDurationMs, lastLineStart + EOF_FALLBACK_LINE_MS)
        } else {
            lastLineStart + EOF_FALLBACK_LINE_MS
        }
    }

    data class UnsyncedClipWindow(
        val startMs: Long,
        val endMs: Long,
        val durationMs: Long
    )

    fun computeUnsyncedWindow(
        requestedStartMs: Long,
        requestedDurationMs: Long,
        trackDurationMs: Long
    ): UnsyncedClipWindow {
        val safeTrackDur = trackDurationMs.coerceAtLeast(1_000L)
        val minDur = minOf(MIN_UNSYNCED_DURATION_MS, safeTrackDur)
        val maxDur = minOf(MAX_CLIP_DURATION_MS, safeTrackDur)
        val clampedDur = requestedDurationMs.coerceIn(minDur, maxDur)
        val maxStart = (safeTrackDur - clampedDur).coerceAtLeast(0L)
        val clampedStart = requestedStartMs.coerceIn(0L, maxStart)
        val end = (clampedStart + clampedDur).coerceAtMost(safeTrackDur)
        return UnsyncedClipWindow(
            startMs = clampedStart,
            endMs = end,
            durationMs = (end - clampedStart).coerceAtLeast(1_000L)
        )
    }
}

/**
 * Pure Kotlin PCM normalization, Hi-Res resampling (e.g. 24-bit/32-bit/float & 88.2/96 kHz FLAC ->
 * 16-bit & 44.1/48 kHz AAC-compatible PCM), and 300ms fade-in / 500ms fade-out ramps.
 */
object LyricsShareAudioProcessor {
    const val PCM_ENCODING_16BIT = 2
    const val PCM_ENCODING_8BIT = 3
    const val PCM_ENCODING_FLOAT = 4
    const val PCM_ENCODING_24BIT_PACKED = 21
    const val PCM_ENCODING_32BIT = 22

    private val AAC_SUPPORTED_RATES = intArrayOf(
        8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000
    )

    fun selectTargetAacSampleRate(inputSampleRateHz: Int): Int {
        if (inputSampleRateHz <= 0) return 44_100
        if (inputSampleRateHz in AAC_SUPPORTED_RATES) return inputSampleRateHz
        return if (inputSampleRateHz % 48_000 == 0 || inputSampleRateHz == 96_000 || inputSampleRateHz == 192_000) {
            48_000
        } else {
            44_100
        }
    }

    fun decodePcmBytesToShorts(bytes: ByteArray, pcmEncoding: Int): ShortArray {
        return when (pcmEncoding) {
            PCM_ENCODING_8BIT -> {
                ShortArray(bytes.size) { i ->
                    (((bytes[i].toInt() and 0xFF) - 128) shl 8).toShort()
                }
            }
            PCM_ENCODING_24BIT_PACKED -> {
                val sampleCount = bytes.size / 3
                ShortArray(sampleCount) { i ->
                    val base = i * 3
                    val b0 = bytes[base].toInt() and 0xFF
                    val b1 = bytes[base + 1].toInt() and 0xFF
                    val b2 = bytes[base + 2].toInt() // signed MSB
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    (sample24 shr 8).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            PCM_ENCODING_32BIT -> {
                val sampleCount = bytes.size / 4
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                ShortArray(sampleCount) {
                    val v32 = bb.getInt()
                    (v32 shr 16).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            PCM_ENCODING_FLOAT -> {
                val sampleCount = bytes.size / 4
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                ShortArray(sampleCount) {
                    val f = bb.getFloat().let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
                    (f * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            else -> {
                // Default PCM 16-bit little-endian
                val sampleCount = bytes.size / 2
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                ShortArray(sampleCount) { bb.getShort() }
            }
        }
    }

    fun normalizeChannels(samples: ShortArray, srcChannels: Int): Pair<ShortArray, Int> {
        if (srcChannels <= 1) return samples to 1
        if (srcChannels == 2) return samples to 2
        // Multi-channel (>2ch surround FLAC/WAV) -> downmix cleanly to stereo
        val frameCount = samples.size / srcChannels
        val out = ShortArray(frameCount * 2)
        for (f in 0 until frameCount) {
            val base = f * srcChannels
            var leftSum = samples[base].toInt()
            var rightSum = samples[base + 1].toInt()
            if (srcChannels >= 3) {
                val center = samples[base + 2].toInt() / 2
                leftSum += center
                rightSum += center
            }
            for (ch in 3 until srcChannels) {
                val extra = samples[base + ch].toInt() / 2
                if (ch % 2 == 1) leftSum += extra else rightSum += extra
            }
            val divisor = if (srcChannels >= 3) 2 else 1
            out[f * 2] = (leftSum / divisor).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            out[f * 2 + 1] = (rightSum / divisor).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out to 2
    }

    fun linearResample(
        samples: ShortArray,
        channels: Int,
        srcSampleRate: Int,
        dstSampleRate: Int
    ): ShortArray {
        if (srcSampleRate <= 0 || dstSampleRate <= 0 || srcSampleRate == dstSampleRate || samples.isEmpty()) {
            return samples
        }
        val safeChannels = channels.coerceAtLeast(1)
        val srcFrames = samples.size / safeChannels
        if (srcFrames <= 1) return samples
        val dstFrames = ((srcFrames.toLong() * dstSampleRate.toLong()) / srcSampleRate.toLong())
            .toInt()
            .coerceAtLeast(1)
        val out = ShortArray(dstFrames * safeChannels)
        val ratio = srcSampleRate.toDouble() / dstSampleRate.toDouble()

        for (df in 0 until dstFrames) {
            val srcPos = df * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceAtMost(srcFrames - 1)
            val frac = (srcPos - idx0).toFloat()
            val outBase = df * safeChannels
            val inBase0 = idx0 * safeChannels
            val inBase1 = idx1 * safeChannels
            for (ch in 0 until safeChannels) {
                val s0 = samples[inBase0 + ch].toFloat()
                val s1 = samples[inBase1 + ch].toFloat()
                val interp = s0 + (s1 - s0) * frac
                out[outBase + ch] = interp.roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return out
    }

    fun applyFadeInOut(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
        fadeInMs: Int = 300,
        fadeOutMs: Int = 500
    ) {
        val safeChannels = channels.coerceAtLeast(1)
        val totalFrames = samples.size / safeChannels
        if (totalFrames <= 0 || sampleRate <= 0) return

        val fadeInFrames = ((sampleRate.toLong() * fadeInMs.toLong()) / 1000L)
            .toInt()
            .coerceIn(0, totalFrames / 2)
        val fadeOutFrames = ((sampleRate.toLong() * fadeOutMs.toLong()) / 1000L)
            .toInt()
            .coerceIn(0, totalFrames - fadeInFrames)

        for (f in 0 until fadeInFrames) {
            val gain = if (fadeInFrames <= 1) 1f else f.toFloat() / fadeInFrames.toFloat()
            val base = f * safeChannels
            for (ch in 0 until safeChannels) {
                samples[base + ch] = (samples[base + ch] * gain).roundToInt().toShort()
            }
        }

        val fadeOutStart = totalFrames - fadeOutFrames
        for (f in fadeOutStart until totalFrames) {
            val remaining = (totalFrames - 1 - f).coerceAtLeast(0)
            val gain = if (fadeOutFrames <= 1) 0f else remaining.toFloat() / fadeOutFrames.toFloat()
            val base = f * safeChannels
            for (ch in 0 until safeChannels) {
                samples[base + ch] = (samples[base + ch] * gain).roundToInt().toShort()
            }
        }
    }
}

/**
 * Shared 9:16 card renderer used for both static PNG card generation,
 * H.264 video frame synthesis, and fast API-24+ compatible blurred artwork backgrounds.
 */
object LyricsShareCardRenderer {
    const val VIDEO_WIDTH = 720
    const val VIDEO_HEIGHT = 1280
    const val PNG_WIDTH = 1080
    const val PNG_HEIGHT = 1920

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun loadArtworkBitmap(
        context: Context,
        track: AudioTrack,
        maxSizePx: Int = 640
    ): Bitmap? {
        if (track.contentUri.isNotBlank() && !track.contentUri.startsWith("http", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(track.contentUri) }.getOrNull()
            if (uri != null) {
                val retriever = MediaMetadataRetriever()
                val embeddedBytes = try {
                    retriever.setDataSource(context, uri)
                    retriever.embeddedPicture
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
                if (embeddedBytes != null) {
                    decodeSampledArgb(embeddedBytes, maxSizePx)?.let { return it }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        context.contentResolver.loadThumbnail(uri, Size(maxSizePx, maxSizePx), null)
                    }.getOrNull()?.let { return it }
                }
            }
        }

        val artStr = track.albumArtUri?.takeIf { it.isNotBlank() } ?: return null
        if (artStr.startsWith("http://", ignoreCase = true) || artStr.startsWith("https://", ignoreCase = true)) {
            val remoteBytes = runCatching {
                val req = Request.Builder().url(artStr).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            }.getOrNull()
            if (remoteBytes != null) {
                decodeSampledArgb(remoteBytes, maxSizePx)?.let { return it }
            }
        } else {
            val artUri = runCatching { Uri.parse(artStr) }.getOrNull()
            if (artUri != null) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(artUri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes != null) {
                    decodeSampledArgb(bytes, maxSizePx)?.let { return it }
                }
            }
        }
        return null
    }

    private fun decodeSampledArgb(bytes: ByteArray, maxSizePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > maxSizePx * 2 ||
                (bounds.outHeight / sampleSize) > maxSizePx * 2
            ) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates a soft blurred background bitmap compatible from Android 7 (API 24) to Android 16.
     */
    fun createBlurredBackgroundBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val smallW = 54
        val smallH = ((targetHeight.toFloat() / targetWidth.toFloat()) * smallW)
            .roundToInt()
            .coerceIn(48, 128)
        val tiny = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val blurredSmall = fastBoxBlur(tiny, radius = 6, passes = 2)
        if (blurredSmall !== tiny && !tiny.isRecycled) {
            tiny.recycle()
        }
        val full = Bitmap.createScaledBitmap(blurredSmall, targetWidth, targetHeight, true)
        if (full !== blurredSmall && !blurredSmall.isRecycled) {
            blurredSmall.recycle()
        }
        return full
    }

    private fun fastBoxBlur(source: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 1 || h <= 1 || radius <= 0) return source
        var current = IntArray(w * h)
        var next = IntArray(w * h)
        source.getPixels(current, 0, w, 0, 0, w, h)
        val div = radius * 2 + 1

        repeat(passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    var r = 0
                    var g = 0
                    var b = 0
                    for (k in -radius..radius) {
                        val sx = (x + k).coerceIn(0, w - 1)
                        val c = current[rowOffset + sx]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                    }
                    next[rowOffset + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var r = 0
                    var g = 0
                    var b = 0
                    for (k in -radius..radius) {
                        val sy = (y + k).coerceIn(0, h - 1)
                        val c = next[sy * w + x]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                    }
                    current[y * w + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                }
            }
        }
        return Bitmap.createBitmap(current, w, h, Bitmap.Config.ARGB_8888)
    }

    fun drawShareCardFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        spec: LyricsShareClipSpec,
        artworkBitmap: Bitmap?,
        blurredBgBitmap: Bitmap?,
        frameElapsedMs: Long,
        isStaticImage: Boolean = false
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = w / 720f

        val copperAmberInt = 0xFFD98A3A.toInt()
        val accentInt = when (spec.style) {
            ShareCardStyle.COPPER_AMBER_DARK -> copperAmberInt
            ShareCardStyle.DYNAMIC_ARTWORK, ShareCardStyle.BLURRED_ARTWORK -> spec.dynamicAccentColorInt
        }

        // 1. Background fill
        when (spec.style) {
            ShareCardStyle.DYNAMIC_ARTWORK -> {
                val topBg = ColorUtils.blendARGB(0xFF0B0D12.toInt(), accentInt, 0.48f)
                val midBg = ColorUtils.blendARGB(0xFF0B0D12.toInt(), accentInt, 0.24f)
                val botBg = 0xFF080A0E.toInt()
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, h,
                        intArrayOf(topBg, midBg, botBg),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, bgPaint)
            }
            ShareCardStyle.COPPER_AMBER_DARK -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, h,
                        intArrayOf(0xFF171310.toInt(), 0xFF0E0F14.toInt(), 0xFF08090C.toInt()),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, bgPaint)
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        w * 0.5f, h * 0.25f, w * 0.72f,
                        intArrayOf(ColorUtils.setAlphaComponent(copperAmberInt, 62), 0x00000000),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, glowPaint)
            }
            ShareCardStyle.BLURRED_ARTWORK -> {
                if (blurredBgBitmap != null && !blurredBgBitmap.isRecycled) {
                    val srcRect = Rect(0, 0, blurredBgBitmap.width, blurredBgBitmap.height)
                    val dstRect = RectF(0f, 0f, w, h)
                    val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(blurredBgBitmap, srcRect, dstRect, bmpPaint)
                    val scrimPaint = Paint().apply {
                        shader = LinearGradient(
                            0f, 0f, 0f, h,
                            intArrayOf(0x88080A0F.toInt(), 0xA4080A0F.toInt(), 0xCC06070B.toInt()),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, w, h, scrimPaint)
                } else {
                    val fallbackBg = ColorUtils.blendARGB(0xFF0B0D12.toInt(), accentInt, 0.30f)
                    canvas.drawColor(fallbackBg)
                }
            }
        }

        // 2. Main Glass/Elevated Card Container
        val cardMarginX = 48f * scale
        val cardTop = 124f * scale
        val cardBottom = h - 124f * scale
        val cardRect = RectF(cardMarginX, cardTop, w - cardMarginX, cardBottom)
        val cardCorner = 36f * scale

        val cardBgColor = when (spec.style) {
            ShareCardStyle.DYNAMIC_ARTWORK -> ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(0xFF10131C.toInt(), accentInt, 0.20f),
                215
            )
            ShareCardStyle.COPPER_AMBER_DARK -> 0xE614151B.toInt()
            ShareCardStyle.BLURRED_ARTWORK -> 0xBA0F1219.toInt()
        }
        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardFillPaint)

        val borderColor = ColorUtils.setAlphaComponent(accentInt, if (spec.style == ShareCardStyle.COPPER_AMBER_DARK) 115 else 92)
        val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * scale
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardStrokePaint)

        val innerPad = 34f * scale
        val contentLeft = cardRect.left + innerPad
        val contentRight = cardRect.right - innerPad
        val contentWidth = (contentRight - contentLeft).toInt().coerceAtLeast(100)

        // 3. Header: Artwork + Title + Artist + Format Badge
        val artSize = 112f * scale
        val artTop = cardRect.top + innerPad
        val artRect = RectF(contentLeft, artTop, contentLeft + artSize, artTop + artSize)
        drawRoundedArtwork(canvas, artworkBitmap, artRect, 20f * scale, accentInt, scale)

        val textStart = artRect.right + 22f * scale
        val headerTextWidth = (contentRight - textStart).toInt().coerceAtLeast(80)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 31f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = buildBoundedStaticLayout(
            text = spec.track.title.ifBlank { "Sin título" },
            paint = titlePaint,
            width = headerTextWidth,
            maxLines = 2
        )

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xC8FFFFFF.toInt()
            textSize = 23f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val artistLayout = buildBoundedStaticLayout(
            text = spec.track.artist.ifBlank { "Artista desconocido" },
            paint = artistPaint,
            width = headerTextWidth,
            maxLines = 1
        )

        val totalHeaderTextHeight = titleLayout.height + 6f * scale + artistLayout.height + 28f * scale
        var textY = artRect.centerY() - totalHeaderTextHeight / 2f
        if (textY < artRect.top - 4f * scale) textY = artRect.top - 4f * scale

        canvas.save()
        canvas.translate(textStart, textY)
        titleLayout.draw(canvas)
        canvas.restore()

        val artistY = textY + titleLayout.height + 6f * scale
        canvas.save()
        canvas.translate(textStart, artistY)
        artistLayout.draw(canvas)
        canvas.restore()

        // Audio format badge pill
        val isLossless = spec.track.format == AudioFormat.FLAC || spec.track.format == AudioFormat.WAV
        val badgeText = if (isLossless) "${spec.track.format.label} LOSSLESS" else spec.track.format.label
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.blendARGB(accentInt, 0xFFFFFFFF.toInt(), 0.35f)
            textSize = 15.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeW = badgeTextPaint.measureText(badgeText) + 20f * scale
        val badgeH = 26f * scale
        val badgeTop = artistY + artistLayout.height + 8f * scale
        val badgeRect = RectF(textStart, badgeTop, textStart + badgeW, badgeTop + badgeH)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(accentInt, 48)
        }
        canvas.drawRoundRect(badgeRect, 13f * scale, 13f * scale, badgeBgPaint)
        canvas.drawText(
            badgeText,
            badgeRect.left + 10f * scale,
            badgeRect.centerY() + badgeTextPaint.textSize * 0.34f,
            badgeTextPaint
        )

        // Subtle divider line below header
        val headerBottom = maxOf(artRect.bottom, badgeRect.bottom) + 26f * scale
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x24FFFFFF.toInt()
            strokeWidth = 1.2f * scale
        }
        canvas.drawLine(contentLeft, headerBottom, contentRight, headerBottom, dividerPaint)

        // Footer zone: progress bar + Davoo Player watermark
        val footerAreaHeight = 96f * scale
        val bodyTop = headerBottom + 24f * scale
        val bodyBottom = cardRect.bottom - innerPad - footerAreaHeight
        val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(120f * scale)

        // 4. Body: Synced Verses OR Unsynced Cover + Spectrum Visualizer
        if (spec.isSyncedLyrics && spec.verses.isNotEmpty()) {
            drawSyncedVersesBody(
                canvas = canvas,
                contentLeft = contentLeft,
                contentWidth = contentWidth,
                bodyTop = bodyTop,
                bodyHeight = bodyHeight,
                spec = spec,
                accentInt = accentInt,
                absoluteTimeMs = spec.startMs + frameElapsedMs,
                isStaticImage = isStaticImage,
                scale = scale
            )
        } else {
            drawUnsyncedVisualizerBody(
                canvas = canvas,
                cardCenterX = cardRect.centerX(),
                contentLeft = contentLeft,
                contentWidth = contentWidth,
                bodyTop = bodyTop,
                bodyHeight = bodyHeight,
                spec = spec,
                artworkBitmap = artworkBitmap,
                accentInt = accentInt,
                frameElapsedMs = frameElapsedMs,
                scale = scale
            )
        }

        // 5. Progress bar & Davoo Player watermark
        val progressY = cardRect.bottom - innerPad - 60f * scale
        val progressFraction = if (isStaticImage) {
            0.55f
        } else {
            (frameElapsedMs.toFloat() / spec.clipDurationMs.toFloat()).coerceIn(0f, 1f)
        }

        val trackBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val barHeight = 5f * scale
        val barRect = RectF(contentLeft, progressY, contentRight, progressY + barHeight)
        canvas.drawRoundRect(barRect, barHeight / 2f, barHeight / 2f, trackBarPaint)

        val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.blendARGB(accentInt, 0xFFFFFFFF.toInt(), 0.25f)
            style = Paint.Style.FILL
        }
        val fillRight = contentLeft + (contentRight - contentLeft) * progressFraction
        if (fillRight > contentLeft) {
            val fillRect = RectF(contentLeft, progressY, fillRight, progressY + barHeight)
            canvas.drawRoundRect(fillRect, barHeight / 2f, barHeight / 2f, activeBarPaint)
        }

        // Watermark row
        val footerY = cardRect.bottom - innerPad - 12f * scale
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val timeLabel = if (isStaticImage) {
            if (spec.isSyncedLyrics) {
                "${formatShareTime(spec.startMs)} · ${formatShareTime(spec.endMs)}"
            } else {
                "${formatShareTime(spec.startMs)} · ${formatShareTime(spec.track.durationMs)}"
            }
        } else {
            "${formatShareTime(frameElapsedMs)} / ${formatShareTime(spec.clipDurationMs)}"
        }
        canvas.drawText(timeLabel, contentLeft, footerY, timePaint)

        val brandText = "Davoo Player"
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val brandWidth = brandPaint.measureText(brandText)
        val dotRadius = 6.5f * scale
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.blendARGB(accentInt, 0xFFFFFFFF.toInt(), 0.20f)
        }
        canvas.drawCircle(contentRight - brandWidth - 14f * scale, footerY - 6.5f * scale, dotRadius, dotPaint)
        canvas.drawText(brandText, contentRight - brandWidth, footerY, brandPaint)
    }

    private fun drawSyncedVersesBody(
        canvas: Canvas,
        contentLeft: Float,
        contentWidth: Int,
        bodyTop: Float,
        bodyHeight: Float,
        spec: LyricsShareClipSpec,
        accentInt: Int,
        absoluteTimeMs: Long,
        isStaticImage: Boolean,
        scale: Float
    ) {
        val verses = spec.verses
        val count = verses.size.coerceAtLeast(1)
        val hasTrans = spec.includeTranslation && verses.any { !it.secondaryText.isNullOrBlank() }

        // Adaptive font size so 1-6 lines (with optional translation) always fit comfortably
        var primarySizeSp = when {
            count <= 2 && !hasTrans -> 38f
            count <= 3 && !hasTrans -> 33f
            count <= 4 && !hasTrans -> 29f
            count <= 5 && !hasTrans -> 26f
            hasTrans && count <= 3 -> 27f
            else -> 22.5f
        } * scale

        var verseGap = (if (count <= 3) 22f else 15f) * scale
        var primaryLayouts: List<StaticLayout>
        var secondaryLayouts: List<StaticLayout?>
        var totalHeight: Float

        // Auto-shrink if wrapped lines exceed available card height
        var attempt = 0
        while (true) {
            val secSizeSp = (primarySizeSp * 0.72f).coerceAtLeast(15f * scale)
            val pList = ArrayList<StaticLayout>(verses.size)
            val sList = ArrayList<StaticLayout?>(verses.size)
            var hAccum = 0f

            for (idx in verses.indices) {
                val verse = verses[idx]
                val isCurrent = isStaticImage || (absoluteTimeMs >= verse.startMs && absoluteTimeMs < verse.endMs)
                val isPast = !isStaticImage && absoluteTimeMs >= verse.endMs
                val textColor = when {
                    isCurrent -> 0xFFFFFFFF.toInt()
                    isPast -> 0xB8FFFFFF.toInt()
                    else -> 0x73FFFFFF.toInt()
                }
                val pPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = primarySizeSp
                    typeface = Typeface.create(
                        Typeface.DEFAULT,
                        if (isCurrent) Typeface.BOLD else Typeface.NORMAL
                    )
                }
                val pLayout = buildBoundedStaticLayout(verse.text, pPaint, contentWidth, maxLines = 3)
                pList += pLayout
                hAccum += pLayout.height

                val sec = if (hasTrans) verse.secondaryText?.takeIf { it.isNotBlank() } else null
                if (sec != null) {
                    val secBase = ColorUtils.blendARGB(accentInt, 0xFFFFFFFF.toInt(), 0.55f)
                    val secColor = ColorUtils.setAlphaComponent(
                        secBase,
                        when {
                            isCurrent -> 240
                            isPast -> 170
                            else -> 115
                        }
                    )
                    val sPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = secColor
                        textSize = secSizeSp
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    }
                    val sLayout = buildBoundedStaticLayout(sec, sPaint, contentWidth, maxLines = 2)
                    sList += sLayout
                    hAccum += 4f * scale + sLayout.height
                } else {
                    sList += null
                }

                if (idx < verses.lastIndex) {
                    hAccum += verseGap
                }
            }

            primaryLayouts = pList
            secondaryLayouts = sList
            totalHeight = hAccum
            if (totalHeight <= bodyHeight || attempt >= 5) {
                break
            }
            primarySizeSp *= 0.88f
            verseGap *= 0.85f
            attempt++
        }

        var cursorY = bodyTop + ((bodyHeight - totalHeight) / 2f).coerceAtLeast(0f)
        for (idx in verses.indices) {
            val verse = verses[idx]
            val pLayout = primaryLayouts[idx]
            val sLayout = secondaryLayouts[idx]
            val isActive = !isStaticImage && absoluteTimeMs >= verse.startMs && absoluteTimeMs < verse.endMs

            if (isActive) {
                val barX = contentLeft - 14f * scale
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.blendARGB(accentInt, 0xFFFFFFFF.toInt(), 0.25f)
                }
                val activeBlockH = pLayout.height + (sLayout?.let { it.height + 4f * scale } ?: 0f)
                canvas.drawRoundRect(
                    RectF(barX, cursorY + 4f * scale, barX + 5f * scale, cursorY + activeBlockH - 4f * scale),
                    3f * scale,
                    3f * scale,
                    barPaint
                )
            }

            canvas.save()
            canvas.translate(contentLeft, cursorY)
            pLayout.draw(canvas)
            canvas.restore()
            cursorY += pLayout.height

            if (sLayout != null) {
                cursorY += 4f * scale
                canvas.save()
                canvas.translate(contentLeft, cursorY)
                sLayout.draw(canvas)
                canvas.restore()
                cursorY += sLayout.height
            }

            cursorY += verseGap
        }
    }

    private fun drawUnsyncedVisualizerBody(
        canvas: Canvas,
        cardCenterX: Float,
        contentLeft: Float,
        contentWidth: Int,
        bodyTop: Float,
        bodyHeight: Float,
        spec: LyricsShareClipSpec,
        artworkBitmap: Bitmap?,
        accentInt: Int,
        frameElapsedMs: Long,
        scale: Float
    ) {
        val excerpt = spec.plainExcerpt?.trim()?.takeIf { it.isNotEmpty() }
        val heroArtSize = (if (excerpt != null) 210f else 270f) * scale
        val visualizerH = 96f * scale
        val gap = 28f * scale

        val excerptLayout = excerpt?.let {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xEAFFFFFF.toInt()
                textSize = 23f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            buildBoundedStaticLayout(it, p, contentWidth, maxLines = 4, alignment = Layout.Alignment.ALIGN_CENTER)
        }

        val totalBlockH = heroArtSize + gap + visualizerH + (excerptLayout?.let { gap + it.height } ?: 0f)
        var cursorY = bodyTop + ((bodyHeight - totalBlockH) / 2f).coerceAtLeast(0f)

        // Centered album artwork spotlight
        val artRect = RectF(
            cardCenterX - heroArtSize / 2f,
            cursorY,
            cardCenterX + heroArtSize / 2f,
            cursorY + heroArtSize
        )
        drawRoundedArtwork(canvas, artworkBitmap, artRect, 26f * scale, accentInt, scale)
        cursorY += heroArtSize + gap

        // Animated spectrum visualizer (22 symmetric bars)
        val barCount = 22
        val totalBarSpan = contentWidth * 0.86f
        val barSpacing = 6f * scale
        val barW = ((totalBarSpan - barSpacing * (barCount - 1)) / barCount).coerceAtLeast(4f * scale)
        val startX = cardCenterX - (barCount * barW + (barCount - 1) * barSpacing) / 2f
        val timeSec = frameElapsedMs / 1000.0
        val centerIdx = (barCount - 1) / 2.0

        val activeBarColor = ColorUtils.blendARGB(accentInt, 0xFFFFFFFF.toInt(), 0.30f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        for (i in 0 until barCount) {
            val distFromCenter = (abs(i - centerIdx) / centerIdx).toFloat()
            val envelope = (1f - distFromCenter * 0.52f).coerceIn(0.35f, 1f)
            val wave1 = sin(timeSec * 5.2 + i * 0.55)
            val wave2 = cos(timeSec * 3.4 - i * 0.82)
            val level = (0.48 + 0.32 * wave1 + 0.20 * wave2).toFloat().coerceIn(0.18f, 1f)
            val barH = (visualizerH * level * envelope).coerceAtLeast(10f * scale)
            val left = startX + i * (barW + barSpacing)
            val top = cursorY + (visualizerH - barH) / 2f
            barPaint.color = ColorUtils.setAlphaComponent(activeBarColor, (175 + (level * 80).toInt()).coerceIn(140, 255))
            canvas.drawRoundRect(
                RectF(left, top, left + barW, top + barH),
                barW / 2f,
                barW / 2f,
                barPaint
            )
        }
        cursorY += visualizerH

        if (excerptLayout != null) {
            cursorY += gap
            canvas.save()
            canvas.translate(contentLeft, cursorY)
            excerptLayout.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawRoundedArtwork(
        canvas: Canvas,
        artworkBitmap: Bitmap?,
        rect: RectF,
        cornerRadius: Float,
        accentInt: Int,
        scale: Float
    ) {
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        if (artworkBitmap != null && !artworkBitmap.isRecycled) {
            // Center-crop scaling into rect
            val bmpW = artworkBitmap.width.toFloat()
            val bmpH = artworkBitmap.height.toFloat()
            val targetRatio = rect.width() / rect.height()
            val bmpRatio = bmpW / bmpH
            val srcRect = if (bmpRatio > targetRatio) {
                val cropW = bmpH * targetRatio
                val left = ((bmpW - cropW) / 2f).toInt()
                Rect(left, 0, (left + cropW).toInt(), artworkBitmap.height)
            } else {
                val cropH = bmpW / targetRatio
                val top = ((bmpH - cropH) / 2f).toInt()
                Rect(0, top, artworkBitmap.width, (top + cropH).toInt())
            }
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(artworkBitmap, srcRect, rect, bmpPaint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(
                        ColorUtils.blendARGB(0xFF1D2230.toInt(), accentInt, 0.45f),
                        ColorUtils.blendARGB(0xFF11141C.toInt(), accentInt, 0.18f)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, fallbackPaint)
            // Subtle vinyl disc icon in center
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55FFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.28f, ringPaint)
            val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(accentInt, 210)
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.08f, centerPaint)
        }
        canvas.restore()
    }

    private fun buildBoundedStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.12f)
            .build()
    }

    fun formatShareTime(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format("%d:%02d", mins, secs)
    }
}
