package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object CrossfadeMath {
    fun equalPowerGains(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        val theta = p * (PI / 2.0)
        val outgoing = cos(theta).toFloat().coerceIn(0f, 1f)
        val incoming = sin(theta).toFloat().coerceIn(0f, 1f)
        return outgoing to incoming
    }

    fun shouldTriggerCrossfade(
        crossfadeSeconds: Int,
        durationMs: Long,
        positionMs: Long,
        hasNextItem: Boolean,
        repeatMode: Int,
        playbackSpeed: Float,
        stopAfterCurrentTrack: Boolean,
        alreadyTransitioning: Boolean
    ): Boolean {
        if (crossfadeSeconds <= 0 ||
            !hasNextItem ||
            repeatMode == Player.REPEAT_MODE_ONE ||
            stopAfterCurrentTrack ||
            alreadyTransitioning ||
            durationMs <= 0L ||
            positionMs < 0L
        ) {
            return false
        }
        val speed = playbackSpeed.coerceAtLeast(0.5f)
        val fadeWallMs = crossfadeSeconds * 1000L
        val totalWallMs = (durationMs / speed).toLong()
        if (totalWallMs <= fadeWallMs * 2L) return false
        val remainingWallMs = ((durationMs - positionMs) / speed).toLong()
        return remainingWallMs in 1L..fadeWallMs
    }

    fun computeMonitorDelayMs(
        crossfadeSeconds: Int,
        durationMs: Long,
        positionMs: Long,
        playbackSpeed: Float
    ): Long {
        if (crossfadeSeconds <= 0 || durationMs <= 0L) return 1_500L
        val speed = playbackSpeed.coerceAtLeast(0.5f)
        val fadeWallMs = crossfadeSeconds * 1000L
        val remainingWallMs = ((durationMs - positionMs).coerceAtLeast(0L) / speed).toLong()
        return if (remainingWallMs > fadeWallMs) {
            (remainingWallMs - fadeWallMs - 100L).coerceIn(80L, 2_500L)
        } else {
            100L
        }
    }
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsStore: PlaybackPreferencesStore
    private val effectsController = AudioEffectsController()
    private val tenBandEqProcessor = TenBandEqualizerAudioProcessor()
    private val stereoBalanceProcessor = StereoBalanceAudioProcessor()
    private val outgoingEqProcessor = TenBandEqualizerAudioProcessor()
    private val outgoingBalanceProcessor = StereoBalanceAudioProcessor()
    private var outgoingPlayer: ExoPlayer? = null
    private lateinit var mediaAudioAttributes: AudioAttributes

    @Volatile
    private var isServiceDestroyed: Boolean = false
    private var sessionPersistedOnStop: Boolean = false
    private var currentSettings: AudioSettings = AudioSettings()
    private var currentReplayGainMultiplier: Float = 1.0f
    private var stopAfterCurrentTrack: Boolean = false
    private var crossfadeMonitorJob: Job? = null
    private var crossfadeFadeJob: Job? = null
    private var activeFadeInActive: Boolean = false
    private var isAdvancingViaCrossfade: Boolean = false
    private var lastCrossfadeIncomingMediaId: String? = null
    private var lastCrossfadeAdvanceAtMs: Long = 0L
    private var awaitingCrossfadeMediaTransition: Boolean = false
    private var awaitingCrossfadeDiscontinuity: Boolean = false
    private var lastCrossfadeSourceKey: String? = null
    private var gapPauseJob: Job? = null
    private var isInTrackGapPause: Boolean = false
    private var pauseFadeJob: Job? = null
    private var persistSessionJob: Job? = null
    private var lastPersistedAtMs: Long = 0L
    private var cachedTimelineCount: Int = -1
    private var cachedWindowTrackIds: List<String> = emptyList()
    private var cachedTimelineSnapshots: List<SavedMediaItemSnapshot> = emptyList()
    private var cachedSnapshotWindowStart: Int = 0
    private var cachedTimelineDirty: Boolean = true
    private var hasPersistedExactStructureInService: Boolean = false
    private var lastWidgetSignature: String? = null

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
        mediaAudioAttributes = audioAttributes

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(tenBandEqProcessor, stereoBalanceProcessor))
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

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                cachedTimelineDirty = true
                hasPersistedExactStructureInService = false
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val triggeredByCrossfade = consumeCrossfadeMediaTransition(mediaItem?.mediaId)
                gapPauseJob?.cancel()
                isInTrackGapPause = false
                sessionPersistedOnStop = false
                lastCrossfadeSourceKey = null
                if (!triggeredByCrossfade && (activeFadeInActive || crossfadeFadeJob?.isActive == true)) {
                    stopCrossfadeFadeAndOutgoing(player)
                }
                refreshReplayGainAndVolume(player, mediaItem)
                persistCurrentSession(player)
                updateHomeWidgets(player)
                if (currentSettings.crossfadeSeconds > 0 && player.isPlaying) {
                    startCrossfadeMonitor(player)
                } else if (shouldInsertTrackGap(reason, mediaItem, player)) {
                    scheduleTrackGapPause(player, mediaItem!!.mediaId)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isInTrackGapPause && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    gapPauseJob?.cancel()
                    isInTrackGapPause = false
                }
                if (!playWhenReady && (activeFadeInActive || crossfadeFadeJob?.isActive == true)) {
                    crossfadeMonitorJob?.cancel()
                    stopCrossfadeFadeAndOutgoing(player)
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
                    if (shouldAbortCrossfadeOnIsPlayingChanged(player)) {
                        crossfadeMonitorJob?.cancel()
                        stopCrossfadeFadeAndOutgoing(player)
                    }
                    persistCurrentSession(player)
                }
                if (isPlaying || !isInTrackGapPause) {
                    updateHomeWidgets(player)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                sessionPersistedOnStop = false
                persistCurrentSession(player)
                val targetId = newPosition.mediaItem?.mediaId ?: player.currentMediaItem?.mediaId
                val isFadeSeek = reason == Player.DISCONTINUITY_REASON_SEEK &&
                    consumeCrossfadeDiscontinuity(targetId)
                if (reason == Player.DISCONTINUITY_REASON_SEEK && !isFadeSeek) {
                    if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                        lastCrossfadeSourceKey = null
                    }
                    if (activeFadeInActive || crossfadeFadeJob?.isActive == true) {
                        stopCrossfadeFadeAndOutgoing(player)
                    }
                }
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
        val sessionPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun play() {
                performPlayWithOptionalFade(player)
            }

            override fun pause() {
                performPauseWithOptionalFade(player)
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) {
                    performPlayWithOptionalFade(player)
                } else {
                    performPauseWithOptionalFade(player)
                }
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            sessionPlayer,
            SonoraMediaLibraryCallback(customButtons)
        )
            .setSessionActivity(sessionActivityPendingIntent)
            .setBitmapLoader(CacheBitmapLoader(SonoraArtworkBitmapLoader(this)))
            .setCustomLayout(customButtons)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val rawPlayer = mediaLibrarySession?.player
        val sessionPlayer = rawPlayer ?: return result
        val action = intent?.action
        when (action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (sessionPlayer.mediaItemCount == 0) {
                    val saved = prefsStore.loadPlaybackSession()
                    if (saved != null && saved.items.isNotEmpty()) {
                        val items = saved.items.map { prefsStore.savedItemToMediaItem(it) }
                        val idx = saved.currentIndex.coerceIn(0, items.lastIndex)
                        sessionPlayer.setMediaItems(items, idx, saved.positionMs)
                        sessionPlayer.shuffleModeEnabled = saved.shuffleEnabled
                        sessionPlayer.repeatMode = saved.repeatMode
                        sessionPlayer.prepare()
                        sessionPlayer.play()
                    }
                } else if (sessionPlayer.isPlaying) {
                    sessionPlayer.pause()
                } else {
                    if (sessionPlayer.playbackState == Player.STATE_IDLE) sessionPlayer.prepare()
                    sessionPlayer.play()
                }
            }
            ACTION_WIDGET_NEXT -> {
                if (sessionPlayer.hasNextMediaItem()) {
                    sessionPlayer.seekToNextMediaItem()
                }
            }
            ACTION_WIDGET_PREV -> {
                if (sessionPlayer.currentPosition > 3500L) {
                    sessionPlayer.seekTo(0L)
                } else if (sessionPlayer.hasPreviousMediaItem()) {
                    sessionPlayer.seekToPreviousMediaItem()
                } else {
                    sessionPlayer.seekTo(0L)
                }
            }
        }
        // If a widget PendingIntent started this service via startForegroundService() while
        // the player has nothing to play, satisfy Android's foreground requirement briefly before stopping.
        if (action in setOf(ACTION_WIDGET_PLAY_PAUSE, ACTION_WIDGET_NEXT, ACTION_WIDGET_PREV) &&
            sessionPlayer.mediaItemCount == 0 &&
            !sessionPlayer.playWhenReady
        ) {
            postIdleForegroundAndStop()
        }
        return result
    }

    private fun performPlayWithOptionalFade(rawPlayer: ExoPlayer) {
        pauseFadeJob?.cancel()
        pauseFadeJob = null
        if (rawPlayer.playbackState == Player.STATE_IDLE) {
            runCatching { rawPlayer.prepare() }
        }
        if (!currentSettings.fadeOnPauseEnabled || isServiceDestroyed || activeFadeInActive) {
            if (!activeFadeInActive) {
                runCatching { rawPlayer.volume = currentReplayGainMultiplier }
            }
            runCatching { rawPlayer.play() }
            return
        }
        val targetVol = currentReplayGainMultiplier
        runCatching {
            rawPlayer.volume = 0f
            rawPlayer.play()
        }
        pauseFadeJob = serviceScope.launch {
            val steps = 6
            val stepDelay = 30L
            for (step in 1..steps) {
                delay(stepDelay)
                if (isServiceDestroyed || !rawPlayer.playWhenReady) break
                if (!activeFadeInActive) {
                    val v = targetVol * (step.toFloat() / steps.toFloat())
                    runCatching { rawPlayer.volume = v }
                }
            }
            if (!isServiceDestroyed && !activeFadeInActive) {
                runCatching { rawPlayer.volume = targetVol }
            }
        }
    }

    private fun performPauseWithOptionalFade(rawPlayer: ExoPlayer) {
        pauseFadeJob?.cancel()
        pauseFadeJob = null
        if (!currentSettings.fadeOnPauseEnabled ||
            isServiceDestroyed ||
            isInTrackGapPause ||
            !rawPlayer.playWhenReady ||
            rawPlayer.playbackState == Player.STATE_IDLE ||
            rawPlayer.playbackState == Player.STATE_ENDED
        ) {
            runCatching { rawPlayer.pause() }
            return
        }
        val startVol = runCatching { rawPlayer.volume }.getOrDefault(currentReplayGainMultiplier)
            .coerceIn(0f, 1.4f)
        if (startVol <= 0.02f) {
            runCatching {
                rawPlayer.pause()
                rawPlayer.volume = currentReplayGainMultiplier
            }
            return
        }
        pauseFadeJob = serviceScope.launch {
            val steps = 6
            val stepDelay = 30L
            for (step in 1..steps) {
                delay(stepDelay)
                if (isServiceDestroyed) break
                val frac = 1f - (step.toFloat() / steps.toFloat())
                runCatching { rawPlayer.volume = startVol * frac }
            }
            if (!isServiceDestroyed) {
                runCatching {
                    rawPlayer.pause()
                    rawPlayer.volume = currentReplayGainMultiplier
                }
            }
        }
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
        crossfadeMonitorJob?.cancel()
        crossfadeMonitorJob = null
        crossfadeFadeJob?.cancel()
        crossfadeFadeJob = null
        activeFadeInActive = false
        releaseOutgoingPlayer()
        gapPauseJob?.cancel()
        gapPauseJob = null
        isInTrackGapPause = false
        pauseFadeJob?.cancel()
        pauseFadeJob = null
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
        com.example.ui.components.RemoteArtImageLoaderProvider.trimMemory(level)
        com.example.ui.theme.ArtworkColorExtractor.trimMemory(level)
        com.example.data.repository.DeviceMusicScanner.trimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN &&
            !activeFadeInActive &&
            crossfadeFadeJob?.isActive != true
        ) {
            releaseOutgoingPlayer()
        }
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        crossfadeMonitorJob?.cancel()
        crossfadeFadeJob?.cancel()
        activeFadeInActive = false
        releaseOutgoingPlayer()
        gapPauseJob?.cancel()
        isInTrackGapPause = false
        pauseFadeJob?.cancel()
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
        val safePreamp = settings.preampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 12f) ?: 0f
        val safeRgPreamp = settings.replayGainPreampDb.takeIf { it.isFinite() }?.coerceIn(-6f, 12f) ?: 3.0f
        val safeRgUntaggedPreamp = settings.replayGainUntaggedPreampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 0f) ?: -4.0f
        val normalizedGains = com.example.model.EqPreset.normalizeToTenBands(settings.bandGainsMb)
        val normalized = settings.copy(
            bandGainsMb = normalizedGains,
            preampDb = safePreamp,
            replayGainPreampDb = safeRgPreamp,
            replayGainUntaggedPreampDb = safeRgUntaggedPreamp,
            playbackSpeed = safeSpeed,
            stereoBalance = safeBalance,
            bassBoostStrength = settings.bassBoostStrength.coerceIn(0, 1000),
            virtualizerStrength = settings.virtualizerStrength.coerceIn(0, 1000),
            crossfadeSeconds = settings.crossfadeSeconds.coerceIn(0, 12)
        )
        currentSettings = normalized

        if (forceAll ||
            previous.eqEnabled != normalized.eqEnabled ||
            previous.preampDb != safePreamp ||
            previous.bandGainsMb != normalizedGains
        ) {
            tenBandEqProcessor.updateSettings(
                eqEnabled = normalized.eqEnabled,
                preampDb = safePreamp,
                bandGainsMb = normalizedGains
            )
            outgoingEqProcessor.updateSettings(
                eqEnabled = normalized.eqEnabled,
                preampDb = safePreamp,
                bandGainsMb = normalizedGains
            )
        }
        if (forceAll || previous.stereoBalance != safeBalance) {
            stereoBalanceProcessor.setBalance(safeBalance)
            outgoingBalanceProcessor.setBalance(safeBalance)
        }
        if (forceAll || previous.playbackSpeed != safeSpeed) {
            runCatching { player.setPlaybackParameters(PlaybackParameters(safeSpeed)) }
            runCatching { outgoingPlayer?.setPlaybackParameters(PlaybackParameters(safeSpeed)) }
        }
        val rgChanged = forceAll ||
            previous.replayGainEnabled != normalized.replayGainEnabled ||
            previous.replayGainPreampDb != normalized.replayGainPreampDb ||
            previous.replayGainUntaggedPreampDb != normalized.replayGainUntaggedPreampDb
        val dspChanged = forceAll ||
            previous.bassBoostStrength != normalized.bassBoostStrength ||
            previous.virtualizerStrength != normalized.virtualizerStrength ||
            rgChanged
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
                crossfadeMonitorJob?.cancel()
                stopCrossfadeFadeAndOutgoing(player, releaseOutgoing = true)
            }
        }
        if (rgChanged) {
            refreshReplayGainAndVolume(player, player.currentMediaItem)
        }
        if ((normalized.gaplessEnabled || normalized.crossfadeSeconds > 0) && isInTrackGapPause) {
            gapPauseJob?.cancel()
            isInTrackGapPause = false
            if (!isServiceDestroyed && player.mediaItemCount > 0 && !player.playWhenReady) {
                runCatching { player.play() }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun shouldInsertTrackGap(
        reason: Int,
        mediaItem: MediaItem?,
        player: Player
    ): Boolean {
        return !currentSettings.gaplessEnabled &&
            currentSettings.crossfadeSeconds <= 0 &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
            mediaItem != null &&
            player.playWhenReady
    }

    @androidx.annotation.VisibleForTesting
    internal fun updateSettingsForTest(settings: AudioSettings) {
        mediaLibrarySession?.player?.let { player ->
            applyAudioSettingsToPlayer(player, settings, forceAll = true)
        }
    }

    private fun scheduleTrackGapPause(player: Player, targetMediaId: String) {
        gapPauseJob?.cancel()
        isInTrackGapPause = true
        runCatching { player.pause() }
        gapPauseJob = serviceScope.launch {
            delay(TRACK_GAP_PAUSE_MS)
            isInTrackGapPause = false
            if (!isServiceDestroyed &&
                player.currentMediaItem?.mediaId == targetMediaId &&
                !player.playWhenReady
            ) {
                runCatching { player.play() }
            }
        }
    }

    private fun applyResolvedReplayGain(player: Player, trackDb: Float?) {
        val gainPair = computeReplayGainSplit(trackDb, currentSettings)
        currentReplayGainMultiplier = gainPair.first
        if (!activeFadeInActive) {
            runCatching { player.volume = gainPair.first }
        }
        effectsController.setTrackHasReplayGainTag(trackDb != null)
        effectsController.setReplayGainBoostDb(gainPair.second)
        if (player is ExoPlayer && player.audioSessionId > 0) {
            effectsController.attachToSession(player.audioSessionId, currentSettings)
        } else {
            effectsController.applySettings(currentSettings)
        }
    }

    private fun refreshReplayGainAndVolume(player: Player, mediaItem: MediaItem?) {
        if (isServiceDestroyed) return
        if (!currentSettings.replayGainEnabled || mediaItem == null) {
            currentReplayGainMultiplier = 1.0f
            if (!activeFadeInActive) {
                runCatching { player.volume = 1.0f }
            }
            effectsController.setTrackHasReplayGainTag(false)
            effectsController.setReplayGainBoostDb(0f)
            effectsController.applySettings(currentSettings)
            return
        }
        val extraGain = mediaItem.mediaMetadata.extras?.getFloat(PlaybackController.EXTRA_REPLAY_GAIN_DB, Float.NaN)
        if (extraGain != null && !extraGain.isNaN()) {
            applyResolvedReplayGain(player, extraGain)
        } else {
            val uri = mediaItem.localConfiguration?.uri
            val targetMediaId = mediaItem.mediaId
            if (uri != null) {
                serviceScope.launch(Dispatchers.IO) {
                    val db = runCatching {
                        AudioTagParsers.extractReplayGainDb(this@PlaybackService, uri)
                    }.getOrNull() ?: prefsStore.loadComputedReplayGain(targetMediaId)
                    serviceScope.launch(Dispatchers.Main.immediate) {
                        if (isServiceDestroyed ||
                            !currentSettings.replayGainEnabled ||
                            mediaLibrarySession?.player?.currentMediaItem?.mediaId != targetMediaId
                        ) {
                            return@launch
                        }
                        applyResolvedReplayGain(player, db)
                    }
                }
            } else {
                applyResolvedReplayGain(player, null)
            }
        }
    }

    private fun dbToLinear(db: Float): Float {
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0.15f, 1.0f)
    }

    @androidx.annotation.VisibleForTesting
    internal fun shouldAbortCrossfadeOnIsPlayingChanged(player: Player): Boolean {
        return !player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED ||
            player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
    }

    private fun resolveTrackDurationMs(player: Player, item: MediaItem?): Long {
        val playerDur = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
        if (playerDur > 0L && playerDur != C.TIME_UNSET) return playerDur
        return item?.mediaMetadata?.extras?.getLong(PlaybackController.EXTRA_DURATION_MS, 0L)?.takeIf { it > 0L } ?: 0L
    }

    private fun consumeCrossfadeMediaTransition(mediaId: String?): Boolean {
        if (isAdvancingViaCrossfade) {
            awaitingCrossfadeMediaTransition = false
            return true
        }
        if (awaitingCrossfadeMediaTransition &&
            mediaId != null &&
            mediaId == lastCrossfadeIncomingMediaId &&
            (SystemClock.elapsedRealtime() - lastCrossfadeAdvanceAtMs) <= 2_000L
        ) {
            awaitingCrossfadeMediaTransition = false
            return true
        }
        return false
    }

    private fun consumeCrossfadeDiscontinuity(mediaId: String?): Boolean {
        if (isAdvancingViaCrossfade) {
            awaitingCrossfadeDiscontinuity = false
            return true
        }
        if (awaitingCrossfadeDiscontinuity &&
            mediaId != null &&
            mediaId == lastCrossfadeIncomingMediaId &&
            (SystemClock.elapsedRealtime() - lastCrossfadeAdvanceAtMs) <= 2_000L
        ) {
            awaitingCrossfadeDiscontinuity = false
            return true
        }
        return false
    }

    private fun getOrCreateOutgoingPlayer(): ExoPlayer {
        outgoingPlayer?.let { return it }
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(outgoingEqProcessor, outgoingBalanceProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        return ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(mediaAudioAttributes, false)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()
            .also { outgoingPlayer = it }
    }

    private fun stopOutgoingPlayer(player: ExoPlayer? = outgoingPlayer) {
        if (player == null) return
        runCatching {
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
    }

    private fun releaseOutgoingPlayer() {
        val out = outgoingPlayer ?: return
        outgoingPlayer = null
        runCatching {
            out.playWhenReady = false
            out.stop()
            out.release()
        }
    }

    private fun stopCrossfadeFadeAndOutgoing(
        mainPlayer: Player,
        releaseOutgoing: Boolean = false
    ) {
        crossfadeFadeJob?.cancel()
        crossfadeFadeJob = null
        activeFadeInActive = false
        awaitingCrossfadeMediaTransition = false
        awaitingCrossfadeDiscontinuity = false
        if (!isServiceDestroyed) {
            runCatching { mainPlayer.volume = currentReplayGainMultiplier }
        }
        if (releaseOutgoing) {
            releaseOutgoingPlayer()
        } else {
            stopOutgoingPlayer()
        }
    }

    private fun startCrossfadeMonitor(player: Player) {
        crossfadeMonitorJob?.cancel()
        val fadeSec = currentSettings.crossfadeSeconds
        if (fadeSec <= 0) {
            if (!activeFadeInActive) {
                runCatching { player.volume = currentReplayGainMultiplier }
            }
            return
        }
        crossfadeMonitorJob = serviceScope.launch {
            while (isActive && !isServiceDestroyed && runCatching { player.isPlaying }.getOrDefault(false)) {
                val settingsFadeSec = currentSettings.crossfadeSeconds
                if (settingsFadeSec <= 0) break

                val item = runCatching { player.currentMediaItem }.getOrNull()
                val duration = resolveTrackDurationMs(player, item)
                val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                val hasNext = runCatching { player.hasNextMediaItem() }.getOrDefault(false)
                val repeatMode = runCatching { player.repeatMode }.getOrDefault(Player.REPEAT_MODE_OFF)
                val speed = currentSettings.playbackSpeed.coerceAtLeast(0.5f)
                val idx = runCatching { player.currentMediaItemIndex }.getOrDefault(-1)
                val trackSourceKey = item?.let { "${it.mediaId}@$idx" }
                val alreadyTriggeredForTrack = trackSourceKey != null && trackSourceKey == lastCrossfadeSourceKey

                val shouldTrigger = CrossfadeMath.shouldTriggerCrossfade(
                    crossfadeSeconds = settingsFadeSec,
                    durationMs = duration,
                    positionMs = pos,
                    hasNextItem = hasNext,
                    repeatMode = repeatMode,
                    playbackSpeed = speed,
                    stopAfterCurrentTrack = stopAfterCurrentTrack,
                    alreadyTransitioning = activeFadeInActive ||
                        crossfadeFadeJob?.isActive == true ||
                        alreadyTriggeredForTrack
                )

                if (shouldTrigger && item != null) {
                    lastCrossfadeSourceKey = trackSourceKey
                    val remainingWallMs = ((duration - pos).coerceAtLeast(0L) / speed).toLong()
                    val effectiveFadeMs = minOf(settingsFadeSec * 1000L, remainingWallMs)
                        .coerceAtLeast(350L)
                    launchOverlappingCrossfade(player, item, duration, effectiveFadeMs)
                    break
                }

                val delayMs = CrossfadeMath.computeMonitorDelayMs(
                    crossfadeSeconds = settingsFadeSec,
                    durationMs = duration,
                    positionMs = pos,
                    playbackSpeed = speed
                )
                delay(delayMs)
            }
        }
    }

    private fun launchOverlappingCrossfade(
        mainPlayer: Player,
        outgoingItem: MediaItem,
        outgoingDurationMs: Long,
        fadeWallMs: Long
    ) {
        crossfadeFadeJob?.cancel()
        crossfadeFadeJob = serviceScope.launch {
            performOverlappingCrossfade(mainPlayer, outgoingItem, outgoingDurationMs, fadeWallMs)
        }
    }

    private suspend fun performOverlappingCrossfade(
        mainPlayer: Player,
        outgoingItem: MediaItem,
        outgoingDurationMs: Long,
        fadeWallMs: Long
    ) {
        val speed = currentSettings.playbackSpeed.coerceAtLeast(0.5f)
        val outgoingBaseGain = currentReplayGainMultiplier
        val prePosMs = runCatching { mainPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
        val incomingItem = runCatching {
            val nextIdx = mainPlayer.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) mainPlayer.getMediaItemAt(nextIdx) else null
        }.getOrNull() ?: return

        val prewarmLeadMs = 300L
        val maxSafeCuePos = (outgoingDurationMs - 450L).coerceAtLeast(prePosMs)
        var targetCuePosMs = (prePosMs + prewarmLeadMs).coerceAtMost(maxSafeCuePos)

        val outgoing = getOrCreateOutgoingPlayer()
        outgoingEqProcessor.updateSettings(
            eqEnabled = currentSettings.eqEnabled,
            preampDb = currentSettings.preampDb,
            bandGainsMb = currentSettings.bandGainsMb
        )
        outgoingBalanceProcessor.setBalance(currentSettings.stereoBalance)
        runCatching {
            outgoing.setPlaybackParameters(PlaybackParameters(speed))
            outgoing.volume = 0f
            outgoing.playWhenReady = false
            outgoing.setMediaItem(outgoingItem, targetCuePosMs)
            outgoing.prepare()
        }

        // Wait silently while mainPlayer keeps playing outgoingItem uninterrupted until outgoing decoder is ready
        var waitedReadyMs = 0L
        var outgoingReady = false
        while (!isServiceDestroyed && waitedReadyMs < 1_200L) {
            val mainPlaying = runCatching {
                mainPlayer.isPlaying && mainPlayer.currentMediaItem?.mediaId == outgoingItem.mediaId
            }.getOrDefault(false)
            if (!mainPlaying) {
                stopOutgoingPlayer(outgoing)
                return
            }
            val remainingNow = (outgoingDurationMs - runCatching { mainPlayer.currentPosition }.getOrDefault(0L))
            if (remainingNow < 400L) {
                stopOutgoingPlayer(outgoing)
                return
            }
            val state = runCatching { outgoing.playbackState }.getOrDefault(Player.STATE_IDLE)
            if (state == Player.STATE_READY) {
                outgoingReady = true
                break
            }
            if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) break
            delay(10L)
            waitedReadyMs += 10L
        }

        if (!outgoingReady || isServiceDestroyed) {
            stopOutgoingPlayer(outgoing)
            return
        }

        // Synchronize outgoing position accurately with mainPlayer before starting AudioTrack
        val currentMainPos = runCatching { mainPlayer.currentPosition }.getOrDefault(0L)
        if (currentMainPos > targetCuePosMs + 60L) {
            targetCuePosMs = (currentMainPos + 65L).coerceAtMost((outgoingDurationMs - 320L).coerceAtLeast(0L))
            runCatching { outgoing.seekTo(targetCuePosMs) }
            var reSeekWaitMs = 0L
            while (!isServiceDestroyed && reSeekWaitMs < 250L) {
                if (runCatching { outgoing.playbackState == Player.STATE_READY }.getOrDefault(false)) break
                delay(10L)
                reSeekWaitMs += 10L
            }
        } else if (currentMainPos < targetCuePosMs - 40L) {
            var syncWaitMs = 0L
            while (!isServiceDestroyed && syncWaitMs < 400L) {
                val posNow = runCatching { mainPlayer.currentPosition }.getOrDefault(0L)
                if (posNow >= targetCuePosMs - 30L) break
                if (!runCatching { mainPlayer.isPlaying }.getOrDefault(false)) {
                    stopOutgoingPlayer(outgoing)
                    return
                }
                delay(10L)
                syncWaitMs += 10L
            }
        }

        // Start outgoing audio pipeline silently so it is actively outputting frames on handoff
        runCatching {
            outgoing.volume = 0f
            outgoing.play()
        }
        var playStartWaitMs = 0L
        while (!isServiceDestroyed && playStartWaitMs < 150L) {
            if (runCatching { outgoing.isPlaying }.getOrDefault(false)) break
            delay(10L)
            playStartWaitMs += 10L
        }

        val stillValidBeforeHandoff = !isServiceDestroyed &&
            runCatching {
                mainPlayer.isPlaying && mainPlayer.currentMediaItem?.mediaId == outgoingItem.mediaId
            }.getOrDefault(false)
        if (!stillValidBeforeHandoff) {
            stopOutgoingPlayer(outgoing)
            return
        }

        val handoffPosMs = runCatching { mainPlayer.currentPosition }.getOrDefault(0L)
        val remainingAtHandoffWallMs = ((outgoingDurationMs - handoffPosMs).coerceAtLeast(350L) / speed).toLong()
        val actualFadeWallMs = minOf(fadeWallMs, remainingAtHandoffWallMs).coerceAtLeast(350L)

        activeFadeInActive = true
        lastCrossfadeIncomingMediaId = incomingItem.mediaId
        lastCrossfadeAdvanceAtMs = SystemClock.elapsedRealtime()
        awaitingCrossfadeMediaTransition = true
        awaitingCrossfadeDiscontinuity = true
        runCatching { outgoing.volume = outgoingBaseGain }
        runCatching { mainPlayer.volume = 0f }
        isAdvancingViaCrossfade = true
        try {
            mainPlayer.seekToNextMediaItem()
            if (!mainPlayer.playWhenReady) {
                mainPlayer.play()
            }
        } finally {
            isAdvancingViaCrossfade = false
        }

        try {
            // Let incoming track finish buffering cleanly while outgoing continues at full gain
            var incomingBufferWaitMs = 0L
            while (!isServiceDestroyed && activeFadeInActive && incomingBufferWaitMs < 450L) {
                val mainOk = runCatching {
                    mainPlayer.playWhenReady && mainPlayer.playbackState != Player.STATE_IDLE
                }.getOrDefault(false)
                if (!mainOk) break
                if (runCatching { mainPlayer.isPlaying }.getOrDefault(false)) break
                delay(15L)
                incomingBufferWaitMs += 15L
            }

            val outRemainingAfterWaitMs = runCatching {
                val outPos = outgoing.currentPosition.coerceAtLeast(0L)
                ((outgoingDurationMs - outPos).coerceAtLeast(300L) / speed).toLong()
            }.getOrDefault(actualFadeWallMs)
            val loopFadeWallMs = minOf(actualFadeWallMs, outRemainingAfterWaitMs).coerceAtLeast(300L)
            val fadeStartMs = SystemClock.elapsedRealtime()

            while (!isServiceDestroyed && activeFadeInActive) {
                val mainActive = runCatching {
                    mainPlayer.currentMediaItem?.mediaId == incomingItem.mediaId &&
                        mainPlayer.playWhenReady &&
                        mainPlayer.playbackState != Player.STATE_IDLE &&
                        mainPlayer.playbackState != Player.STATE_ENDED
                }.getOrDefault(false)
                if (!mainActive) break

                val elapsed = (SystemClock.elapsedRealtime() - fadeStartMs).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / loopFadeWallMs.toFloat()).coerceIn(0f, 1f)
                val (outFactor, inFactor) = CrossfadeMath.equalPowerGains(progress)

                runCatching { mainPlayer.volume = currentReplayGainMultiplier * inFactor }
                val outState = runCatching { outgoing.playbackState }.getOrDefault(Player.STATE_ENDED)
                if (outState != Player.STATE_ENDED && outState != Player.STATE_IDLE) {
                    runCatching { outgoing.volume = outgoingBaseGain * outFactor }
                }

                if (progress >= 1f) break
                delay(CROSSFADE_STEP_MS)
            }
        } finally {
            activeFadeInActive = false
            awaitingCrossfadeMediaTransition = false
            awaitingCrossfadeDiscontinuity = false
            if (!isServiceDestroyed) {
                runCatching { mainPlayer.volume = currentReplayGainMultiplier }
            }
            stopOutgoingPlayer(outgoing)
        }
    }

    private fun persistCurrentSession(player: Player, synchronous: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!synchronous && now - lastPersistedAtMs < 400L && player.isPlaying) return
        lastPersistedAtMs = now
        val count = player.mediaItemCount
        if (count == 0) return

        val rawIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val posMs = player.currentPosition.coerceAtLeast(0L)
        val shuffle = player.shuffleModeEnabled
        val repeat = player.repeatMode

        val canUseCachedTimeline = !cachedTimelineDirty &&
            cachedTimelineCount == count &&
            cachedWindowTrackIds.size == count &&
            rawIndex in cachedWindowTrackIds.indices

        if (canUseCachedTimeline && hasPersistedExactStructureInService) {
            val currentMediaId = cachedWindowTrackIds[rawIndex]
            if (currentMediaId.isNotBlank()) {
                val writeBlock = {
                    val updated = prefsStore.updateExactPosition(
                        trackId = currentMediaId,
                        queueIndex = rawIndex,
                        positionMs = posMs,
                        shuffleEnabled = shuffle,
                        repeatMode = repeat
                    )
                    if (!updated) {
                        prefsStore.updateExactFromService(
                            trackId = currentMediaId,
                            windowTrackIds = cachedWindowTrackIds,
                            windowIndex = rawIndex,
                            positionMs = posMs,
                            shuffleEnabled = shuffle,
                            repeatMode = repeat
                        )
                    }
                }
                if (synchronous) {
                    writeBlock()
                } else {
                    persistSessionJob?.cancel()
                    persistSessionJob = serviceScope.launch(Dispatchers.IO) { writeBlock() }
                }
                return
            }
        }

        val halfWindow = MAX_PERSISTED_ITEMS / 2
        val startIdx = if (count <= MAX_PERSISTED_ITEMS) 0 else (rawIndex - halfWindow).coerceIn(0, count - MAX_PERSISTED_ITEMS)
        val endIdxExclusive = (startIdx + MAX_PERSISTED_ITEMS).coerceAtMost(count)

        val windowIds = ArrayList<String>(count)
        val snapshots = ArrayList<SavedMediaItemSnapshot>(endIdxExclusive - startIdx)
        for (idx in 0 until count) {
            val item = runCatching { player.getMediaItemAt(idx) }.getOrNull() ?: continue
            val meta = item.mediaMetadata
            val trackId = meta.extras?.getString(PlaybackController.EXTRA_TRACK_ID) ?: item.mediaId
            windowIds.add(trackId)
            if (idx in startIdx until endIdxExclusive) {
                val uri = item.localConfiguration?.uri?.toString() ?: continue
                snapshots.add(
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
                )
            }
        }
        if (snapshots.isEmpty()) return
        cachedTimelineCount = count
        cachedWindowTrackIds = windowIds
        cachedTimelineSnapshots = snapshots
        cachedSnapshotWindowStart = startIdx
        cachedTimelineDirty = false
        hasPersistedExactStructureInService = true

        val currentMediaId = player.currentMediaItem?.let { item ->
            item.mediaMetadata.extras?.getString(PlaybackController.EXTRA_TRACK_ID) ?: item.mediaId
        }.orEmpty()

        val session = SavedPlaybackSession(
            items = snapshots,
            currentIndex = (rawIndex - startIdx).coerceIn(0, snapshots.lastIndex),
            positionMs = posMs,
            shuffleEnabled = shuffle,
            repeatMode = repeat
        )
        if (synchronous) {
            prefsStore.savePlaybackSession(session)
            if (currentMediaId.isNotBlank()) {
                prefsStore.updateExactFromService(
                    trackId = currentMediaId,
                    windowTrackIds = windowIds,
                    windowIndex = rawIndex,
                    positionMs = posMs,
                    shuffleEnabled = shuffle,
                    repeatMode = repeat
                )
            }
        } else {
            persistSessionJob?.cancel()
            persistSessionJob = serviceScope.launch(Dispatchers.IO) {
                prefsStore.savePlaybackSession(session)
                if (currentMediaId.isNotBlank()) {
                    prefsStore.updateExactFromService(
                        trackId = currentMediaId,
                        windowTrackIds = windowIds,
                        windowIndex = rawIndex,
                        positionMs = posMs,
                        shuffleEnabled = shuffle,
                        repeatMode = repeat
                    )
                }
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
        val signature = "${item?.mediaId.orEmpty()}|$title|$artist|${artworkUri.orEmpty()}|$playing"
        if (signature == lastWidgetSignature) return
        lastWidgetSignature = signature

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
                .add(SessionCommand(ACTION_SET_STOP_AFTER_CURRENT, Bundle.EMPTY))
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
                ACTION_SET_STOP_AFTER_CURRENT -> {
                    stopAfterCurrentTrack = args.getBoolean(ARG_STOP_AFTER_CURRENT, false)
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

    companion object {
        internal fun computeReplayGainSplit(
            trackGainDb: Float?,
            settings: AudioSettings
        ): Pair<Float, Float> {
            if (!settings.replayGainEnabled) return 1.0f to 0f
            val effectiveDb = if (trackGainDb != null && trackGainDb.isFinite()) {
                trackGainDb + settings.replayGainPreampDb.coerceIn(-6f, 12f)
            } else {
                settings.replayGainUntaggedPreampDb.coerceIn(-12f, 0f)
            }
            val attenuationLinear = if (effectiveDb < 0f) {
                10.0.pow(effectiveDb / 20.0).toFloat().coerceIn(0.15f, 1.0f)
            } else {
                1.0f
            }
            val positiveBoostDb = if (effectiveDb > 0f) {
                effectiveDb.coerceIn(0f, 12f)
            } else {
                0f
            }
            return attenuationLinear to positiveBoostDb
        }

        internal fun audioSettingsFromBundle(args: Bundle): AudioSettings? {
            if (!args.containsKey(ARG_EQ_ENABLED)) return null
            val presetName = args.getString(ARG_EQ_PRESET)
            val preset = com.example.model.EqPreset.entries.firstOrNull { it.name == presetName }
                ?: com.example.model.EqPreset.FLAT
            val gains = com.example.model.EqPreset.normalizeToTenBands(
                args.getIntArray(ARG_BAND_GAINS_MB)?.toList().orEmpty()
            )
            return AudioSettings(
                eqEnabled = args.getBoolean(ARG_EQ_ENABLED, false),
                eqPreset = preset,
                bandGainsMb = gains,
                preampDb = args.getFloat(ARG_PREAMP_DB, 0f),
                activeProfileId = args.getString(ARG_PROFILE_ID),
                activeProfileName = args.getString(ARG_PROFILE_NAME),
                bassBoostStrength = args.getInt(ARG_BASS_BOOST, 0),
                virtualizerStrength = args.getInt(ARG_VIRTUALIZER, 0),
                crossfadeSeconds = args.getInt(ARG_CROSSFADE, 0),
                gaplessEnabled = args.getBoolean(ARG_GAPLESS, true),
                replayGainEnabled = args.getBoolean(ARG_REPLAYGAIN, false),
                replayGainPreampDb = args.getFloat(ARG_REPLAYGAIN_PREAMP_DB, 3.0f),
                replayGainUntaggedPreampDb = args.getFloat(ARG_REPLAYGAIN_UNTAGGED_PREAMP_DB, -4.0f),
                playbackSpeed = args.getFloat(ARG_SPEED, 1.0f),
                stereoBalance = args.getFloat(ARG_BALANCE, 0f),
                dynamicArtworkColors = args.getBoolean(ARG_DYNAMIC_COLORS, true),
                fadeOnPauseEnabled = args.getBoolean(ARG_FADE_ON_PAUSE, false)
            )
        }

        const val NOTIFICATION_CHANNEL_ID = "sonora_playback_channel"
        const val ACTION_SEEK_BACK_10 = "com.example.playback.ACTION_SEEK_BACK_10"
        const val ACTION_SEEK_FORWARD_10 = "com.example.playback.ACTION_SEEK_FORWARD_10"
        const val ACTION_APPLY_AUDIO_SETTINGS = "com.example.playback.ACTION_APPLY_AUDIO_SETTINGS"
        const val ACTION_SET_STOP_AFTER_CURRENT = "com.example.playback.ACTION_SET_STOP_AFTER_CURRENT"

        const val ARG_EQ_ENABLED = "arg_eq_enabled"
        const val ARG_EQ_PRESET = "arg_eq_preset"
        const val ARG_BAND_GAINS_MB = "arg_band_gains_mb"
        const val ARG_PREAMP_DB = "arg_preamp_db"
        const val ARG_PROFILE_ID = "arg_profile_id"
        const val ARG_PROFILE_NAME = "arg_profile_name"
        const val ARG_BASS_BOOST = "arg_bass_boost"
        const val ARG_VIRTUALIZER = "arg_virtualizer"
        const val ARG_CROSSFADE = "arg_crossfade"
        const val ARG_GAPLESS = "arg_gapless"
        const val ARG_REPLAYGAIN = "arg_replaygain"
        const val ARG_REPLAYGAIN_PREAMP_DB = "arg_replaygain_preamp_db"
        const val ARG_REPLAYGAIN_UNTAGGED_PREAMP_DB = "arg_replaygain_untagged_preamp_db"
        const val ARG_SPEED = "arg_speed"
        const val ARG_BALANCE = "arg_balance"
        const val ARG_DYNAMIC_COLORS = "arg_dynamic_colors"
        const val ARG_FADE_ON_PAUSE = "arg_fade_on_pause"
        const val ARG_STOP_AFTER_CURRENT = "arg_stop_after_current"

        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_PREV"

        private const val SEEK_INCREMENT_MS = 10_000L
        private const val TRACK_GAP_PAUSE_MS = 450L
        private const val CROSSFADE_STEP_MS = 30L
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
