package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options
import coil.size.pxOrElse
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Model passed to Coil's [ImageLoader] to extract embedded album art directly from an
 * audio file URI (MP3 ID3 APIC / FLAC METADATA_BLOCK_PICTURE), falling back to a
 * MediaStore album art URI if present.
 */
data class EmbeddedAudioArtRequest(
    val audioUri: String,
    val fallbackAlbumArtUri: String? = null
)

class EmbeddedAudioArtKeyer : Keyer<EmbeddedAudioArtRequest> {
    override fun key(data: EmbeddedAudioArtRequest, options: Options): String {
        return "audio_art:${data.audioUri}:${options.size.width}:${options.size.height}"
    }
}

class EmbeddedAudioArtFetcher(
    private val data: EmbeddedAudioArtRequest,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val context = options.context
        val targetWidth = options.size.width.pxOrElse { DEFAULT_THUMBNAIL_PX }.coerceIn(32, 1024)
        val targetHeight = options.size.height.pxOrElse { DEFAULT_THUMBNAIL_PX }.coerceIn(32, 1024)

        if (data.audioUri.isNotBlank()) {
            val audioUri = runCatching { Uri.parse(data.audioUri) }.getOrNull()
            if (audioUri != null) {
                // 1. Extract embedded picture directly from the MP3/FLAC file
                currentCoroutineContext().ensureActive()
                val embeddedBytes = extractEmbeddedArtBytes(context, audioUri)
                if (embeddedBytes != null) {
                    currentCoroutineContext().ensureActive()
                    val bitmap = decodeSampledBitmap(embeddedBytes, targetWidth, targetHeight)
                    if (bitmap != null) {
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, bitmap),
                            isSampled = true,
                            dataSource = DataSource.DISK
                        )
                    }
                }

                // 2. Fallback: system content resolver thumbnail (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val sysThumb = runCatching {
                        context.contentResolver.loadThumbnail(
                            audioUri,
                            Size(targetWidth, targetHeight),
                            null
                        )
                    }.getOrNull()
                    if (sysThumb != null) {
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, sysThumb),
                            isSampled = true,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            }
        }

        // 3. Fallback: MediaStore albumArtUri or custom picked image stream if available
        val fallbackStr = data.fallbackAlbumArtUri?.takeIf { it.isNotBlank() }
        val fallbackUri = fallbackStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (fallbackUri != null && !isLikelyAudioUri(context, fallbackUri)) {
            val fallbackBytes = runCatching {
                context.contentResolver.openInputStream(fallbackUri)?.use { stream ->
                    readUpToBytes(stream, MAX_IMAGE_STREAM_BYTES)
                }
            }.getOrNull()
            if (fallbackBytes != null) {
                val bitmap = decodeSampledBitmap(fallbackBytes, targetWidth, targetHeight)
                if (bitmap != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }

        null
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

    private fun extractEmbeddedArtBytes(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampledBitmap(
        bytes: ByteArray,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
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

    class Factory : Fetcher.Factory<EmbeddedAudioArtRequest> {
        override fun create(
            data: EmbeddedAudioArtRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = EmbeddedAudioArtFetcher(data, options)
    }

    companion object {
        private const val DEFAULT_THUMBNAIL_PX = 256
        private const val MAX_IMAGE_STREAM_BYTES = 6 * 1024 * 1024
    }
}

object AudioArtImageLoaderProvider {
    @Volatile
    private var instance: ImageLoader? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            val appContext = context.applicationContext
            instance ?: ImageLoader.Builder(appContext)
                .memoryCache {
                    coil.memory.MemoryCache.Builder(appContext)
                        .maxSizePercent(0.10)
                        .build()
                }
                .fetcherDispatcher(Dispatchers.IO.limitedParallelism(3))
                .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
                .diskCache(null)
                .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                .networkCachePolicy(coil.request.CachePolicy.DISABLED)
                .components {
                    add(EmbeddedAudioArtKeyer())
                    add(EmbeddedAudioArtFetcher.Factory())
                }
                .crossfade(false)
                .build()
                .also { instance = it }
        }
    }

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun trimMemory(level: Int) {
        val cache = instance?.memoryCache ?: return
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            cache.clear()
        } else {
            cache.trimMemory(level)
        }
    }

    /**
     * Evicts cached thumbnails for removed audio URIs asynchronously on a background thread
     * so deleting large folders never stalls the UI thread.
     */
    fun evictUrisAsync(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val cache = instance?.memoryCache ?: return
        val uriSet = uris.toHashSet()
        cleanupScope.launch {
            val keysToRemove = cache.keys.filter { key ->
                val raw = key.key
                if (!raw.startsWith("audio_art:")) return@filter false
                // Key format: "audio_art:<audioUri>:<w>:<h>"
                uriSet.any { uri -> raw.startsWith("audio_art:$uri:") }
            }
            keysToRemove.forEach { key -> cache.remove(key) }
        }
    }
}

/**
 * Uses Coil with [EmbeddedAudioArtFetcher] to extract, downsample, cache, and display
 * the embedded album art thumbnail for an audio file, falling back to the default artwork.
 */
@Composable
fun AudioAlbumArtThumbnail(
    audioUri: String,
    albumArtUri: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    thumbnailSizePx: Int = 144,
    crossfade: Boolean = false
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { AudioArtImageLoaderProvider.get(context) }
    val request = remember(audioUri, albumArtUri, thumbnailSizePx, crossfade, context) {
        ImageRequest.Builder(context)
            .data(EmbeddedAudioArtRequest(audioUri = audioUri, fallbackAlbumArtUri = albumArtUri))
            .size(thumbnailSizePx)
            .crossfade(crossfade)
            .build()
    }

    AsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        placeholder = painterResource(id = R.drawable.img_default_album_art),
        error = painterResource(id = R.drawable.img_default_album_art),
        fallback = painterResource(id = R.drawable.img_default_album_art),
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    )
}
