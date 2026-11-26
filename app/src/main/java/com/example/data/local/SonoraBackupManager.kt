package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.ai.TranslationEngine
import com.example.model.AppThemeMode
import com.example.model.AppearancePreferences
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.CustomThemeColors
import com.example.model.EqPreset
import com.example.model.HeadphoneEqProfile
import com.example.model.SortOrder
import com.example.model.ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

@Serializable
data class BackupAppearanceDto(
    val themeModeName: String = AppThemeMode.DARK.name,
    val presetName: String = ThemePreset.COPPER_AMBER.name,
    val primaryArgb: Long = 0xFFF5A623L,
    val secondaryArgb: Long = 0xFF38BDF8L,
    val backgroundArgb: Long = 0xFF0C0E13L,
    val surfaceArgb: Long = 0xFF14171FL,
    val textArgb: Long? = null,
    val dynamicArtworkColors: Boolean = true
)

@Serializable
data class BackupAudioSettingsDto(
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
data class BackupCustomEqProfileDto(
    val id: String,
    val name: String,
    val preampDb: Float = 0f,
    val bandGainsMb: List<Int> = List(10) { 0 }
)

@Serializable
data class BackupTrackStatsDto(
    val trackId: String,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)

@Serializable
data class BackupTagOverrideDto(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val customArtworkUri: String? = null
)

@Serializable
data class BackupPlaylistTrackDto(
    val trackId: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = -1L,
    val albumArtUri: String? = null,
    val durationMs: Long = 0L,
    val formatName: String = AudioFormat.MP3.name,
    val mimeType: String = "audio/mpeg",
    val sizeBytes: Long = 0L,
    val folderName: String = "Música",
    val isSafImported: Boolean = false,
    val position: Int = 0
)

@Serializable
data class BackupPlaylistDto(
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<BackupPlaylistTrackDto> = emptyList()
)

@Serializable
data class SonoraBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appName: String = "Davoo Player",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val appearance: BackupAppearanceDto = BackupAppearanceDto(),
    val audioSettings: BackupAudioSettingsDto = BackupAudioSettingsDto(),
    val customEqProfiles: List<BackupCustomEqProfileDto> = emptyList(),
    val preferredTranslationEngine: String = TranslationEngine.GOOGLE.name,
    val autoUpdateCheckEnabled: Boolean = true,
    val githubRepoUrl: String = "",
    val librarySortOrder: String = SortOrder.RECENT.name,
    val excludedFolders: List<String> = emptyList(),
    val trackStats: List<BackupTrackStatsDto> = emptyList(),
    val trackTagOverrides: List<BackupTagOverrideDto> = emptyList(),
    val playlists: List<BackupPlaylistDto> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class BackupRestoreResult(
    val playlistsRestored: Int,
    val trackStatsRestored: Int,
    val customProfilesRestored: Int,
    val appearance: AppearancePreferences,
    val audioSettings: AudioSettings,
    val customEqProfiles: List<HeadphoneEqProfile>,
    val preferredTranslationEngine: TranslationEngine,
    val autoUpdateCheckEnabled: Boolean,
    val githubRepoUrl: String
)

class SonoraBackupManager(
    private val context: Context,
    private val database: SonoraDatabase,
    private val prefsStore: PlaybackPreferencesStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val appearance = prefsStore.loadAppearancePreferences()
        val audio = prefsStore.loadAudioSettings()
        val eqProfiles = prefsStore.loadCustomEqProfiles()
        val translationEngine = prefsStore.loadTranslationEngine()
        val autoUpdates = prefsStore.isAutoUpdateCheckEnabled()
        val repoUrl = prefsStore.loadGithubRepoUrl()
        val sortOrder = prefsStore.loadLibrarySortOrder()

        val excludedFolders = database.excludedFolderDao().getAllExcludedOnce().map { it.folderName }
        val trackStats = database.trackStatsDao().getAllStatsOnce().map {
            BackupTrackStatsDto(
                trackId = it.trackId,
                isFavorite = it.isFavorite,
                playCount = it.playCount,
                lastPlayedAt = it.lastPlayedAt
            )
        }
        val tagOverrides = database.trackTagOverrideDao().getAllOverridesOnce().map {
            BackupTagOverrideDto(
                trackId = it.trackId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                albumArtist = it.albumArtist,
                genre = it.genre,
                year = it.year,
                trackNumber = it.trackNumber,
                customArtworkUri = it.customArtworkUri
            )
        }
        val playlists = database.playlistDao().getAllPlaylistsWithEntriesOnce().map { item ->
            BackupPlaylistDto(
                name = item.playlist.name,
                description = item.playlist.description,
                createdAt = item.playlist.createdAt,
                tracks = item.entries
                    .sortedBy { it.position }
                    .map { e ->
                        BackupPlaylistTrackDto(
                            trackId = e.trackId,
                            contentUri = e.contentUri,
                            title = e.title,
                            artist = e.artist,
                            album = e.album,
                            albumId = e.albumId,
                            albumArtUri = e.albumArtUri,
                            durationMs = e.durationMs,
                            formatName = e.formatName,
                            mimeType = e.mimeType,
                            sizeBytes = e.sizeBytes,
                            folderName = e.folderName,
                            isSafImported = e.isSafImported,
                            position = e.position
                        )
                    }
            )
        }

        val payload = SonoraBackupPayload(
            appearance = BackupAppearanceDto(
                themeModeName = appearance.themeMode.name,
                presetName = appearance.preset.name,
                primaryArgb = appearance.customColors.primaryArgb,
                secondaryArgb = appearance.customColors.secondaryArgb,
                backgroundArgb = appearance.customColors.backgroundArgb,
                surfaceArgb = appearance.customColors.surfaceArgb,
                textArgb = appearance.customColors.textArgb,
                dynamicArtworkColors = appearance.dynamicArtworkColors
            ),
            audioSettings = BackupAudioSettingsDto(
                eqEnabled = audio.eqEnabled,
                eqPresetName = audio.eqPreset.name,
                bandGainsMb = EqPreset.normalizeToTenBands(audio.bandGainsMb),
                preampDb = audio.preampDb,
                activeProfileId = audio.activeProfileId,
                activeProfileName = audio.activeProfileName,
                bassBoostStrength = audio.bassBoostStrength,
                virtualizerStrength = audio.virtualizerStrength,
                crossfadeSeconds = audio.crossfadeSeconds,
                gaplessEnabled = audio.gaplessEnabled,
                replayGainEnabled = audio.replayGainEnabled,
                replayGainPreampDb = audio.replayGainPreampDb,
                replayGainUntaggedPreampDb = audio.replayGainUntaggedPreampDb,
                playbackSpeed = audio.playbackSpeed,
                stereoBalance = audio.stereoBalance,
                dynamicArtworkColors = appearance.dynamicArtworkColors,
                fadeOnPauseEnabled = audio.fadeOnPauseEnabled
            ),
            customEqProfiles = eqProfiles.map {
                BackupCustomEqProfileDto(
                    id = it.id,
                    name = it.model,
                    preampDb = it.preampDb,
                    bandGainsMb = EqPreset.normalizeToTenBands(it.bandGainsMb)
                )
            },
            preferredTranslationEngine = translationEngine.name,
            autoUpdateCheckEnabled = autoUpdates,
            githubRepoUrl = repoUrl,
            librarySortOrder = sortOrder.name,
            excludedFolders = excludedFolders,
            trackStats = trackStats,
            trackTagOverrides = tagOverrides,
            playlists = playlists
        )

        withContext(Dispatchers.Default) {
            json.encodeToString(payload)
        }
    }

    suspend fun exportToUri(uri: Uri): Result<SonoraBackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val serialized = buildBackupJson()
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("No se pudo abrir el archivo de destino")
            out.use { stream ->
                stream.write(serialized.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            json.decodeFromString<SonoraBackupPayload>(serialized)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val rawJson = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: throw IllegalStateException("No se pudo leer el archivo seleccionado")
            importFromJsonString(rawJson)
        }
    }

    suspend fun importFromJsonString(rawJson: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val payload = withContext(Dispatchers.Default) {
            json.decodeFromString<SonoraBackupPayload>(rawJson)
        }
        require(payload.schemaVersion >= 1) {
            "Formato de respaldo incompatible"
        }

        val restoredAppearance = AppearancePreferences(
            themeMode = AppThemeMode.entries.firstOrNull { it.name == payload.appearance.themeModeName }
                ?: AppThemeMode.DARK,
            preset = ThemePreset.entries.firstOrNull { it.name == payload.appearance.presetName }
                ?: ThemePreset.COPPER_AMBER,
            customColors = CustomThemeColors(
                primaryArgb = payload.appearance.primaryArgb,
                secondaryArgb = payload.appearance.secondaryArgb,
                backgroundArgb = payload.appearance.backgroundArgb,
                surfaceArgb = payload.appearance.surfaceArgb,
                textArgb = payload.appearance.textArgb
            ),
            dynamicArtworkColors = payload.appearance.dynamicArtworkColors
        )

        val restoredAudio = AudioSettings(
            eqEnabled = payload.audioSettings.eqEnabled,
            eqPreset = EqPreset.entries.firstOrNull { it.name == payload.audioSettings.eqPresetName }
                ?: EqPreset.FLAT,
            bandGainsMb = EqPreset.normalizeToTenBands(payload.audioSettings.bandGainsMb),
            preampDb = payload.audioSettings.preampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 12f) ?: 0f,
            activeProfileId = payload.audioSettings.activeProfileId,
            activeProfileName = payload.audioSettings.activeProfileName,
            bassBoostStrength = payload.audioSettings.bassBoostStrength.coerceIn(0, 1000),
            virtualizerStrength = payload.audioSettings.virtualizerStrength.coerceIn(0, 1000),
            crossfadeSeconds = payload.audioSettings.crossfadeSeconds.coerceIn(0, 12),
            gaplessEnabled = payload.audioSettings.gaplessEnabled,
            replayGainEnabled = payload.audioSettings.replayGainEnabled,
            replayGainPreampDb = payload.audioSettings.replayGainPreampDb.takeIf { it.isFinite() }?.coerceIn(-6f, 12f) ?: 3.0f,
            replayGainUntaggedPreampDb = payload.audioSettings.replayGainUntaggedPreampDb.takeIf { it.isFinite() }?.coerceIn(-12f, 0f) ?: -4.0f,
            playbackSpeed = payload.audioSettings.playbackSpeed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.0f) ?: 1.0f,
            stereoBalance = payload.audioSettings.stereoBalance.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
            dynamicArtworkColors = restoredAppearance.dynamicArtworkColors,
            fadeOnPauseEnabled = payload.audioSettings.fadeOnPauseEnabled
        )

        val restoredProfiles = payload.customEqProfiles.map { dto ->
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

        val engine = TranslationEngine.fromName(payload.preferredTranslationEngine)
        prefsStore.saveAppearancePreferences(restoredAppearance)
        prefsStore.saveAudioSettings(restoredAudio)
        val restoredSortOrder = SortOrder.entries.firstOrNull { it.name == payload.librarySortOrder }
        if (restoredSortOrder != null) {
            prefsStore.saveLibrarySortOrder(restoredSortOrder)
        }
        if (restoredProfiles.isNotEmpty()) {
            prefsStore.saveCustomEqProfiles(restoredProfiles)
        }
        prefsStore.saveTranslationEngine(engine)
        prefsStore.setAutoUpdateCheckEnabled(payload.autoUpdateCheckEnabled)
        if (payload.githubRepoUrl.isNotBlank()) {
            prefsStore.saveGithubRepoUrl(payload.githubRepoUrl)
        }

        if (payload.excludedFolders.isNotEmpty()) {
            database.excludedFolderDao().excludeFolders(
                payload.excludedFolders.distinct().map { ExcludedFolderEntity(folderName = it) }
            )
        }

        if (payload.trackStats.isNotEmpty()) {
            val existingStats = database.trackStatsDao().getAllStatsOnce().associateBy { it.trackId }
            val merged = payload.trackStats.map { dto ->
                val cur = existingStats[dto.trackId]
                TrackStatsEntity(
                    trackId = dto.trackId,
                    isFavorite = dto.isFavorite || (cur?.isFavorite == true),
                    playCount = maxOf(dto.playCount, cur?.playCount ?: 0),
                    lastPlayedAt = maxOf(dto.lastPlayedAt, cur?.lastPlayedAt ?: 0L)
                )
            }
            database.trackStatsDao().upsertAll(merged)
        }

        if (payload.trackTagOverrides.isNotEmpty()) {
            database.trackTagOverrideDao().upsertAll(
                payload.trackTagOverrides.map { dto ->
                    TrackTagOverrideEntity(
                        trackId = dto.trackId,
                        title = dto.title,
                        artist = dto.artist,
                        album = dto.album,
                        albumArtist = dto.albumArtist,
                        genre = dto.genre,
                        year = dto.year,
                        trackNumber = dto.trackNumber,
                        customArtworkUri = dto.customArtworkUri
                    )
                }
            )
        }

        val playlistDao = database.playlistDao()
        var restoredPlaylistCount = 0
        for (pl in payload.playlists) {
            val trimmedName = pl.name.trim()
            if (trimmedName.isEmpty()) continue
            val existing = playlistDao.findByNameIgnoreCase(trimmedName)
            val targetId = existing?.id ?: playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = trimmedName,
                    description = pl.description,
                    createdAt = pl.createdAt
                )
            )
            var nextPos = playlistDao.getMaxPosition(targetId) + 1
            for (t in pl.tracks.sortedBy { it.position }) {
                playlistDao.insertPlaylistTrack(
                    PlaylistTrackEntity(
                        playlistId = targetId,
                        trackId = t.trackId,
                        contentUri = t.contentUri,
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        albumId = t.albumId,
                        albumArtUri = t.albumArtUri,
                        durationMs = t.durationMs,
                        formatName = t.formatName,
                        mimeType = t.mimeType,
                        sizeBytes = t.sizeBytes,
                        folderName = t.folderName,
                        isSafImported = t.isSafImported,
                        position = nextPos++
                    )
                )
            }
            restoredPlaylistCount++
        }

        BackupRestoreResult(
            playlistsRestored = restoredPlaylistCount,
            trackStatsRestored = payload.trackStats.size,
            customProfilesRestored = restoredProfiles.size,
            appearance = restoredAppearance,
            audioSettings = restoredAudio,
            customEqProfiles = prefsStore.loadCustomEqProfiles(),
            preferredTranslationEngine = engine,
            autoUpdateCheckEnabled = payload.autoUpdateCheckEnabled,
            githubRepoUrl = prefsStore.loadGithubRepoUrl()
        )
    }
}
