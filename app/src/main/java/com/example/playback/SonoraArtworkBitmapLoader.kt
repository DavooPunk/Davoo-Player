package com.example.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.example.R
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable

/**
 * Custom Media3 [BitmapLoader] used by [PlaybackService] so Android media notifications
 * and the Android Quick Settings / Lock Screen media control panel always display embedded
 * ID3 / FLAC artwork (or MediaStore / default artwork) cleanly.
 */
@OptIn(UnstableApi::class)
class SonoraArtworkBitmapLoader(
    private val context: Context
) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get()
    private val delegate = DataSourceBitmapLoader(appContext)

    override fun supportsMimeType(mimeType: String): Boolean {
        return delegate.supportsMimeType(mimeType) || mimeType.startsWith("audio/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executor.submit(Callable {
            decodeSampledBitmap(data, MAX_ARTWORK_PX, MAX_ARTWORK_PX)
                ?: decodeFallbackArtwork()
        })
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return executor.submit(Callable {
            loadArtworkSynchronously(uri)
                ?: decodeFallbackArtwork()
        })
    }

    private fun loadArtworkSynchronously(uri: Uri): Bitmap? {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return runCatching { delegate.loadBitmap(uri).get() }.getOrNull()
        }

        // 1. Try extracting embedded artwork directly from the audio file (MP3 ID3 APIC / FLAC picture)
        val embedded = extractEmbeddedArtBytes(uri)
        if (embedded != null) {
            decodeSampledBitmap(embedded, MAX_ARTWORK_PX, MAX_ARTWORK_PX)?.let { return it }
        }

        // 2. Only read stream bytes if this URI actually represents an image (NEVER readBytes() on a 50MB FLAC/WAV audio file!)
        val mime = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty().lowercase()
        val isAudioOrVideoUri = mime.startsWith("audio/") ||
            mime.startsWith("video/") ||
            uri.toString().contains("/audio/media/")
        if (!isAudioOrVideoUri) {
            val streamBytes = readBoundedStream(uri, MAX_IMAGE_STREAM_BYTES)
            if (streamBytes != null) {
                decodeSampledBitmap(streamBytes, MAX_ARTWORK_PX, MAX_ARTWORK_PX)?.let { return it }
            }
        }

        // 3. Try system content resolver thumbnail on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumb = runCatching {
                appContext.contentResolver.loadThumbnail(uri, Size(MAX_ARTWORK_PX, MAX_ARTWORK_PX), null)
            }.getOrNull()
            if (thumb != null) return thumb
        }

        // 4. Delegate to Media3 DataSourceBitmapLoader only for non-audio schemes
        if (!isAudioOrVideoUri) {
            return runCatching { delegate.loadBitmap(uri).get() }.getOrNull()
        }
        return null
    }

    private fun readBoundedStream(uri: Uri, maxBytes: Int): ByteArray? {
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return@use null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun extractEmbeddedArtBytes(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.embeddedPicture
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeFallbackArtwork(): Bitmap {
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(appContext.resources, R.drawable.img_default_album_art, opts)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    opts.outWidth.coerceAtLeast(1),
                    opts.outHeight.coerceAtLeast(1),
                    MAX_ARTWORK_PX,
                    MAX_ARTWORK_PX
                )
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeResource(appContext.resources, R.drawable.img_default_album_art, decodeOpts)
        }.getOrNull() ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        }.getOrNull()
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    companion object {
        private const val MAX_ARTWORK_PX = 384
        private const val MAX_IMAGE_STREAM_BYTES = 6 * 1024 * 1024
    }
}
