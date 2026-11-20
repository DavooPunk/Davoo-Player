package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.repository.LastFmRepository
import com.example.data.repository.LastFmScrobblePolicy
import com.example.data.repository.MusicRepository
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTechSpecs
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.EqBandInfo
import com.example.model.EqPreset
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentTrack: AudioTrack? = null,
    val queue: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val techSpecs: AudioTechSpecs? = null,
    val metadata: AudioTrackMetadata? = null,
    val playbackError: String? = null
)

class PlaybackController(
    private val context: Context,
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val lastFmRepository: LastFmRepository? = null
) {
    private val prefsStore = PlaybackPreferencesStore(context)
    private val bandHelper = AudioEffectsController()

    private data class PendingPlayRequest(
        val tracks: List<AudioTrack>,
        val startIndex: Int,
        val shuffle: Boolean
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingPlayRequest: PendingPlayRequest? = null
    private var tickerJob: Job? = null
    private var techSpecJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var settingsSyncJob: Job? = null
    private var scrobbleDismissJob: Job? = null
    @Volatile
    private var trackLookup: Map<String, AudioTrack> = emptyMap()
    private var lastRecordedTrackId: String? = null
    private var restoredSessionAttempted: Boolean = false

    private var currentScrobbleTrackId: String? = null
    private var currentScrobbleStartEpochSec: Long = 0L
    private var nowPlayingReportedForSession: Boolean = false
    private var scrobbledCurrentSession: Boolean = false

    private val _scrobbleNotice = MutableStateFlow<String?>(null)
    val scrobbleNotice: StateFlow<String?> = _scrobbleNotice.asStateFlow()

    private val _playerFlow = MutableStateFlow<Player?>(null)
    val playerFlow: StateFlow<Player?> = _playerFlow.asStateFlow()

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _audioSettings = MutableStateFlow(prefsStore.loadAudioSettings())
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()

    private val _eqBands = MutableStateFlow(bandHelper.getBands(_audioSettings.value))
    val eqBands: StateFlow<List<EqBandInfo>> = _eqBands.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private val _stopAfterCurrentTrack = MutableStateFlow(false)
    val stopAfterCurrentTrack: StateFlow<Boolean> = _stopAfterCurrentTrack.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY
                )
            ) {
                syncFromPlayer(player)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_stopAfterCurrentTrack.value &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            ) {
                _stopAfterCurrentTrack.value = false
                mediaController?.pause()
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            ) {
                resetCurrentScrobbleSession()
            }
            mediaController?.let { syncFromPlayer(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val pos = mediaController?.currentPosition?.coerceAtLeast(0L)
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    positionMs = pos ?: it.positionMs
                )
            }
            if (isPlaying) {
                startPositionTicker()
                recordCurrentTrackPlayIfNeeded()
                _uiState.value.currentTrack?.let { reportLastFmNowPlayingIfNeeded(it) }
            } else {
                stopPositionTicker()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    playbackError = "No se pudo reproducir la pista (${error.errorCodeName})"
                )
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    _playerFlow.value = controller
                    controller.addListener(playerListener)
                    val pending = pendingPlayRequest
                    pendingPlayRequest = null
                    if (pending != null) {
                        playTracks(pending.tracks, pending.startIndex, pending.shuffle)
                    } else {
                        restoreSavedSessionIfIdle(controller)
                        syncFromPlayer(controller)
                    }
                    sendAudioSettingsToService(controller)
                    if (controller.isPlaying) startPositionTicker()
                }.onFailure {
                    controllerFuture = null
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        stopPositionTicker()
        sleepTimerJob?.cancel()
        techSpecJob?.cancel()
        scrobbleDismissJob?.cancel()
        if (settingsSyncJob?.isActive == true) {
            settingsSyncJob?.cancel()
            prefsStore.saveAudioSettings(_audioSettings.value)
        }
        settingsSyncJob = null
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        bandHelper.release()
        trackLookup = emptyMap()
        _playerFlow.value = null
    }

    fun clearScrobbleNotice() {
        scrobbleDismissJob?.cancel()
        _scrobbleNotice.value = null
    }

    fun clearError() {
        _uiState.update { it.copy(playbackError = null) }
    }

    fun updateAudioSettings(newSettings: AudioSettings, immediate: Boolean = true) {
        val sanitized = newSettings.copy(
            bandGainsMb = EqPreset.normalizeToTenBands(newSettings.bandGainsMb),
            preampDb = newSettings.preampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 12f) ?: 0f,
            bassBoostStrength = newSettings.bassBoostStrength.coerceIn(0, 1000),
            virtualizerStrength = newSettings.virtualizerStrength.coerceIn(0, 1000),
            crossfadeSeconds = newSettings.crossfadeSeconds.coerceIn(0, 12),
            playbackSpeed = newSettings.playbackSpeed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.0f) ?: 1.0f,
            stereoBalance = newSettings.stereoBalance.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        )
        _audioSettings.value = sanitized
        _eqBands.value = bandHelper.getBands(sanitized)

        settingsSyncJob?.cancel()
        if (immediate) {
            settingsSyncJob = null
            prefsStore.saveAudioSettings(sanitized)
            mediaController?.let { sendAudioSettingsToService(it, sanitized) }
        } else {
            settingsSyncJob = scope.launch {
                delay(45L)
                prefsStore.saveAudioSettings(sanitized)
                mediaController?.let { sendAudioSettingsToService(it, sanitized) }
            }
        }
    }

    fun startSleepTimer(minutes: Int?, afterCurrentTrack: Boolean) {
        sleepTimerJob?.cancel()
        _stopAfterCurrentTrack.value = afterCurrentTrack
        if (afterCurrentTrack || minutes == null || minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }
        val totalMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining -= 1000L
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            if (isActive) {
                _sleepTimerRemainingMs.value = null
                _stopAfterCurrentTrack.value = false
                mediaController?.pause()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
        _stopAfterCurrentTrack.value = false
    }

    fun playTracks(tracks: List<AudioTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        trackLookup = tracks.associateBy { it.id }
        lastRecordedTrackId = null
        resetCurrentScrobbleSession()

        val controller = mediaController
        if (controller == null) {
            pendingPlayRequest = PendingPlayRequest(tracks, safeIndex, shuffle)
            connect()
            return
        }

        // Bound queue window sent over Binder IPC so huge libraries never trigger TransactionTooLargeException
        val windowStart = if (tracks.size <= MAX_IPC_QUEUE_SIZE) {
            0
        } else {
            (safeIndex - MAX_IPC_QUEUE_SIZE / 2).coerceIn(0, tracks.size - MAX_IPC_QUEUE_SIZE)
        }
        val windowEnd = (windowStart + MAX_IPC_QUEUE_SIZE).coerceAtMost(tracks.size)
        val windowedTracks = tracks.subList(windowStart, windowEnd)
        val adjustedIndex = (safeIndex - windowStart).coerceIn(0, windowedTracks.lastIndex)

        val mediaItems = windowedTracks.map { it.toMediaItem() }
        controller.shuffleModeEnabled = shuffle
        controller.setMediaItems(mediaItems, adjustedIndex, 0L)
        controller.prepare()
        controller.play()
        syncFromPlayer(controller)
        recordCurrentTrackPlayIfNeeded()
    }

    fun playNext(track: AudioTrack) {
        val controller = mediaController ?: return
        trackLookup = trackLookup + (track.id to track)
        if (controller.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, track.toMediaItem())
        syncFromPlayer(controller)
    }

    fun enqueueTrack(track: AudioTrack) {
        val controller = mediaController ?: return
        trackLookup = trackLookup + (track.id to track)
        if (controller.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
            return
        }
        controller.addMediaItem(track.toMediaItem())
        syncFromPlayer(controller)
    }

    fun enqueueTracks(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val controller = mediaController ?: return
        trackLookup = trackLookup + tracks.associateBy { it.id }
        if (controller.mediaItemCount == 0) {
            playTracks(tracks, 0)
            return
        }
        controller.addMediaItems(tracks.take(MAX_IPC_QUEUE_SIZE).map { it.toMediaItem() })
        syncFromPlayer(controller)
    }

    val player: Player?
        get() = mediaController

    fun play() {
        val controller = mediaController ?: return
        if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
            controller.prepare()
        }
        controller.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val maxDuration = controller.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val clamped = positionMs.coerceIn(0L, maxDuration)
        controller.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun seekBackward(deltaMs: Long = 10_000L) {
        val controller = mediaController ?: return
        seekTo(controller.currentPosition - deltaMs)
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        val controller = mediaController ?: return
        seekTo(controller.currentPosition + deltaMs)
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
            syncFromPlayer(controller)
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        syncFromPlayer(controller)
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncFromPlayer(controller)
    }

    /** Refresh track metadata inside active UI state when favorites or tags are edited. */
    fun refreshTrackReferences(updatedTracks: List<AudioTrack>) {
        if (updatedTracks.isEmpty()) return
        trackLookup = trackLookup + updatedTracks.associateBy { it.id }
        _uiState.update { state ->
            val current = state.currentTrack?.let { trackLookup[it.id] ?: it }
            val updatedQueue = state.queue.map { q -> trackLookup[q.id] ?: q }
            state.copy(currentTrack = current, queue = updatedQueue)
        }
    }

    /** Immediately reflect favorite state changes in active playback track and queue. */
    fun setTrackFavorite(trackId: String, isFavorite: Boolean) {
        trackLookup[trackId]?.let { existing ->
            trackLookup = trackLookup + (trackId to existing.copy(isFavorite = isFavorite))
        }
        _uiState.update { state ->
            val updatedCurrent = state.currentTrack?.let { current ->
                if (current.id == trackId) current.copy(isFavorite = isFavorite) else current
            }
            val updatedQueue = state.queue.map { q ->
                if (q.id == trackId) q.copy(isFavorite = isFavorite) else q
            }
            state.copy(currentTrack = updatedCurrent, queue = updatedQueue)
        }
    }

    private fun restoreSavedSessionIfIdle(controller: MediaController) {
        if (restoredSessionAttempted || controller.mediaItemCount > 0) return
        restoredSessionAttempted = true
        val saved = prefsStore.loadPlaybackSession() ?: return
        if (saved.items.isEmpty()) return
        val items = saved.items.map { prefsStore.savedItemToMediaItem(it) }
        val idx = saved.currentIndex.coerceIn(0, items.lastIndex)
        controller.shuffleModeEnabled = saved.shuffleEnabled
        controller.repeatMode = saved.repeatMode
        controller.setMediaItems(items, idx, saved.positionMs)
        controller.prepare()
    }

    private fun sendAudioSettingsToService(
        controller: MediaController,
        settings: AudioSettings = _audioSettings.value
    ) {
        runCatching {
            val args = Bundle().apply {
                putBoolean(PlaybackService.ARG_EQ_ENABLED, settings.eqEnabled)
                putString(PlaybackService.ARG_EQ_PRESET, settings.eqPreset.name)
                putIntArray(PlaybackService.ARG_BAND_GAINS_MB, settings.bandGainsMb.toIntArray())
                putFloat(PlaybackService.ARG_PREAMP_DB, settings.preampDb)
                settings.activeProfileId?.let { putString(PlaybackService.ARG_PROFILE_ID, it) }
                settings.activeProfileName?.let { putString(PlaybackService.ARG_PROFILE_NAME, it) }
                putInt(PlaybackService.ARG_BASS_BOOST, settings.bassBoostStrength)
                putInt(PlaybackService.ARG_VIRTUALIZER, settings.virtualizerStrength)
                putInt(PlaybackService.ARG_CROSSFADE, settings.crossfadeSeconds)
                putBoolean(PlaybackService.ARG_GAPLESS, settings.gaplessEnabled)
                putBoolean(PlaybackService.ARG_REPLAYGAIN, settings.replayGainEnabled)
                putFloat(PlaybackService.ARG_SPEED, settings.playbackSpeed)
                putFloat(PlaybackService.ARG_BALANCE, settings.stereoBalance)
                putBoolean(PlaybackService.ARG_DYNAMIC_COLORS, settings.dynamicArtworkColors)
            }
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_APPLY_AUDIO_SETTINGS, Bundle.EMPTY),
                args
            )
        }
    }

    private fun syncFromPlayer(player: Player) {
        val count = player.mediaItemCount
        val queue = buildList(count) {
            for (i in 0 until count) {
                val item = runCatching { player.getMediaItemAt(i) }.getOrNull() ?: continue
                resolveTrackFromMediaItem(item)?.let { add(it) }
            }
        }
        val currentIdx = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentTrack = if (currentIdx >= 0) queue[currentIdx] else null
        val duration = player.duration.takeIf { it > 0 } ?: (currentTrack?.durationMs ?: 0L)
        val previousTrackId = _uiState.value.currentTrack?.id

        _uiState.update {
            it.copy(
                currentTrack = currentTrack,
                queue = queue,
                currentIndex = currentIdx,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        }

        if (currentTrack != null && currentTrack.id != previousTrackId) {
            resetCurrentScrobbleSession()
            loadTrackMetadataAndSpecs(currentTrack)
            if (player.isPlaying) {
                recordCurrentTrackPlayIfNeeded()
                reportLastFmNowPlayingIfNeeded(currentTrack)
            }
        } else if (currentTrack == null) {
            resetCurrentScrobbleSession()
            techSpecJob?.cancel()
            _uiState.update { it.copy(techSpecs = null, metadata = null) }
        }
    }

    private fun resetCurrentScrobbleSession() {
        currentScrobbleTrackId = null
        currentScrobbleStartEpochSec = 0L
        nowPlayingReportedForSession = false
        scrobbledCurrentSession = false
    }

    private fun reportLastFmNowPlayingIfNeeded(track: AudioTrack) {
        if (currentScrobbleTrackId != track.id) {
            currentScrobbleTrackId = track.id
            currentScrobbleStartEpochSec = System.currentTimeMillis() / 1000L
            nowPlayingReportedForSession = false
            scrobbledCurrentSession = false
        }
        if (nowPlayingReportedForSession) return
        val lfm = lastFmRepository ?: return
        if (!lfm.hasActiveSession()) return
        nowPlayingReportedForSession = true
        scope.launch(Dispatchers.IO) {
            lfm.updateNowPlaying(
                artist = track.artist,
                track = track.title,
                album = track.album,
                durationMs = track.durationMs
            )
        }
    }

    private fun maybeSendLastFmScrobble(track: AudioTrack, positionMs: Long, durationMs: Long) {
        val lfm = lastFmRepository ?: return
        if (!lfm.hasActiveSession()) return
        if (currentScrobbleTrackId != track.id) {
            currentScrobbleTrackId = track.id
            currentScrobbleStartEpochSec = System.currentTimeMillis() / 1000L
            nowPlayingReportedForSession = false
            scrobbledCurrentSession = false
        }
        if (!LastFmScrobblePolicy.shouldScrobble(
                trackId = track.id,
                positionMs = positionMs,
                durationMs = durationMs,
                alreadyScrobbled = scrobbledCurrentSession
            )
        ) {
            return
        }
        scrobbledCurrentSession = true
        val startTimestamp = currentScrobbleStartEpochSec.takeIf { it > 0L }
            ?: (System.currentTimeMillis() / 1000L)
        scope.launch(Dispatchers.IO) {
            lfm.scrobbleTrack(
                artist = track.artist,
                track = track.title,
                timestampSec = startTimestamp,
                album = track.album,
                durationMs = durationMs
            ).onSuccess {
                _scrobbleNotice.value = "Scrobble enviado"
                scrobbleDismissJob?.cancel()
                scrobbleDismissJob = scope.launch {
                    delay(4_000L)
                    _scrobbleNotice.value = null
                }
            }
        }
    }

    private fun recordCurrentTrackPlayIfNeeded() {
        val track = _uiState.value.currentTrack ?: return
        if (track.id == lastRecordedTrackId) return
        lastRecordedTrackId = track.id
        scope.launch(Dispatchers.IO) {
            runCatching { repository.recordPlay(track.id) }
        }
    }

    private fun loadTrackMetadataAndSpecs(track: AudioTrack) {
        techSpecJob?.cancel()
        techSpecJob = scope.launch(Dispatchers.IO) {
            val metadata = runCatching { repository.extractTrackMetadata(track) }.getOrNull() ?: return@launch
            _uiState.update { state ->
                if (state.currentTrack?.id == track.id) {
                    state.copy(techSpecs = metadata.techSpecs, metadata = metadata)
                } else state
            }
        }
    }

    private fun startPositionTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val controller = mediaController ?: break
                if (!controller.isPlaying) break
                val pos = controller.currentPosition.coerceAtLeast(0L)
                val currentTrack = _uiState.value.currentTrack
                val dur = controller.duration.takeIf { it > 0 }
                    ?: (currentTrack?.durationMs ?: 0L)
                _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                if (currentTrack != null && dur > 0L) {
                    maybeSendLastFmScrobble(currentTrack, pos, dur)
                }
                delay(350L)
            }
        }
    }

    private fun stopPositionTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun AudioTrack.toMediaItem(): MediaItem {
        // Keep Bundle extras lean so large queues stay well under Android Binder IPC limits
        val extras = Bundle().apply {
            putString(EXTRA_TRACK_ID, id)
            putString(EXTRA_FORMAT, format.name)
            putLong(EXTRA_DURATION_MS, durationMs)
            albumArtUri?.let { putString(EXTRA_ALBUM_ART_URI, it) }
            replayGainDb?.let { putFloat(EXTRA_REPLAY_GAIN_DB, it) }
        }
        val artworkSourceUri = Uri.parse(albumArtUri ?: contentUri)
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist.ifBlank { artist })
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(artworkSourceUri)
            .apply {
                if (genre.isNotBlank()) setGenre(genre)
                if (trackNumber > 0) setTrackNumber(trackNumber)
                if (year > 0) setReleaseYear(year)
            }
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(contentUri))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun resolveTrackFromMediaItem(item: MediaItem): AudioTrack? {
        trackLookup[item.mediaId]?.let { return it }
        val meta = item.mediaMetadata
        val extras = meta.extras
        val uri = item.localConfiguration?.uri?.toString() ?: return null
        val formatName = extras?.getString(EXTRA_FORMAT) ?: AudioFormat.MP3.name
        val format = AudioFormat.entries.firstOrNull { it.name == formatName } ?: AudioFormat.MP3
        val gain = extras?.let {
            val v = it.getFloat(EXTRA_REPLAY_GAIN_DB, Float.NaN)
            if (v.isNaN()) null else v
        }
        return AudioTrack(
            id = extras?.getString(EXTRA_TRACK_ID) ?: item.mediaId,
            contentUri = uri,
            title = meta.title?.toString() ?: "Pista",
            artist = meta.artist?.toString() ?: "Artista desconocido",
            album = meta.albumTitle?.toString() ?: "Álbum desconocido",
            albumId = -1L,
            albumArtUri = extras?.getString(EXTRA_ALBUM_ART_URI) ?: meta.artworkUri?.toString(),
            durationMs = extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
            format = format,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            dateAddedSec = 0L,
            folderName = "Música",
            isSafImported = false,
            albumArtist = meta.albumArtist?.toString().orEmpty(),
            genre = meta.genre?.toString().orEmpty(),
            year = meta.releaseYear ?: 0,
            trackNumber = meta.trackNumber ?: 0,
            replayGainDb = gain,
            filePath = ""
        )
    }

    companion object {
        private const val MAX_IPC_QUEUE_SIZE = 250
        const val EXTRA_TRACK_ID = "sonora_track_id"
        const val EXTRA_FORMAT = "sonora_format"
        const val EXTRA_DURATION_MS = "sonora_duration_ms"
        const val EXTRA_ALBUM_ART_URI = "sonora_album_art_uri"
        const val EXTRA_REPLAY_GAIN_DB = "sonora_replay_gain_db"
    }
}
