package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.BuildConfig
import com.example.ai.DeepLLyricsTranslator
import com.example.ai.TranslationEngine
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.HeadphoneEqProfile
import com.example.model.LastFmSession
import com.example.model.SdCardFolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val playbackSpeed: Float = 1.0f,
    val stereoBalance: Float = 0f,
    val dynamicArtworkColors: Boolean = true
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
                playbackSpeed = dto.playbackSpeed.coerceIn(0.5f, 2.0f),
                stereoBalance = dto.stereoBalance.coerceIn(-1f, 1f),
                dynamicArtworkColors = dto.dynamicArtworkColors
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
            playbackSpeed = settings.playbackSpeed,
            stereoBalance = settings.stereoBalance,
            dynamicArtworkColors = settings.dynamicArtworkColors
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

    private fun saveCustomEqProfiles(profiles: List<HeadphoneEqProfile>) {
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

    fun savePlaybackSession(session: SavedPlaybackSession) {
        if (session.items.isEmpty()) return
        runCatching {
            prefs.edit().putString(KEY_SAVED_SESSION, json.encodeToString(session)).apply()
        }
    }

    fun loadPlaybackSession(): SavedPlaybackSession? {
        val raw = prefs.getString(KEY_SAVED_SESSION, null) ?: return null
        return runCatching {
            json.decodeFromString<SavedPlaybackSession>(raw)
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
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

    fun isAutoUpdateCheckEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, false)

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

    companion object {
        private const val PREFS_NAME = "sonora_audio_prefs"
        private const val KEY_AUDIO_SETTINGS = "audio_settings_json"
        private const val KEY_CUSTOM_EQ_PROFILES = "custom_eq_profiles_json"
        private const val KEY_SAVED_SESSION = "saved_playback_session_json"
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
    }
}
