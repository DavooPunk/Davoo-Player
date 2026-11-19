package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.local.SavedMediaItemSnapshot
import com.example.data.local.SavedPlaybackSession
import com.example.data.local.SonoraDatabase
import com.example.data.repository.AudioTagParsers
import com.example.data.repository.DeviceMusicScanner
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.widget.SonoraWidgetProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsStore: PlaybackPreferencesStore
    private val effectsController = AudioEffectsController()
    private val stereoBalanceProcessor = StereoBalanceAudioProcessor()

    @Volatile
    private var isServiceDestroyed: Boolean = false
    private var sessionPersistedOnStop: Boolean = false
    private var currentSettings: AudioSettings = AudioSettings()
    private var currentReplayGainMultiplier: Float = 1.0f
    private var crossfadeJob: Job? = null
    private var persistSessionJob: Job? = null
    private var lastPersistedAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        isServiceDestroyed = false
        ensurePlaybackNotificationChannel()
        prefsStore = PlaybackPreferencesStore(this)
        currentSettings = prefsStore.loadAudioSettings()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(stereoBalanceProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()

        applyAudioSettingsToPlayer(player, currentSettings, forceAll = true)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effectsController.attachToSession(audioSessionId, currentSettings)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sessionPersistedOnStop = false
                refreshReplayGainAndVolume(player, mediaItem)
                persistCurrentSession(player)
                updateHomeWidgets(player)
                if (currentSettings.crossfadeSeconds > 0 && player.isPlaying) {
                    serviceScope.launch { fadeInPlayer(player) }
                    startCrossfadeMonitor(player)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player.audioSessionId > 0) {
                    effectsController.attachToSession(player.audioSessionId, currentSettings)
                }
                if (isPlaying) {
                    sessionPersistedOnStop = false
                    startCrossfadeMonitor(player)
                } else {
                    crossfadeJob?.cancel()
                    persistCurrentSession(player)
                }
                updateHomeWidgets(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                sessionPersistedOnStop = false
                persistCurrentSession(player)
                if (player.isPlaying) {
                    startCrossfadeMonitor(player)
                }
            }
        })

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification_music)
            }
        setMediaNotificationProvider(notificationProvider)

        val customButtons = buildCustomMediaButtons()

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            SonoraMediaLibraryCallback(customButtons)
        )
            .setSessionActivity(sessionActivityPendingIntent)
            .setBitmapLoader(CacheBitmapLoader(SonoraArtworkBitmapLoader(this)))
            .setCustomLayout(customButtons)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val player = mediaLibrarySession?.player ?: return result
        val action = intent?.action
        when (action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (player.mediaItemCount == 0) {
                    val saved = prefsStore.loadPlaybackSession()
                    if (saved != null && saved.items.isNotEmpty()) {
                        val items = saved.items.map { prefsStore.savedItemToMediaItem(it) }
                        val idx = saved.currentIndex.coerceIn(0, items.lastIndex)
                        player.setMediaItems(items, idx, saved.positionMs)
                        player.shuffleModeEnabled = saved.shuffleEnabled
                        player.repeatMode = saved.repeatMode
                        player.prepare()
                        player.play()
                    }
                } else if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                }
            }
            ACTION_WIDGET_NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
            }
            ACTION_WIDGET_PREV -> {
                if (player.currentPosition > 3500L) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0L)
                }
            }
        }
        // If a widget PendingIntent started this service via startForegroundService() while
        // the player has nothing to play, satisfy Android's foreground requirement briefly before stopping.
        if (action in setOf(ACTION_WIDGET_PLAY_PAUSE, ACTION_WIDGET_NEXT, ACTION_WIDGET_PREV) &&
            player.mediaItemCount == 0 &&
            !player.playWhenReady
        ) {
            postIdleForegroundAndStop()
        }
        return result
    }

    private fun postIdleForegroundAndStop() {
        runCatching {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_notification_music)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.widget_idle_subtitle))
                .build()
            startForeground(WIDGET_IDLE_NOTIFICATION_ID, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    @androidx.annotation.VisibleForTesting
    internal fun activePlayerOrNull(): Player? = mediaLibrarySession?.player

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownFromTaskRemoval()
        super.onTaskRemoved(rootIntent)
    }

    private fun shutdownFromTaskRemoval() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        persistSessionJob?.cancel()
        persistSessionJob = null

        val player = mediaLibrarySession?.player
        if (player != null) {
            if (player.mediaItemCount > 0) {
                persistCurrentSession(player, synchronous = true)
                sessionPersistedOnStop = true
            }
            val meta = player.currentMediaItem?.mediaMetadata
            val title = meta?.title?.toString() ?: getString(R.string.app_name)
            val artist = meta?.artist?.toString() ?: getString(R.string.widget_idle_subtitle)

            runCatching {
                player.playWhenReady = false
                player.pause()
                player.stop()
            }
            SonoraWidgetProvider.setAllWidgetsPaused(
                context = this,
                title = title,
                artist = artist
            )
        }

        effectsController.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { stopForeground(true) }
        }
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancelAll()
        }
        stopSelf()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.example.ui.components.AudioArtImageLoaderProvider.trimMemory(level)
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        crossfadeJob?.cancel()
        persistSessionJob?.cancel()
        mediaLibrarySession?.run {
            if (!sessionPersistedOnStop) {
                persistCurrentSession(player, synchronous = true)
            }
            effectsController.release()
            player.release()
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun applyAudioSettingsToPlayer(
        player: Player,
        settings: AudioSettings,
        forceAll: Boolean = false
    ) {
        if (isServiceDestroyed) return
        val previous = currentSettings
        val safeSpeed = settings.playbackSpeed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.0f) ?: 1.0f
        val safeBalance = settings.stereoBalance.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        val normalized = settings.copy(
            playbackSpeed = safeSpeed,
            stereoBalance = safeBalance,
            bassBoostStrength = settings.bassBoostStrength.coerceIn(0, 1000),
            virtualizerStrength = settings.virtualizerStrength.coerceIn(0, 1000),
            crossfadeSeconds = settings.crossfadeSeconds.coerceIn(0, 12)
        )
        currentSettings = normalized

        if (forceAll || previous.stereoBalance != safeBalance) {
            stereoBalanceProcessor.setBalance(safeBalance)
        }
        if (forceAll || previous.playbackSpeed != safeSpeed) {
            runCatching { player.setPlaybackParameters(PlaybackParameters(safeSpeed)) }
        }
        val dspChanged = forceAll ||
            previous.eqEnabled != normalized.eqEnabled ||
            previous.bandGainsMb != normalized.bandGainsMb ||
            previous.bassBoostStrength != normalized.bassBoostStrength ||
            previous.virtualizerStrength != normalized.virtualizerStrength ||
            previous.replayGainEnabled != normalized.replayGainEnabled
        if (dspChanged) {
            if (player is ExoPlayer && player.audioSessionId > 0) {
                effectsController.attachToSession(player.audioSessionId, normalized)
            } else {
                effectsController.applySettings(normalized)
            }
        }
        if (forceAll || previous.crossfadeSeconds != normalized.crossfadeSeconds) {
            if (normalized.crossfadeSeconds > 0 && runCatching { player.isPlaying }.getOrDefault(false)) {
                startCrossfadeMonitor(player)
            } else if (normalized.crossfadeSeconds <= 0) {
                crossfadeJob?.cancel()
            }
        }
        if (forceAll || previous.replayGainEnabled != normalized.replayGainEnabled) {
            refreshReplayGainAndVolume(player, player.currentMediaItem)
        }
    }

    private fun refreshReplayGainAndVolume(player: Player, mediaItem: MediaItem?) {
        if (isServiceDestroyed) return
        if (!currentSettings.replayGainEnabled || mediaItem == null) {
            currentReplayGainMultiplier = 1.0f
            runCatching { player.volume = 1.0f }
            effectsController.setTrackHasReplayGainTag(false)
            effectsController.applySettings(currentSettings)
            return
        }
        val extraGain = mediaItem.mediaMetadata.extras?.getFloat(PlaybackController.EXTRA_REPLAY_GAIN_DB, Float.NaN)
        if (extraGain != null && !extraGain.isNaN()) {
            currentReplayGainMultiplier = dbToLinear(extraGain)
            runCatching { player.volume = currentReplayGainMultiplier }
            effectsController.setTrackHasReplayGainTag(true)
            effectsController.applySettings(currentSettings)
        } else {
            val uri = mediaItem.localConfiguration?.uri
            val targetMediaId = mediaItem.mediaId
            if (uri != null) {
                serviceScope.launch(Dispatchers.IO) {
                    val db = runCatching { AudioTagParsers.extractReplayGainDb(this@PlaybackService, uri) }.getOrNull()
                    val mult = if (db != null) dbToLinear(db) else 1.0f
                    serviceScope.launch(Dispatchers.Main.immediate) {
                        if (isServiceDestroyed ||
                            !currentSettings.replayGainEnabled ||
                            mediaLibrarySession?.player?.currentMediaItem?.mediaId != targetMediaId
                        ) {
                            return@launch
                        }
                        currentReplayGainMultiplier = mult
                        runCatching { player.volume = mult }
                        effectsController.setTrackHasReplayGainTag(db != null)
                        if (player is ExoPlayer && player.audioSessionId > 0) {
                            effectsController.attachToSession(player.audioSessionId, currentSettings)
                        } else {
                            effectsController.applySettings(currentSettings)
                        }
                    }
                }
            } else {
                currentReplayGainMultiplier = 1.0f
                runCatching { player.volume = 1.0f }
                effectsController.setTrackHasReplayGainTag(false)
                effectsController.applySettings(currentSettings)
            }
        }
    }

    private fun dbToLinear(db: Float): Float {
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0.25f, 1.4f)
    }

    private fun startCrossfadeMonitor(player: Player) {
        crossfadeJob?.cancel()
        val fadeSec = currentSettings.crossfadeSeconds
        if (fadeSec <= 0) {
            runCatching { player.volume = currentReplayGainMultiplier }
            return
        }
        val fadeMs = fadeSec * 1000L
        crossfadeJob = serviceScope.launch {
            while (isActive && !isServiceDestroyed && runCatching { player.isPlaying }.getOrDefault(false)) {
                val duration = runCatching { player.duration }.getOrDefault(0L)
                val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                val hasNext = runCatching { player.hasNextMediaItem() }.getOrDefault(false)
                var nextDelayMs = 2_000L
                if (duration > fadeMs * 2 && hasNext) {
                    val remaining = duration - pos
                    if (remaining in 1..fadeMs) {
                        val factor = (remaining.toFloat() / fadeMs.toFloat()).coerceIn(0.05f, 1f)
                        runCatching { player.volume = currentReplayGainMultiplier * factor }
                        nextDelayMs = 120L
                    } else {
                        if (pos > fadeMs) {
                            runCatching { player.volume = currentReplayGainMultiplier }
                        }
                        if (remaining > fadeMs) {
                            val speed = currentSettings.playbackSpeed.coerceAtLeast(0.5f)
                            val wallTimeUntilFade = ((remaining - fadeMs) / speed).toLong()
                            nextDelayMs = (wallTimeUntilFade - 180L).coerceIn(150L, 4_000L)
                        }
                    }
                } else {
                    runCatching { player.volume = currentReplayGainMultiplier }
                }
                delay(nextDelayMs)
            }
        }
    }

    private suspend fun fadeInPlayer(player: Player) {
        val steps = 8
        for (i in 1..steps) {
            if (isServiceDestroyed || !runCatching { player.isPlaying }.getOrDefault(false)) break
            runCatching { player.volume = currentReplayGainMultiplier * (i.toFloat() / steps.toFloat()) }
            delay(80L)
        }
        if (!isServiceDestroyed) {
            runCatching { player.volume = currentReplayGainMultiplier }
        }
    }

    private fun persistCurrentSession(player: Player, synchronous: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!synchronous && now - lastPersistedAtMs < 400L && player.isPlaying) return
        lastPersistedAtMs = now
        val count = player.mediaItemCount
        if (count == 0) return

        val rawIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val halfWindow = MAX_PERSISTED_ITEMS / 2
        val startIdx = if (count <= MAX_PERSISTED_ITEMS) 0 else (rawIndex - halfWindow).coerceIn(0, count - MAX_PERSISTED_ITEMS)
        val endIdxExclusive = (startIdx + MAX_PERSISTED_ITEMS).coerceAtMost(count)

        val snapshots = (startIdx until endIdxExclusive).mapNotNull { idx ->
            val item = runCatching { player.getMediaItemAt(idx) }.getOrNull() ?: return@mapNotNull null
            val uri = item.localConfiguration?.uri?.toString() ?: return@mapNotNull null
            val meta = item.mediaMetadata
            SavedMediaItemSnapshot(
                id = item.mediaId,
                contentUri = uri,
                title = meta.title?.toString() ?: "Pista",
                artist = meta.artist?.toString() ?: "Artista desconocido",
                album = meta.albumTitle?.toString() ?: "Álbum desconocido",
                albumArtUri = meta.extras?.getString(PlaybackController.EXTRA_ALBUM_ART_URI)
                    ?: meta.artworkUri?.toString(),
                formatName = meta.extras?.getString(PlaybackController.EXTRA_FORMAT) ?: "MP3",
                durationMs = meta.extras?.getLong(PlaybackController.EXTRA_DURATION_MS) ?: 0L
            )
        }
        if (snapshots.isEmpty()) return
        val session = SavedPlaybackSession(
            items = snapshots,
            currentIndex = (rawIndex - startIdx).coerceIn(0, snapshots.lastIndex),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
        if (synchronous) {
            prefsStore.savePlaybackSession(session)
        } else {
            persistSessionJob?.cancel()
            persistSessionJob = serviceScope.launch(Dispatchers.IO) {
                prefsStore.savePlaybackSession(session)
            }
        }
    }

    private fun updateHomeWidgets(player: Player) {
        if (isServiceDestroyed) return
        val item = player.currentMediaItem
        val meta = item?.mediaMetadata
        val title = meta?.title?.toString() ?: getString(R.string.app_name)
        val artist = meta?.artist?.toString() ?: getString(R.string.widget_idle_subtitle)
        val artworkUri = meta?.extras?.getString(PlaybackController.EXTRA_ALBUM_ART_URI)
            ?: meta?.artworkUri?.toString()
        val audioUri = item?.localConfiguration?.uri?.toString()
        val playing = player.isPlaying

        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                SonoraWidgetProvider.updateAllWidgets(
                    context = this@PlaybackService,
                    title = title,
                    artist = artist,
                    isPlaying = playing,
                    artworkUri = artworkUri,
                    audioUri = audioUri
                )
            }
        }
    }

    private fun ensurePlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildCustomMediaButtons(): List<CommandButton> {
        val seekBackCommand = SessionCommand(ACTION_SEEK_BACK_10, Bundle.EMPTY)
        val seekForwardCommand = SessionCommand(ACTION_SEEK_FORWARD_10, Bundle.EMPTY)
        return listOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setSessionCommand(seekBackCommand)
                .setDisplayName("-10 s")
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                .setSessionCommand(seekForwardCommand)
                .setDisplayName("+10 s")
                .build()
        )
    }

    private inner class SonoraMediaLibraryCallback(
        private val customButtons: List<CommandButton>
    ) : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_SEEK_BACK_10, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SEEK_FORWARD_10, Bundle.EMPTY))
                .add(SessionCommand(ACTION_APPLY_AUDIO_SETTINGS, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .setCustomLayout(customButtons)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                ACTION_SEEK_BACK_10 -> {
                    val target = (player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                    player.seekTo(target)
                }
                ACTION_SEEK_FORWARD_10 -> {
                    val maxDur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val target = (player.currentPosition + SEEK_INCREMENT_MS).coerceIn(0L, maxDur)
                    player.seekTo(target)
                }
                ACTION_APPLY_AUDIO_SETTINGS -> {
                    val updated = audioSettingsFromBundle(args) ?: prefsStore.loadAudioSettings()
                    applyAudioSettingsToPlayer(player, updated)
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updated = mediaItems.map { item ->
                if (item.localConfiguration == null && item.requestMetadata.mediaUri != null) {
                    item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                } else {
                    item
                }
            }.toMutableList()
            return Futures.immediateFuture(updated)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = prefsStore.loadPlaybackSession()
                ?: return Futures.immediateFailedFuture(IllegalStateException("No saved playback session"))
            if (saved.items.isEmpty()) {
                return Futures.immediateFailedFuture(IllegalStateException("Saved playback session is empty"))
            }
            val items = saved.items.map { prefsStore.savedItemToMediaItem(it) }
            val startIndex = saved.currentIndex.coerceIn(0, items.lastIndex)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, startIndex, saved.positionMs)
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = buildBrowsableItem(ROOT_ID, "Davoo Player", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == ROOT_ID) {
                val folders = ImmutableList.of(
                    buildBrowsableItem(NODE_TRACKS, "Todas las canciones", MediaMetadata.MEDIA_TYPE_MUSIC),
                    buildBrowsableItem(NODE_FAVORITES, "Favoritas", MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    buildBrowsableItem(NODE_ALBUMS, "Álbumes", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                    buildBrowsableItem(NODE_PLAYLISTS, "Listas de reproducción", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                )
                return Futures.immediateFuture(LibraryResult.ofItemList(folders, params))
            }

            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                val items = runCatching { loadChildrenForNode(parentId) }.getOrDefault(emptyList())
                val paged = if (pageSize > 0 && page >= 0) {
                    items.drop(page * pageSize).take(pageSize)
                } else {
                    items
                }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (mediaId == ROOT_ID || mediaId == NODE_TRACKS || mediaId == NODE_FAVORITES ||
                mediaId == NODE_ALBUMS || mediaId == NODE_PLAYLISTS
            ) {
                return Futures.immediateFuture(
                    LibraryResult.ofItem(
                        buildBrowsableItem(mediaId, mediaId, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                        null
                    )
                )
            }
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                val tracks = loadVisibleLibraryTracks()
                val found = tracks.firstOrNull { it.id == mediaId }
                if (found != null) {
                    future.set(LibraryResult.ofItem(found.toBrowsablePlayableMediaItem(), null))
                } else {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
            }
            return future
        }

        private suspend fun loadChildrenForNode(parentId: String): List<MediaItem> {
            val db = SonoraDatabase.getInstance(this@PlaybackService)
            return when {
                parentId == NODE_TRACKS -> {
                    loadVisibleLibraryTracks().map { it.toBrowsablePlayableMediaItem() }
                }
                parentId == NODE_FAVORITES -> {
                    loadVisibleLibraryTracks().filter { it.isFavorite }.map { it.toBrowsablePlayableMediaItem() }
                }
                parentId == NODE_ALBUMS -> {
                    loadVisibleLibraryTracks()
                        .groupBy { it.album }
                        .keys
                        .sorted()
                        .map { album ->
                            buildBrowsableItem(
                                "$NODE_ALBUM_PREFIX$album",
                                album,
                                MediaMetadata.MEDIA_TYPE_ALBUM
                            )
                        }
                }
                parentId.startsWith(NODE_ALBUM_PREFIX) -> {
                    val album = parentId.removePrefix(NODE_ALBUM_PREFIX)
                    loadVisibleLibraryTracks()
                        .filter { it.album == album }
                        .map { it.toBrowsablePlayableMediaItem() }
                }
                parentId == NODE_PLAYLISTS -> {
                    db.playlistDao().observePlaylistsWithEntries().first().map { pl ->
                        buildBrowsableItem(
                            "$NODE_PLAYLIST_PREFIX${pl.playlist.id}",
                            pl.playlist.name,
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    }
                }
                parentId.startsWith(NODE_PLAYLIST_PREFIX) -> {
                    val plId = parentId.removePrefix(NODE_PLAYLIST_PREFIX).toLongOrNull() ?: return emptyList()
                    val pl = db.playlistDao().observePlaylistsWithEntries().first()
                        .firstOrNull { it.playlist.id == plId } ?: return emptyList()
                    pl.entries.sortedBy { it.position }.map { it.toAudioTrack().toBrowsablePlayableMediaItem() }
                }
                else -> emptyList()
            }
        }

        private suspend fun loadVisibleLibraryTracks(): List<AudioTrack> {
            val db = SonoraDatabase.getInstance(this@PlaybackService)
            val scanner = DeviceMusicScanner(this@PlaybackService)
            val mediaStoreTracks = runCatching { scanner.scanMediaStoreAudio() }.getOrDefault(emptyList())
            val sdFolders = runCatching { prefsStore.loadSdCardFolders() }.getOrDefault(emptyList())
            val sdTracks = if (sdFolders.isNotEmpty()) {
                runCatching { scanner.scanDocumentTreeFolders(sdFolders).first }.getOrDefault(emptyList())
            } else emptyList()
            val imported = db.importedTrackDao().observeImportedTracks().first().map { it.toAudioTrack() }
            val statsMap = db.trackStatsDao().observeAllStats().first().associateBy { it.trackId }
            val excluded = db.excludedFolderDao().observeExcludedFolders().first().map { it.folderName }.toSet()
            val existingUris = mediaStoreTracks.mapTo(HashSet()) { it.contentUri }
            val dedupSd = sdTracks.filterNot { it.contentUri in existingUris }
            dedupSd.forEach { existingUris.add(it.contentUri) }
            return (mediaStoreTracks + dedupSd + imported.filterNot { it.contentUri in existingUris })
                .distinctBy { it.id }
                .filterNot { it.folderName in excluded }
                .map { t ->
                    val s = statsMap[t.id]
                    t.copy(
                        isFavorite = s?.isFavorite ?: false,
                        playCount = s?.playCount ?: 0,
                        lastPlayedAt = s?.lastPlayedAt ?: 0L
                    )
                }
        }

        private fun buildBrowsableItem(id: String, title: String, mediaType: Int): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(mediaType)
                        .build()
                )
                .build()
        }

        private fun AudioTrack.toBrowsablePlayableMediaItem(): MediaItem {
            val meta = MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(Uri.parse(albumArtUri ?: contentUri))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
            return MediaItem.Builder()
                .setMediaId(id)
                .setUri(Uri.parse(contentUri))
                .setMediaMetadata(meta)
                .build()
        }
    }

    private fun audioSettingsFromBundle(args: Bundle): AudioSettings? {
        if (!args.containsKey(ARG_EQ_ENABLED)) return null
        val presetName = args.getString(ARG_EQ_PRESET)
        val preset = com.example.model.EqPreset.entries.firstOrNull { it.name == presetName }
            ?: com.example.model.EqPreset.FLAT
        val gains = args.getIntArray(ARG_BAND_GAINS_MB)?.toList()?.takeIf { it.isNotEmpty() }
            ?: listOf(0, 0, 0, 0, 0)
        return AudioSettings(
            eqEnabled = args.getBoolean(ARG_EQ_ENABLED, false),
            eqPreset = preset,
            bandGainsMb = gains,
            bassBoostStrength = args.getInt(ARG_BASS_BOOST, 0),
            virtualizerStrength = args.getInt(ARG_VIRTUALIZER, 0),
            crossfadeSeconds = args.getInt(ARG_CROSSFADE, 0),
            gaplessEnabled = args.getBoolean(ARG_GAPLESS, true),
            replayGainEnabled = args.getBoolean(ARG_REPLAYGAIN, false),
            playbackSpeed = args.getFloat(ARG_SPEED, 1.0f),
            stereoBalance = args.getFloat(ARG_BALANCE, 0f),
            dynamicArtworkColors = args.getBoolean(ARG_DYNAMIC_COLORS, true)
        )
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sonora_playback_channel"
        const val ACTION_SEEK_BACK_10 = "com.example.playback.ACTION_SEEK_BACK_10"
        const val ACTION_SEEK_FORWARD_10 = "com.example.playback.ACTION_SEEK_FORWARD_10"
        const val ACTION_APPLY_AUDIO_SETTINGS = "com.example.playback.ACTION_APPLY_AUDIO_SETTINGS"

        const val ARG_EQ_ENABLED = "arg_eq_enabled"
        const val ARG_EQ_PRESET = "arg_eq_preset"
        const val ARG_BAND_GAINS_MB = "arg_band_gains_mb"
        const val ARG_BASS_BOOST = "arg_bass_boost"
        const val ARG_VIRTUALIZER = "arg_virtualizer"
        const val ARG_CROSSFADE = "arg_crossfade"
        const val ARG_GAPLESS = "arg_gapless"
        const val ARG_REPLAYGAIN = "arg_replaygain"
        const val ARG_SPEED = "arg_speed"
        const val ARG_BALANCE = "arg_balance"
        const val ARG_DYNAMIC_COLORS = "arg_dynamic_colors"

        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_PREV"

        private const val SEEK_INCREMENT_MS = 10_000L
        private const val MAX_PERSISTED_ITEMS = 120
        private const val WIDGET_IDLE_NOTIFICATION_ID = 9002
        private const val ROOT_ID = "sonora_root"
        private const val NODE_TRACKS = "sonora_tracks"
        private const val NODE_FAVORITES = "sonora_favorites"
        private const val NODE_ALBUMS = "sonora_albums"
        private const val NODE_PLAYLISTS = "sonora_playlists"
        private const val NODE_ALBUM_PREFIX = "sonora_album:"
        private const val NODE_PLAYLIST_PREFIX = "sonora_playlist:"
    }
}
