package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import com.example.MainActivity
import com.example.R
import com.example.data.local.PlaybackPreferencesStore
import com.example.playback.PlaybackService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SonoraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext ?: context
        val idsCopy = appWidgetIds.copyOf()
        val pendingResult = runCatching { goAsync() }.getOrNull()
        widgetExecutor.execute {
            try {
                val prefs = PlaybackPreferencesStore(appContext)
                val saved = prefs.loadPlaybackSession()
                val current = saved?.items?.getOrNull(saved.currentIndex)
                updateWidgets(
                    context = appContext,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = idsCopy,
                    title = current?.title ?: appContext.getString(R.string.app_name),
                    artist = current?.artist ?: appContext.getString(R.string.widget_idle_subtitle),
                    isPlaying = false,
                    artworkUri = current?.albumArtUri,
                    audioUri = current?.contentUri
                )
            } finally {
                runCatching { pendingResult?.finish() }
            }
        }
    }

    companion object {
        private const val WIDGET_ART_MAX_PX = 160
        private const val MAX_WIDGET_STREAM_BYTES = 4 * 1024 * 1024
        private val widgetExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "sonora-widget-worker").apply { isDaemon = true }
        }

        @VisibleForTesting
        internal fun awaitPendingWidgetUpdatesForTest(timeoutMs: Long = 3_000L): Boolean {
            return runCatching {
                widgetExecutor.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
                true
            }.getOrDefault(false)
        }

        fun setAllWidgetsPaused(
            context: Context,
            title: String,
            artist: String
        ) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val component = ComponentName(context, SonoraWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                val views = RemoteViews(context.packageName, R.layout.widget_sonora_player)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_widget_play)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        }

        fun updateAllWidgets(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artworkUri: String?,
            audioUri: String?
        ) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val component = ComponentName(context, SonoraWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                updateWidgets(
                    context = context,
                    appWidgetManager = manager,
                    appWidgetIds = ids,
                    title = title,
                    artist = artist,
                    isPlaying = isPlaying,
                    artworkUri = artworkUri,
                    audioUri = audioUri
                )
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artworkUri: String?,
            audioUri: String?
        ) {
            if (appWidgetIds.isEmpty()) return
            val bitmap = decodeWidgetArtwork(context, audioUri, artworkUri)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPending = PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevPending = serviceActionPendingIntent(context, PlaybackService.ACTION_WIDGET_PREV, 101)
            val playPausePending = serviceActionPendingIntent(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE, 102)
            val nextPending = serviceActionPendingIntent(context, PlaybackService.ACTION_WIDGET_NEXT, 103)

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_sonora_player)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                views.setImageViewResource(
                    R.id.widget_btn_play_pause,
                    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                )
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_artwork, bitmap)
                } else {
                    views.setImageViewResource(R.id.widget_artwork, R.drawable.img_default_album_art)
                }

                views.setOnClickPendingIntent(R.id.widget_root, launchPending)
                views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)
                views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePending)
                views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

                runCatching {
                    appWidgetManager.updateAppWidget(widgetId, views)
                }.onFailure {
                    views.setImageViewResource(R.id.widget_artwork, R.drawable.img_default_album_art)
                    runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
                }
            }
        }

        private fun serviceActionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, intent, flags)
            } else {
                PendingIntent.getService(context, requestCode, intent, flags)
            }
        }

        internal fun decodeWidgetArtwork(
            context: Context,
            audioUri: String?,
            artworkUri: String?
        ): Bitmap? {
            if (!audioUri.isNullOrBlank()) {
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
            if (!artworkUri.isNullOrBlank() && !artworkUri.contains("/audio/media/")) {
                val streamBytes = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(artworkUri))?.use { input ->
                        val buf = ByteArray(8192)
                        val out = java.io.ByteArrayOutputStream()
                        var total = 0
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            total += r
                            if (total > MAX_WIDGET_STREAM_BYTES) return@use null
                            out.write(buf, 0, r)
                        }
                        out.toByteArray()
                    }
                }.getOrNull()
                if (streamBytes != null) {
                    decodeSampledRgb565(streamBytes)?.let { return it }
                }
            }
            return null
        }

        internal fun decodeSampledRgb565(bytes: ByteArray): Bitmap? {
            if (bytes.isEmpty()) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                var sample = 1
                val halfW = bounds.outWidth / 2
                val halfH = bounds.outHeight / 2
                while ((halfW / sample) >= WIDGET_ART_MAX_PX && (halfH / sample) >= WIDGET_ART_MAX_PX) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }.getOrNull()
        }
    }
}
