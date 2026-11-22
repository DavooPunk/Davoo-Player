package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Pure-Kotlin artwork color extractor that computes a rich ambient accent color from
 * embedded MP3/FLAC artwork or MediaStore album art for dynamic Now Playing gradients.
 */
object ArtworkColorExtractor {

    private val colorCache = LruCache<String, Color>(32)
    private val extractionSemaphore = Semaphore(permits = 2)

    fun trimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> colorCache.evictAll()
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> colorCache.trimToSize(16)
        }
    }

    suspend fun extractAccentColor(
        context: Context,
        audioUri: String,
        albumArtUri: String?,
        fallbackColor: Color
    ): Color {
        val cacheKey = "$audioUri|$albumArtUri"
        colorCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            colorCache.get(cacheKey)?.let { return@withContext it }
            extractionSemaphore.withPermit {
                ensureActive()
                colorCache.get(cacheKey)?.let { return@withPermit it }
                val bitmap = decodeSmallArtwork(context, audioUri, albumArtUri) ?: return@withPermit fallbackColor
                val extracted = try {
                    ensureActive()
                    computeDominantVibrantColor(bitmap, fallbackColor)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                colorCache.put(cacheKey, extracted)
                extracted
            }
        }
    }

    private const val MAX_IMAGE_STREAM_BYTES = 6 * 1024 * 1024
    private const val TARGET_COLOR_SAMPLE_PX = 64

    private fun decodeSmallArtwork(context: Context, audioUri: String, albumArtUri: String?): Bitmap? {
        if (audioUri.isNotBlank() && !audioUri.startsWith("http", ignoreCase = true)) {
            val retriever = MediaMetadataRetriever()
            val bytes = try {
                retriever.setDataSource(context, Uri.parse(audioUri))
                retriever.embeddedPicture
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
            if (bytes != null) {
                decodeSampledRgb565(bytes)?.let { return it }
            }
        }

        if (!albumArtUri.isNullOrBlank() && !albumArtUri.startsWith("http", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(albumArtUri) }.getOrNull()
            if (uri != null && !isLikelyAudioUri(context, uri)) {
                val streamBytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        readUpToBytes(stream, MAX_IMAGE_STREAM_BYTES)
                    }
                }.getOrNull()
                if (streamBytes != null) {
                    decodeSampledRgb565(streamBytes)?.let { return it }
                }
            }
        }
        return null
    }

    private fun isLikelyAudioUri(context: Context, uri: Uri): Boolean {
        val str = uri.toString().lowercase()
        if (str.contains("/audio/media/")) return true
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase()
        return mime?.startsWith("audio/") == true
    }

    private fun readUpToBytes(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return if (out.size() > 0) out.toByteArray() else null
    }

    private fun decodeSampledRgb565(bytes: ByteArray): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > TARGET_COLOR_SAMPLE_PX * 2 ||
                (bounds.outHeight / sampleSize) > TARGET_COLOR_SAMPLE_PX * 2
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun computeDominantVibrantColor(bitmap: Bitmap, fallback: Color): Color {
        val stepX = (bitmap.width / 16).coerceAtLeast(1)
        val stepY = (bitmap.height / 16).coerceAtLeast(1)
        var bestScore = 0f
        var bestR = 0
        var bestG = 0
        var bestB = 0

        val hsv = FloatArray(3)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                // Prefer saturated, non-black, non-blown-out tones for dark obsidian backdrop
                if (value in 0.25f..0.95f && sat > 0.22f) {
                    val score = sat * 1.6f + (1f - kotlin.math.abs(value - 0.65f))
                    if (score > bestScore) {
                        bestScore = score
                        bestR = r
                        bestG = g
                        bestB = b
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (bestScore <= 0f) return fallback
        // Boost brightness gently so dark themes stay legible and atmospheric
        android.graphics.Color.RGBToHSV(bestR, bestG, bestB, hsv)
        hsv[1] = hsv[1].coerceIn(0.42f, 0.88f)
        hsv[2] = hsv[2].coerceIn(0.68f, 0.96f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
