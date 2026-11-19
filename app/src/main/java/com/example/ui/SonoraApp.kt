package com.example.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.repository.LrcLibLyricsFetcher
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.LyricsData
import com.example.model.PlaylistSummary
import com.example.model.SmartPlaylistType
import com.example.ui.components.AudioAlbumArtThumbnail
import com.example.ui.components.BatchAddToPlaylistBottomSheet
import com.example.ui.components.CreateOrEditPlaylistDialog
import com.example.ui.components.CreditsBottomSheet
import com.example.ui.components.EqualizerAndDspSheet
import com.example.ui.components.FoldersExclusionSheet
import com.example.ui.components.LyricsBottomSheet
import com.example.ui.components.Media3FloatingBottomBar
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.PlaylistMosaicCover
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerSheet
import com.example.ui.components.TagEditorBottomSheet
import com.example.ui.components.TrackMetadataBottomSheet
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDuration
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartPlaylistDetailScreen
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan
import com.example.ui.viewmodel.SonoraUiState
import com.example.ui.viewmodel.SonoraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Library : Route

    @Serializable
    data object Albums : Route

    @Serializable
    data object Playlists : Route

    @Serializable
    data class AlbumDetail(val albumName: String) : Route

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : Route

    @Serializable
    data class SmartPlaylistDetail(val typeName: String) : Route

    @Serializable
    data object NowPlaying : Route

    @Serializable
    data object Settings : Route
}

private data class TopDestination(
    val route: Route,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(
        route = Route.Library,
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Rounded.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        testTag = "nav_library"
    ),
    TopDestination(
        route = Route.Albums,
        labelRes = R.string.nav_collection,
        selectedIcon = Icons.Rounded.Album,
        unselectedIcon = Icons.Outlined.Album,
        testTag = "nav_albums"
    ),
    TopDestination(
        route = Route.Playlists,
        labelRes = R.string.nav_playlists,
        selectedIcon = Icons.AutoMirrored.Rounded.QueueMusic,
        unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic,
        testTag = "nav_playlists"
    )
)

@Composable
fun SonoraApp(
    viewModel: SonoraViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val activePlayer by viewModel.mediaPlayer.collectAsStateWithLifecycle()
    val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val eqBands by viewModel.eqBands.collectAsStateWithLifecycle()
    val customEqProfiles by viewModel.customEqProfiles.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val stopAfterCurrentTrack by viewModel.stopAfterCurrentTrack.collectAsStateWithLifecycle()
    val lyricsTranslationState by viewModel.lyricsTranslationState.collectAsStateWithLifecycle()
    val preferredTranslationEngine by viewModel.preferredTranslationEngine.collectAsStateWithLifecycle()
    var requestedArtistDrilldown by remember { mutableStateOf<String?>(null) }

    val navController = rememberNavController()
    val safePopBackStack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Modal states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var pendingTracksForNewPlaylist by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var pendingMediaStoreDeleteCount by remember { mutableStateOf(0) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showFoldersSheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }

    var metadataTargetTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var loadedTrackMetadata by remember { mutableStateOf<AudioTrackMetadata?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(false) }

    var lyricsTargetTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var loadedLyrics by remember { mutableStateOf<LyricsData?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(false) }

    var tagEditorTargetTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var pickedCustomCoverUri by remember { mutableStateOf<String?>(null) }

    var pendingExportPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var requestedNotificationPerm by remember { mutableStateOf(false) }

    // Dynamic accent color for floating bottom bar
    var dynamicBottomBarAccent by remember(playbackState.currentTrack?.id) { mutableStateOf<Color?>(null) }
    LaunchedEffect(
        playbackState.currentTrack?.id,
        playbackState.currentTrack?.albumArtUri,
        audioSettings.dynamicArtworkColors
    ) {
        val current = playbackState.currentTrack
        dynamicBottomBarAccent = if (current != null && audioSettings.dynamicArtworkColors) {
            val base = if (current.format == AudioFormat.FLAC || current.format == AudioFormat.WAV) {
                LosslessCyan
            } else CopperAmber
            ArtworkColorExtractor.extractAccentColor(
                context = context,
                audioUri = current.contentUri,
                albumArtUri = current.albumArtUri,
                fallbackColor = base
            )
        } else null
    }

    // Permission & SAF file launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioPerm = viewModel.requiredAudioPermission()
        viewModel.onPermissionResult(results[audioPerm] == true)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris)
        }
    }

    val openSdFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.addSdCardFolder(treeUri)
        }
    }

    val pickCoverImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pickedCustomCoverUri = uri.toString()
        }
    }

    val pickLrcFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = lyricsTargetTrack
        if (uri != null && target != null) {
            coroutineScope.launch {
                isLoadingLyrics = true
                val saved = viewModel.loadAndSaveLyricsFromUri(target, uri)
                if (saved != null) {
                    loadedLyrics = saved
                }
                isLoadingLyrics = false
            }
        }
    }

    val importM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPlaylistM3u(uri)
        }
    }

    val exportM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val targetPlaylist = pendingExportPlaylist
        pendingExportPlaylist = null
        if (uri != null && targetPlaylist != null) {
            viewModel.exportPlaylistM3u(targetPlaylist, uri)
        }
    }

    val deleteTracksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val count = pendingMediaStoreDeleteCount
        pendingMediaStoreDeleteCount = 0
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshLibrary()
            viewModel.notifyMessage(
                if (count == 1) "Canción eliminada del dispositivo"
                else "$count canciones eliminadas del dispositivo"
            )
        } else {
            viewModel.notifyMessage("Eliminación cancelada")
        }
    }

    val handleBatchDeleteTracks: (List<AudioTrack>) -> Unit = { tracks ->
        val safTracks = tracks.filter { it.isSafImported }
        val sdTreeTracks = tracks.filter { it.isDocumentTreeTrack }
        val mediaStoreTracks = tracks.filterNot { it.isSafImported || it.isDocumentTreeTrack }
        if (safTracks.isNotEmpty()) {
            viewModel.removeImportedTracks(safTracks, showFeedback = mediaStoreTracks.isEmpty() && sdTreeTracks.isEmpty())
        }
        if (sdTreeTracks.isNotEmpty()) {
            coroutineScope.launch {
                var deletedSd = 0
                withContext(Dispatchers.IO) {
                    for (sdTrack in sdTreeTracks) {
                        val deleted = runCatching {
                            android.provider.DocumentsContract.deleteDocument(
                                context.contentResolver,
                                Uri.parse(sdTrack.contentUri)
                            )
                        }.getOrDefault(false)
                        if (deleted) deletedSd++
                    }
                }
                if (deletedSd > 0) {
                    viewModel.refreshLibrary()
                    if (mediaStoreTracks.isEmpty()) {
                        viewModel.notifyMessage(
                            if (deletedSd == 1) "Canción eliminada de la tarjeta SD"
                            else "$deletedSd canciones eliminadas de la tarjeta SD"
                        )
                    }
                } else if (mediaStoreTracks.isEmpty() && safTracks.isEmpty()) {
                    viewModel.notifyMessage("Android no permitió eliminar archivos de esta tarjeta SD (solo lectura)")
                }
            }
        }
        if (mediaStoreTracks.isNotEmpty()) {
            val uris = mediaStoreTracks.mapNotNull { runCatching { Uri.parse(it.contentUri) }.getOrNull() }
            if (uris.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val launched = runCatching {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                        pendingMediaStoreDeleteCount = uris.size
                        deleteTracksLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }.isSuccess
                    if (!launched) {
                        pendingMediaStoreDeleteCount = 0
                        viewModel.notifyMessage("No se pudo abrir el diálogo de eliminación del sistema")
                    }
                } else {
                    coroutineScope.launch {
                        var deleted = 0
                        var recoverableSender: android.content.IntentSender? = null
                        withContext(Dispatchers.IO) {
                            for (uri in uris) {
                                try {
                                    if (context.contentResolver.delete(uri, null, null) > 0) {
                                        deleted++
                                    }
                                } catch (e: SecurityException) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        e is RecoverableSecurityException &&
                                        recoverableSender == null
                                    ) {
                                        recoverableSender = e.userAction.actionIntent.intentSender
                                    }
                                } catch (_: Throwable) {
                                    // Skip invalid or unmounted volume URIs safely
                                }
                            }
                        }
                        if (deleted > 0) {
                            viewModel.refreshLibrary()
                        }
                        val sender = recoverableSender
                        if (sender != null) {
                            pendingMediaStoreDeleteCount = uris.size - deleted
                            deleteTracksLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        } else if (deleted > 0) {
                            viewModel.notifyMessage(
                                if (deleted == 1) "Canción eliminada del dispositivo"
                                else "$deleted canciones eliminadas del dispositivo"
                            )
                        } else if (safTracks.isEmpty() && sdTreeTracks.isEmpty()) {
                            viewModel.notifyMessage("No se pudieron eliminar las canciones seleccionadas")
                        }
                    }
                }
            }
        }
    }

    val launchPermissionRequest = {
        val perms = buildList {
            add(viewModel.requiredAudioPermission())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        audioPermissionLauncher.launch(perms)
    }

    val ensureNotificationPermissionForPlayback = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !requestedNotificationPerm &&
            !viewModel.hasNotificationPermission()
        ) {
            requestedNotificationPerm = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launchSafImport = {
        importFilesLauncher.launch(
            arrayOf("audio/mpeg", "audio/flac", "audio/x-flac", "audio/wav", "audio/mp4", "audio/ogg", "audio/*")
        )
    }

    LaunchedEffect(metadataTargetTrack?.id) {
        val target = metadataTargetTrack
        if (target == null) {
            loadedTrackMetadata = null
            isLoadingMetadata = false
        } else {
            isLoadingMetadata = true
            loadedTrackMetadata = viewModel.loadTrackMetadata(target)
            isLoadingMetadata = false
        }
    }

    LaunchedEffect(lyricsTargetTrack?.id) {
        val target = lyricsTargetTrack
        viewModel.resetLyricsTranslation()
        if (target == null) {
            loadedLyrics = null
            isLoadingLyrics = false
        } else {
            isLoadingLyrics = true
            loadedLyrics = viewModel.loadLyrics(target)
            isLoadingLyrics = false
        }
    }

    // Snackbar messages from ViewModel or Playback
    val activeMessage = uiState.snackbarMessage ?: playbackState.playbackError
    LaunchedEffect(activeMessage) {
        if (activeMessage != null) {
            snackbarHostState.showSnackbar(activeMessage)
            viewModel.consumeSnackbar()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isNowPlaying = currentDestination?.hasRoute<Route.NowPlaying>() == true
    val isSettings = currentDestination?.hasRoute<Route.Settings>() == true
    val isMainTab = currentDestination?.hasRoute<Route.Library>() == true ||
        currentDestination?.hasRoute<Route.Albums>() == true ||
        currentDestination?.hasRoute<Route.Playlists>() == true

    BackHandler(enabled = uiState.isGlobalSearchOpen && isMainTab) {
        viewModel.closeGlobalSearch()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen && !isNowPlaying) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    topDestinations.forEach { dest ->
                        val selected = when (dest.route) {
                            Route.Library -> currentDestination?.hasRoute<Route.Library>() == true
                            Route.Albums -> currentDestination?.hasRoute<Route.Albums>() == true ||
                                currentDestination?.hasRoute<Route.AlbumDetail>() == true
                            Route.Playlists -> currentDestination?.hasRoute<Route.Playlists>() == true ||
                                currentDestination?.hasRoute<Route.PlaylistDetail>() == true ||
                                currentDestination?.hasRoute<Route.SmartPlaylistDetail>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                viewModel.closeGlobalSearch()
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (isNowPlaying) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isNowPlaying) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (isWideScreen) it.navigationBarsPadding() else it },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(
                                visible = playbackState.currentTrack != null,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut()
                            ) {
                                val player = activePlayer
                                val openNowPlaying = {
                                    navController.navigate(Route.NowPlaying) {
                                        launchSingleTop = true
                                    }
                                }
                                if (player != null) {
                                    Media3FloatingBottomBar(
                                        player = player,
                                        accentOverride = dynamicBottomBarAccent,
                                        onOpenNowPlaying = openNowPlaying,
                                        modifier = Modifier
                                            .widthIn(max = 680.dp)
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                } else if (playbackState.currentTrack != null) {
                                    MiniPlayerBar(
                                        playbackState = playbackState,
                                        onOpenNowPlaying = openNowPlaying,
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onSkipNext = viewModel::skipNext,
                                        modifier = Modifier
                                            .widthIn(max = 680.dp)
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            if (!isWideScreen && !isSettings) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    topDestinations.forEach { dest ->
                                        val selected = when (dest.route) {
                                            Route.Library -> currentDestination?.hasRoute<Route.Library>() == true
                                            Route.Albums -> currentDestination?.hasRoute<Route.Albums>() == true ||
                                                currentDestination?.hasRoute<Route.AlbumDetail>() == true
                                            Route.Playlists -> currentDestination?.hasRoute<Route.Playlists>() == true ||
                                                currentDestination?.hasRoute<Route.PlaylistDetail>() == true ||
                                                currentDestination?.hasRoute<Route.SmartPlaylistDetail>() == true
                                            else -> false
                                        }
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                viewModel.closeGlobalSearch()
                                                navController.navigate(dest.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = stringResource(dest.labelRes)
                                                )
                                            },
                                            label = { Text(stringResource(dest.labelRes)) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.testTag(dest.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val bottomContentPadding = PaddingValues(
                        bottom = innerPadding.calculateBottomPadding()
                    )

                    NavHost(
                        navController = navController,
                        startDestination = Route.Library,
                        modifier = Modifier
                            .fillMaxSize()
                            .let {
                                if (!isNowPlaying) it.widthIn(max = 960.dp) else it
                            }
                    ) {
                        composable<Route.Library> {
                            LibraryScreen(
                                uiState = uiState,
                                playbackState = playbackState,
                                onRequestPermission = launchPermissionRequest,
                                onImportFiles = launchSafImport,
                                onOpenSettings = {
                                    viewModel.closeGlobalSearch()
                                    navController.navigate(Route.Settings) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenGlobalSearch = viewModel::openGlobalSearch,
                                onSearchChange = viewModel::updateSearchQuery,
                                onSelectSortOrder = viewModel::selectSortOrder,
                                onPlayTrack = { tracks, index ->
                                    ensureNotificationPermissionForPlayback()
                                    viewModel.playTracks(tracks, index, shuffle = false)
                                },
                                onShuffleAll = { tracks ->
                                    ensureNotificationPermissionForPlayback()
                                    val randomStart = tracks.indices.randomOrNull() ?: 0
                                    viewModel.playTracks(tracks, randomStart, shuffle = true)
                                },
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::enqueueTrack,
                                onAddToPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onShowLyrics = { track -> lyricsTargetTrack = track },
                                onEditTags = { track ->
                                    pickedCustomCoverUri = null
                                    tagEditorTargetTrack = track
                                },
                                bottomContentPadding = bottomContentPadding,
                                onShowMetadata = { track -> metadataTargetTrack = track },
                                onAddTracksToPlaylist = { tracks -> tracksToAddToPlaylist = tracks },
                                onEnqueueTracks = viewModel::enqueueTracks,
                                onDeleteTracks = handleBatchDeleteTracks
                            )
                        }

                        composable<Route.Settings> {
                            SettingsScreen(
                                totalTracksCount = uiState.allTracks.size,
                                isScanning = uiState.isScanning,
                                excludedFoldersCount = uiState.excludedFolders.size,
                                sdCardFoldersCount = uiState.sdCardFolders.size,
                                audioSettings = audioSettings,
                                isSleepTimerActive = sleepTimerRemainingMs != null || stopAfterCurrentTrack,
                                preferredTranslationEngine = preferredTranslationEngine,
                                onBack = safePopBackStack,
                                onRefreshLibrary = viewModel::refreshLibrary,
                                onImportFiles = launchSafImport,
                                onOpenFoldersManager = { showFoldersSheet = true },
                                onAddSdCardFolder = { openSdFolderLauncher.launch(null) },
                                onOpenEqualizer = { showEqualizerSheet = true },
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onUpdateAudioSettings = { viewModel.updateAudioSettings(it, immediate = true) },
                                onSelectTranslationEngine = viewModel::setPreferredTranslationEngine,
                                onOpenAboutAndLegal = { showCreditsSheet = true },
                                bottomContentPadding = bottomContentPadding
                            )
                        }

                        composable<Route.Albums> {
                            AlbumsScreen(
                                albums = uiState.albums,
                                artists = uiState.artists,
                                genres = uiState.genres,
                                folders = uiState.folders,
                                playbackState = playbackState,
                                onAlbumClick = { album ->
                                    navController.navigate(Route.AlbumDetail(album.albumName))
                                },
                                onPlayTracks = { tracks, index ->
                                    ensureNotificationPermissionForPlayback()
                                    viewModel.playTracks(tracks, index, shuffle = false)
                                },
                                onShuffleTracks = { tracks ->
                                    ensureNotificationPermissionForPlayback()
                                    val randomStart = tracks.indices.randomOrNull() ?: 0
                                    viewModel.playTracks(tracks, randomStart, shuffle = true)
                                },
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::enqueueTrack,
                                onAddToPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onShowLyrics = { track -> lyricsTargetTrack = track },
                                onEditTags = { track ->
                                    pickedCustomCoverUri = null
                                    tagEditorTargetTrack = track
                                },
                                onShowMetadata = { track -> metadataTargetTrack = track },
                                onToggleFolderExclude = viewModel::toggleFolderExclusion,
                                onOpenGlobalSearch = viewModel::openGlobalSearch,
                                requestedArtistName = requestedArtistDrilldown,
                                onConsumeRequestedArtist = { requestedArtistDrilldown = null },
                                bottomContentPadding = bottomContentPadding
                            )
                        }

                        composable<Route.AlbumDetail> { entry ->
                            val detail = entry.toRoute<Route.AlbumDetail>()
                            val album = uiState.albums.find { it.albumName == detail.albumName }
                            AlbumDetailScreen(
                                album = album,
                                playbackState = playbackState,
                                onBack = safePopBackStack,
                                onPlayTrack = { tracks, index ->
                                    ensureNotificationPermissionForPlayback()
                                    viewModel.playTracks(tracks, index, shuffle = false)
                                },
                                onShuffleAlbum = { tracks ->
                                    ensureNotificationPermissionForPlayback()
                                    val randomStart = tracks.indices.randomOrNull() ?: 0
                                    viewModel.playTracks(tracks, randomStart, shuffle = true)
                                },
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::enqueueTrack,
                                onAddToPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                                bottomContentPadding = bottomContentPadding,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onShowLyrics = { track -> lyricsTargetTrack = track },
                                onEditTags = { track ->
                                    pickedCustomCoverUri = null
                                    tagEditorTargetTrack = track
                                },
                                onShowMetadata = { track -> metadataTargetTrack = track }
                            )
                        }

                        composable<Route.Playlists> {
                            PlaylistsScreen(
                                playlists = uiState.playlists,
                                favoriteCount = uiState.favoriteTracks.size,
                                recentCount = uiState.recentTracks.size,
                                mostPlayedCount = uiState.mostPlayedTracks.size,
                                historyCount = uiState.historyTracks.size,
                                onCreatePlaylist = {
                                    pendingTracksForNewPlaylist = emptyList()
                                    showCreatePlaylistDialog = true
                                },
                                onImportM3u = {
                                    importM3uLauncher.launch(
                                        arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "*/*")
                                    )
                                },
                                onOpenGlobalSearch = viewModel::openGlobalSearch,
                                onSmartPlaylistClick = { smartType ->
                                    navController.navigate(Route.SmartPlaylistDetail(smartType.name))
                                },
                                onPlaylistClick = { playlist ->
                                    navController.navigate(Route.PlaylistDetail(playlist.id))
                                },
                                bottomContentPadding = bottomContentPadding
                            )
                        }

                        composable<Route.SmartPlaylistDetail> { entry ->
                            val detail = entry.toRoute<Route.SmartPlaylistDetail>()
                            val type = SmartPlaylistType.entries.firstOrNull { it.name == detail.typeName }
                                ?: SmartPlaylistType.FAVORITES
                            val tracks = when (type) {
                                SmartPlaylistType.FAVORITES -> uiState.favoriteTracks
                                SmartPlaylistType.RECENTLY_ADDED -> uiState.recentTracks
                                SmartPlaylistType.MOST_PLAYED -> uiState.mostPlayedTracks
                                SmartPlaylistType.HISTORY -> uiState.historyTracks
                            }
                            SmartPlaylistDetailScreen(
                                type = type,
                                tracks = tracks,
                                playbackState = playbackState,
                                onBack = safePopBackStack,
                                onPlayTracks = { list, index ->
                                    ensureNotificationPermissionForPlayback()
                                    viewModel.playTracks(list, index, shuffle = false)
                                },
                                onShuffleTracks = { list ->
                                    ensureNotificationPermissionForPlayback()
                                    val randomStart = list.indices.randomOrNull() ?: 0
                                    viewModel.playTracks(list, randomStart, shuffle = true)
                                },
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::enqueueTrack,
                                onAddToOtherPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onShowLyrics = { track -> lyricsTargetTrack = track },
                                onEditTags = { track ->
                                    pickedCustomCoverUri = null
                                    tagEditorTargetTrack = track
                                },
                                onShowMetadata = { track -> metadataTargetTrack = track },
                                bottomContentPadding = bottomContentPadding
                            )
                        }

                        composable<Route.PlaylistDetail> { entry ->
                            val detail = entry.toRoute<Route.PlaylistDetail>()
                            val playlist = uiState.playlists.find { it.id == detail.playlistId }
                            PlaylistDetailScreen(
                                playlist = playlist,
                                allTracks = uiState.allTracks,
                                playbackState = playbackState,
                                onBack = safePopBackStack,
                                onPlayTracks = { tracks, index ->
                                    ensureNotificationPermissionForPlayback()
                                    viewModel.playTracks(tracks, index, shuffle = false)
                                },
                                onShuffleTracks = { tracks ->
                                    ensureNotificationPermissionForPlayback()
                                    val randomStart = tracks.indices.randomOrNull() ?: 0
                                    viewModel.playTracks(tracks, randomStart, shuffle = true)
                                },
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::enqueueTrack,
                                onAddToOtherPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                                onAddSelectedTracks = viewModel::addTracksToPlaylist,
                                onRemoveTrack = viewModel::removeTrackFromPlaylist,
                                onMoveTrack = viewModel::moveTrackInPlaylist,
                                onUpdatePlaylist = viewModel::updatePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onExportM3u = { target ->
                                    pendingExportPlaylist = target
                                    val safeFileName = target.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                    exportM3uLauncher.launch("$safeFileName.m3u")
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onShowLyrics = { track -> lyricsTargetTrack = track },
                                onEditTags = { track ->
                                    pickedCustomCoverUri = null
                                    tagEditorTargetTrack = track
                                },
                                bottomContentPadding = bottomContentPadding,
                                onShowMetadata = { track -> metadataTargetTrack = track },
                                onRemoveTracks = viewModel::removeTracksFromPlaylist,
                                onAddTracksToOtherPlaylist = { tracks -> tracksToAddToPlaylist = tracks }
                            )
                        }

                        composable<Route.NowPlaying> {
                            NowPlayingScreen(
                                playbackState = playbackState,
                                audioSettings = audioSettings,
                                sleepTimerRemainingMs = sleepTimerRemainingMs,
                                stopAfterCurrentTrack = stopAfterCurrentTrack,
                                onCollapse = safePopBackStack,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSeekTo = viewModel::seekTo,
                                onSeekBackward = { viewModel.seekBackward() },
                                onSeekForward = { viewModel.seekForward() },
                                onSkipPrevious = viewModel::skipPrevious,
                                onSkipNext = viewModel::skipNext,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeat = viewModel::cycleRepeatMode,
                                onOpenQueue = { showQueueSheet = true },
                                onAddToPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onOpenEqualizer = { showEqualizerSheet = true },
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onShowLyrics = { track -> lyricsTargetTrack = track },
                                onEditTags = { track ->
                                    pickedCustomCoverUri = null
                                    tagEditorTargetTrack = track
                                },
                                onShowMetadata = { track -> metadataTargetTrack = track }
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.isGlobalSearchOpen && isMainTab,
                        enter = fadeIn() + slideInVertically { -it / 6 },
                        exit = fadeOut() + slideOutVertically { -it / 6 },
                        modifier = Modifier
                            .fillMaxSize()
                            .let { if (!isNowPlaying) it.widthIn(max = 960.dp) else it }
                    ) {
                        GlobalSearchOverlay(
                            uiState = uiState,
                            currentTrackId = playbackState.currentTrack?.id,
                            isPlaying = playbackState.isPlaying,
                            bottomContentPadding = bottomContentPadding,
                            onQueryChange = viewModel::updateSearchQuery,
                            onClose = viewModel::closeGlobalSearch,
                            onPlayTrack = { tracks, index ->
                                ensureNotificationPermissionForPlayback()
                                viewModel.playTracks(tracks, index, shuffle = false)
                            },
                            onPlayNext = viewModel::playNext,
                            onAddToQueue = viewModel::enqueueTrack,
                            onAddToPlaylist = { track -> tracksToAddToPlaylist = listOf(track) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onShowLyrics = { track -> lyricsTargetTrack = track },
                            onEditTags = { track ->
                                pickedCustomCoverUri = null
                                tagEditorTargetTrack = track
                            },
                            onShowMetadata = { track -> metadataTargetTrack = track },
                            onOpenAlbum = { albumName ->
                                viewModel.closeGlobalSearch()
                                navController.navigate(Route.AlbumDetail(albumName))
                            },
                            onOpenArtist = { artistName ->
                                viewModel.closeGlobalSearch()
                                requestedArtistDrilldown = artistName
                                navController.navigate(Route.Albums) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenPlaylist = { playlistId ->
                                viewModel.closeGlobalSearch()
                                navController.navigate(Route.PlaylistDetail(playlistId))
                            }
                        )
                    }
                }
            }
        }
    }

    // Create playlist dialog
    if (showCreatePlaylistDialog) {
        CreateOrEditPlaylistDialog(
            title = "Nueva lista de reproducción",
            confirmLabel = "Crear",
            onDismiss = {
                showCreatePlaylistDialog = false
                pendingTracksForNewPlaylist = emptyList()
            },
            onConfirm = { name, description ->
                val initialTracks = pendingTracksForNewPlaylist
                showCreatePlaylistDialog = false
                pendingTracksForNewPlaylist = emptyList()
                viewModel.createPlaylistWithTracks(name, description, initialTracks)
            }
        )
    }

    // Add single or multiple tracks to playlist bottom sheet
    if (tracksToAddToPlaylist.isNotEmpty()) {
        val batch = tracksToAddToPlaylist
        BatchAddToPlaylistBottomSheet(
            tracks = batch,
            playlists = uiState.playlists,
            onDismiss = { tracksToAddToPlaylist = emptyList() },
            onSelectPlaylist = { playlist ->
                tracksToAddToPlaylist = emptyList()
                viewModel.addTracksToPlaylist(playlist.id, playlist.name, batch)
            },
            onCreateNewPlaylist = {
                pendingTracksForNewPlaylist = batch
                tracksToAddToPlaylist = emptyList()
                showCreatePlaylistDialog = true
            }
        )
    }

    // Queue sheet
    if (showQueueSheet) {
        QueueBottomSheet(
            playbackState = playbackState,
            onDismiss = { showQueueSheet = false },
            onSelectIndex = viewModel::skipToQueueIndex,
            onRemoveIndex = viewModel::removeFromQueue
        )
    }

    // Track & Album Metadata sheet
    metadataTargetTrack?.let { target ->
        TrackMetadataBottomSheet(
            track = target,
            metadata = loadedTrackMetadata,
            isLoading = isLoadingMetadata,
            onDismiss = { metadataTargetTrack = null }
        )
    }

    // Equalizer & DSP Audio Settings sheet
    if (showEqualizerSheet) {
        EqualizerAndDspSheet(
            settings = audioSettings,
            bands = eqBands,
            onUpdateSettings = { viewModel.updateAudioSettings(it, immediate = true) },
            onUpdateSettingsThrottled = { updated, immediate ->
                viewModel.updateAudioSettings(updated, immediate = immediate)
            },
            customProfiles = customEqProfiles,
            onSaveCustomProfile = viewModel::saveCustomEqProfile,
            onDeleteCustomProfile = viewModel::deleteCustomEqProfile,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    // Sleep Timer sheet
    if (showSleepTimerSheet) {
        SleepTimerSheet(
            remainingMs = sleepTimerRemainingMs,
            stopAfterCurrentTrack = stopAfterCurrentTrack,
            onSelectMinutes = { mins -> viewModel.startSleepTimer(mins, afterCurrentTrack = false) },
            onSelectAfterTrack = { viewModel.startSleepTimer(null, afterCurrentTrack = true) },
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    // Folders & SD Card Manager sheet
    if (showFoldersSheet) {
        FoldersExclusionSheet(
            folders = uiState.folders,
            onToggleExclude = viewModel::toggleFolderExclusion,
            onDismiss = { showFoldersSheet = false },
            sdCardFolders = uiState.sdCardFolders,
            removingSdFolders = uiState.removingSdFolderUris,
            onAddSdCardFolder = { openSdFolderLauncher.launch(null) },
            onRemoveSdCardFolder = viewModel::removeSdCardFolder
        )
    }

    // Non-intrusive Credits sheet
    if (showCreditsSheet) {
        CreditsBottomSheet(
            onDismiss = { showCreditsSheet = false }
        )
    }

    // Synced & Embedded Lyrics sheet
    lyricsTargetTrack?.let { target ->
        LyricsBottomSheet(
            track = target,
            lyrics = loadedLyrics,
            isLoading = isLoadingLyrics,
            positionMs = if (playbackState.currentTrack?.id == target.id) playbackState.positionMs else 0L,
            onSeekTo = { targetMs ->
                if (playbackState.currentTrack?.id == target.id) {
                    viewModel.seekTo(targetMs)
                }
            },
            onPickLrcFile = {
                pickLrcFileLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
            },
            onDismiss = { lyricsTargetTrack = null },
            onClearSavedLyrics = {
                coroutineScope.launch {
                    isLoadingLyrics = true
                    loadedLyrics = viewModel.clearSavedLyrics(target)
                    isLoadingLyrics = false
                }
            },
            onSearchOnlineLyrics = { customQuery ->
                viewModel.searchOnlineLyrics(target, customQuery)
            },
            onSaveOnlineLyrics = { candidate ->
                isLoadingLyrics = true
                val saved = viewModel.saveDownloadedLyrics(target, candidate)
                if (saved != null) {
                    loadedLyrics = saved
                }
                isLoadingLyrics = false
            },
            onOpenLyricsify = { query ->
                val url = LrcLibLyricsFetcher.buildLyricsifySearchUrl(query)
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.isSuccess
                if (!opened) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("No se encontró un navegador para abrir Lyricsify")
                    }
                }
            },
            translationState = lyricsTranslationState,
            selectedEngine = preferredTranslationEngine,
            onSelectEngine = viewModel::setPreferredTranslationEngine,
            onTranslateLyrics = { lyricsData, targetLanguage, engine ->
                viewModel.translateLyrics(target, lyricsData, targetLanguage, engine)
            },
            onSaveManualTranslation = { lyricsData, pastedText, targetLanguage ->
                viewModel.saveManualLyricsTranslation(target, lyricsData, pastedText, targetLanguage)
            },
            onClearSavedTranslation = {
                viewModel.clearSavedLyricsTranslation(target)
            },
            onOpenHumanTranslationSite = { url, siteName ->
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.isSuccess
                if (!opened) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("No se encontró un navegador para abrir $siteName")
                    }
                }
            }
        )
    }

    // In-app Tag & Cover Editor sheet (uses zero-permission Photo Picker)
    tagEditorTargetTrack?.let { target ->
        TagEditorBottomSheet(
            track = target,
            pickedArtworkUri = pickedCustomCoverUri,
            onPickCoverImage = {
                pickCoverImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearCustomCover = { pickedCustomCoverUri = null },
            onSave = { title, artist, album, albumArtist, genre, year, trackNum, artworkUri ->
                viewModel.saveTrackTagOverride(
                    trackId = target.id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    genre = genre,
                    year = year,
                    trackNumber = trackNum,
                    customArtworkUri = artworkUri
                )
            },
            onDismiss = {
                tagEditorTargetTrack = null
                pickedCustomCoverUri = null
            }
        )
    }
}

@Composable
fun GlobalSearchOverlay(
    uiState: SonoraUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    bottomContentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPlayTrack: (List<AudioTrack>, Int) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onShowLyrics: (AudioTrack) -> Unit,
    onEditTags: (AudioTrack) -> Unit,
    onShowMetadata: (AudioTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("global_search_container")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Buscar canciones, álbumes, artistas o listas…",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = CopperAmber)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag("global_search_clear_button")
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("global_search_input")
                )

                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("global_search_close_button")
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar búsqueda global"
                        )
                    }
                }
            }

            val trimmed = uiState.searchQuery.trim()
            val hasResults = uiState.globalMatchingTracks.isNotEmpty() ||
                uiState.globalMatchingAlbums.isNotEmpty() ||
                uiState.globalMatchingArtists.isNotEmpty() ||
                uiState.globalMatchingPlaylists.isNotEmpty()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("global_search_results_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (trimmed.isEmpty()) {
                    item(key = "global_search_hint") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Búsqueda global en tiempo real",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Escribe para filtrar al instante entre ${uiState.allTracks.size} canciones, ${uiState.albums.size} álbumes, ${uiState.artists.size} artistas y ${uiState.playlists.size} listas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!hasResults) {
                    item(key = "global_search_empty") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("global_search_empty_state")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sin resultados para «$trimmed»",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Prueba con otro título, artista, álbum o nombre de lista.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    if (uiState.globalMatchingAlbums.isNotEmpty()) {
                        item(key = "section_albums") {
                            Text(
                                text = "Álbumes (${uiState.globalMatchingAlbums.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = CopperAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(
                            items = uiState.globalMatchingAlbums.take(12),
                            key = { "album_${it.albumName}" }
                        ) { album ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenAlbum(album.albumName) }
                                    .testTag("global_search_album_${album.albumName}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AudioAlbumArtThumbnail(
                                        audioUri = album.tracks.firstOrNull()?.contentUri.orEmpty(),
                                        albumArtUri = album.albumArtUri,
                                        contentDescription = album.albumName,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = album.albumName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${album.artist.ifBlank { "Álbum" }} · ${album.tracks.size} pistas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.globalMatchingArtists.isNotEmpty()) {
                        item(key = "section_artists") {
                            Text(
                                text = "Artistas (${uiState.globalMatchingArtists.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = LosslessCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(
                            items = uiState.globalMatchingArtists.take(10),
                            key = { "artist_${it.artistName}" }
                        ) { artist ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenArtist(artist.artistName) }
                                    .testTag("global_search_artist_${artist.artistName}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = LosslessCyan.copy(alpha = 0.16f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = LosslessCyan
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.artistName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${artist.albumCount} álbumes · ${artist.tracks.size} canciones",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.globalMatchingPlaylists.isNotEmpty()) {
                        item(key = "section_playlists") {
                            Text(
                                text = "Listas (${uiState.globalMatchingPlaylists.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = CopperAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(
                            items = uiState.globalMatchingPlaylists.take(10),
                            key = { "playlist_${it.id}" }
                        ) { playlist ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenPlaylist(playlist.id) }
                                    .testTag("global_search_playlist_${playlist.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlaylistMosaicCover(
                                        artworkUris = playlist.artworkUris,
                                        contentDescription = playlist.name,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.trackCount} canciones · ${formatDuration(playlist.totalDurationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.globalMatchingTracks.isNotEmpty()) {
                        item(key = "section_tracks") {
                            Text(
                                text = "Canciones (${uiState.globalMatchingTracks.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(
                            items = uiState.globalMatchingTracks,
                            key = { "track_${it.id}" }
                        ) { track ->
                            val idx = uiState.globalMatchingTracks.indexOf(track)
                            val isCurrent = currentTrackId == track.id
                            TrackRow(
                                track = track,
                                isCurrentTrack = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                onClick = {
                                    if (idx >= 0) {
                                        onPlayTrack(uiState.globalMatchingTracks, idx)
                                    }
                                },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onShowMetadata = { onShowMetadata(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onShowLyrics = { onShowLyrics(track) },
                                onEditTags = { onEditTags(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}
