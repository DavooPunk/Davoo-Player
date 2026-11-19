package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioTrack
import com.example.model.SortOrder
import com.example.playback.PlaybackUiState
import com.example.ui.components.LazyColumnFastScroller
import com.example.ui.components.TrackRow
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan
import com.example.ui.viewmodel.SonoraUiState

@Composable
fun LibraryScreen(
    uiState: SonoraUiState,
    playbackState: PlaybackUiState,
    onRequestPermission: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSearchChange: (String) -> Unit = {},
    onSelectSortOrder: (SortOrder) -> Unit,
    onPlayTrack: (List<AudioTrack>, Int) -> Unit,
    onShuffleAll: (List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit = {},
    onShowLyrics: (AudioTrack) -> Unit = {},
    onEditTags: (AudioTrack) -> Unit = {},
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onShowMetadata: (AudioTrack) -> Unit = {},
    onAddTracksToPlaylist: (List<AudioTrack>) -> Unit = {},
    onEnqueueTracks: (List<AudioTrack>) -> Unit = {},
    onDeleteTracks: (List<AudioTrack>) -> Unit = {}
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val selectedTracks by remember(uiState.filteredTracks, selectedTrackIds) {
        derivedStateOf { uiState.filteredTracks.filter { it.id in selectedTrackIds } }
    }

    LaunchedEffect(uiState.filteredTracks) {
        if (isSelectionMode) {
            val validIds = uiState.filteredTracks.mapTo(HashSet()) { it.id }
            val pruned = selectedTrackIds.intersect(validIds)
            if (pruned != selectedTrackIds) {
                selectedTrackIds = pruned
            }
            if (uiState.filteredTracks.isEmpty()) {
                isSelectionMode = false
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedTrackIds = emptySet()
    }

    val headerItemCount =
        (if (!uiState.hasAudioPermission) 1 else 0) +
        (if (uiState.filteredTracks.isNotEmpty()) 1 else 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        AnimatedVisibility(visible = isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_selection_bar")
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
                            .testTag("selection_close_button")
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
                    val allSelected = uiState.filteredTracks.isNotEmpty() &&
                        selectedTracks.size == uiState.filteredTracks.size
                    IconButton(
                        onClick = {
                            selectedTrackIds = if (allSelected) {
                                emptySet()
                            } else {
                                uiState.filteredTracks.mapTo(HashSet()) { it.id }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("selection_select_all_button")
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
                                onAddTracksToPlaylist(selectedTracks)
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("selection_add_to_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Añadir seleccionadas a lista",
                            tint = if (selectedTracks.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectedTracks.isNotEmpty()) {
                                onEnqueueTracks(selectedTracks)
                                isSelectionMode = false
                                selectedTrackIds = emptySet()
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("selection_enqueue_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Agregar seleccionadas a la cola",
                            tint = if (selectedTracks.isNotEmpty()) LosslessCyan
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectedTracks.isNotEmpty()) {
                                showBatchDeleteConfirm = true
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("selection_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar seleccionadas",
                            tint = if (selectedTracks.isNotEmpty()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        // Fixed Top Header with always-visible top-right Search & Settings buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DAVOO PLAYER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tu Música",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp
                        )
                    }
                    Text(
                        text = if (uiState.isScanning) {
                            "Actualizando biblioteca…"
                        } else {
                            val count = uiState.allTracks.size
                            "$count ${if (count == 1) "canción" else "canciones"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("library_settings_button")
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Abrir opciones",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = onOpenGlobalSearch,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("header_search_button")
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar en toda la biblioteca",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("library_lazy_column"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 76.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // Permission card if system audio permission is not yet granted
                if (!uiState.hasAudioPermission) {
                    item(key = "permission_card", contentType = "permission") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audio_permission_card"),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, CopperAmber.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LibraryMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.permission_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.permission_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = onRequestPermission,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("grant_audio_permission_button")
                                    ) {
                                        Text(stringResource(R.string.permission_grant))
                                    }
                                    OutlinedButton(
                                        onClick = onImportFiles,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("import_files_fallback_button")
                                    ) {
                                        Text("Abrir archivo")
                                    }
                                }
                            }
                        }
                    }
                }

                // Unified Playback + Sort + Multi-select strip (no MP3/FLAC clutter)
                if (uiState.filteredTracks.isNotEmpty()) {
                    item(key = "actions_strip", contentType = "actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onPlayTrack(uiState.filteredTracks, 0) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("play_all_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reproducir")
                                }
                                OutlinedButton(
                                    onClick = { onShuffleAll(uiState.filteredTracks) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("shuffle_all_button")
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_shuffle_all))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    FilledTonalButton(
                                        onClick = { sortMenuExpanded = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("sort_order_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                                            contentDescription = "Ordenar",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = uiState.sortOrder.label,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false }
                                    ) {
                                        SortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(order.label) },
                                                onClick = {
                                                    sortMenuExpanded = false
                                                    onSelectSortOrder(order)
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (isSelectionMode) {
                                            isSelectionMode = false
                                            selectedTrackIds = emptySet()
                                        } else {
                                            isSelectionMode = true
                                            selectedTrackIds = emptySet()
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("library_select_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Checklist,
                                        contentDescription = "Selección múltiple",
                                        tint = if (isSelectionMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Empty state or Unified Tracks list
                if (uiState.filteredTracks.isEmpty() && !uiState.isScanning) {
                    item(key = "empty_state", contentType = "empty") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag("empty_library_state"),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_library),
                                    contentDescription = stringResource(R.string.empty_library_title),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) "Sin resultados para «${uiState.searchQuery}»"
                                    else stringResource(R.string.empty_library_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.empty_library_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = onImportFiles,
                                    modifier = Modifier.testTag("empty_import_button")
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Importar archivos de audio")
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = uiState.filteredTracks,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "audio_track" }
                    ) { index, track ->
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
                                    onPlayTrack(uiState.filteredTracks, index)
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
                            onAddToPlaylist = { onAddToPlaylist(track) },
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
                itemCount = uiState.filteredTracks.size,
                headerItemCount = headerItemCount,
                bottomPadding = bottomContentPadding.calculateBottomPadding() + 12.dp,
                bubbleLabelForTrackIndex = { idx ->
                    uiState.filteredTracks.getOrNull(idx)?.title
                        ?.firstOrNull()?.uppercaseChar()?.toString()
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    if (showBatchDeleteConfirm) {
        val count = selectedTracks.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text("Eliminar $count ${if (count == 1) "canción" else "canciones"}")
            },
            text = {
                Text(
                    "Se eliminarán los archivos de audio seleccionados de tu biblioteca" +
                        " (Android pedirá confirmación del sistema para archivos locales del dispositivo)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = selectedTracks.toList()
                        showBatchDeleteConfirm = false
                        isSelectionMode = false
                        selectedTrackIds = emptySet()
                        onDeleteTracks(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_batch_delete_button")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
