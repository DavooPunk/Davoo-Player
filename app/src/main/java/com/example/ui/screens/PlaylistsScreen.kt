package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.PlaylistSummary
import com.example.model.SmartPlaylistType
import com.example.playback.PlaybackUiState
import com.example.ui.components.AudioAlbumArtThumbnail
import com.example.ui.components.CreateOrEditPlaylistDialog
import com.example.ui.components.FormatBadge
import com.example.ui.components.LazyColumnFastScroller
import com.example.ui.components.PlaylistMosaicCover
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDuration
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    favoriteCount: Int = 0,
    recentCount: Int = 0,
    mostPlayedCount: Int = 0,
    historyCount: Int = 0,
    onCreatePlaylist: () -> Unit,
    onImportM3u: () -> Unit = {},
    onSmartPlaylistClick: (SmartPlaylistType) -> Unit = {},
    onPlaylistClick: (PlaylistSummary) -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlists_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURACIÓN PERSONAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Listas (${playlists.size})",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                OutlinedButton(
                    onClick = onImportM3u,
                    modifier = Modifier.testTag("import_m3u_button")
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar .m3u")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "smart_playlists_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Listas inteligentes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val smartItems = listOf(
                                Triple(SmartPlaylistType.FAVORITES, favoriteCount, Icons.Rounded.Favorite),
                                Triple(SmartPlaylistType.RECENTLY_ADDED, recentCount, Icons.Rounded.AutoAwesome),
                                Triple(SmartPlaylistType.MOST_PLAYED, mostPlayedCount, Icons.Rounded.LocalFireDepartment),
                                Triple(SmartPlaylistType.HISTORY, historyCount, Icons.Rounded.History)
                            )
                            items(smartItems) { (type, count, icon) ->
                                val tint = if (type == SmartPlaylistType.FAVORITES || type == SmartPlaylistType.MOST_PLAYED) {
                                    CopperAmber
                                } else LosslessCyan
                                Surface(
                                    onClick = { onSmartPlaylistClick(type) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .width(168.dp)
                                        .testTag("smart_playlist_${type.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, tint = tint)
                                        Text(
                                            text = type.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$count canciones",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tus listas personalizadas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty_custom_playlists") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text(
                                    text = stringResource(R.string.empty_playlists_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.empty_playlists_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onCreatePlaylist,
                                    modifier = Modifier.testTag("empty_create_playlist_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_create_playlist))
                                }
                            }
                        }
                    }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onPlaylistClick(playlist) }
                                .testTag("playlist_card_${playlist.id}"),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                ) {
                                    PlaylistMosaicCover(
                                        artworkUris = playlist.artworkUris,
                                        contentDescription = playlist.name,
                                        cornerRadius = 14.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (playlist.hasFlac) {
                                        FormatBadge(
                                            format = AudioFormat.FLAC,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.trackCount} canciones · ${formatDuration(playlist.totalDurationMs)}",
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
        }

        if (playlists.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onCreatePlaylist,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_create_playlist)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = bottomContentPadding.calculateBottomPadding() + 16.dp)
                    .testTag("fab_create_playlist")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistDetailScreen(
    type: SmartPlaylistType,
    tracks: List<AudioTrack>,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onShuffleTracks: (List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToOtherPlaylist: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onShowLyrics: (AudioTrack) -> Unit,
    onEditTags: (AudioTrack) -> Unit,
    onShowMetadata: (AudioTrack) -> Unit,
    bottomContentPadding: PaddingValues
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().testTag("smart_playlist_detail_screen")) {
        TopAppBar(
            title = { Text(type.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "smart_hero") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = type.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${tracks.size} canciones · ${formatDuration(tracks.sumOf { it.durationMs })}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPlayTracks(tracks, 0) },
                                enabled = tracks.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reproducir")
                            }
                            OutlinedButton(
                                onClick = { onShuffleTracks(tracks) },
                                enabled = tracks.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aleatorio")
                            }
                        }
                    }
                }

                if (tracks.isEmpty()) {
                    item(key = "smart_empty") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Aún no hay canciones en esta lista inteligente.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                        val isCurrent = playbackState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && playbackState.isPlaying,
                            onClick = { onPlayTracks(tracks, index) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToOtherPlaylist(track) },
                            onShowMetadata = { onShowMetadata(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onShowLyrics = { onShowLyrics(track) },
                            onEditTags = { onEditTags(track) }
                        )
                    }
                }
            }

            LazyColumnFastScroller(
                listState = listState,
                itemCount = tracks.size,
                headerItemCount = 1,
                bottomPadding = bottomContentPadding.calculateBottomPadding() + 12.dp,
                bubbleLabelForTrackIndex = { idx ->
                    tracks.getOrNull(idx)?.title?.firstOrNull()?.uppercaseChar()?.toString()
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistSummary?,
    allTracks: List<AudioTrack>,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onShuffleTracks: (List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToOtherPlaylist: (AudioTrack) -> Unit,
    onAddSelectedTracks: (Long, String, List<AudioTrack>) -> Unit,
    onRemoveTrack: (Long, String) -> Unit,
    onMoveTrack: (Long, Int, Int) -> Unit,
    onUpdatePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onExportM3u: (PlaylistSummary) -> Unit = {},
    onToggleFavorite: (AudioTrack) -> Unit = {},
    onShowLyrics: (AudioTrack) -> Unit = {},
    onEditTags: (AudioTrack) -> Unit = {},
    bottomContentPadding: PaddingValues,
    onShowMetadata: (AudioTrack) -> Unit = {},
    onRemoveTracks: (Long, List<String>) -> Unit = { pid, ids ->
        ids.forEach { onRemoveTrack(pid, it) }
    },
    onAddTracksToOtherPlaylist: (List<AudioTrack>) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddTracksSheet by remember { mutableStateOf(false) }
    var showBatchRemoveConfirm by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState()

    val playlistTracks = playlist?.tracks.orEmpty()
    val selectedTracks by remember(playlistTracks, selectedTrackIds) {
        derivedStateOf { playlistTracks.filter { it.id in selectedTrackIds } }
    }

    LaunchedEffect(playlistTracks) {
        if (isSelectionMode) {
            val validIds = playlistTracks.mapTo(HashSet()) { it.id }
            val pruned = selectedTrackIds.intersect(validIds)
            if (pruned != selectedTrackIds) {
                selectedTrackIds = pruned
            }
            if (playlistTracks.isEmpty()) {
                isSelectionMode = false
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedTrackIds = emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("playlist_detail_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = playlist?.name ?: "Lista",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("playlist_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                if (playlist != null) {
                    if (playlist.tracks.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (isSelectionMode) {
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                } else {
                                    isSelectionMode = true
                                }
                            },
                            modifier = Modifier.testTag("playlist_select_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = if (isSelectionMode) "Salir de selección múltiple" else "Seleccionar canciones",
                                tint = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = { onExportM3u(playlist) },
                        enabled = playlist.tracks.isNotEmpty(),
                        modifier = Modifier.testTag("export_m3u_button")
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Exportar lista .m3u")
                    }
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("edit_playlist_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar lista")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_playlist_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar lista",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        AnimatedVisibility(visible = isSelectionMode && playlist != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_selection_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("playlist_selection_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancelar selección")
                    }
                    Text(
                        text = "${selectedTracks.size} sel.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    val allSelected = playlistTracks.isNotEmpty() &&
                        selectedTracks.size == playlistTracks.size
                    IconButton(
                        onClick = {
                            selectedTrackIds = if (allSelected) {
                                emptySet()
                            } else {
                                playlistTracks.mapTo(HashSet()) { it.id }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("playlist_selection_select_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = if (allSelected) "Deseleccionar todo" else "Seleccionar todo",
                            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectedTracks.isNotEmpty()) {
                                onAddTracksToOtherPlaylist(selectedTracks)
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("playlist_selection_add_to_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Añadir seleccionadas a otra lista",
                            tint = if (selectedTracks.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectedTracks.isNotEmpty()) {
                                showBatchRemoveConfirm = true
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("playlist_selection_remove_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Quitar seleccionadas de la lista",
                            tint = if (selectedTracks.isNotEmpty()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lista no encontrada", style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("playlist_detail_lazy_column"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "playlist_hero") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlaylistMosaicCover(
                            artworkUris = playlist.artworkUris,
                            contentDescription = playlist.name,
                            cornerRadius = 22.dp,
                            modifier = Modifier.size(180.dp)
                        )
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        if (playlist.description.isNotBlank()) {
                            Text(
                                text = playlist.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "${playlist.trackCount} canciones · ${formatDuration(playlist.totalDurationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPlayTracks(playlist.tracks, 0) },
                                enabled = playlist.tracks.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("playlist_play_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reproducir")
                            }
                            OutlinedButton(
                                onClick = { onShuffleTracks(playlist.tracks) },
                                enabled = playlist.tracks.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("playlist_shuffle_button")
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aleatorio")
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddTracksSheet = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playlist_add_tracks_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Agregar canciones de la biblioteca")
                        }
                    }
                }

                if (playlist.tracks.isEmpty()) {
                    item(key = "empty_playlist_tracks") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Esta lista está vacía",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Pulsa «Agregar canciones de la biblioteca» para añadir temas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(playlist.tracks, key = { _, t -> t.id }) { index, track ->
                        val isCurrent = playbackState.currentTrack?.id == track.id
                        val isSelected = track.id in selectedTrackIds
                        TrackRow(
                            track = track,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && playbackState.isPlaying,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedTrackIds = if (isSelected) {
                                        selectedTrackIds - track.id
                                    } else {
                                        selectedTrackIds + track.id
                                    }
                                } else {
                                    onPlayTracks(playlist.tracks, index)
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedTrackIds = selectedTrackIds + track.id
                            },
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToOtherPlaylist(track) },
                            onShowMetadata = { onShowMetadata(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onShowLyrics = { onShowLyrics(track) },
                            onEditTags = { onEditTags(track) },
                            trailingExtraContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { onMoveTrack(playlist.id, index, index - 1) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "Subir pista",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (index < playlist.tracks.lastIndex) {
                                        IconButton(
                                            onClick = { onMoveTrack(playlist.id, index, index + 1) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDownward,
                                                contentDescription = "Bajar pista",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRemoveTrack(playlist.id, track.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.RemoveCircleOutline,
                                            contentDescription = "Quitar de la lista",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            LazyColumnFastScroller(
                listState = listState,
                itemCount = playlist.tracks.size,
                headerItemCount = 1,
                bottomPadding = bottomContentPadding.calculateBottomPadding() + 12.dp,
                bubbleLabelForTrackIndex = { idx ->
                    playlist.tracks.getOrNull(idx)?.title?.firstOrNull()?.uppercaseChar()?.toString()
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    if (showEditDialog && playlist != null) {
        CreateOrEditPlaylistDialog(
            title = "Editar lista",
            confirmLabel = "Guardar",
            initialName = playlist.name,
            initialDescription = playlist.description,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newDesc ->
                showEditDialog = false
                onUpdatePlaylist(playlist.id, newName, newDesc)
            }
        )
    }

    if (showDeleteConfirm && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar «${playlist.name}»") },
            text = { Text("Se borrará la lista personalizada, pero tus archivos de audio seguirán intactos en el dispositivo.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist(playlist.id)
                        onBack()
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showBatchRemoveConfirm && playlist != null) {
        val count = selectedTracks.size
        AlertDialog(
            onDismissRequest = { showBatchRemoveConfirm = false },
            title = {
                Text("Quitar $count ${if (count == 1) "canción" else "canciones"}")
            },
            text = {
                Text("Se quitarán de «${playlist.name}», pero tus archivos seguirán intactos en el dispositivo.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToRemove = selectedTracks.map { it.id }
                        showBatchRemoveConfirm = false
                        isSelectionMode = false
                        selectedTrackIds = emptySet()
                        onRemoveTracks(playlist.id, idsToRemove)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_playlist_batch_remove_button")
                ) {
                    Text("Quitar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchRemoveConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddTracksSheet && playlist != null) {
        BatchAddTracksSheet(
            playlist = playlist,
            allTracks = allTracks,
            onDismiss = { showAddTracksSheet = false },
            onConfirm = { chosen ->
                showAddTracksSheet = false
                onAddSelectedTracks(playlist.id, playlist.name, chosen)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchAddTracksSheet(
    playlist: PlaylistSummary,
    allTracks: List<AudioTrack>,
    onDismiss: () -> Unit,
    onConfirm: (List<AudioTrack>) -> Unit
) {
    val existingIds = remember(playlist.tracks) { playlist.tracks.map { it.id }.toHashSet() }
    val availableTracks = remember(allTracks, existingIds) {
        allTracks.filterNot { it.id in existingIds }
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredAvailable = remember(availableTracks, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) availableTracks
        else availableTracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }
    val selectedTracks = remember { mutableStateListOf<AudioTrack>() }
    val sheetListState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Agregar canciones", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${selectedTracks.size} seleccionadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (filteredAvailable.isNotEmpty()) {
                    val allFilteredSelected = filteredAvailable.all { cand ->
                        selectedTracks.any { it.id == cand.id }
                    }
                    TextButton(
                        onClick = {
                            if (allFilteredSelected) {
                                val filteredIds = filteredAvailable.mapTo(HashSet()) { it.id }
                                selectedTracks.removeAll { it.id in filteredIds }
                            } else {
                                val currentIds = selectedTracks.mapTo(HashSet()) { it.id }
                                filteredAvailable.forEach { cand ->
                                    if (cand.id !in currentIds) selectedTracks.add(cand)
                                }
                            }
                        },
                        modifier = Modifier.testTag("batch_add_select_all_button")
                    ) {
                        Text(if (allFilteredSelected) "Limpiar" else "Seleccionar todo")
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { onConfirm(selectedTracks.toList()) },
                    enabled = selectedTracks.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_batch_add_button")
                ) {
                    Text("Agregar")
                }
            }

            if (availableTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar canción o artista…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_add_search_input")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (availableTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Todas las canciones disponibles ya están en esta lista.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (filteredAvailable.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin coincidencias para «$searchQuery».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    LazyColumn(
                        state = sheetListState,
                        contentPadding = PaddingValues(end = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredAvailable, key = { it.id }) { track ->
                            val checked = selectedTracks.any { it.id == track.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (checked) selectedTracks.removeAll { it.id == track.id }
                                        else selectedTracks.add(track)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            if (!checked) selectedTracks.add(track)
                                        } else {
                                            selectedTracks.removeAll { it.id == track.id }
                                        }
                                    }
                                )
                                AudioAlbumArtThumbnail(
                                    audioUri = track.contentUri,
                                    albumArtUri = track.albumArtUri,
                                    contentDescription = track.title,
                                    cornerRadius = 8.dp,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FormatBadge(format = track.format)
                                        Text(
                                            text = track.artist,
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

                    LazyColumnFastScroller(
                        listState = sheetListState,
                        itemCount = filteredAvailable.size,
                        headerItemCount = 0,
                        topPadding = 2.dp,
                        bottomPadding = 4.dp,
                        bubbleLabelForTrackIndex = { idx ->
                            filteredAvailable.getOrNull(idx)?.title?.firstOrNull()?.uppercaseChar()?.toString()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}
