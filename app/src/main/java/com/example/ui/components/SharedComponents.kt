package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.R
import com.example.data.repository.DeviceMusicScanner
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.PlaylistSummary
import com.example.playback.PlaybackUiState

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%d KB", bytes / 1024L)
}

@Composable
fun AlbumArtwork(
    artworkUri: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    AsyncImage(
        model = artworkUri,
        contentDescription = contentDescription,
        placeholder = painterResource(id = R.drawable.img_default_album_art),
        error = painterResource(id = R.drawable.img_default_album_art),
        fallback = painterResource(id = R.drawable.img_default_album_art),
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    )
}

@Composable
fun PlaylistMosaicCover(
    artworkUris: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        when {
            artworkUris.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AlbumArtwork(
                            artworkUri = artworkUris[0],
                            contentDescription = contentDescription,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        AlbumArtwork(
                            artworkUri = artworkUris[1],
                            contentDescription = contentDescription,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AlbumArtwork(
                            artworkUri = artworkUris[2],
                            contentDescription = contentDescription,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        AlbumArtwork(
                            artworkUri = artworkUris[3],
                            contentDescription = contentDescription,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
            artworkUris.isNotEmpty() -> {
                AlbumArtwork(
                    artworkUri = artworkUris.first(),
                    contentDescription = contentDescription,
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                AlbumArtwork(
                    artworkUri = null,
                    contentDescription = contentDescription,
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun FormatBadge(
    format: AudioFormat,
    modifier: Modifier = Modifier
) {
    val isLossless = format == AudioFormat.FLAC || format == AudioFormat.WAV
    val bgColor = if (isLossless) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (isLossless) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val borderColor = if (isLossless) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(0.75.dp, borderColor)
    ) {
        Text(
            text = format.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val heights = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "eq")
        val bar1 by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b1"
        )
        val bar2 by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(560, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b2"
        )
        val bar3 by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b3"
        )
        listOf(bar1, bar2, bar3)
    } else {
        listOf(0.45f, 0.7f, 0.35f)
    }

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .graphicsLayer {
                        scaleY = frac.coerceIn(0.15f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    onShowMetadata: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onShowLyrics: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onDeleteFromDevice: (() -> Unit)? = null,
    trailingExtraContent: (@Composable () -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val resolvedDurationMs = if (track.durationMs > 0L) {
        track.durationMs
    } else if (track.contentUri.isNotBlank()) {
        val extracted by produceState(
            initialValue = 0L,
            key1 = track.contentUri
        ) {
            val found = DeviceMusicScanner.extractDurationFromFile(
                context = context,
                uri = Uri.parse(track.contentUri)
            )
            if (found != null && found > 0L) {
                value = found
            }
        }
        extracted
    } else {
        0L
    }

    val rowBackground = when {
        isSelectionMode && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        isCurrentTrack -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackground)
            .let { base ->
                if (onLongClick != null) {
                    base.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    base.clickable(onClick = onClick)
                }
            }
            .testTag("track_row_${track.id}")
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier
                    .padding(end = 6.dp)
                    .testTag("track_select_checkbox_${track.id}")
            )
        }

        Box(modifier = Modifier.size(50.dp)) {
            AudioAlbumArtThumbnail(
                audioUri = track.contentUri,
                albumArtUri = track.albumArtUri,
                contentDescription = track.album,
                cornerRadius = 10.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("track_thumbnail_${track.id}")
            )
            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(isPlaying = isPlaying)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "· ${formatDuration(resolvedDurationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("track_duration_${track.id}")
                )
            }
        }

        if (!isSelectionMode) {
            trailingExtraContent?.invoke()

            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("track_fav_${track.id}")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (track.isFavorite) "Quitar de favoritas" else "Marcar como favorita",
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("track_menu_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opciones de ${track.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            if (menuExpanded) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reproducir siguiente") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Agregar a la cola") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Agregar a una lista") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                if (onShowLyrics != null) {
                    DropdownMenuItem(
                        text = { Text("Ver letra") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lyrics, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onShowLyrics()
                        }
                    )
                }
                if (onEditTags != null) {
                    DropdownMenuItem(
                        text = { Text("Editar etiquetas") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onEditTags()
                        }
                    )
                }
                if (onShowMetadata != null) {
                    DropdownMenuItem(
                        text = { Text("Ver metadatos") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Info, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onShowMetadata()
                        }
                    )
                }
                if (onDeleteFromDevice != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Eliminar del dispositivo",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteFromDevice()
                        },
                        modifier = Modifier.testTag("track_delete_menu_${track.id}")
                    )
                }
            }
            }
        }
        }
    }
}

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipPrevious: (() -> Unit)? = null
) {
    val track = playbackState.currentTrack ?: return
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedSwipeX by animateFloatAsState(targetValue = swipeOffsetX, label = "mini_player_swipe")
    val currentOnSkipNext by rememberUpdatedState(onSkipNext)
    val currentOnSkipPrevious by rememberUpdatedState(onSkipPrevious)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer { translationX = animatedSwipeX * 0.35f }
            .pointerInput(track.id) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                        swipeOffsetX = totalDrag.coerceIn(-140f, 140f)
                    },
                    onDragEnd = {
                        if (totalDrag < -80f) {
                            currentOnSkipNext()
                        } else if (totalDrag > 80f) {
                            currentOnSkipPrevious?.invoke()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f }
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenNowPlaying)
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioAlbumArtThumbnail(
                    audioUri = track.contentUri,
                    albumArtUri = track.albumArtUri,
                    contentDescription = track.title,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        FormatBadge(format = track.format)
                    }
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente canción",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = if (track.format == AudioFormat.FLAC) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
fun CreateOrEditPlaylistDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la lista") },
                    placeholder = { Text("Ej. Vinilos FLAC nocturnos") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    placeholder = { Text("Ej. Alta fidelidad para escuchar con auriculares") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_playlist_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    track: AudioTrack,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistSummary) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    BatchAddToPlaylistBottomSheet(
        tracks = listOf(track),
        playlists = playlists,
        onDismiss = onDismiss,
        onSelectPlaylist = onSelectPlaylist,
        onCreateNewPlaylist = onCreateNewPlaylist
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAddToPlaylistBottomSheet(
    tracks: List<AudioTrack>,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistSummary) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    val selectedIds = remember(tracks) { tracks.map { it.id }.toHashSet() }
    val subtitle = if (tracks.size == 1) {
        val single = tracks.first()
        "${single.title} · ${single.format.label}"
    } else {
        "${tracks.size} canciones seleccionadas"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("add_to_playlist_sheet")
        ) {
            Text(
                text = "Agregar a una lista",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onCreateNewPlaylist,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_create_playlist_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear nueva lista")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "Todavía no tienes listas personalizadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = playlists,
                        key = { it.id },
                        contentType = { "playlist_row" }
                    ) { playlist ->
                        val existingCount = playlist.tracks.count { it.id in selectedIds }
                        val statusLabel = when {
                            tracks.size == 1 && existingCount == 1 ->
                                "Ya incluida · ${playlist.trackCount} canciones"
                            existingCount > 0 ->
                                "$existingCount ya incluidas · ${playlist.trackCount} canciones"
                            else -> "${playlist.trackCount} canciones"
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectPlaylist(playlist) }
                                .testTag("sheet_playlist_item_${playlist.id}"),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistMosaicCover(
                                    artworkUris = playlist.artworkUris,
                                    contentDescription = playlist.name,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.size(46.dp)
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
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (existingCount > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playbackState: PlaybackUiState,
    onDismiss: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> }
) {
    val hasActiveIndex = playbackState.currentIndex in playbackState.queue.indices
    val queueOffset = if (hasActiveIndex) playbackState.currentIndex else 0
    val visibleQueue = remember(playbackState.queue, queueOffset, hasActiveIndex) {
        if (playbackState.queue.isEmpty()) emptyList()
        else if (hasActiveIndex) playbackState.queue.subList(queueOffset, playbackState.queue.size).toList()
        else playbackState.queue
    }
    val visibleEntries = remember(visibleQueue) {
        val counts = HashMap<String, Int>(visibleQueue.size)
        visibleQueue.map { track ->
            val occ = (counts[track.id] ?: 0) + 1
            counts[track.id] = occ
            "${track.id}#$occ" to track
        }
    }

    val queueListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { queueListState.firstVisibleItemIndex > 0 }
    }

    val density = LocalDensity.current
    val defaultItemHeightPx = with(density) { 64.dp.toPx() }
    var measuredItemHeightPx by remember { mutableFloatStateOf(defaultItemHeightPx) }
    var draggedVisualIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val currentQueueOffset by rememberUpdatedState(queueOffset)
    val currentVisibleSize by rememberUpdatedState(visibleQueue.size)
    val currentHasActiveIndex by rememberUpdatedState(hasActiveIndex)
    val currentOnMoveItem by rememberUpdatedState(onMoveItem)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("queue_bottom_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cola de reproducción",
                        style = MaterialTheme.typography.titleLarge
                    )
                    val subtitleText = when {
                        visibleQueue.isEmpty() -> "Sin canciones en cola"
                        hasActiveIndex -> {
                            val upcomingCount = (visibleQueue.size - 1).coerceAtLeast(0)
                            if (upcomingCount == 0) "Sonando ahora · Última canción"
                            else "Sonando ahora · $upcomingCount ${if (upcomingCount == 1) "siguiente" else "siguientes"}"
                        }
                        else -> "${visibleQueue.size} ${if (visibleQueue.size == 1) "canción" else "canciones"} en cola"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = showScrollToTop) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    queueListState.animateScrollToItem(0)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("queue_scroll_to_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerticalAlignTop,
                                contentDescription = "Volver arriba en la cola",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar cola")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            if (visibleEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "La cola está vacía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = queueListState,
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("queue_lazy_column")
                ) {
                    itemsIndexed(
                        items = visibleEntries,
                        key = { _, entry -> entry.first },
                        contentType = { _, _ -> "queue_track_row" }
                    ) { visualIndex, entry ->
                        val item = entry.second
                        val isCurrent = hasActiveIndex && visualIndex == 0
                        val actualIndex = queueOffset + visualIndex
                        val isDragging = draggedVisualIndex == visualIndex
                        val canReorder = !isCurrent && (if (hasActiveIndex) visibleQueue.size > 2 else visibleQueue.size > 1)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                                isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                                else -> Color.Transparent
                            },
                            border = if (isCurrent) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            } else if (isDragging) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            } else null,
                            shadowElevation = if (isDragging) 6.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    val scale = if (isDragging) 1.02f else 1f
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .onSizeChanged { size ->
                                    if (size.height > 0) {
                                        measuredItemHeightPx = size.height.toFloat()
                                    }
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectIndex(actualIndex) }
                                .then(
                                    if (isCurrent) Modifier.testTag("queue_now_playing_item")
                                    else Modifier.testTag("queue_item_${item.id}_$visualIndex")
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = "Sonando ahora",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "$visualIndex",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                AudioAlbumArtThumbnail(
                                    audioUri = item.contentUri,
                                    albumArtUri = item.albumArtUri,
                                    contentDescription = item.title,
                                    cornerRadius = 8.dp,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (isCurrent) {
                                        Text(
                                            text = "SONANDO AHORA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FormatBadge(format = item.format)
                                        Text(
                                            text = item.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (!isCurrent) {
                                    if (canReorder) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .pointerInput(entry.first) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggedVisualIndex = visualIndex
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggedVisualIndex = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggedVisualIndex = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val activeVisual = draggedVisualIndex ?: return@detectDragGestures
                                                            dragOffsetY += dragAmount.y
                                                            val stepPx = measuredItemHeightPx.coerceAtLeast(48f)
                                                            val steps = (dragOffsetY / stepPx).toInt()
                                                            if (steps != 0) {
                                                                val minIdx = if (currentHasActiveIndex) 1 else 0
                                                                val maxIdx = (currentVisibleSize - 1).coerceAtLeast(minIdx)
                                                                val targetVisual = (activeVisual + steps).coerceIn(minIdx, maxIdx)
                                                                if (targetVisual != activeVisual) {
                                                                    currentOnMoveItem(
                                                                        currentQueueOffset + activeVisual,
                                                                        currentQueueOffset + targetVisual
                                                                    )
                                                                    dragOffsetY -= (targetVisual - activeVisual) * stepPx
                                                                    draggedVisualIndex = targetVisual
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                                .testTag("queue_drag_handle_$visualIndex"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragHandle,
                                                contentDescription = "Reordenar ${item.title}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRemoveIndex(actualIndex) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("queue_remove_button_$visualIndex")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Quitar de la cola",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMetadataBottomSheet(
    track: AudioTrack,
    metadata: AudioTrackMetadata?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("track_metadata_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AudioAlbumArtThumbnail(
                    audioUri = track.contentUri,
                    albumArtUri = track.albumArtUri,
                    contentDescription = track.album,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(64.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.title ?: track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metadata?.artist ?: track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FormatBadge(format = track.format)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar metadatos")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (isLoading && metadata == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                val resolvedTitle = metadata?.title ?: track.title
                val resolvedArtist = metadata?.artist ?: track.artist
                val resolvedAlbum = metadata?.album ?: track.album
                val resolvedAlbumArtist = metadata?.albumArtist?.takeIf { it.isNotBlank() }
                    ?: track.albumArtist.takeIf { it.isNotBlank() }
                    ?: resolvedArtist
                val resolvedGenre = metadata?.genre?.takeIf { it.isNotBlank() } ?: track.genre
                val resolvedYear = (metadata?.year ?: track.year).takeIf { it > 0 }?.toString()
                val resolvedTrackNum = (metadata?.trackNumber ?: track.trackNumber).takeIf { it > 0 }?.toString()
                val resolvedDuration = formatDuration(metadata?.durationMs ?: track.durationMs)
                val techBadge = metadata?.techSpecs?.formatBadge(track.format) ?: track.format.label
                val sizeStr = formatFileSize(track.sizeBytes)

                MetadataRow(label = "Título", value = resolvedTitle)
                MetadataRow(label = "Artista", value = resolvedArtist)
                MetadataRow(label = "Álbum", value = resolvedAlbum)
                MetadataRow(label = "Artista del álbum", value = resolvedAlbumArtist)
                if (resolvedGenre.isNotBlank()) {
                    MetadataRow(label = "Género", value = resolvedGenre)
                }
                if (resolvedYear != null) {
                    MetadataRow(label = "Año", value = resolvedYear)
                }
                if (resolvedTrackNum != null) {
                    MetadataRow(label = "N.º de pista", value = resolvedTrackNum)
                }
                MetadataRow(label = "Duración", value = resolvedDuration)
                MetadataRow(label = "Calidad y formato", value = techBadge)
                if (sizeStr.isNotEmpty()) {
                    MetadataRow(label = "Tamaño", value = sizeStr)
                }
                MetadataRow(label = "Carpeta", value = track.folderName)
                MetadataRow(
                    label = "Carátula incrustada",
                    value = if (metadata?.hasEmbeddedArtwork == true || track.albumArtUri != null) {
                        "Disponible"
                    } else "No detectada"
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f)
        )
    }
}
