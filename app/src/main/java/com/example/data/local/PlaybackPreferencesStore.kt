package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.ai.TranslationEngine
import com.example.model.AudioSettings
import com.example.model.EqPreset
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
    val bandGainsMb: List<Int> = listOf(0, 0, 0, 0, 0),
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
                bandGainsMb = dto.bandGainsMb.ifEmpty { listOf(0, 0, 0, 0, 0) },
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
            bandGainsMb = settings.bandGainsMb,
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

    fun loadTranslationEngine(): TranslationEngine {
        val raw = prefs.getString(KEY_TRANSLATION_ENGINE, null)
        return TranslationEngine.fromName(raw)
    }

    fun saveTranslationEngine(engine: TranslationEngine) {
        runCatching {
            prefs.edit().putString(KEY_TRANSLATION_ENGINE, engine.name).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "sonora_audio_prefs"
        private const val KEY_AUDIO_SETTINGS = "audio_settings_json"
        private const val KEY_SAVED_SESSION = "saved_playback_session_json"
        private const val KEY_SD_TREE_FOLDERS = "sd_tree_folders_json"
        private const val KEY_TRANSLATION_ENGINE = "preferred_translation_engine"
    }
}
