package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.BuildConfig
import com.example.ai.DeepLLyricsTranslator
import com.example.ai.TranslationEngine
import com.example.model.AppThemeMode
import com.example.model.AppearancePreferences
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.model.CustomThemeColors
import com.example.model.EqPreset
import com.example.model.HeadphoneEqProfile
import com.example.model.LastFmSession
import com.example.model.SdCardFolder
import com.example.model.ThemePreset
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
data class SavedMediaItemSnapshot(
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String? = null,
    val formatName: String = "MP3",
    val durationMs: Long = 0L
)

@Serializable
data class SavedPlaybackSession(
    val items: List<SavedMediaItemSnapshot> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

@Serializable
data class ExactPlaybackSession(
    val lastTrackId: String,
    val lastQueueTrackIds: List<String> = emptyList(),
    val lastQueueIndex: Int = 0,
    val lastPositionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

data class RestoredQueuePlan(
    val tracks: List<AudioTrack>,
    val startIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int
)

object PlaybackSessionRestorePlanner {
    fun buildPlan(
        saved: ExactPlaybackSession,
        libraryTracks: List<AudioTrack>
    ): RestoredQueuePlan? {
        if (libraryTracks.isEmpty() || saved.lastTrackId.isBlank()) return null
        val trackById = libraryTracks.associateBy { it.id }
        val reconstructed = if (saved.lastQueueTrackIds.isNotEmpty()) {
            saved.lastQueueTrackIds.mapNotNull { trackById[it] }
        } else {
            emptyList()
        }
        val queue = if (reconstructed.isNotEmpty()) {
            reconstructed
        } else {
            val single = trackById[saved.lastTrackId] ?: return null
            listOf(single)
        }
        val matchingIndices = queue.indices.filter { queue[it].id == saved.lastTrackId }
        val resolvedIndex = if (matchingIndices.isNotEmpty()) {
            matchingIndices.minByOrNull { abs(it - saved.lastQueueIndex) } ?: matchingIndices.first()
        } else {
            saved.lastQueueIndex.coerceIn(0, queue.lastIndex)
        }
        val resolvedPosMs = if (queue[resolvedIndex].id == saved.lastTrackId) {
            saved.lastPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }
        return RestoredQueuePlan(
            tracks = queue,
            startIndex = resolvedIndex,
            positionMs = resolvedPosMs,
            shuffleEnabled = saved.shuffleEnabled,
            repeatMode = saved.repeatMode
        )
    }
}

@Serializable
private data class AudioSettingsDto(
    val eqEnabled: Boolean = false,
    val eqPresetName: String = EqPreset.FLAT.name,
    val bandGainsMb: List<Int> = List(10) { 0 },
    val preampDb: Float = 0f,
    val activeProfileId: String? = null,
    val activeProfileName: String? = null,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val crossfadeSeconds: Int = 0,
    val gaplessEnabled: Boolean = true,
    val replayGainEnabled: Boolean = false,
    val replayGainPreampDb: Float = 3.0f,
    val replayGainUntaggedPreampDb: Float = -4.0f,
    val playbackSpeed: Float = 1.0f,
    val stereoBalance: Float = 0f,
    val dynamicArtworkColors: Boolean = true,
    val fadeOnPauseEnabled: Boolean = false
)

@Serializable
private data class CustomEqProfileDto(
    val id: String,
    val name: String,
    val preampDb: Float = 0f,
    val bandGainsMb: List<Int> = List(10) { 0 }
)

@Serializable
private data class PersistedTreeFolderDto(
    val treeUri: String,
    val displayName: String,
    val addedAtMs: Long = 0L
)

@Serializable
private data class AppearancePreferencesDto(
    val themeModeName: String = AppThemeMode.DARK.name,
    val presetName: String = ThemePreset.COPPER_AMBER.name,
    val primaryArgb: Long = 0xFFF5A623L,
    val secondaryArgb: Long = 0xFF38BDF8L,
    val backgroundArgb: Long = 0xFF0C0E13L,
    val surfaceArgb: Long = 0xFF14171FL,
    val textArgb: Long? = null,
    val dynamicArtworkColors: Boolean = true
)

class PlaybackPreferencesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAudioSettings(): AudioSettings {
        val raw = prefs.getString(KEY_AUDIO_SETTINGS, null) ?: return AudioSettings()
        return runCatching {
            val dto = json.decodeFromString<AudioSettingsDto>(raw)
            AudioSettings(
                eqEnabled = dto.eqEnabled,
                eqPreset = EqPreset.entries.firstOrNull { it.name == dto.eqPresetName } ?: EqPreset.FLAT,
                bandGainsMb = EqPreset.normalizeToTenBands(dto.bandGainsMb),
                preampDb = dto.preampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 12f) ?: 0f,
                activeProfileId = dto.activeProfileId,
                activeProfileName = dto.activeProfileName,
                bassBoostStrength = dto.bassBoostStrength.coerceIn(0, 1000),
                virtualizerStrength = dto.virtualizerStrength.coerceIn(0, 1000),
                crossfadeSeconds = dto.crossfadeSeconds.coerceIn(0, 12),
                gaplessEnabled = dto.gaplessEnabled,
                replayGainEnabled = dto.replayGainEnabled,
                replayGainPreampDb = dto.replayGainPreampDb.takeIf { it.isFinite() }?.coerceIn(-6f, 12f) ?: 3.0f,
                replayGainUntaggedPreampDb = dto.replayGainUntaggedPreampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 0f) ?: -4.0f,
                playbackSpeed = dto.playbackSpeed.coerceIn(0.5f, 2.0f),
                stereoBalance = dto.stereoBalance.coerceIn(-1f, 1f),
                dynamicArtworkColors = dto.dynamicArtworkColors,
                fadeOnPauseEnabled = dto.fadeOnPauseEnabled
            )
        }.getOrDefault(AudioSettings())
    }

    fun saveAudioSettings(settings: AudioSettings) {
        val dto = AudioSettingsDto(
            eqEnabled = settings.eqEnabled,
            eqPresetName = settings.eqPreset.name,
            bandGainsMb = EqPreset.normalizeToTenBands(settings.bandGainsMb),
            preampDb = settings.preampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 12f) ?: 0f,
            activeProfileId = settings.activeProfileId,
            activeProfileName = settings.activeProfileName,
            bassBoostStrength = settings.bassBoostStrength,
            virtualizerStrength = settings.virtualizerStrength,
            crossfadeSeconds = settings.crossfadeSeconds,
            gaplessEnabled = settings.gaplessEnabled,
            replayGainEnabled = settings.replayGainEnabled,
            replayGainPreampDb = settings.replayGainPreampDb.takeIf { it.isFinite() }?.coerceIn(-6f, 12f) ?: 3.0f,
            replayGainUntaggedPreampDb = settings.replayGainUntaggedPreampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 0f) ?: -4.0f,
            playbackSpeed = settings.playbackSpeed,
            stereoBalance = settings.stereoBalance,
            dynamicArtworkColors = settings.dynamicArtworkColors,
            fadeOnPauseEnabled = settings.fadeOnPauseEnabled
        )
        runCatching {
            prefs.edit().putString(KEY_AUDIO_SETTINGS, json.encodeToString(dto)).apply()
        }
    }

    fun loadCustomEqProfiles(): List<HeadphoneEqProfile> {
        val raw = prefs.getString(KEY_CUSTOM_EQ_PROFILES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<CustomEqProfileDto>>(raw).map { dto ->
                HeadphoneEqProfile(
                    id = dto.id,
                    brand = "Personal",
                    model = dto.name,
                    formFactor = "Perfil guardado",
                    preampDb = dto.preampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 12f) ?: 0f,
                    bandGainsMb = EqPreset.normalizeToTenBands(dto.bandGainsMb),
                    isCustom = true
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomEqProfile(profile: HeadphoneEqProfile): List<HeadphoneEqProfile> {
        val current = loadCustomEqProfiles().filterNot { it.id == profile.id }
        val updated = listOf(profile.copy(isCustom = true, bandGainsMb = EqPreset.normalizeToTenBands(profile.bandGainsMb))) + current
        saveCustomEqProfiles(updated)
        return updated
    }

    fun deleteCustomEqProfile(profileId: String): List<HeadphoneEqProfile> {
        val updated = loadCustomEqProfiles().filterNot { it.id == profileId }
        saveCustomEqProfiles(updated)
        return updated
    }

    fun saveCustomEqProfiles(profiles: List<HeadphoneEqProfile>) {
        val dtos = profiles.map {
            CustomEqProfileDto(
                id = it.id,
                name = it.model,
                preampDb = it.preampDb,
                bandGainsMb = EqPreset.normalizeToTenBands(it.bandGainsMb)
            )
        }
        runCatching {
            prefs.edit().putString(KEY_CUSTOM_EQ_PROFILES, json.encodeToString(dtos)).apply()
        }
    }

    fun loadAppearancePreferences(): AppearancePreferences {
        val raw = prefs.getString(KEY_APPEARANCE_PREFS, null)
        if (raw == null) {
            return AppearancePreferences(
                dynamicArtworkColors = loadAudioSettings().dynamicArtworkColors
            )
        }
        return runCatching {
            val dto = json.decodeFromString<AppearancePreferencesDto>(raw)
            AppearancePreferences(
                themeMode = AppThemeMode.entries.firstOrNull { it.name == dto.themeModeName } ?: AppThemeMode.DARK,
                preset = ThemePreset.entries.firstOrNull { it.name == dto.presetName } ?: ThemePreset.COPPER_AMBER,
                customColors = CustomThemeColors(
                    primaryArgb = dto.primaryArgb,
                    secondaryArgb = dto.secondaryArgb,
                    backgroundArgb = dto.backgroundArgb,
                    surfaceArgb = dto.surfaceArgb,
                    textArgb = dto.textArgb
                ),
                dynamicArtworkColors = dto.dynamicArtworkColors
            )
        }.getOrDefault(
            AppearancePreferences(dynamicArtworkColors = loadAudioSettings().dynamicArtworkColors)
        )
    }

    fun saveAppearancePreferences(appearance: AppearancePreferences) {
        val dto = AppearancePreferencesDto(
            themeModeName = appearance.themeMode.name,
            presetName = appearance.preset.name,
            primaryArgb = appearance.customColors.primaryArgb,
            secondaryArgb = appearance.customColors.secondaryArgb,
            backgroundArgb = appearance.customColors.backgroundArgb,
            surfaceArgb = appearance.customColors.surfaceArgb,
            textArgb = appearance.customColors.textArgb,
            dynamicArtworkColors = appearance.dynamicArtworkColors
        )
        runCatching {
            prefs.edit().putString(KEY_APPEARANCE_PREFS, json.encodeToString(dto)).apply()
            val currentAudio = loadAudioSettings()
            if (currentAudio.dynamicArtworkColors != appearance.dynamicArtworkColors) {
                saveAudioSettings(currentAudio.copy(dynamicArtworkColors = appearance.dynamicArtworkColors))
            }
        }
    }

    private val sessionCacheLock = Any()
    @Volatile
    private var cachedSavedSession: SavedPlaybackSession? = null
    @Volatile
    private var cachedExactSession: ExactPlaybackSession? = null

    fun savePlaybackSession(session: SavedPlaybackSession) {
        if (session.items.isEmpty()) return
        synchronized(sessionCacheLock) {
            cachedSavedSession = session
            runCatching {
                prefs.edit().putString(KEY_SAVED_SESSION, json.encodeToString(session)).apply()
            }
        }
    }

    fun loadPlaybackSession(): SavedPlaybackSession? {
        cachedSavedSession?.let { return it }
        return synchronized(sessionCacheLock) {
            cachedSavedSession?.let { return@synchronized it }
            val raw = prefs.getString(KEY_SAVED_SESSION, null) ?: return@synchronized null
            runCatching {
                json.decodeFromString<SavedPlaybackSession>(raw)
            }.getOrNull()?.takeIf { it.items.isNotEmpty() }?.also {
                cachedSavedSession = it
            }
        }
    }

    fun saveExactPlaybackSession(session: ExactPlaybackSession) {
        if (session.lastTrackId.isBlank()) return
        synchronized(sessionCacheLock) {
            val capped = capExactSessionQueue(session)
            cachedExactSession = capped
            runCatching {
                prefs.edit().putString(KEY_EXACT_SESSION, json.encodeToString(capped)).apply()
            }
        }
    }

    fun loadExactPlaybackSession(): ExactPlaybackSession? {
        cachedExactSession?.let { return it }
        return synchronized(sessionCacheLock) {
            cachedExactSession?.let { return@synchronized it }
            val raw = prefs.getString(KEY_EXACT_SESSION, null) ?: return@synchronized null
            runCatching {
                json.decodeFromString<ExactPlaybackSession>(raw)
            }.getOrNull()?.takeIf { it.lastTrackId.isNotBlank() }?.also {
                cachedExactSession = it
            }
        }
    }

    /**
     * Fast path for periodic position ticks, pauses, and seeks when the queue structure has not
     * changed: updates cached exact & fallback session metadata in memory and avoids rebuilding
     * full MediaItem snapshots or re-decoding Preferences JSON.
     */
    fun updateExactPosition(
        trackId: String,
        queueIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: Int
    ): Boolean {
        if (trackId.isBlank()) return false
        val safePos = positionMs.coerceAtLeast(0L)
        return synchronized(sessionCacheLock) {
            val existing = loadExactPlaybackSessionUnlocked() ?: return@synchronized false
            if (existing.lastQueueTrackIds.isEmpty()) return@synchronized false
            val clampedIdx = queueIndex.coerceIn(0, existing.lastQueueTrackIds.lastIndex)
            if (existing.lastQueueTrackIds[clampedIdx] != trackId && trackId !in existing.lastQueueTrackIds) {
                return@synchronized false
            }
            val resolvedIdx = if (existing.lastQueueTrackIds[clampedIdx] == trackId) {
                clampedIdx
            } else {
                val matching = existing.lastQueueTrackIds.indices.filter { existing.lastQueueTrackIds[it] == trackId }
                matching.minByOrNull { abs(it - existing.lastQueueIndex) } ?: return@synchronized false
            }

            val updatedExact = existing.copy(
                lastTrackId = trackId,
                lastQueueIndex = resolvedIdx,
                lastPositionMs = safePos,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode
            )
            cachedExactSession = updatedExact

            val existingSaved = loadPlaybackSessionUnlocked()
            val updatedSaved = existingSaved?.let { saved ->
                val savedIdx = saved.items.indexOfFirst { it.id == trackId }
                if (savedIdx >= 0) {
                    saved.copy(
                        currentIndex = savedIdx,
                        positionMs = safePos,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode
                    )
                } else null
            }
            if (updatedSaved != null) {
                cachedSavedSession = updatedSaved
            }

            runCatching {
                val editor = prefs.edit().putString(KEY_EXACT_SESSION, json.encodeToString(updatedExact))
                if (updatedSaved != null) {
                    editor.putString(KEY_SAVED_SESSION, json.encodeToString(updatedSaved))
                }
                editor.apply()
            }
            true
        }
    }

    private fun loadPlaybackSessionUnlocked(): SavedPlaybackSession? {
        cachedSavedSession?.let { return it }
        val raw = prefs.getString(KEY_SAVED_SESSION, null) ?: return null
        return runCatching {
            json.decodeFromString<SavedPlaybackSession>(raw)
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }?.also {
            cachedSavedSession = it
        }
    }

    private fun loadExactPlaybackSessionUnlocked(): ExactPlaybackSession? {
        cachedExactSession?.let { return it }
        val raw = prefs.getString(KEY_EXACT_SESSION, null) ?: return null
        return runCatching {
            json.decodeFromString<ExactPlaybackSession>(raw)
        }.getOrNull()?.takeIf { it.lastTrackId.isNotBlank() }?.also {
            cachedExactSession = it
        }
    }

    fun updateExactFromService(
        trackId: String,
        windowTrackIds: List<String>,
        windowIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: Int
    ) {
        if (trackId.isBlank()) return
        synchronized(sessionCacheLock) {
            val existing = loadExactPlaybackSessionUnlocked()
            val nextSession = if (existing != null &&
                existing.lastQueueTrackIds.size >= windowTrackIds.size &&
                trackId in existing.lastQueueTrackIds
            ) {
                val matching = existing.lastQueueTrackIds.indices.filter { existing.lastQueueTrackIds[it] == trackId }
                val bestIdx = matching.minByOrNull { abs(it - existing.lastQueueIndex) } ?: matching.first()
                existing.copy(
                    lastTrackId = trackId,
                    lastQueueIndex = bestIdx,
                    lastPositionMs = positionMs.coerceAtLeast(0L),
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode
                )
            } else {
                val safeIds = if (windowTrackIds.isNotEmpty()) windowTrackIds else listOf(trackId)
                val safeIdx = windowIndex.coerceIn(0, safeIds.lastIndex)
                ExactPlaybackSession(
                    lastTrackId = trackId,
                    lastQueueTrackIds = safeIds,
                    lastQueueIndex = safeIdx,
                    lastPositionMs = positionMs.coerceAtLeast(0L),
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode
                )
            }
            val capped = capExactSessionQueue(nextSession)
            cachedExactSession = capped
            runCatching {
                prefs.edit().putString(KEY_EXACT_SESSION, json.encodeToString(capped)).apply()
            }
        }
    }

    private fun capExactSessionQueue(session: ExactPlaybackSession): ExactPlaybackSession {
        val ids = session.lastQueueTrackIds
        if (ids.size <= MAX_EXACT_QUEUE_IDS) {
            val safeIdx = if (ids.isEmpty()) 0 else session.lastQueueIndex.coerceIn(0, ids.lastIndex)
            return session.copy(
                lastQueueIndex = safeIdx,
                lastPositionMs = session.lastPositionMs.coerceAtLeast(0L)
            )
        }
        val clampedIdx = session.lastQueueIndex.coerceIn(0, ids.lastIndex)
        val half = MAX_EXACT_QUEUE_IDS / 2
        val start = (clampedIdx - half).coerceIn(0, ids.size - MAX_EXACT_QUEUE_IDS)
        val end = (start + MAX_EXACT_QUEUE_IDS).coerceAtMost(ids.size)
        return session.copy(
            lastQueueTrackIds = ids.subList(start, end),
            lastQueueIndex = (clampedIdx - start).coerceIn(0, (end - start - 1).coerceAtLeast(0)),
            lastPositionMs = session.lastPositionMs.coerceAtLeast(0L)
        )
    }

    fun savedItemToMediaItem(snap: SavedMediaItemSnapshot): MediaItem {
        val artwork = Uri.parse(snap.albumArtUri ?: snap.contentUri)
        val metadata = MediaMetadata.Builder()
            .setTitle(snap.title)
            .setDisplayTitle(snap.title)
            .setArtist(snap.artist)
            .setAlbumTitle(snap.album)
            .setArtworkUri(artwork)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(snap.id)
            .setUri(Uri.parse(snap.contentUri))
            .setMediaMetadata(metadata)
            .build()
    }

    fun loadSdCardFolders(): List<SdCardFolder> {
        val raw = prefs.getString(KEY_SD_TREE_FOLDERS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PersistedTreeFolderDto>>(raw).map {
                SdCardFolder(
                    treeUri = it.treeUri,
                    displayName = it.displayName,
                    addedAtMs = it.addedAtMs
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveSdCardFolders(folders: List<SdCardFolder>) {
        val dtos = folders.distinctBy { it.treeUri }.map {
            PersistedTreeFolderDto(
                treeUri = it.treeUri,
                displayName = it.displayName,
                addedAtMs = it.addedAtMs
            )
        }
        runCatching {
            prefs.edit().putString(KEY_SD_TREE_FOLDERS, json.encodeToString(dtos)).apply()
        }
    }

    fun addSdCardFolder(folder: SdCardFolder): List<SdCardFolder> {
        val current = loadSdCardFolders().filterNot { it.treeUri == folder.treeUri }
        val updated = current + folder
        saveSdCardFolders(updated)
        return updated
    }

    fun removeSdCardFolder(treeUri: String): List<SdCardFolder> {
        val updated = loadSdCardFolders().filterNot { it.treeUri == treeUri }
        saveSdCardFolders(updated)
        return updated
    }

    fun loadTranslationEngine(apiKey: String = BuildConfig.DEEPL_API_KEY): TranslationEngine {
        val raw = prefs.getString(KEY_TRANSLATION_ENGINE, null)
        return if (raw != null) {
            TranslationEngine.fromName(raw)
        } else if (DeepLLyricsTranslator.isValidApiKey(apiKey)) {
            TranslationEngine.DEEPL
        } else {
            TranslationEngine.GOOGLE
        }
    }

    fun saveTranslationEngine(engine: TranslationEngine) {
        runCatching {
            prefs.edit().putString(KEY_TRANSLATION_ENGINE, engine.name).apply()
        }
    }

    fun loadDeepSeekApiKey(): String = prefs.getString(KEY_DEEPSEEK_API_KEY, null)?.trim().orEmpty()

    fun saveDeepSeekApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        runCatching {
            prefs.edit().apply {
                if (trimmed.isEmpty()) remove(KEY_DEEPSEEK_API_KEY)
                else putString(KEY_DEEPSEEK_API_KEY, trimmed)
            }.apply()
        }
    }

    fun isAutoUpdateCheckEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true)

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        runCatching {
            prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, enabled).apply()
        }
    }

    fun loadGithubRepoUrl(defaultUrl: String = ""): String {
        return prefs.getString(KEY_GITHUB_REPO_URL, defaultUrl)?.trim().orEmpty()
    }

    fun saveGithubRepoUrl(url: String) {
        runCatching {
            prefs.edit().putString(KEY_GITHUB_REPO_URL, url.trim()).apply()
        }
    }

    fun loadLastUpdateCheckTimestampMs(): Long = prefs.getLong(KEY_LAST_UPDATE_CHECK_MS, 0L)

    fun saveLastUpdateCheckTimestampMs(timestampMs: Long) {
        runCatching {
            prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_MS, timestampMs).apply()
        }
    }

    fun loadLastNotifiedUpdateVersion(): String? = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)

    fun saveLastNotifiedUpdateVersion(version: String) {
        runCatching {
            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, version).apply()
        }
    }

    fun saveLastFmSession(session: LastFmSession) {
        runCatching {
            prefs.edit()
                .putString(KEY_LFM_SESSION_KEY, session.sessionKey.trim())
                .putString(KEY_LFM_USERNAME, session.username.trim())
                .putString(KEY_LFM_AVATAR_URL, session.avatarUrl.orEmpty().trim())
                .apply()
        }
    }

    fun loadLastFmSession(): LastFmSession? {
        val key = prefs.getString(KEY_LFM_SESSION_KEY, null)?.trim().orEmpty()
        val username = prefs.getString(KEY_LFM_USERNAME, null)?.trim().orEmpty()
        if (key.isEmpty() || username.isEmpty()) return null
        val avatar = prefs.getString(KEY_LFM_AVATAR_URL, null)?.trim()?.takeIf { it.isNotEmpty() }
        return LastFmSession(sessionKey = key, username = username, avatarUrl = avatar)
    }

    fun updateLastFmAvatar(avatarUrl: String?) {
        runCatching {
            prefs.edit().putString(KEY_LFM_AVATAR_URL, avatarUrl.orEmpty().trim()).apply()
        }
    }

    fun clearLastFmSession() {
        runCatching {
            prefs.edit()
                .remove(KEY_LFM_SESSION_KEY)
                .remove(KEY_LFM_USERNAME)
                .remove(KEY_LFM_AVATAR_URL)
                .apply()
        }
    }

    fun saveLastFmCredentialsOverride(apiKey: String, sharedSecret: String) {
        runCatching {
            prefs.edit()
                .putString(KEY_LFM_API_KEY, apiKey.trim())
                .putString(KEY_LFM_SHARED_SECRET, sharedSecret.trim())
                .apply()
        }
    }

    fun clearLastFmCredentialsOverride() {
        runCatching {
            prefs.edit()
                .remove(KEY_LFM_API_KEY)
                .remove(KEY_LFM_SHARED_SECRET)
                .apply()
        }
    }

    fun loadLastFmApiKeyOverride(): String = prefs.getString(KEY_LFM_API_KEY, null)?.trim().orEmpty()

    fun loadLastFmSharedSecretOverride(): String = prefs.getString(KEY_LFM_SHARED_SECRET, null)?.trim().orEmpty()

    fun loadLibrarySortOrder(): com.example.model.SortOrder {
        val raw = prefs.getString(KEY_LIBRARY_SORT_ORDER, null) ?: return com.example.model.SortOrder.RECENT
        return com.example.model.SortOrder.entries.firstOrNull { it.name == raw } ?: com.example.model.SortOrder.RECENT
    }

    fun saveLibrarySortOrder(order: com.example.model.SortOrder) {
        runCatching {
            prefs.edit().putString(KEY_LIBRARY_SORT_ORDER, order.name).apply()
        }
    }

    fun loadComputedReplayGainMap(): Map<String, Float> {
        val raw = prefs.getString(KEY_COMPUTED_REPLAY_GAIN_MAP, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, Float>>(raw)
                .filterValues { it.isFinite() }
                .mapValues { (_, db) -> db.coerceIn(-12f, 12f) }
        }.getOrDefault(emptyMap())
    }

    fun loadComputedReplayGain(trackId: String): Float? {
        if (trackId.isBlank()) return null
        return loadComputedReplayGainMap()[trackId]
    }

    fun saveComputedReplayGainMap(entries: Map<String, Float>) {
        val clean = entries
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isFinite() }
            .mapValues { (_, db) -> db.coerceIn(-12f, 12f) }
        runCatching {
            prefs.edit().putString(KEY_COMPUTED_REPLAY_GAIN_MAP, json.encodeToString(clean)).apply()
        }
    }

    fun mergeComputedReplayGains(newEntries: Map<String, Float>): Map<String, Float> {
        val merged = loadComputedReplayGainMap().toMutableMap()
        newEntries.forEach { (id, db) ->
            if (id.isNotBlank() && db.isFinite()) {
                merged[id] = db.coerceIn(-12f, 12f)
            }
        }
        saveComputedReplayGainMap(merged)
        return merged
    }

    fun clearComputedReplayGains() {
        runCatching {
            prefs.edit().remove(KEY_COMPUTED_REPLAY_GAIN_MAP).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "sonora_audio_prefs"
        private const val KEY_AUDIO_SETTINGS = "audio_settings_json"
        private const val KEY_CUSTOM_EQ_PROFILES = "custom_eq_profiles_json"
        private const val KEY_SAVED_SESSION = "saved_playback_session_json"
        private const val KEY_EXACT_SESSION = "exact_playback_session_json"
        private const val MAX_EXACT_QUEUE_IDS = 2500
        private const val KEY_SD_TREE_FOLDERS = "sd_tree_folders_json"
        private const val KEY_TRANSLATION_ENGINE = "preferred_translation_engine"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check_enabled"
        private const val KEY_GITHUB_REPO_URL = "github_repo_url"
        private const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_update_version"
        private const val KEY_LFM_SESSION_KEY = "lfm_session_key"
        private const val KEY_LFM_USERNAME = "lfm_username"
        private const val KEY_LFM_AVATAR_URL = "lfm_avatar_url"
        private const val KEY_LFM_API_KEY = "lfm_api_key_override"
        private const val KEY_LFM_SHARED_SECRET = "lfm_shared_secret_override"
        private const val KEY_APPEARANCE_PREFS = "appearance_preferences_json"
        private const val KEY_LIBRARY_SORT_ORDER = "library_sort_order"
        private const val KEY_COMPUTED_REPLAY_GAIN_MAP = "computed_replay_gain_map_json"
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
    }
}
