package com.example.data.repository

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.remote.MusicApiService
import com.example.model.AudioTrack
import com.example.model.BatchAutoTagUiState
import com.example.model.BatchItemOutcome
import com.example.model.BatchLyricsUiState
import com.example.model.DeezerTagSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

class LibraryEnrichmentCoordinator(
    private val context: Context,
    private val musicApiService: MusicApiService,
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val onRefreshTracks: (List<AudioTrack>) -> Unit,
    private val onSnackbar: (String) -> Unit
) {

    private val _batchAutoTagState = MutableStateFlow(BatchAutoTagUiState())
    val batchAutoTagState: StateFlow<BatchAutoTagUiState> = _batchAutoTagState.asStateFlow()

    private val _pendingBatchWriteSender = MutableStateFlow<IntentSender?>(null)
    val pendingBatchWriteSender: StateFlow<IntentSender?> = _pendingBatchWriteSender.asStateFlow()

    private val _batchLyricsState = MutableStateFlow(BatchLyricsUiState())
    val batchLyricsState: StateFlow<BatchLyricsUiState> = _batchLyricsState.asStateFlow()

    private val _downloadedLrcExportCount = MutableStateFlow(0)
    val downloadedLrcExportCount: StateFlow<Int> = _downloadedLrcExportCount.asStateFlow()

    private var batchAutoTagJob: Job? = null
    private var batchLyricsJob: Job? = null

    private data class PendingAutoTagContinuation(
        val pendingRequest: TrackTagEditRequest,
        val remainingTracks: List<AudioTrack>,
        val coverCache: MutableMap<String, String?>,
        val overwriteArtwork: Boolean
    )

    private var pendingAutoTagContinuation: PendingAutoTagContinuation? = null
    private val downloadedLrcForExport = Collections.synchronizedList(mutableListOf<Pair<AudioTrack, String>>())

    suspend fun searchDeezerTagSuggestions(
        track: AudioTrack,
        customQuery: String? = null
    ): Result<List<DeezerTagSuggestion>> {
        val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        return musicApiService.searchDeezerTagSuggestions(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSec = durationSec,
            customQuery = customQuery,
            limit = 5
        )
    }

    suspend fun resolveDeezerTagSuggestion(
        suggestion: DeezerTagSuggestion
    ): Pair<DeezerTagSuggestion, String?> {
        val enriched = musicApiService.fetchDeezerTrackTagDetails(suggestion)
        val cacheKey = (enriched.albumId.takeIf { it > 0L } ?: enriched.deezerTrackId).toString()
        val localCoverUri = enriched.coverXlUrl?.let { coverUrl ->
            AudioTagWriter.downloadAndCacheArtworkFile(context, coverUrl, cacheKey)
        }
        return enriched to localCoverUri
    }

    fun openBatchAutoTag(tracks: List<AudioTrack>, onlyMissingByDefault: Boolean) {
        if (_batchAutoTagState.value.isRunning) {
            _batchAutoTagState.update { it.copy(isVisible = true) }
            return
        }
        val localTracks = tracks.filterNot { isStreamingUri(it.contentUri) }
        _batchAutoTagState.value = BatchAutoTagUiState(
            isVisible = true,
            onlyMissingMetadata = onlyMissingByDefault,
            overwriteArtwork = true,
            targetTracks = localTracks
        )
    }

    fun setBatchAutoTagOnlyMissing(value: Boolean) {
        _batchAutoTagState.update { it.copy(onlyMissingMetadata = value) }
    }

    fun setBatchAutoTagOverwriteArtwork(value: Boolean) {
        _batchAutoTagState.update { it.copy(overwriteArtwork = value) }
    }

    fun dismissBatchAutoTag() {
        if (_batchAutoTagState.value.isRunning) return
        _pendingBatchWriteSender.value = null
        pendingAutoTagContinuation = null
        _batchAutoTagState.update { it.copy(isVisible = false) }
    }

    fun cancelBatchAutoTag() {
        batchAutoTagJob?.cancel()
        batchAutoTagJob = null
        pendingAutoTagContinuation = null
        _pendingBatchWriteSender.value = null
        _batchAutoTagState.update {
            it.copy(
                isRunning = false,
                waitingForWritePermission = false,
                completed = true,
                currentTrackLabel = ""
            )
        }
    }

    fun startBatchAutoTag() {
        val current = _batchAutoTagState.value
        if (current.isRunning) return
        val candidates = if (current.onlyMissingMetadata) {
            current.targetTracks.filter(::needsMetadataEnrichment)
        } else {
            current.targetTracks
        }
        if (candidates.isEmpty()) {
            _batchAutoTagState.update {
                it.copy(
                    isRunning = false,
                    completed = true,
                    totalCount = 0,
                    processedCount = 0,
                    skippedCount = current.targetTracks.size,
                    recentResults = listOf(
                        BatchItemOutcome(
                            trackTitle = "Biblioteca completa",
                            detail = "Todas las pistas ya tienen metadatos y carátula",
                            isSuccess = true
                        )
                    )
                )
            }
            return
        }

        batchAutoTagJob?.cancel()
        pendingAutoTagContinuation = null
        _pendingBatchWriteSender.value = null
        _batchAutoTagState.update {
            it.copy(
                isRunning = true,
                waitingForWritePermission = false,
                completed = false,
                totalCount = candidates.size,
                processedCount = 0,
                updatedCount = 0,
                skippedCount = 0,
                failedCount = 0,
                currentTrackLabel = candidates.first().title,
                recentResults = emptyList()
            )
        }
        val coverCache = mutableMapOf<String, String?>()
        batchAutoTagJob = scope.launch {
            runAutoTagLoop(
                pendingFirstRequest = null,
                tracksToProcess = candidates,
                coverCache = coverCache,
                overwriteArtwork = current.overwriteArtwork
            )
        }
    }

    fun resumeBatchAutoTagAfterPermission(granted: Boolean) {
        _pendingBatchWriteSender.value = null
        val continuation = pendingAutoTagContinuation
        pendingAutoTagContinuation = null
        if (continuation == null) {
            _batchAutoTagState.update {
                it.copy(isRunning = false, waitingForWritePermission = false, completed = true)
            }
            return
        }
        if (!granted) {
            _batchAutoTagState.update {
                it.copy(
                    isRunning = false,
                    waitingForWritePermission = false,
                    completed = true,
                    currentTrackLabel = "",
                    recentResults = (listOf(
                        BatchItemOutcome(
                            trackTitle = continuation.pendingRequest.track.title,
                            detail = "Permiso de escritura denegado",
                            isSuccess = false
                        )
                    ) + it.recentResults).take(40)
                )
            }
            onSnackbar("Permiso de escritura cancelado")
            return
        }
        _batchAutoTagState.update { it.copy(waitingForWritePermission = false, isRunning = true) }
        batchAutoTagJob = scope.launch {
            runAutoTagLoop(
                pendingFirstRequest = continuation.pendingRequest,
                tracksToProcess = continuation.remainingTracks,
                coverCache = continuation.coverCache,
                overwriteArtwork = continuation.overwriteArtwork
            )
        }
    }

    private suspend fun runAutoTagLoop(
        pendingFirstRequest: TrackTagEditRequest?,
        tracksToProcess: List<AudioTrack>,
        coverCache: MutableMap<String, String?>,
        overwriteArtwork: Boolean
    ) {
        var isFirstPending = pendingFirstRequest
        for ((index, track) in tracksToProcess.withIndex()) {
            _batchAutoTagState.update { it.copy(currentTrackLabel = "${track.artist} — ${track.title}") }

            val request: TrackTagEditRequest? = if (isFirstPending != null && index == 0) {
                val req = isFirstPending
                isFirstPending = null
                req
            } else {
                buildDeezerTagRequestForTrack(track, coverCache, overwriteArtwork)
            }

            if (request == null) {
                _batchAutoTagState.update { state ->
                    state.copy(
                        processedCount = state.processedCount + 1,
                        failedCount = state.failedCount + 1,
                        recentResults = (listOf(
                            BatchItemOutcome(track.title, "Sin coincidencia en Deezer", false)
                        ) + state.recentResults).take(40)
                    )
                }
                continue
            }

            when (val outcome = AudioTagWriter.writeTagsAndRename(context, request)) {
                is PhysicalTagEditOutcome.NeedsWritePermission -> {
                    val remainingFromHere = tracksToProcess.subList(index, tracksToProcess.size)
                    val batchSender = AudioTagWriter.createBatchWritePermissionSender(context, remainingFromHere)
                        ?: outcome.intentSender
                    pendingAutoTagContinuation = PendingAutoTagContinuation(
                        pendingRequest = request,
                        remainingTracks = remainingFromHere,
                        coverCache = coverCache,
                        overwriteArtwork = overwriteArtwork
                    )
                    _batchAutoTagState.update { it.copy(waitingForWritePermission = true) }
                    _pendingBatchWriteSender.value = batchSender
                    return
                }
                is PhysicalTagEditOutcome.Success -> {
                    repository.applyEditedTrackInLibrary(request.track, outcome.updatedTrack)
                    onRefreshTracks(listOf(outcome.updatedTrack))
                    _batchAutoTagState.update { state ->
                        state.copy(
                            processedCount = state.processedCount + 1,
                            updatedCount = state.updatedCount + 1,
                            recentResults = (listOf(
                                BatchItemOutcome(
                                    trackTitle = outcome.updatedTrack.title,
                                    detail = if (outcome.physicalTagsWritten) "Archivo y biblioteca" else "Biblioteca actualizada",
                                    isSuccess = true
                                )
                            ) + state.recentResults).take(40)
                        )
                    }
                }
                is PhysicalTagEditOutcome.Failure -> {
                    _batchAutoTagState.update { state ->
                        state.copy(
                            processedCount = state.processedCount + 1,
                            failedCount = state.failedCount + 1,
                            recentResults = (listOf(
                                BatchItemOutcome(track.title, outcome.reason, false)
                            ) + state.recentResults).take(40)
                        )
                    }
                }
            }
        }

        // If pendingFirstRequest was passed with empty tracksToProcess, handle edge case cleanly
        if (tracksToProcess.isEmpty() && isFirstPending != null) {
            val outcome = AudioTagWriter.writeTagsAndRename(context, isFirstPending)
            if (outcome is PhysicalTagEditOutcome.Success) {
                repository.applyEditedTrackInLibrary(isFirstPending.track, outcome.updatedTrack)
                onRefreshTracks(listOf(outcome.updatedTrack))
                _batchAutoTagState.update {
                    it.copy(processedCount = it.processedCount + 1, updatedCount = it.updatedCount + 1)
                }
            }
        }

        _batchAutoTagState.update {
            it.copy(
                isRunning = false,
                waitingForWritePermission = false,
                completed = true,
                currentTrackLabel = ""
            )
        }
        val finalUpdated = _batchAutoTagState.value.updatedCount
        onSnackbar(
            if (finalUpdated > 0) "Auto-etiquetado completado: $finalUpdated pistas actualizadas"
            else "Auto-etiquetado finalizado sin cambios"
        )
    }

    private suspend fun buildDeezerTagRequestForTrack(
        track: AudioTrack,
        coverCache: MutableMap<String, String?>,
        overwriteArtwork: Boolean
    ): TrackTagEditRequest? {
        val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        val searchResult = musicApiService.searchDeezerTagSuggestions(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSec = durationSec,
            limit = 3
        ).getOrNull() ?: return null
        val best = searchResult.firstOrNull() ?: return null
        val enriched = musicApiService.fetchDeezerTrackTagDetails(best)

        val wantsArtwork = overwriteArtwork || track.albumArtUri.isNullOrBlank()
        val cachedCoverUri = if (wantsArtwork && !enriched.coverXlUrl.isNullOrBlank()) {
            val url = enriched.coverXlUrl
            if (coverCache.containsKey(url)) {
                coverCache[url]
            } else {
                val key = (enriched.albumId.takeIf { it > 0L } ?: enriched.deezerTrackId).toString()
                AudioTagWriter.downloadAndCacheArtworkFile(context, url, key).also {
                    coverCache[url] = it
                }
            }
        } else {
            null
        }

        val currentBase = AudioTagWriter.splitFileName(track, context).baseName
        return TrackTagEditRequest(
            track = track,
            newFileNameBase = currentBase,
            title = enriched.title.ifBlank { track.title },
            artist = enriched.artist.ifBlank { track.artist },
            album = enriched.album.ifBlank { track.album },
            albumArtist = enriched.albumArtist.ifBlank { track.albumArtist },
            genre = enriched.genre.ifBlank { track.genre },
            year = if (enriched.year > 0) enriched.year else track.year,
            trackNumber = if (enriched.trackNumber > 0) enriched.trackNumber else track.trackNumber,
            customArtworkUri = cachedCoverUri ?: track.albumArtUri
        )
    }

    // =========================================================================
    // Batch .lrc Synced Lyrics Downloader (LRCLIB)
    // =========================================================================

    fun openBatchLyrics(tracks: List<AudioTrack>) {
        if (_batchLyricsState.value.isRunning) {
            _batchLyricsState.update { it.copy(isVisible = true) }
            return
        }
        val localTracks = tracks.filterNot { isStreamingUri(it.contentUri) }
        _batchLyricsState.value = BatchLyricsUiState(
            isVisible = true,
            syncedOnly = true,
            overwriteExisting = false,
            targetTracks = localTracks
        )
    }

    fun setBatchLyricsSyncedOnly(value: Boolean) {
        _batchLyricsState.update { it.copy(syncedOnly = value) }
    }

    fun setBatchLyricsOverwrite(value: Boolean) {
        _batchLyricsState.update { it.copy(overwriteExisting = value) }
    }

    fun dismissBatchLyrics() {
        if (_batchLyricsState.value.isRunning || _batchLyricsState.value.isExportingFolder) return
        _batchLyricsState.update { it.copy(isVisible = false) }
    }

    fun cancelBatchLyrics() {
        batchLyricsJob?.cancel()
        batchLyricsJob = null
        _batchLyricsState.update {
            it.copy(isRunning = false, completed = true, currentTrackLabel = "")
        }
    }

    fun startBatchLyrics() {
        val current = _batchLyricsState.value
        if (current.isRunning) return
        val targets = current.targetTracks
        if (targets.isEmpty()) return

        batchLyricsJob?.cancel()
        downloadedLrcForExport.clear()
        _downloadedLrcExportCount.value = 0

        _batchLyricsState.update {
            it.copy(
                isRunning = true,
                completed = false,
                totalCount = targets.size,
                processedCount = 0,
                syncedDownloadedCount = 0,
                plainDownloadedCount = 0,
                skippedAlreadySyncedCount = 0,
                notFoundCount = 0,
                currentTrackLabel = targets.first().title,
                recentResults = emptyList()
            )
        }

        val syncedOnly = current.syncedOnly
        val overwrite = current.overwriteExisting

        batchLyricsJob = scope.launch {
            try {
                val savedLyricsMap = repository.getAllSavedLyricsOnce()
                val semaphore = Semaphore(3)
                val stateMutex = Mutex()

                coroutineScope {
                    targets.map { track ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val existingSaved = savedLyricsMap[track.id]
                                if (!overwrite) {
                                    if (existingSaved != null) {
                                        val isSavedSynced = AudioTagParsers.parseLrcText(
                                            existingSaved.rawLrcText,
                                            existingSaved.sourceLabel
                                        )?.isSynced == true
                                        if (isSavedSynced || !syncedOnly) {
                                            stateMutex.withLock {
                                                _batchLyricsState.update { s ->
                                                    s.copy(
                                                        processedCount = s.processedCount + 1,
                                                        skippedAlreadySyncedCount = s.skippedAlreadySyncedCount + 1,
                                                        currentTrackLabel = "${track.artist} — ${track.title}",
                                                        recentResults = (listOf(
                                                            BatchItemOutcome(
                                                                track.title,
                                                                if (isSavedSynced) "Ya tiene .lrc" else "Ya tiene letra",
                                                                true
                                                            )
                                                        ) + s.recentResults).take(40)
                                                    )
                                                }
                                            }
                                            return@withPermit
                                        }
                                    } else if (repository.hasEmbeddedSyncedLyrics(track)) {
                                        stateMutex.withLock {
                                            _batchLyricsState.update { s ->
                                                s.copy(
                                                    processedCount = s.processedCount + 1,
                                                    skippedAlreadySyncedCount = s.skippedAlreadySyncedCount + 1,
                                                    currentTrackLabel = "${track.artist} — ${track.title}",
                                                    recentResults = (listOf(
                                                        BatchItemOutcome(track.title, "Letra incrustada", true)
                                                    ) + s.recentResults).take(40)
                                                )
                                            }
                                        }
                                        return@withPermit
                                    }
                                }

                                val candidates = repository.searchOnlineLyrics(track).getOrNull().orEmpty()
                                val trackDurationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
                                val chosen = pickBestLyricsCandidate(
                                    candidates = candidates,
                                    trackDurationSec = trackDurationSec,
                                    syncedOnly = syncedOnly
                                )

                                if (chosen == null) {
                                    stateMutex.withLock {
                                        _batchLyricsState.update { s ->
                                            s.copy(
                                                processedCount = s.processedCount + 1,
                                                notFoundCount = s.notFoundCount + 1,
                                                currentTrackLabel = "${track.artist} — ${track.title}",
                                                recentResults = (listOf(
                                                    BatchItemOutcome(
                                                        track.title,
                                                        if (syncedOnly) "Sin .lrc sincronizada" else "No encontrada",
                                                        false
                                                    )
                                                ) + s.recentResults).take(40)
                                            )
                                        }
                                    }
                                    return@withPermit
                                }

                                val savedOk = repository.saveBatchDownloadedLyrics(track.id, chosen)
                                if (savedOk) {
                                    downloadedLrcForExport.add(track to chosen.rawLyrics)
                                    _downloadedLrcExportCount.value = downloadedLrcForExport.size
                                }
                                stateMutex.withLock {
                                    _batchLyricsState.update { s ->
                                        s.copy(
                                            processedCount = s.processedCount + 1,
                                            syncedDownloadedCount = s.syncedDownloadedCount +
                                                if (savedOk && chosen.isSynced) 1 else 0,
                                            plainDownloadedCount = s.plainDownloadedCount +
                                                if (savedOk && !chosen.isSynced) 1 else 0,
                                            notFoundCount = s.notFoundCount + if (!savedOk) 1 else 0,
                                            currentTrackLabel = "${track.artist} — ${track.title}",
                                            recentResults = (listOf(
                                                BatchItemOutcome(
                                                    track.title,
                                                    if (savedOk) {
                                                        if (chosen.isSynced) ".lrc sincronizada" else "Letra plana"
                                                    } else "Error al guardar",
                                                    savedOk
                                                )
                                            ) + s.recentResults).take(40)
                                        )
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
            } catch (c: CancellationException) {
                throw c
            } finally {
                val syncedCount = _batchLyricsState.value.syncedDownloadedCount
                val plainCount = _batchLyricsState.value.plainDownloadedCount
                _batchLyricsState.update {
                    it.copy(isRunning = false, completed = true, currentTrackLabel = "")
                }
                val totalSaved = syncedCount + plainCount
                if (totalSaved > 0) {
                    onSnackbar("Letras guardadas: $syncedCount .lrc sincronizadas" +
                        if (plainCount > 0) " y $plainCount planas" else "")
                } else {
                    onSnackbar("Búsqueda de letras finalizada")
                }
            }
        }
    }

    fun exportDownloadedLrcToTreeUri(treeUri: Uri) {
        val snapshot = synchronized(downloadedLrcForExport) { downloadedLrcForExport.toList() }
        if (snapshot.isEmpty()) {
            onSnackbar("No hay letras recién descargadas para exportar")
            return
        }
        scope.launch {
            _batchLyricsState.update { it.copy(isExportingFolder = true) }
            val exportedCount = withContext(Dispatchers.IO) {
                val parentDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                    ?: return@withContext 0
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                var count = 0
                for ((track, rawLrc) in snapshot) {
                    val baseName = AudioTagWriter.splitFileName(track, context).baseName
                    val fileName = "${AudioTagWriter.sanitizeFileBaseName(baseName)}.lrc"
                    val createdUri = runCatching {
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            parentDocUri,
                            "application/octet-stream",
                            fileName
                        )
                    }.getOrNull() ?: continue
                    val written = runCatching {
                        context.contentResolver.openOutputStream(createdUri)
                            ?.bufferedWriter(Charsets.UTF_8)
                            ?.use { writer -> writer.write(rawLrc) }
                        true
                    }.getOrDefault(false)
                    if (written) count++
                }
                count
            }
            _batchLyricsState.update { it.copy(isExportingFolder = false) }
            onSnackbar(
                if (exportedCount > 0) "$exportedCount archivos .lrc exportados a la carpeta"
                else "No se pudieron crear archivos .lrc en esa carpeta"
            )
        }
    }

    companion object {
        fun needsMetadataEnrichment(track: AudioTrack): Boolean {
            val missingCover = track.albumArtUri.isNullOrBlank()
            val missingArtist = track.artist.isBlank() ||
                track.artist.equals("Artista desconocido", ignoreCase = true) ||
                track.artist.equals("Unknown Artist", ignoreCase = true) ||
                track.artist.equals("<unknown>", ignoreCase = true)
            val missingAlbum = track.album.isBlank() ||
                track.album.equals("Álbum desconocido", ignoreCase = true) ||
                track.album.equals("Unknown Album", ignoreCase = true) ||
                track.album.equals("<unknown>", ignoreCase = true)
            return missingCover || missingArtist || missingAlbum || track.year <= 0 || track.trackNumber <= 0
        }

        fun pickBestLyricsCandidate(
            candidates: List<OnlineLyricsCandidate>,
            trackDurationSec: Int?,
            syncedOnly: Boolean
        ): OnlineLyricsCandidate? {
            if (candidates.isEmpty()) return null
            val syncedPool = candidates.filter { it.isSynced }
            val syncedMatch = syncedPool.firstOrNull { cand ->
                trackDurationSec == null ||
                    cand.durationSec == null ||
                    kotlin.math.abs(cand.durationSec - trackDurationSec) <= 25
            } ?: if (trackDurationSec == null) syncedPool.firstOrNull() else null

            if (syncedMatch != null) return syncedMatch
            if (syncedOnly) return null

            val plainPool = candidates.filter { !it.isSynced }
            return plainPool.firstOrNull { cand ->
                trackDurationSec == null ||
                    cand.durationSec == null ||
                    kotlin.math.abs(cand.durationSec - trackDurationSec) <= 25
            } ?: plainPool.firstOrNull()
        }

        private fun isStreamingUri(uri: String): Boolean {
            return uri.startsWith("http://", ignoreCase = true) ||
                uri.startsWith("https://", ignoreCase = true)
        }
    }
}
