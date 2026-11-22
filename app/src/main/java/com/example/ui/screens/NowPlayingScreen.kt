package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.playback.PlaybackPositionState
import com.example.playback.PlaybackUiState
import com.example.ui.components.AudioAlbumArtThumbnail
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.CopperAmberBright
import com.example.ui.theme.CopperAmberContainer
import com.example.ui.theme.LosslessCyan
import com.example.ui.theme.LosslessCyanContainer
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    audioSettings: AudioSettings = AudioSettings(),
    sleepTimerRemainingMs: Long? = null,
    stopAfterCurrentTrack: Boolean = false,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onShowLyrics: (AudioTrack) -> Unit = {},
    onEditTags: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier,
    onShowMetadata: (AudioTrack) -> Unit = {},
    scrobbleStatusNotice: String? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    livePositionFlow: StateFlow<PlaybackPositionState>? = null
) {
    val context = LocalContext.current
    val track = playbackState.currentTrack
    val isLossless = track?.format == AudioFormat.FLAC || track?.format == AudioFormat.WAV
    val baseAccent = if (isLossless) LosslessCyan else CopperAmber

    var dynamicColor by remember(track?.id) { mutableStateOf<Color?>(null) }
    LaunchedEffect(track?.id, track?.albumArtUri, audioSettings.dynamicArtworkColors) {
        dynamicColor = if (track != null && audioSettings.dynamicArtworkColors) {
            ArtworkColorExtractor.extractAccentColor(
                context = context,
                audioUri = track.contentUri,
                albumArtUri = track.albumArtUri,
                fallbackColor = baseAccent
            )
        } else {
            null
        }
    }

    val accentColor by animateColorAsState(
        targetValue = dynamicColor ?: baseAccent,
        animationSpec = tween(durationMillis = 550),
        label = "now_playing_accent"
    )

    var dragOffsetX by remember(track?.id) { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(targetValue = dragOffsetX, label = "artwork_swipe")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("now_playing_screen")
    ) {
        if (track == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar")
                }
                Text("No hay ninguna pista seleccionada", style = MaterialTheme.typography.titleMedium)
            }
            return
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val availWidth = maxWidth
            val availHeight = maxHeight
            val isCompactHeight = availHeight < 650.dp
            val maxContainerWidth = minOf(availWidth, 560.dp)
            val maxArtworkByWidth = (maxContainerWidth - 40.dp) * 0.78f
            val fixedChromeReserve = if (!scrobbleStatusNotice.isNullOrBlank()) 392.dp else 366.dp
            val maxArtworkByHeight = (availHeight - fixedChromeReserve).coerceAtLeast(140.dp)
            val artworkSize = minOf(maxArtworkByWidth, maxArtworkByHeight).coerceIn(140.dp, 288.dp)
            val extraVerticalSpace = (availHeight - artworkSize - fixedChromeReserve).coerceAtLeast(0.dp)
            val sectionGap = (if (isCompactHeight) 8.dp else 10.dp) + (extraVerticalSpace / 6f).coerceIn(0.dp, 12.dp)

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = if (isCompactHeight) 6.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Minimizar reproductor",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "REPRODUCIENDO DESDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = track.folderName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Ver cola de reproducción"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sectionGap))

                // Hero Artwork with responsive sizing & horizontal swipe-to-skip gesture
                Box(
                    modifier = Modifier
                        .size(artworkSize)
                        .graphicsLayer {
                            translationX = animatedDragX * 0.45f
                            rotationZ = (animatedDragX / 45f).coerceIn(-6f, 6f)
                        }
                        .pointerInput(track.id) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                    dragOffsetX = totalDrag.coerceIn(-180f, 180f)
                                },
                                onDragEnd = {
                                    if (totalDrag < -110f) {
                                        onSkipNext()
                                    } else if (totalDrag > 110f) {
                                        onSkipPrevious()
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f }
                            )
                        }
                        .shadow(
                            elevation = if (isCompactHeight) 16.dp else 22.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = accentColor,
                            spotColor = accentColor
                        )
                        .testTag("now_playing_artwork")
                ) {
                    AudioAlbumArtThumbnail(
                        audioUri = track.contentUri,
                        albumArtUri = track.albumArtUri,
                        contentDescription = track.album,
                        cornerRadius = 24.dp,
                        thumbnailSizePx = 512,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(sectionGap))

                // Live Audio Visualizer + Technical Format Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiveSpectrumVisualizer(
                        isPlaying = playbackState.isPlaying,
                        accentColor = accentColor
                    )
                    val techLabel = playbackState.techSpecs?.formatBadge(track.format) ?: track.format.label
                    val sizeText = formatFileSize(track.sizeBytes)
                    val fullBadge = if (sizeText.isNotEmpty()) "$techLabel · $sizeText" else techLabel
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isLossless) LosslessCyanContainer else CopperAmberContainer,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("now_playing_tech_badge")
                    ) {
                        Text(
                            text = fullBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLossless) LosslessCyan else CopperAmberBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !scrobbleStatusNotice.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = LosslessCyanContainer,
                        border = BorderStroke(1.dp, LosslessCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("now_playing_scrobble_badge")
                    ) {
                        Text(
                            text = scrobbleStatusNotice.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = LosslessCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sectionGap))

            // Track Metadata + Favorite Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val displayArtist = playbackState.metadata?.artist ?: track.artist
                    val displayAlbum = playbackState.metadata?.album ?: track.album
                    val displayYear = (playbackState.metadata?.year ?: track.year).takeIf { it > 0 }
                    val displayGenre = (playbackState.metadata?.genre ?: track.genre).takeIf { it.isNotBlank() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onOpenArtist?.invoke(displayArtist) },
                            enabled = onOpenArtist != null && displayArtist.isNotBlank(),
                            shape = RoundedCornerShape(100.dp),
                            color = accentColor.copy(alpha = 0.14f),
                            border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("now_playing_artist_link")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "Ir al artista",
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = displayArtist,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = { onOpenAlbum?.invoke(displayAlbum) },
                            enabled = onOpenAlbum != null && displayAlbum.isNotBlank(),
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("now_playing_album_link")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = "Ir al álbum",
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = displayAlbum,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val extraMeta = buildList {
                        if (displayYear != null) add(displayYear.toString())
                        if (displayGenre != null) add(displayGenre)
                    }.joinToString(" · ")
                    if (extraMeta.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = extraMeta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { onToggleFavorite(track) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_favorite_button")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (track.isFavorite) "Quitar de favoritas" else "Marcar favorita",
                        tint = if (track.isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionGap * 0.75f))

            // Isolated Scrubber so 350ms position ticks never recompose the artwork, gradient, or tools
            NowPlayingScrubberSection(
                fallbackPositionMs = playbackState.positionMs,
                fallbackDurationMs = playbackState.durationMs,
                livePositionFlow = livePositionFlow,
                accentColor = accentColor,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(sectionGap))

            // Transport Controls (clean without 10s jump buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Modo aleatorio",
                        tint = if (playbackState.shuffleEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Pista anterior",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF0C0E13)
                    ),
                    modifier = Modifier
                        .size(if (isCompactHeight) 62.dp else 66.dp)
                        .testTag("now_playing_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente pista",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_repeat_button")
                ) {
                    val repeatIcon = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    }
                    val repeatActive = playbackState.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Modo repetición",
                        tint = if (repeatActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Secondary Audiophile Tools Strip
            val timerLabel = when {
                stopAfterCurrentTrack -> "Al terminar"
                sleepTimerRemainingMs != null -> formatDuration(sleepTimerRemainingMs)
                else -> "Temporizador"
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    NowPlayingActionChip(
                        icon = Icons.Rounded.Lyrics,
                        label = "Letras",
                        tint = accentColor,
                        onClick = { onShowLyrics(track) },
                        testTag = "now_playing_lyrics_button"
                    )
                }
                item {
                    NowPlayingActionChip(
                        icon = Icons.Rounded.Equalizer,
                        label = if (audioSettings.eqEnabled) "EQ (${audioSettings.eqPreset.label})" else "Ecualizador",
                        tint = if (audioSettings.eqEnabled) LosslessCyan else accentColor,
                        onClick = onOpenEqualizer,
                        testTag = "now_playing_eq_button"
                    )
                }
                item {
                    NowPlayingActionChip(
                        icon = Icons.Rounded.Bedtime,
                        label = timerLabel,
                        tint = if (sleepTimerRemainingMs != null || stopAfterCurrentTrack) CopperAmber else accentColor,
                        onClick = onOpenSleepTimer,
                        testTag = "now_playing_timer_button"
                    )
                }
                item {
                    NowPlayingActionChip(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = "Guardar en lista",
                        tint = accentColor,
                        onClick = { onAddToPlaylist(track) },
                        testTag = "now_playing_add_to_playlist_button"
                    )
                }
                item {
                    NowPlayingActionChip(
                        icon = Icons.Rounded.Edit,
                        label = "Etiquetas",
                        tint = accentColor,
                        onClick = { onEditTags(track) },
                        testTag = "now_playing_edit_tags_button"
                    )
                }
                item {
                    NowPlayingActionChip(
                        icon = Icons.Rounded.Info,
                        label = "Metadatos",
                        tint = accentColor,
                        onClick = { onShowMetadata(track) },
                        testTag = "now_playing_metadata_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NowPlayingScrubberSection(
    fallbackPositionMs: Long,
    fallbackDurationMs: Long,
    livePositionFlow: StateFlow<PlaybackPositionState>?,
    accentColor: Color,
    onSeekTo: (Long) -> Unit
) {
    val livePosState = livePositionFlow?.collectAsStateWithLifecycle()?.value
    val activePosMs = livePosState?.positionMs ?: fallbackPositionMs
    val activeDurMs = (livePosState?.durationMs?.takeIf { it > 0L } ?: fallbackDurationMs).coerceAtLeast(0L)
    val totalMs = activeDurMs.coerceAtLeast(1L)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (activePosMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }

    Slider(
        value = currentFraction,
        onValueChange = { fraction ->
            isDragging = true
            dragFraction = fraction
        },
        onValueChangeFinished = {
            val seekTarget = (dragFraction * totalMs).toLong()
            onSeekTo(seekTarget)
            isDragging = false
        },
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag("now_playing_slider")
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val displayedPos = if (isDragging) (dragFraction * totalMs).toLong() else activePosMs
        Text(
            text = formatDuration(displayedPos),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatDuration(activeDurMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NowPlayingActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun LiveSpectrumVisualizer(
    isPlaying: Boolean,
    accentColor: Color
) {
    val levels = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "spectrum")
        val b1 by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
            label = "s1"
        )
        val b2 by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
            label = "s2"
        )
        val b3 by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(290, easing = LinearEasing), RepeatMode.Reverse),
            label = "s3"
        )
        val b4 by transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(410, easing = LinearEasing), RepeatMode.Reverse),
            label = "s4"
        )
        listOf(b1, b2, b3, b4)
    } else {
        listOf(0.25f, 0.45f, 0.3f, 0.2f)
    }

    Row(
        modifier = Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .graphicsLayer {
                        scaleY = level.coerceIn(0.15f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }
    }
}
