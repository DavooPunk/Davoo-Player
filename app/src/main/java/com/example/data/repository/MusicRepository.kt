package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ai.LyricsTranslationLanguage
import com.example.data.local.ExcludedFolderDao
import com.example.data.local.ExcludedFolderEntity
import com.example.data.local.ImportedTrackDao
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.TrackLyricsDao
import com.example.data.local.TrackLyricsEntity
import com.example.data.local.TrackLyricsTranslationDao
import com.example.data.local.TrackLyricsTranslationEntity
import com.example.data.local.TrackStatsDao
import com.example.data.local.TrackTagOverrideDao
import com.example.data.local.TrackTagOverrideEntity
import com.example.model.AudioTechSpecs
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.LyricsData
import com.example.model.PlaylistSummary
import com.example.model.SdCardFolder
import com.example.ui.components.AudioArtImageLoaderProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val scanner: DeviceMusicScanner,
    private val playlistDao: PlaylistDao,
    private val importedTrackDao: ImportedTrackDao,
    private val trackStatsDao: TrackStatsDao,
    private val trackTagOverrideDao: TrackTagOverrideDao,
    private val excludedFolderDao: ExcludedFolderDao,
    private val trackLyricsDao: TrackLyricsDao,
    private val onlineLyricsFetcher: OnlineLyricsFetcher = LrcLibLyricsFetcher(),
    private val trackLyricsTranslationDao: TrackLyricsTranslationDao? = null
) {
    private val prefsStore = PlaybackPreferencesStore(context)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanMutex = Mutex()
    private val mediaStoreTracksFlow = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val sdTracksFlow = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _sdCardFolders = MutableStateFlow(prefsStore.loadSdCardFolders())
    val sdCardFoldersFlow: StateFlow<List<SdCardFolder>> = _sdCardFolders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val excludedFoldersFlow: Flow<Set<String>> = excludedFolderDao.observeExcludedFolders()
        .map { list -> list.map { it.folderName }.toSet() }

    private val rawSourcesFlow: Flow<List<AudioTrack>> = combine(
        mediaStoreTracksFlow,
        sdTracksFlow,
        importedTrackDao.observeImportedTracks()
    ) { mediaTracks, sdTracks, importedEntities ->
        val seenUris = HashSet<String>(mediaTracks.size + sdTracks.size + importedEntities.size)
        val result = ArrayList<AudioTrack>(mediaTracks.size + sdTracks.size + importedEntities.size)
        for (track in mediaTracks) {
            if (seenUris.add(track.contentUri)) result.add(track)
        }
        for (track in sdTracks) {
            if (seenUris.add(track.contentUri)) result.add(track)
        }
        for (entity in importedEntities) {
            val track = entity.toAudioTrack()
            if (seenUris.add(track.contentUri)) result.add(track)
        }
        result.distinctBy { it.id }
    }

    private val combinedRawTracksFlow: Flow<List<AudioTrack>> = combine(
        rawSourcesFlow,
        trackStatsDao.observeAllStats(),
        trackTagOverrideDao.observeAllOverrides()
    ) { merged, statsList, overridesList ->
        val statsMap = statsList.associateBy { it.trackId }
        val overrideMap = overridesList.associateBy { it.trackId }

        merged.map { track ->
            val stats = statsMap[track.id]
            val ov = overrideMap[track.id]
            track.copy(
                title = ov?.title?.takeIf { it.isNotBlank() } ?: track.title,
                artist = ov?.artist?.takeIf { it.isNotBlank() } ?: track.artist,
                album = ov?.album?.takeIf { it.isNotBlank() } ?: track.album,
                albumArtist = ov?.albumArtist?.takeIf { it.isNotBlank() } ?: track.albumArtist,
                genre = ov?.genre?.takeIf { it.isNotBlank() } ?: track.genre,
                year = ov?.year?.takeIf { it > 0 } ?: track.year,
                trackNumber = ov?.trackNumber?.takeIf { it > 0 } ?: track.trackNumber,
                albumArtUri = ov?.customArtworkUri ?: track.albumArtUri,
                isFavorite = stats?.isFavorite ?: false,
                playCount = stats?.playCount ?: 0,
                lastPlayedAt = stats?.lastPlayedAt ?: 0L
            )
        }
    }

    /** Full track list including tracks in excluded folders (for Folder manager UI). */
    val unfilteredTracksFlow: Flow<List<AudioTrack>> = combinedRawTracksFlow

    /** Visible library tracks excluding blacklisted folders. */
    val allTracksFlow: Flow<List<AudioTrack>> = combine(
        combinedRawTracksFlow,
        excludedFoldersFlow
    ) { tracks, excluded ->
        if (excluded.isEmpty()) tracks else tracks.filterNot { it.folderName in excluded }
    }

    val playlistsFlow: Flow<List<PlaylistSummary>> = combine(
        playlistDao.observePlaylistsWithEntries(),
        combinedRawTracksFlow,
        trackStatsDao.observeAllStats()
    ) { list, allTracks, statsList ->
        val trackById = allTracks.associateBy { it.id }
        val statsMap = statsList.associateBy { it.trackId }
        list.map { item ->
            val sortedTracks = item.entries
                .sortedBy { it.position }
                .map { entry ->
                    trackById[entry.trackId] ?: entry.toAudioTrack().let { fallback ->
                        val stats = statsMap[fallback.id]
                        if (stats != null) {
                            fallback.copy(
                                isFavorite = stats.isFavorite,
                                playCount = stats.playCount,
                                lastPlayedAt = stats.lastPlayedAt
                            )
                        } else fallback
                    }
                }
            PlaylistSummary(
                id = item.playlist.id,
                name = item.playlist.name,
                description = item.playlist.description,
                createdAt = item.playlist.createdAt,
                tracks = sortedTracks
            )
        }
    }

    suspend fun refreshMediaStoreLibrary(includeMediaStore: Boolean = true) {
        scanMutex.withLock {
            _isScanning.value = true
            try {
                if (includeMediaStore) {
                    mediaStoreTracksFlow.value = runCatching { scanner.scanMediaStoreAudio() }
                        .getOrDefault(mediaStoreTracksFlow.value)
                }
                scanPersistedSdFoldersLocked()
            } finally {
                _isScanning.value = false
            }
        }
    }

    suspend fun addSdCardFolder(treeUri: Uri): SdCardFolder? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val uriString = treeUri.toString()
        val displayName = runCatching { scanner.resolveTreeFolderDisplayName(treeUri) }
            .getOrDefault("Tarjeta SD")
        val added = SdCardFolder(
            treeUri = uriString,
            displayName = displayName,
            addedAtMs = System.currentTimeMillis()
        )
        return scanMutex.withLock {
            _isScanning.value = true
            try {
                prefsStore.addSdCardFolder(added)
                scanPersistedSdFoldersLocked()
                _sdCardFolders.value.firstOrNull { it.treeUri == uriString } ?: added
            } finally {
                _isScanning.value = false
            }
        }
    }

    suspend fun removeSdCardFolder(treeUri: String) {
        val removedUris = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scanMutex.withLock {
                prefsStore.removeSdCardFolder(treeUri)
                val targetFolderName = _sdCardFolders.value
                    .firstOrNull { it.treeUri == treeUri }
                    ?.displayName
                val currentTracks = sdTracksFlow.value
                val (toRemove, remaining) = currentTracks.partition { track ->
                    track.sourceTreeUri == treeUri ||
                        track.contentUri.startsWith(treeUri) ||
                        (targetFolderName != null &&
                            track.isDocumentTreeTrack &&
                            track.folderName == targetFolderName)
                }
                sdTracksFlow.value = remaining
                _sdCardFolders.value = _sdCardFolders.value.filterNot { it.treeUri == treeUri }
                toRemove.map { it.contentUri }
            }
        }
        evictRemovedUrisInBackground(removedUris)
    }

    private fun evictRemovedUrisInBackground(uris: List<String>) {
        if (uris.isEmpty()) return
        cleanupScope.launch {
            AudioArtImageLoaderProvider.evictUrisAsync(uris)
            DeviceMusicScanner.evictDurationsAsync(uris)
        }
    }

    private suspend fun scanPersistedSdFoldersLocked() {
        val persisted = prefsStore.loadSdCardFolders()
        if (persisted.isEmpty()) {
            sdTracksFlow.value = emptyList()
            _sdCardFolders.value = emptyList()
            return
        }
        val (tracks, counts) = runCatching { scanner.scanDocumentTreeFolders(persisted) }
            .getOrDefault(emptyList<AudioTrack>() to emptyMap())
        sdTracksFlow.value = tracks
        _sdCardFolders.value = persisted.map { folder ->
            folder.copy(trackCount = counts[folder.treeUri] ?: 0)
        }
    }

    suspend fun importAudioFiles(uris: List<Uri>): Int {
        if (uris.isEmpty()) return 0
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        val inspected = scanner.inspectSafUris(uris)
        if (inspected.isNotEmpty()) {
            importedTrackDao.upsertTracks(inspected)
        }
        return inspected.size
    }

    suspend fun extractTechSpecs(uriString: String): AudioTechSpecs {
        return scanner.extractTechSpecs(uriString)
    }

    suspend fun extractTrackMetadata(track: AudioTrack): AudioTrackMetadata {
        return scanner.extractTrackMetadata(track).let { meta ->
            meta.copy(
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumArtist = track.albumArtist.ifBlank { meta.albumArtist },
                genre = track.genre.ifBlank { meta.genre },
                year = if (track.year > 0) track.year else meta.year,
                trackNumber = if (track.trackNumber > 0) track.trackNumber else meta.trackNumber
            )
        }
    }

    suspend fun setFavorite(trackId: String, isFavorite: Boolean): Boolean =
        trackStatsDao.setFavorite(trackId, isFavorite)

    suspend fun toggleFavorite(trackId: String): Boolean = trackStatsDao.toggleFavorite(trackId)

    suspend fun recordPlay(trackId: String) = trackStatsDao.recordPlay(trackId)

    suspend fun saveTrackTagOverride(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        customArtworkUri: String?
    ) {
        trackTagOverrideDao.upsert(
            TrackTagOverrideEntity(
                trackId = trackId,
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim(),
                albumArtist = albumArtist.trim(),
                genre = genre.trim(),
                year = year.coerceAtLeast(0),
                trackNumber = trackNumber.coerceAtLeast(0),
                customArtworkUri = customArtworkUri
            )
        )
    }

    suspend fun excludeFolder(folderName: String) {
        val removedUris = withContext(Dispatchers.IO) {
            val uris = combinedRawTracksFlow.first()
                .filter { it.folderName == folderName }
                .map { it.contentUri }
            excludedFolderDao.excludeFolder(ExcludedFolderEntity(folderName = folderName))
            uris
        }
        evictRemovedUrisInBackground(removedUris)
    }

    suspend fun excludeFolders(folderNames: List<String>) {
        if (folderNames.isEmpty()) return
        val distinct = folderNames.distinct()
        val removedUris = withContext(Dispatchers.IO) {
            val nameSet = distinct.toHashSet()
            val uris = combinedRawTracksFlow.first()
                .filter { it.folderName in nameSet }
                .map { it.contentUri }
            excludedFolderDao.excludeFolders(
                distinct.map { ExcludedFolderEntity(folderName = it) }
            )
            uris
        }
        evictRemovedUrisInBackground(removedUris)
    }

    suspend fun includeFolder(folderName: String) {
        excludedFolderDao.includeFolder(folderName)
    }

    suspend fun loadLyrics(track: AudioTrack): LyricsData? = withContext(Dispatchers.IO) {
        val saved = runCatching { trackLyricsDao.getLyricsForTrack(track.id) }.getOrNull()
        if (saved != null) {
            AudioTagParsers.parseLrcText(saved.rawLrcText, saved.sourceLabel)?.let { return@withContext it }
        }
        AudioTagParsers.extractLyricsForTrack(context, track)
    }

    suspend fun loadAndSaveLyricsFromUri(trackId: String, uri: Uri): LyricsData? = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val maxBytes = 1024 * 1024
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) break
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull() ?: return@withContext null

        val parsed = AudioTagParsers.parseLrcText(raw, AudioTagParsers.SAVED_LRC_SOURCE_LABEL)
            ?: return@withContext null

        runCatching {
            trackLyricsDao.upsert(
                TrackLyricsEntity(
                    trackId = trackId,
                    rawLrcText = raw,
                    sourceLabel = AudioTagParsers.SAVED_LRC_SOURCE_LABEL
                )
            )
            trackLyricsTranslationDao?.deleteForTrack(trackId)
        }
        parsed
    }

    suspend fun clearSavedLyrics(track: AudioTrack): LyricsData? = withContext(Dispatchers.IO) {
        runCatching {
            trackLyricsDao.deleteForTrack(track.id)
            trackLyricsTranslationDao?.deleteForTrack(track.id)
        }
        AudioTagParsers.extractLyricsForTrack(context, track)
    }

    suspend fun searchOnlineLyrics(
        track: AudioTrack,
        customQuery: String? = null
    ): Result<List<OnlineLyricsCandidate>> {
        val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        return onlineLyricsFetcher.searchLyrics(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSec = durationSec,
            customQuery = customQuery
        )
    }

    suspend fun saveDownloadedLyrics(
        trackId: String,
        candidate: OnlineLyricsCandidate
    ): LyricsData? = withContext(Dispatchers.IO) {
        val parsed = AudioTagParsers.parseLrcText(
            candidate.rawLyrics,
            AudioTagParsers.ONLINE_LRC_SOURCE_LABEL
        ) ?: return@withContext null

        runCatching {
            trackLyricsDao.upsert(
                TrackLyricsEntity(
                    trackId = trackId,
                    rawLrcText = candidate.rawLyrics,
                    sourceLabel = AudioTagParsers.ONLINE_LRC_SOURCE_LABEL
                )
            )
            trackLyricsTranslationDao?.deleteForTrack(trackId)
        }
        parsed
    }

    data class SavedOfflineTranslation(
        val language: LyricsTranslationLanguage,
        val translatedLyrics: LyricsData
    )

    suspend fun loadSavedLyricsTranslation(
        trackId: String,
        originalLyrics: LyricsData
    ): SavedOfflineTranslation? = withContext(Dispatchers.IO) {
        val dao = trackLyricsTranslationDao ?: return@withContext null
        val saved = runCatching { dao.getTranslationForTrack(trackId) }.getOrNull()
            ?: return@withContext null
        val aligned = AudioTagParsers.alignTranslationLines(
            original = originalLyrics,
            rawTranslatedText = saved.rawTranslatedText,
            sourceLabel = saved.sourceLabel
        ) ?: return@withContext null
        SavedOfflineTranslation(
            language = LyricsTranslationLanguage.fromCode(saved.languageCode),
            translatedLyrics = aligned
        )
    }

    suspend fun saveLyricsTranslation(
        trackId: String,
        language: LyricsTranslationLanguage,
        translatedLyrics: LyricsData
    ) = withContext(Dispatchers.IO) {
        val dao = trackLyricsTranslationDao ?: return@withContext
        val serialized = AudioTagParsers.serializeTranslationLines(translatedLyrics)
        if (serialized.isBlank()) return@withContext
        runCatching {
            dao.upsert(
                TrackLyricsTranslationEntity(
                    trackId = trackId,
                    languageCode = language.code,
                    rawTranslatedText = serialized,
                    sourceLabel = translatedLyrics.sourceLabel
                )
            )
        }
    }

    suspend fun saveManualLyricsTranslation(
        trackId: String,
        originalLyrics: LyricsData,
        rawPastedText: String,
        language: LyricsTranslationLanguage
    ): LyricsData? = withContext(Dispatchers.IO) {
        val label = "${AudioTagParsers.MANUAL_TRANSLATION_SOURCE_LABEL} (${language.displayName})"
        val aligned = AudioTagParsers.alignTranslationLines(
            original = originalLyrics,
            rawTranslatedText = rawPastedText,
            sourceLabel = label
        ) ?: return@withContext null
        saveLyricsTranslation(trackId, language, aligned)
        aligned
    }

    suspend fun clearSavedLyricsTranslation(trackId: String) = withContext(Dispatchers.IO) {
        runCatching { trackLyricsTranslationDao?.deleteForTrack(trackId) }
    }

    suspend fun exportPlaylistM3uToUri(playlist: PlaylistSummary, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val m3u = AudioTagParsers.exportPlaylistToM3u(playlist.name, playlist.tracks)
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(m3u)
            }
            true
        }.getOrDefault(false)
    }

    suspend fun importPlaylistFromM3u(uri: Uri): Pair<String, Int> = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        if (raw.isBlank()) return@withContext ("Lista importada" to 0)

        val playlistName = raw.lineSequence()
            .firstOrNull { it.startsWith("#PLAYLIST:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Lista M3U"

        val entries = AudioTagParsers.parseM3uPaths(raw)
        val library = combinedRawTracksFlow.first()
        val matched = entries.mapNotNull { entryPath ->
            val fileName = entryPath.substringAfterLast('/').substringAfterLast('\\')
            library.firstOrNull { t ->
                t.contentUri == entryPath ||
                    (t.filePath.isNotBlank() && t.filePath == entryPath) ||
                    (fileName.isNotBlank() && (t.filePath.endsWith(fileName, ignoreCase = true) ||
                        t.title.equals(fileName.substringBeforeLast('.'), ignoreCase = true)))
            }
        }.distinctBy { it.id }

        val id = createPlaylist(playlistName, "Importada desde archivo .m3u")
        val added = if (matched.isNotEmpty()) addTracksToPlaylist(id, matched) else 0
        playlistName to added
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = trimmed, description = description.trim())
        )
    }

    suspend fun updatePlaylist(playlistId: Long, name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            playlistDao.updatePlaylistDetails(playlistId, trimmed, description.trim())
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<AudioTrack>): Int {
        var pos = playlistDao.getMaxPosition(playlistId) + 1
        var insertedCount = 0
        tracks.forEach { track ->
            val rowId = playlistDao.insertPlaylistTrack(
                PlaylistTrackEntity.fromAudioTrack(
                    playlistId = playlistId,
                    track = track,
                    position = pos
                )
            )
            if (rowId != -1L) {
                pos++
                insertedCount++
            }
        }
        return insertedCount
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        playlistDao.deleteTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<String>): Int {
        if (trackIds.isEmpty()) return 0
        val distinct = trackIds.distinct()
        distinct.chunked(500).forEach { chunk ->
            playlistDao.deleteTracksFromPlaylist(playlistId, chunk)
        }
        return distinct.size
    }

    suspend fun removeImportedTracksByUris(uris: List<String>): Int {
        if (uris.isEmpty()) return 0
        val distinct = uris.distinct()
        distinct.chunked(500).forEach { chunk ->
            importedTrackDao.deleteByUris(chunk)
        }
        evictRemovedUrisInBackground(distinct)
        return distinct.size
    }

    suspend fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        playlistDao.reorderTrack(playlistId, fromIndex, toIndex)
    }
}
