package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.BuildConfig
import com.example.ai.LyricsTranslationCoordinator
import com.example.ai.LyricsTranslationLanguage
import com.example.ai.LyricsTranslationUiState
import com.example.ai.TranslationEngine
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.local.SonoraDatabase
import com.example.data.repository.AppUpdateChecker
import com.example.data.repository.AppUpdateInfo
import com.example.data.repository.DeviceMusicScanner
import com.example.data.repository.LastFmRepository
import com.example.data.repository.LastFmTrackMatcher
import com.example.data.repository.MusicRepository
import com.example.data.repository.OnlineLyricsCandidate
import com.example.data.repository.UpdateCheckResult
import com.example.playback.AppUpdateNotifier
import com.example.model.AlbumGroup
import com.example.model.ArtistGroup
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.DeezerTrackEnrichment
import com.example.model.EqBandInfo
import com.example.model.EqPreset
import com.example.model.FolderGroup
import com.example.model.GenreGroup
import com.example.model.HeadphoneEqProfile
import com.example.model.LastFmPeriod
import com.example.model.LastFmProfileTab
import com.example.model.LastFmSession
import com.example.model.LastFmTrackItem
import com.example.model.LastFmUserInfo
import com.example.model.LibraryFilter
import com.example.model.LyricsData
import com.example.model.PlaylistSummary
import com.example.model.SdCardFolder
import com.example.model.SortOrder
import com.example.playback.PlaybackController
import com.example.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SonoraUiState(
    val allTracks: List<AudioTrack> = emptyList(),
    val filteredTracks: List<AudioTrack> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val genres: List<GenreGroup> = emptyList(),
    val folders: List<FolderGroup> = emptyList(),
    val excludedFolders: Set<String> = emptySet(),
    val sdCardFolders: List<SdCardFolder> = emptyList(),
    val removingSdFolderUris: Map<String, String> = emptyMap(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val favoriteTracks: List<AudioTrack> = emptyList(),
    val recentTracks: List<AudioTrack> = emptyList(),
    val mostPlayedTracks: List<AudioTrack> = emptyList(),
    val historyTracks: List<AudioTrack> = emptyList(),
    val flacCount: Int = 0,
    val mp3Count: Int = 0,
    val otherCount: Int = 0,
    val searchQuery: String = "",
    val isGlobalSearchOpen: Boolean = false,
    val globalMatchingTracks: List<AudioTrack> = emptyList(),
    val globalMatchingAlbums: List<AlbumGroup> = emptyList(),
    val globalMatchingArtists: List<ArtistGroup> = emptyList(),
    val globalMatchingPlaylists: List<PlaylistSummary> = emptyList(),
    val selectedFilter: LibraryFilter = LibraryFilter.ALL,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val isScanning: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val snackbarMessage: String? = null
)

data class AppUpdateUiState(
    val autoCheckEnabled: Boolean = false,
    val githubRepoUrl: String = AppUpdateChecker.DEFAULT_GITHUB_REPO_URL,
    val isChecking: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val lastStatusMessage: String? = null,
    val promptConfigureRepo: Boolean = false
)

data class LastFmUiState(
    val hasCredentials: Boolean = false,
    val currentApiKey: String = "",
    val currentSharedSecret: String = "",
    val session: LastFmSession? = null,
    val userInfo: LastFmUserInfo? = null,
    val selectedTab: LastFmProfileTab = LastFmProfileTab.TOP_TRACKS,
    val selectedPeriod: LastFmPeriod = LastFmPeriod.SEVEN_DAYS,
    val topTracks: List<LastFmTrackItem> = emptyList(),
    val lovedTracks: List<LastFmTrackItem> = emptyList(),
    val recentTracks: List<LastFmTrackItem> = emptyList(),
    val isLoadingTab: Boolean = false,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
    val showCredentialsDialog: Boolean = false,
    val pendingAuthUrl: String? = null
)

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SonoraDatabase.getInstance(application)
    private val scanner = DeviceMusicScanner(application)
    private val repository = MusicRepository(
        context = application,
        scanner = scanner,
        playlistDao = database.playlistDao(),
        importedTrackDao = database.importedTrackDao(),
        trackStatsDao = database.trackStatsDao(),
        trackTagOverrideDao = database.trackTagOverrideDao(),
        excludedFolderDao = database.excludedFolderDao(),
        trackLyricsDao = database.trackLyricsDao(),
        trackLyricsTranslationDao = database.trackLyricsTranslationDao()
    )
    private val preferencesStore = PlaybackPreferencesStore(application)
    private val lastFmRepository = LastFmRepository(preferencesStore)
    private val playbackController = PlaybackController(
        context = application,
        repository = repository,
        scope = viewModelScope,
        lastFmRepository = lastFmRepository
    )

    private val rawSearchQueryFlow = MutableStateFlow("")
    private val debouncedSearchQueryFlow = MutableStateFlow("")
    private val globalSearchOpenFlow = MutableStateFlow(false)
    private val removingSdFoldersFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    private val filterFlow = MutableStateFlow(LibraryFilter.ALL)
    private val sortOrderFlow = MutableStateFlow(SortOrder.RECENT)
    private val permissionFlow = MutableStateFlow(checkAudioPermission())
    private val snackbarFlow = MutableStateFlow<String?>(null)

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState
    val scrobbleNotice: StateFlow<String?> = playbackController.scrobbleNotice
    val mediaPlayer: StateFlow<Player?> = playbackController.playerFlow
    val audioSettings: StateFlow<AudioSettings> = playbackController.audioSettings
    val eqBands: StateFlow<List<EqBandInfo>> = playbackController.eqBands
    val sleepTimerRemainingMs: StateFlow<Long?> = playbackController.sleepTimerRemainingMs
    val stopAfterCurrentTrack: StateFlow<Boolean> = playbackController.stopAfterCurrentTrack

    private val _preferredTranslationEngine = MutableStateFlow(preferencesStore.loadTranslationEngine())
    val preferredTranslationEngine: StateFlow<TranslationEngine> = _preferredTranslationEngine

    private val _customEqProfiles = MutableStateFlow(preferencesStore.loadCustomEqProfiles())
    val customEqProfiles: StateFlow<List<HeadphoneEqProfile>> = _customEqProfiles.asStateFlow()

    private val _lastFmUiState = MutableStateFlow(
        LastFmUiState(
            hasCredentials = lastFmRepository.hasValidCredentials(),
            currentApiKey = lastFmRepository.getEffectiveApiKey(),
            currentSharedSecret = lastFmRepository.getEffectiveSharedSecret(),
            session = lastFmRepository.loadSession()
        )
    )
    val lastFmUiState: StateFlow<LastFmUiState> = _lastFmUiState.asStateFlow()
    private val inFlightDeezerKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var lastHandledOAuthToken: String? = null
    private var launchAuthAfterCredsSave: Boolean = false

    private val updateChecker = AppUpdateChecker()
    private val _updateUiState = MutableStateFlow(
        AppUpdateUiState(
            autoCheckEnabled = preferencesStore.isAutoUpdateCheckEnabled(),
            githubRepoUrl = preferencesStore.loadGithubRepoUrl(AppUpdateChecker.DEFAULT_GITHUB_REPO_URL)
                .ifBlank { AppUpdateChecker.DEFAULT_GITHUB_REPO_URL }
        )
    )
    val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()

    private val lyricsTranslationCoordinator = LyricsTranslationCoordinator(
        scope = viewModelScope,
        onPersistTranslation = { trackId, language, translated ->
            repository.saveLyricsTranslation(trackId, language, translated)
        }
    )
    val lyricsTranslationState: StateFlow<LyricsTranslationUiState> = lyricsTranslationCoordinator.state

    private data class QueryParams(
        val search: String,
        val isGlobalSearchOpen: Boolean,
        val filter: LibraryFilter,
        val sort: SortOrder,
        val isScanning: Boolean,
        val hasPermission: Boolean,
        val snackbar: String?,
        val removingSdFolders: Map<String, String>
    )

    private data class CollectionBundle(
        val visibleTracks: List<AudioTrack>,
        val unfilteredTracks: List<AudioTrack>,
        val excludedFolders: Set<String>,
        val playlists: List<PlaylistSummary>,
        val sdCardFolders: List<SdCardFolder>
    )

    private data class DerivedCollections(
        val allTracks: List<AudioTrack>,
        val albums: List<AlbumGroup>,
        val artists: List<ArtistGroup>,
        val genres: List<GenreGroup>,
        val folders: List<FolderGroup>,
        val excludedFolders: Set<String>,
        val sdCardFolders: List<SdCardFolder>,
        val playlists: List<PlaylistSummary>,
        val favoriteTracks: List<AudioTrack>,
        val recentTracks: List<AudioTrack>,
        val mostPlayedTracks: List<AudioTrack>,
        val historyTracks: List<AudioTrack>,
        val flacCount: Int,
        val mp3Count: Int,
        val otherCount: Int
    )

    private data class GlobalSearchResults(
        val appliedQuery: String = "",
        val matchingTracks: List<AudioTrack> = emptyList(),
        val matchingAlbums: List<AlbumGroup> = emptyList(),
        val matchingArtists: List<ArtistGroup> = emptyList(),
        val matchingPlaylists: List<PlaylistSummary> = emptyList()
    )

    private val globalSearchResultsFlow = MutableStateFlow(GlobalSearchResults())

    private val queryParamsFlow = combine(
        combine(debouncedSearchQueryFlow, globalSearchOpenFlow, filterFlow, sortOrderFlow) { search, open, filter, sort ->
            SearchFilterTriple(search, open, filter, sort)
        },
        combine(repository.isScanning, permissionFlow, snackbarFlow, removingSdFoldersFlow) { scanning, hasPerm, snack, removingSd ->
            StatusBundle(scanning, hasPerm, snack, removingSd)
        }
    ) { first, second ->
        QueryParams(
            search = first.search,
            isGlobalSearchOpen = first.isGlobalSearchOpen,
            filter = first.filter,
            sort = first.sort,
            isScanning = second.isScanning,
            hasPermission = second.hasPermission,
            snackbar = second.snackbar,
            removingSdFolders = second.removingSdFolders
        )
    }

    private data class SearchFilterTriple(
        val search: String,
        val isGlobalSearchOpen: Boolean,
        val filter: LibraryFilter,
        val sort: SortOrder
    )

    private data class StatusBundle(
        val isScanning: Boolean,
        val hasPermission: Boolean,
        val snackbar: String?,
        val removingSdFolders: Map<String, String>
    )

    private val collectionBundleFlow = combine(
        combine(
            repository.allTracksFlow,
            repository.unfilteredTracksFlow,
            repository.excludedFoldersFlow,
            repository.playlistsFlow,
            repository.sdCardFoldersFlow
        ) { visible, unfiltered, excluded, playlists, sdFolders ->
            CollectionBundle(visible, unfiltered, excluded, playlists, sdFolders)
        },
        removingSdFoldersFlow
    ) { bundle, removingMap ->
        if (removingMap.isEmpty()) {
            bundle
        } else {
            val removingUris = removingMap.keys
            val removingNames = removingMap.values.toHashSet()
            val filterOut: (AudioTrack) -> Boolean = { track ->
                track.sourceTreeUri in removingUris ||
                    removingUris.any { uri -> track.contentUri.startsWith(uri) } ||
                    (track.isDocumentTreeTrack && track.folderName in removingNames)
            }
            bundle.copy(
                visibleTracks = bundle.visibleTracks.filterNot(filterOut),
                unfilteredTracks = bundle.unfilteredTracks.filterNot(filterOut),
                sdCardFolders = bundle.sdCardFolders.filterNot { it.treeUri in removingUris }
            )
        }
    }

    private val derivedCollectionsFlow = collectionBundleFlow.map { bundle ->
        val tracks = bundle.visibleTracks
        val flacCount = tracks.count { it.format == AudioFormat.FLAC }
        val mp3Count = tracks.count { it.format == AudioFormat.MP3 }
        val otherCount = tracks.count {
            it.format == AudioFormat.WAV || it.format == AudioFormat.AAC || it.format == AudioFormat.OGG
        }

        val albums = tracks
            .groupBy { it.album }
            .map { (albumName, albumTracks) ->
                val sortedAlbumTracks = albumTracks.sortedWith(
                    compareBy<AudioTrack> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
                AlbumGroup(
                    albumName = albumName,
                    artist = albumTracks.firstNotNullOfOrNull { it.albumArtist.takeIf(String::isNotBlank) }
                        ?: albumTracks.firstOrNull()?.artist.orEmpty(),
                    albumArtUri = albumTracks.firstNotNullOfOrNull { it.albumArtUri },
                    tracks = sortedAlbumTracks,
                    totalDurationMs = albumTracks.sumOf { it.durationMs },
                    hasFlac = albumTracks.any { it.format == AudioFormat.FLAC },
                    year = albumTracks.firstNotNullOfOrNull { it.year.takeIf { y -> y > 0 } },
                    genre = albumTracks.firstNotNullOfOrNull { it.genre.takeIf(String::isNotBlank) }
                )
            }
            .sortedBy { it.albumName.lowercase() }

        val artists = tracks
            .groupBy { it.artist.ifBlank { "Artista desconocido" } }
            .map { (artistName, artistTracks) ->
                val sorted = artistTracks.sortedWith(
                    compareBy<AudioTrack> { it.album.lowercase() }
                        .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
                ArtistGroup(
                    artistName = artistName,
                    albumCount = artistTracks.map { it.album }.distinct().size,
                    tracks = sorted,
                    albumArtUri = artistTracks.firstNotNullOfOrNull { it.albumArtUri },
                    totalDurationMs = artistTracks.sumOf { it.durationMs }
                )
            }
            .sortedBy { it.artistName.lowercase() }

        val genres = tracks
            .groupBy { it.genre.ifBlank { "Sin género" } }
            .map { (genreName, genreTracks) ->
                GenreGroup(
                    genreName = genreName,
                    tracks = genreTracks.sortedBy { it.title.lowercase() },
                    totalDurationMs = genreTracks.sumOf { it.durationMs }
                )
            }
            .sortedBy { it.genreName.lowercase() }

        val folders = bundle.unfilteredTracks
            .groupBy { it.folderName.ifBlank { "Música" } }
            .map { (folderName, folderTracks) ->
                FolderGroup(
                    folderName = folderName,
                    tracks = folderTracks.sortedBy { it.title.lowercase() },
                    totalDurationMs = folderTracks.sumOf { it.durationMs },
                    isExcluded = folderName in bundle.excludedFolders
                )
            }
            .sortedBy { it.folderName.lowercase() }

        val favoriteTracks = tracks.filter { it.isFavorite }.sortedBy { it.title.lowercase() }
        val recentTracks = tracks.sortedByDescending { it.dateAddedSec }.take(30)
        val mostPlayedTracks = tracks.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(30)
        val historyTracks = tracks.filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(30)

        DerivedCollections(
            allTracks = tracks,
            albums = albums,
            artists = artists,
            genres = genres,
            folders = folders,
            excludedFolders = bundle.excludedFolders,
            sdCardFolders = bundle.sdCardFolders,
            playlists = bundle.playlists,
            favoriteTracks = favoriteTracks,
            recentTracks = recentTracks,
            mostPlayedTracks = mostPlayedTracks,
            historyTracks = historyTracks,
            flacCount = flacCount,
            mp3Count = mp3Count,
            otherCount = otherCount
        )
    }.flowOn(Dispatchers.Default)
        .onEach { derived ->
            playbackController.refreshTrackReferences(
                derived.playlists.flatMap { it.tracks } + derived.allTracks
            )
        }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        )

    private val filteredTracksFlow = combine(
        derivedCollectionsFlow,
        debouncedSearchQueryFlow,
        filterFlow,
        sortOrderFlow
    ) { derived, search, filter, sort ->
        val query = search.trim()
        derived.allTracks
            .asSequence()
            .filter { track ->
                when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.FLAC -> track.format == AudioFormat.FLAC
                    LibraryFilter.MP3 -> track.format == AudioFormat.MP3
                    LibraryFilter.OTHER -> track.format == AudioFormat.WAV ||
                        track.format == AudioFormat.AAC ||
                        track.format == AudioFormat.OGG
                }
            }
            .filter { track ->
                if (query.isEmpty()) true
                else {
                    track.title.contains(query, ignoreCase = true) ||
                        track.artist.contains(query, ignoreCase = true) ||
                        track.album.contains(query, ignoreCase = true) ||
                        track.folderName.contains(query, ignoreCase = true) ||
                        track.genre.contains(query, ignoreCase = true)
                }
            }
            .let { seq ->
                when (sort) {
                    SortOrder.RECENT -> seq.sortedByDescending { it.dateAddedSec }
                    SortOrder.TITLE -> seq.sortedBy { it.title.lowercase() }
                    SortOrder.ARTIST -> seq.sortedBy { it.artist.lowercase() }
                    SortOrder.DURATION -> seq.sortedByDescending { it.durationMs }
                    SortOrder.MOST_PLAYED -> seq.sortedByDescending { it.playCount }
                }
            }
            .toList()
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<SonoraUiState> = combine(
        derivedCollectionsFlow,
        filteredTracksFlow,
        globalSearchResultsFlow,
        queryParamsFlow
    ) { derived, filtered, globalResults, params ->
        SonoraUiState(
            allTracks = derived.allTracks,
            filteredTracks = filtered,
            albums = derived.albums,
            artists = derived.artists,
            genres = derived.genres,
            folders = derived.folders,
            excludedFolders = derived.excludedFolders,
            sdCardFolders = derived.sdCardFolders,
            removingSdFolderUris = params.removingSdFolders,
            playlists = derived.playlists,
            favoriteTracks = derived.favoriteTracks,
            recentTracks = derived.recentTracks,
            mostPlayedTracks = derived.mostPlayedTracks,
            historyTracks = derived.historyTracks,
            flacCount = derived.flacCount,
            mp3Count = derived.mp3Count,
            otherCount = derived.otherCount,
            searchQuery = globalResults.appliedQuery,
            isGlobalSearchOpen = params.isGlobalSearchOpen,
            globalMatchingTracks = globalResults.matchingTracks,
            globalMatchingAlbums = globalResults.matchingAlbums,
            globalMatchingArtists = globalResults.matchingArtists,
            globalMatchingPlaylists = globalResults.matchingPlaylists,
            selectedFilter = params.filter,
            sortOrder = params.sort,
            isScanning = params.isScanning,
            hasAudioPermission = params.hasPermission,
            snackbarMessage = params.snackbar
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SonoraUiState(hasAudioPermission = permissionFlow.value)
    )

    init {
        startDebouncedGlobalSearchPipeline()
        playbackController.connect()
        refreshLibrary()
        maybeRunPeriodicUpdateCheck()
    }

    @OptIn(FlowPreview::class)
    private fun startDebouncedGlobalSearchPipeline() {
        viewModelScope.launch {
            rawSearchQueryFlow
                .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .combine(derivedCollectionsFlow) { query, derived -> query to derived }
                .collectLatest { (query, derived) ->
                    val results = if (query.isBlank()) {
                        GlobalSearchResults(appliedQuery = query.trim())
                    } else {
                        withContext(Dispatchers.Default) {
                            computeGlobalSearchResults(query, derived)
                        }
                    }
                    debouncedSearchQueryFlow.value = query
                    globalSearchResultsFlow.value = results
                }
        }
    }

    private fun computeGlobalSearchResults(
        rawQuery: String,
        derived: DerivedCollections
    ): GlobalSearchResults {
        val query = rawQuery.trim()
        if (query.isEmpty()) return GlobalSearchResults(appliedQuery = "")

        val matchingTracks = derived.allTracks
            .asSequence()
            .filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true) ||
                    track.folderName.contains(query, ignoreCase = true) ||
                    track.genre.contains(query, ignoreCase = true)
            }
            .take(40)
            .toList()

        val matchingAlbums = derived.albums.filter { album ->
            album.albumName.contains(query, ignoreCase = true) ||
                album.artist.contains(query, ignoreCase = true)
        }

        val matchingArtists = derived.artists.filter { artist ->
            artist.artistName.contains(query, ignoreCase = true)
        }

        val matchingPlaylists = derived.playlists.filter { playlist ->
            playlist.name.contains(query, ignoreCase = true) ||
                playlist.description.contains(query, ignoreCase = true)
        }

        return GlobalSearchResults(
            appliedQuery = query,
            matchingTracks = matchingTracks,
            matchingAlbums = matchingAlbums,
            matchingArtists = matchingArtists,
            matchingPlaylists = matchingPlaylists
        )
    }

    override fun onCleared() {
        lyricsTranslationCoordinator.reset()
        playbackController.release()
        super.onCleared()
    }

    fun requiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun onPermissionResult(granted: Boolean) {
        permissionFlow.value = granted || checkAudioPermission()
        if (permissionFlow.value) {
            refreshLibrary()
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            permissionFlow.value = checkAudioPermission()
            repository.refreshMediaStoreLibrary(includeMediaStore = permissionFlow.value)
        }
    }

    fun addSdCardFolder(treeUri: Uri) {
        viewModelScope.launch {
            val added = repository.addSdCardFolder(treeUri)
            snackbarFlow.value = if (added != null) {
                "Carpeta «${added.displayName}» vinculada (${added.trackCount} pistas)"
            } else {
                "No se pudo vincular la carpeta seleccionada"
            }
        }
    }

    fun removeSdCardFolder(treeUri: String) {
        val displayName = uiState.value.sdCardFolders
            .firstOrNull { it.treeUri == treeUri }
            ?.displayName
            ?: "Carpeta SD"
        // Optimistic immediate UI update without blocking the rest of the app
        removingSdFoldersFlow.value = removingSdFoldersFlow.value + (treeUri to displayName)
        viewModelScope.launch {
            try {
                repository.removeSdCardFolder(treeUri)
                snackbarFlow.value = "Carpeta «$displayName» quitada"
            } finally {
                removingSdFoldersFlow.value = removingSdFoldersFlow.value - treeUri
            }
        }
    }

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importAudioFiles(uris)
            snackbarFlow.value = if (count > 0) {
                if (count == 1) "Se importó 1 canción a tu biblioteca"
                else "Se importaron $count canciones a tu biblioteca"
            } else {
                "Solo se admiten formatos de audio compatibles (MP3, FLAC, WAV, AAC, OGG)"
            }
        }
    }

    fun openGlobalSearch() {
        globalSearchOpenFlow.value = true
    }

    fun closeGlobalSearch() {
        globalSearchOpenFlow.value = false
        rawSearchQueryFlow.value = ""
        debouncedSearchQueryFlow.value = ""
        globalSearchResultsFlow.value = GlobalSearchResults()
    }

    fun toggleGlobalSearch() {
        if (globalSearchOpenFlow.value) closeGlobalSearch() else openGlobalSearch()
    }

    fun onSearchQueryChange(query: String) {
        rawSearchQueryFlow.value = query
    }

    fun updateSearchQuery(query: String) {
        onSearchQueryChange(query)
    }

    companion object {
        internal const val SEARCH_DEBOUNCE_MS = 150L
    }

    fun selectFilter(filter: LibraryFilter) {
        filterFlow.value = filter
    }

    fun selectSortOrder(order: SortOrder) {
        sortOrderFlow.value = order
    }

    fun consumeSnackbar() {
        snackbarFlow.value = null
        playbackController.clearError()
    }

    // Playback actions
    fun playTracks(tracks: List<AudioTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        playbackController.playTracks(tracks, startIndex, shuffle)
    }

    fun playNext(track: AudioTrack) {
        playbackController.playNext(track)
        snackbarFlow.value = "«${track.title}» sonará a continuación"
    }

    fun enqueueTrack(track: AudioTrack) {
        playbackController.enqueueTrack(track)
        snackbarFlow.value = "«${track.title}» agregada a la cola"
    }

    fun enqueueTracks(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        playbackController.enqueueTracks(tracks)
        snackbarFlow.value = if (tracks.size == 1) {
            "«${tracks.first().title}» agregada a la cola"
        } else {
            "${tracks.size} canciones agregadas a la cola"
        }
    }

    fun play() = playbackController.play()
    fun pause() = playbackController.pause()
    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun seekBackward(deltaMs: Long = 10_000L) = playbackController.seekBackward(deltaMs)
    fun seekForward(deltaMs: Long = 10_000L) = playbackController.seekForward(deltaMs)
    fun skipNext() = playbackController.skipNext()
    fun skipPrevious() = playbackController.skipPrevious()
    fun skipToQueueIndex(index: Int) = playbackController.skipToQueueIndex(index)
    fun removeFromQueue(index: Int) = playbackController.removeFromQueue(index)
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    // DSP, EQ & Sleep Timer
    fun updateAudioSettings(newSettings: AudioSettings, immediate: Boolean = true) {
        playbackController.updateAudioSettings(newSettings, immediate = immediate)
    }

    fun saveCustomEqProfile(name: String, settings: AudioSettings) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val profile = HeadphoneEqProfile(
            id = "custom_${System.currentTimeMillis()}",
            brand = "Personal",
            model = trimmed,
            formFactor = "Perfil guardado",
            preampDb = settings.preampDb.coerceIn(-12f, 12f),
            bandGainsMb = EqPreset.normalizeToTenBands(settings.bandGainsMb),
            isCustom = true
        )
        _customEqProfiles.value = preferencesStore.saveCustomEqProfile(profile)
        updateAudioSettings(
            settings.copy(
                eqEnabled = true,
                eqPreset = EqPreset.CUSTOM,
                activeProfileId = profile.id,
                activeProfileName = profile.fullName
            ),
            immediate = true
        )
        snackbarFlow.value = "Perfil «$trimmed» guardado"
    }

    fun deleteCustomEqProfile(profileId: String) {
        _customEqProfiles.value = preferencesStore.deleteCustomEqProfile(profileId)
        val current = audioSettings.value
        if (current.activeProfileId == profileId) {
            updateAudioSettings(
                current.copy(activeProfileId = null, activeProfileName = null),
                immediate = true
            )
        }
        snackbarFlow.value = "Perfil personalizado eliminado"
    }

    fun startSleepTimer(minutes: Int?, afterCurrentTrack: Boolean) {
        playbackController.startSleepTimer(minutes, afterCurrentTrack)
        snackbarFlow.value = if (afterCurrentTrack) {
            "La música se detendrá al terminar esta canción"
        } else if (minutes != null) {
            "Temporizador configurado a $minutes min"
        } else null
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
        snackbarFlow.value = "Temporizador cancelado"
    }

    // Favorites, Tags, Lyrics & Folder Exclusion
    fun toggleFavorite(track: AudioTrack) {
        val targetFavorite = !track.isFavorite
        playbackController.setTrackFavorite(track.id, targetFavorite)
        viewModelScope.launch {
            val isLocalLibraryTrack = !track.id.startsWith(LastFmTrackItem.PREVIEW_TRACK_ID_PREFIX)
            val isNowFav = if (isLocalLibraryTrack) {
                repository.setFavorite(track.id, targetFavorite)
            } else {
                targetFavorite
            }
            if (lastFmRepository.hasActiveSession()) {
                launch(Dispatchers.IO) {
                    lastFmRepository.setTrackLove(track.artist, track.title, targetFavorite)
                }
                val lookupKey = LastFmTrackItem.buildLookupKey(track.artist, track.title)
                _lastFmUiState.update { state ->
                    state.copy(
                        topTracks = state.topTracks.map {
                            if (it.lookupKey == lookupKey) it.copy(isLoved = targetFavorite) else it
                        },
                        lovedTracks = if (!targetFavorite) {
                            state.lovedTracks.filterNot { it.lookupKey == lookupKey }
                        } else state.lovedTracks,
                        recentTracks = state.recentTracks.map {
                            if (it.lookupKey == lookupKey) it.copy(isLoved = targetFavorite) else it
                        }
                    )
                }
            }
            snackbarFlow.value = if (isNowFav) {
                "«${track.title}» añadida a Favoritas"
            } else {
                "«${track.title}» quitada de Favoritas"
            }
        }
    }

    fun saveTrackTagOverride(
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
        viewModelScope.launch {
            repository.saveTrackTagOverride(
                trackId = trackId,
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                customArtworkUri = customArtworkUri
            )
            snackbarFlow.value = "Etiquetas actualizadas en tu biblioteca"
        }
    }

    fun toggleFolderExclusion(folderName: String, currentlyExcluded: Boolean) {
        viewModelScope.launch {
            if (currentlyExcluded) {
                repository.includeFolder(folderName)
                snackbarFlow.value = "Carpeta «$folderName» incluida en la biblioteca"
            } else {
                repository.excludeFolder(folderName)
                snackbarFlow.value = "Carpeta «$folderName» ocultada de la biblioteca"
            }
        }
    }

    suspend fun loadLyrics(track: AudioTrack): LyricsData? {
        val loaded = repository.loadLyrics(track)
        if (loaded != null && !loaded.hasEmbeddedTranslation) {
            val savedTranslation = repository.loadSavedLyricsTranslation(track.id, loaded)
            if (savedTranslation != null) {
                lyricsTranslationCoordinator.restoreSavedTranslation(
                    trackId = track.id,
                    originalLyrics = loaded,
                    language = savedTranslation.language,
                    translatedLyrics = savedTranslation.translatedLyrics
                )
            }
        }
        return loaded
    }

    fun resetLyricsTranslation() {
        lyricsTranslationCoordinator.reset()
    }

    fun setPreferredTranslationEngine(engine: TranslationEngine) {
        _preferredTranslationEngine.value = engine
        preferencesStore.saveTranslationEngine(engine)
    }

    fun translateLyrics(
        track: AudioTrack,
        lyrics: LyricsData,
        targetLanguage: LyricsTranslationLanguage,
        engine: TranslationEngine = _preferredTranslationEngine.value
    ) {
        if (engine != _preferredTranslationEngine.value) {
            setPreferredTranslationEngine(engine)
        }
        lyricsTranslationCoordinator.translate(
            trackId = track.id,
            trackTitle = track.title,
            trackArtist = track.artist,
            lyrics = lyrics,
            targetLanguage = targetLanguage,
            engine = engine
        )
    }

    fun saveManualLyricsTranslation(
        track: AudioTrack,
        originalLyrics: LyricsData,
        rawPastedText: String,
        targetLanguage: LyricsTranslationLanguage
    ) {
        viewModelScope.launch {
            val aligned = repository.saveManualLyricsTranslation(
                trackId = track.id,
                originalLyrics = originalLyrics,
                rawPastedText = rawPastedText,
                language = targetLanguage
            )
            if (aligned != null) {
                lyricsTranslationCoordinator.restoreSavedTranslation(
                    trackId = track.id,
                    originalLyrics = originalLyrics,
                    language = targetLanguage,
                    translatedLyrics = aligned
                )
                snackbarFlow.value = "Traducción guardada sin conexión para «${track.title}»"
            } else {
                snackbarFlow.value = "Pega al menos una línea de traducción válida"
            }
        }
    }

    fun clearSavedLyricsTranslation(track: AudioTrack) {
        viewModelScope.launch {
            repository.clearSavedLyricsTranslation(track.id)
            lyricsTranslationCoordinator.invalidateTrack(track.id)
            snackbarFlow.value = "Traducción guardada eliminada"
        }
    }

    suspend fun loadAndSaveLyricsFromUri(track: AudioTrack, uri: Uri): LyricsData? {
        val result = repository.loadAndSaveLyricsFromUri(track.id, uri)
        if (result != null) {
            lyricsTranslationCoordinator.invalidateTrack(track.id)
        }
        snackbarFlow.value = if (result != null) {
            "Letra .lrc guardada para «${track.title}»"
        } else {
            "No se encontró letra válida en el archivo .lrc"
        }
        return result
    }

    suspend fun clearSavedLyrics(track: AudioTrack): LyricsData? {
        val fallback = repository.clearSavedLyrics(track)
        lyricsTranslationCoordinator.invalidateTrack(track.id)
        snackbarFlow.value = "Letra guardada eliminada"
        return fallback
    }

    suspend fun searchOnlineLyrics(
        track: AudioTrack,
        customQuery: String? = null
    ): Result<List<OnlineLyricsCandidate>> = repository.searchOnlineLyrics(track, customQuery)

    suspend fun saveDownloadedLyrics(
        track: AudioTrack,
        candidate: OnlineLyricsCandidate
    ): LyricsData? {
        val saved = repository.saveDownloadedLyrics(track.id, candidate)
        if (saved != null) {
            lyricsTranslationCoordinator.invalidateTrack(track.id)
            snackbarFlow.value = "Letra guardada sin conexión para «${track.title}»"
        } else {
            snackbarFlow.value = "No se pudo procesar la letra seleccionada"
        }
        return saved
    }

    // Playlist actions + M3U Import / Export
    fun createPlaylist(name: String, description: String = "", initialTrack: AudioTrack? = null) {
        createPlaylistWithTracks(
            name = name,
            description = description,
            initialTracks = listOfNotNull(initialTrack)
        )
    }

    fun createPlaylistWithTracks(
        name: String,
        description: String = "",
        initialTracks: List<AudioTrack> = emptyList()
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(trimmed, description)
            if (initialTracks.isNotEmpty()) {
                val added = repository.addTracksToPlaylist(id, initialTracks)
                snackbarFlow.value = if (added == 1) {
                    "Lista «$trimmed» creada con «${initialTracks.first().title}»"
                } else {
                    "Lista «$trimmed» creada con $added canciones"
                }
            } else {
                snackbarFlow.value = "Lista «$trimmed» creada"
            }
        }
    }

    fun updatePlaylist(playlistId: Long, name: String, description: String) {
        viewModelScope.launch {
            repository.updatePlaylist(playlistId, name, description)
            snackbarFlow.value = "Lista actualizada"
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            snackbarFlow.value = "Lista eliminada"
        }
    }

    fun addTracksToPlaylist(playlistId: Long, playlistName: String, tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val added = repository.addTracksToPlaylist(playlistId, tracks)
            snackbarFlow.value = when {
                added == 0 -> "Las canciones ya estaban en «$playlistName»"
                added == 1 -> "1 canción agregada a «$playlistName»"
                else -> "$added canciones agregadas a «$playlistName»"
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            snackbarFlow.value = "Canción quitada de la lista"
        }
    }

    fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            val removed = repository.removeTracksFromPlaylist(playlistId, trackIds)
            snackbarFlow.value = if (removed == 1) {
                "1 canción quitada de la lista"
            } else {
                "$removed canciones quitadas de la lista"
            }
        }
    }

    fun removeImportedTracks(tracks: List<AudioTrack>, showFeedback: Boolean = true) {
        val uris = tracks.filter { it.isSafImported }.map { it.contentUri }
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.removeImportedTracksByUris(uris)
            if (showFeedback && count > 0) {
                snackbarFlow.value = if (count == 1) {
                    "1 canción quitada de tu biblioteca"
                } else {
                    "$count canciones quitadas de tu biblioteca"
                }
            }
        }
    }

    fun notifyMessage(message: String) {
        snackbarFlow.value = message
    }

    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun exportPlaylistM3u(playlist: PlaylistSummary, uri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportPlaylistM3uToUri(playlist, uri)
            snackbarFlow.value = if (ok) {
                "Lista «${playlist.name}» exportada en formato .m3u"
            } else {
                "No se pudo exportar la lista"
            }
        }
    }

    fun importPlaylistM3u(uri: Uri) {
        viewModelScope.launch {
            val (name, count) = repository.importPlaylistFromM3u(uri)
            snackbarFlow.value = if (count > 0) {
                "Lista «$name» importada con $count canciones"
            } else {
                "No se encontraron coincidencias en tu biblioteca para «$name»"
            }
        }
    }

    suspend fun loadTrackMetadata(track: AudioTrack): AudioTrackMetadata {
        return repository.extractTrackMetadata(track)
    }

    // Optional GitHub update checker
    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        preferencesStore.setAutoUpdateCheckEnabled(enabled)
        _updateUiState.value = _updateUiState.value.copy(autoCheckEnabled = enabled)
        if (enabled && _updateUiState.value.githubRepoUrl.isNotBlank()) {
            maybeRunPeriodicUpdateCheck(forceImmediate = true)
        }
    }

    fun requestConfigureGithubRepo() {
        _updateUiState.value = _updateUiState.value.copy(promptConfigureRepo = true)
    }

    fun dismissConfigureGithubRepo() {
        _updateUiState.value = _updateUiState.value.copy(promptConfigureRepo = false)
    }

    fun dismissAvailableUpdate() {
        _updateUiState.value = _updateUiState.value.copy(availableUpdate = null)
    }

    fun saveGithubRepoAndCheck(rawUrl: String, checkNow: Boolean = true) {
        val normalized = AppUpdateChecker.normalizeRepoUrl(rawUrl)
        if (normalized.isEmpty()) {
            snackbarFlow.value = "Ingresa un enlace válido de GitHub (ej. ${AppUpdateChecker.DEFAULT_GITHUB_REPO_URL})"
            return
        }
        preferencesStore.saveGithubRepoUrl(normalized)
        _updateUiState.value = _updateUiState.value.copy(
            githubRepoUrl = normalized,
            promptConfigureRepo = false
        )
        if (checkNow) {
            checkForUpdates(manual = true)
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        val current = _updateUiState.value
        if (current.isChecking) return
        val repoUrl = AppUpdateChecker.normalizeRepoUrl(
            current.githubRepoUrl.ifBlank { AppUpdateChecker.DEFAULT_GITHUB_REPO_URL }
        )
        if (repoUrl.isEmpty()) {
            if (manual) {
                _updateUiState.value = current.copy(promptConfigureRepo = true)
            }
            return
        }

        _updateUiState.value = current.copy(isChecking = true)
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate(repoUrl, BuildConfig.VERSION_NAME)
            preferencesStore.saveLastUpdateCheckTimestampMs(System.currentTimeMillis())
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val info = result.info
                    val lastNotified = preferencesStore.loadLastNotifiedUpdateVersion()
                    if (manual || lastNotified != info.latestVersion) {
                        AppUpdateNotifier.notifyUpdateAvailable(getApplication(), info)
                        preferencesStore.saveLastNotifiedUpdateVersion(info.latestVersion)
                    }
                    _updateUiState.value = _updateUiState.value.copy(
                        isChecking = false,
                        availableUpdate = info,
                        lastStatusMessage = "Nueva versión v${info.latestVersion} disponible"
                    )
                }
                is UpdateCheckResult.UpToDate -> {
                    val msg = "Tienes la última versión (v${result.currentVersion})"
                    _updateUiState.value = _updateUiState.value.copy(
                        isChecking = false,
                        availableUpdate = null,
                        lastStatusMessage = msg
                    )
                    if (manual) {
                        snackbarFlow.value = msg
                    }
                }
                UpdateCheckResult.RepoNotConfigured -> {
                    _updateUiState.value = _updateUiState.value.copy(
                        isChecking = false,
                        promptConfigureRepo = manual
                    )
                }
                is UpdateCheckResult.Error -> {
                    _updateUiState.value = _updateUiState.value.copy(
                        isChecking = false,
                        lastStatusMessage = result.message
                    )
                    if (manual) {
                        snackbarFlow.value = result.message
                    }
                }
            }
        }
    }

    private fun maybeRunPeriodicUpdateCheck(forceImmediate: Boolean = false) {
        val state = _updateUiState.value
        if (!state.autoCheckEnabled) return
        val repoUrl = AppUpdateChecker.normalizeRepoUrl(
            state.githubRepoUrl.ifBlank { AppUpdateChecker.DEFAULT_GITHUB_REPO_URL }
        )
        if (repoUrl.isEmpty()) return
        val lastCheck = preferencesStore.loadLastUpdateCheckTimestampMs()
        val elapsed = System.currentTimeMillis() - lastCheck
        if (forceImmediate || elapsed >= AppUpdateChecker.PERIODIC_INTERVAL_MS) {
            checkForUpdates(manual = false)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // Last.fm + Deezer integration
    fun openLastFmCredentialsDialog(connectAfterSave: Boolean = false) {
        launchAuthAfterCredsSave = connectAfterSave
        _lastFmUiState.update {
            it.copy(
                showCredentialsDialog = true,
                currentApiKey = lastFmRepository.getEffectiveApiKey(),
                currentSharedSecret = lastFmRepository.getEffectiveSharedSecret()
            )
        }
    }

    fun dismissLastFmCredentialsDialog() {
        launchAuthAfterCredsSave = false
        _lastFmUiState.update { it.copy(showCredentialsDialog = false) }
    }

    fun saveLastFmCredentials(apiKey: String, sharedSecret: String) {
        val cleanKey = apiKey.trim()
        val cleanSecret = sharedSecret.trim()
        if (cleanKey.isEmpty() || cleanSecret.isEmpty()) {
            snackbarFlow.value = "Ingresa tanto el API Key como el Shared Secret de Last.fm"
            return
        }
        lastFmRepository.saveCredentials(cleanKey, cleanSecret)
        val shouldStartAuth = launchAuthAfterCredsSave
        launchAuthAfterCredsSave = false
        _lastFmUiState.update {
            it.copy(
                hasCredentials = true,
                currentApiKey = cleanKey,
                currentSharedSecret = cleanSecret,
                showCredentialsDialog = false
            )
        }
        if (shouldStartAuth) {
            startLastFmOAuthLogin()
        } else {
            snackbarFlow.value = "Credenciales de Last.fm guardadas"
        }
    }

    fun startLastFmOAuthLogin() {
        if (!lastFmRepository.hasValidCredentials()) {
            openLastFmCredentialsDialog(connectAfterSave = true)
            return
        }
        val authUrl = lastFmRepository.buildAuthUrl() ?: return
        _lastFmUiState.update { it.copy(pendingAuthUrl = authUrl, errorMessage = null) }
    }

    fun consumePendingLastFmAuthUrl() {
        _lastFmUiState.update { it.copy(pendingAuthUrl = null) }
    }

    fun handleLastFmCallbackUri(uri: Uri?) {
        if (uri == null) return
        if (!uri.scheme.equals("davooplayer", ignoreCase = true) ||
            !uri.host.equals("lastfm-callback", ignoreCase = true)
        ) {
            return
        }
        val token = uri.getQueryParameter("token")?.trim().orEmpty()
        if (token.isEmpty() || token == lastHandledOAuthToken) return
        lastHandledOAuthToken = token

        _lastFmUiState.update { it.copy(isAuthenticating = true, errorMessage = null) }
        viewModelScope.launch {
            val res = lastFmRepository.completeOAuthCallback(token)
            res.onSuccess { session ->
                _lastFmUiState.update {
                    it.copy(
                        session = session,
                        isAuthenticating = false,
                        errorMessage = null
                    )
                }
                snackbarFlow.value = "Conectado a Last.fm como @${session.username}"
                refreshLastFmProfile(forceAll = true)
            }.onFailure { err ->
                val msg = err.message ?: "No se pudo iniciar sesión con Last.fm"
                _lastFmUiState.update { it.copy(isAuthenticating = false, errorMessage = msg) }
                snackbarFlow.value = msg
            }
        }
    }

    fun logoutLastFm() {
        lastFmRepository.logout()
        _lastFmUiState.update {
            it.copy(
                session = null,
                userInfo = null,
                topTracks = emptyList(),
                lovedTracks = emptyList(),
                recentTracks = emptyList(),
                errorMessage = null
            )
        }
        snackbarFlow.value = "Sesión de Last.fm cerrada"
    }

    fun selectLastFmTab(tab: LastFmProfileTab) {
        _lastFmUiState.update { it.copy(selectedTab = tab, errorMessage = null) }
        ensureCurrentLastFmTabLoaded()
    }

    fun selectLastFmPeriod(period: LastFmPeriod) {
        if (_lastFmUiState.value.selectedPeriod == period) return
        _lastFmUiState.update { it.copy(selectedPeriod = period, topTracks = emptyList()) }
        loadTopTracks(period)
    }

    fun ensureLastFmProfileLoaded() {
        val state = _lastFmUiState.value
        if (state.session == null) return
        if (state.userInfo == null || state.userInfo.avatarUrl.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                lastFmRepository.fetchUserInfo().onSuccess { info ->
                    _lastFmUiState.update { cur ->
                        cur.copy(
                            userInfo = info,
                            session = cur.session?.copy(avatarUrl = info.avatarUrl ?: cur.session.avatarUrl)
                        )
                    }
                }
            }
        }
        ensureCurrentLastFmTabLoaded()
    }

    fun refreshLastFmProfile(forceAll: Boolean = false) {
        val state = _lastFmUiState.value
        if (state.session == null) return
        lastFmRepository.clearEmptyDeezerCacheEntries()
        viewModelScope.launch(Dispatchers.IO) {
            lastFmRepository.fetchUserInfo().onSuccess { info ->
                _lastFmUiState.update { cur ->
                    cur.copy(
                        userInfo = info,
                        session = cur.session?.copy(avatarUrl = info.avatarUrl ?: cur.session.avatarUrl)
                    )
                }
            }
        }
        if (forceAll) {
            loadTopTracks(state.selectedPeriod)
            loadLovedTracks()
            loadRecentTracks()
        } else {
            when (state.selectedTab) {
                LastFmProfileTab.TOP_TRACKS -> loadTopTracks(state.selectedPeriod)
                LastFmProfileTab.LOVED_TRACKS -> loadLovedTracks()
                LastFmProfileTab.RECENT_TRACKS -> loadRecentTracks()
            }
        }
    }

    private fun ensureCurrentLastFmTabLoaded() {
        val state = _lastFmUiState.value
        if (state.session == null) return
        when (state.selectedTab) {
            LastFmProfileTab.TOP_TRACKS -> if (state.topTracks.isEmpty()) loadTopTracks(state.selectedPeriod)
            LastFmProfileTab.LOVED_TRACKS -> if (state.lovedTracks.isEmpty()) loadLovedTracks()
            LastFmProfileTab.RECENT_TRACKS -> if (state.recentTracks.isEmpty()) loadRecentTracks()
        }
    }

    private fun loadTopTracks(period: LastFmPeriod) {
        _lastFmUiState.update { it.copy(isLoadingTab = true, errorMessage = null) }
        viewModelScope.launch {
            lastFmRepository.fetchTopTracks(period).onSuccess { items ->
                _lastFmUiState.update { cur ->
                    if (cur.selectedPeriod == period) {
                        cur.copy(topTracks = items, isLoadingTab = false, errorMessage = null)
                    } else cur.copy(isLoadingTab = false)
                }
            }.onFailure { err ->
                _lastFmUiState.update {
                    it.copy(isLoadingTab = false, errorMessage = err.message ?: "Error al cargar Top Canciones")
                }
            }
        }
    }

    private fun loadLovedTracks() {
        _lastFmUiState.update { it.copy(isLoadingTab = true, errorMessage = null) }
        viewModelScope.launch {
            lastFmRepository.fetchLovedTracks().onSuccess { items ->
                _lastFmUiState.update { it.copy(lovedTracks = items, isLoadingTab = false, errorMessage = null) }
            }.onFailure { err ->
                _lastFmUiState.update {
                    it.copy(isLoadingTab = false, errorMessage = err.message ?: "Error al cargar Favoritas")
                }
            }
        }
    }

    private fun loadRecentTracks() {
        _lastFmUiState.update { it.copy(isLoadingTab = true, errorMessage = null) }
        viewModelScope.launch {
            lastFmRepository.fetchRecentTracks().onSuccess { items ->
                _lastFmUiState.update { it.copy(recentTracks = items, isLoadingTab = false, errorMessage = null) }
            }.onFailure { err ->
                _lastFmUiState.update {
                    it.copy(isLoadingTab = false, errorMessage = err.message ?: "Error al cargar Historial")
                }
            }
        }
    }

    fun ensureDeezerEnrichment(item: LastFmTrackItem) {
        if (item.deezerLookupDone) return
        val key = item.lookupKey
        lastFmRepository.getCachedDeezer(item.artist, item.title)?.let { cached ->
            applyDeezerEnrichment(key, cached)
            return
        }
        if (!inFlightDeezerKeys.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val enrichment = lastFmRepository.buscarEnDeezer(item.artist, item.title)
                applyDeezerEnrichment(key, enrichment)
            } finally {
                inFlightDeezerKeys.remove(key)
            }
        }
    }

    private fun applyDeezerEnrichment(lookupKey: String, enrichment: DeezerTrackEnrichment?) {
        _lastFmUiState.update { state ->
            state.copy(
                topTracks = state.topTracks.map {
                    if (it.lookupKey == lookupKey) it.copy(deezerEnrichment = enrichment, deezerLookupDone = true) else it
                },
                lovedTracks = state.lovedTracks.map {
                    if (it.lookupKey == lookupKey) it.copy(deezerEnrichment = enrichment, deezerLookupDone = true) else it
                },
                recentTracks = state.recentTracks.map {
                    if (it.lookupKey == lookupKey) it.copy(deezerEnrichment = enrichment, deezerLookupDone = true) else it
                }
            )
        }
    }

    fun playLastFmPreview(item: LastFmTrackItem) {
        val localMatch = LastFmTrackMatcher.findMatchingLocalTrack(item, uiState.value.allTracks)
        if (localMatch != null) {
            playbackController.playTracks(listOf(localMatch), 0, shuffle = false)
            return
        }

        val readyPreview = item.toPreviewAudioTrack()
        if (readyPreview != null) {
            playbackController.playTracks(listOf(readyPreview), 0, shuffle = false)
            return
        }
        viewModelScope.launch {
            val enrichment = withContext(Dispatchers.IO) {
                lastFmRepository.buscarEnDeezer(item.artist, item.title)
            }
            applyDeezerEnrichment(item.lookupKey, enrichment)
            val updatedItem = item.copy(deezerEnrichment = enrichment, deezerLookupDone = true)
            val previewTrack = updatedItem.toPreviewAudioTrack()
            if (previewTrack != null) {
                playbackController.playTracks(listOf(previewTrack), 0, shuffle = false)
            } else {
                snackbarFlow.value = "No hay fragmento de audio disponible en Deezer para «${item.title}»"
            }
        }
    }

    fun toggleLastFmItemLove(item: LastFmTrackItem) {
        if (!lastFmRepository.hasActiveSession()) {
            snackbarFlow.value = "Conecta tu cuenta de Last.fm primero"
            return
        }
        val targetLove = !item.isLoved
        val key = item.lookupKey
        _lastFmUiState.update { state ->
            state.copy(
                topTracks = state.topTracks.map {
                    if (it.lookupKey == key) it.copy(isLoved = targetLove) else it
                },
                lovedTracks = if (!targetLove) {
                    state.lovedTracks.filterNot { it.lookupKey == key }
                } else state.lovedTracks,
                recentTracks = state.recentTracks.map {
                    if (it.lookupKey == key) it.copy(isLoved = targetLove) else it
                }
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            lastFmRepository.setTrackLove(item.artist, item.title, targetLove).onSuccess {
                snackbarFlow.value = if (targetLove) {
                    "«${item.title}» marcada como favorita en Last.fm"
                } else {
                    "«${item.title}» quitada de favoritas en Last.fm"
                }
            }.onFailure { err ->
                snackbarFlow.value = err.message ?: "No se pudo sincronizar favorito con Last.fm"
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        val perm = requiredAudioPermission()
        return ContextCompat.checkSelfPermission(
            getApplication(),
            perm
        ) == PackageManager.PERMISSION_GRANTED
    }
}
