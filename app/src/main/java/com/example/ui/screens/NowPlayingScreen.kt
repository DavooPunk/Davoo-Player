package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.model.LyricsData
import com.example.playback.PlaybackPositionState
import com.example.playback.PlaybackUiState
import com.example.playback.rememberAudioOutputDeviceInfo
import com.example.ui.components.AudioAlbumArtThumbnail
import com.example.ui.components.NowPlayingTab
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.ThemePalette
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
    livePositionFlow: StateFlow<PlaybackPositionState>? = null,
    sleepTimerRemainingFlow: StateFlow<Long?>? = null,
    stopAfterCurrentFlow: StateFlow<Boolean>? = null,
    currentTrackLyrics: LyricsData? = null,
    onQuickTranslateLyrics: (AudioTrack) -> Unit = { onShowLyrics(it) },
    onShareLyricsSnippet: ((AudioTrack) -> Unit)? = null,
    onOpenYtMusicTab: ((NowPlayingTab) -> Unit)? = null
) {
    val context = LocalContext.current
    val outputDevice = rememberAudioOutputDeviceInfo()
    val track = playbackState.currentTrack
    val isLossless = track?.format == AudioFormat.FLAC || track?.format == AudioFormat.WAV
    val baseAccent = if (isLossless) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    var dynamicColor by remember(track?.id) { mutableStateOf<Color?>(null) }
    LaunchedEffect(track?.id, track?.albumArtUri, audioSettings.dynamicArtworkColors, baseAccent) {
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
        targetValue = if (audioSettings.dynamicArtworkColors) (dynamicColor ?: baseAccent) else baseAccent,
        animationSpec = tween(durationMillis = 550),
        label = "now_playing_accent"
    )

    var dragOffsetX by remember(track?.id) { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(targetValue = dragOffsetX, label = "artwork_swipe")
    var showMoreMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.24f),
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
            val isTinyHeight = availHeight < 520.dp
            val isUltraCompact = availHeight < 580.dp
            val isCompactHeight = availHeight < 660.dp
            val horizontalPad = if (availWidth < 360.dp) 16.dp else 18.dp
            val maxContainerWidth = minOf(availWidth, 560.dp)
            val maxArtworkByWidth = (maxContainerWidth - (horizontalPad * 2)).coerceAtLeast(120.dp)

            val scrobbleReserve = if (!scrobbleStatusNotice.isNullOrBlank()) 24.dp else 0.dp
            val fixedChromeReserve = when {
                isTinyHeight -> 372.dp + scrobbleReserve
                isUltraCompact -> 378.dp + scrobbleReserve
                else -> 386.dp + scrobbleReserve
            }
            val minArtwork = when {
                isTinyHeight -> 56.dp
                isUltraCompact -> 112.dp
                else -> 176.dp
            }
            val maxArtworkByHeight = (availHeight - fixedChromeReserve).coerceAtLeast(minArtwork)
            val artworkSize = minOf(maxArtworkByWidth, maxArtworkByHeight).coerceIn(minArtwork, 420.dp)
            val sectionGap = when {
                isTinyHeight -> 2.dp
                isUltraCompact -> 4.dp
                isCompactHeight -> 6.dp
                else -> 10.dp
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = availHeight)
                    .padding(horizontal = horizontalPad, vertical = if (isUltraCompact) 2.dp else 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Header Bar
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                            softWrap = false,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 1500,
                                velocity = 30.dp
                            )
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Más opciones de reproducción"
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.testTag("now_playing_more_dropdown")
                        ) {
                            DropdownMenuItem(
                                text = { Text("Cola de reproducción") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenQueue()
                                },
                                modifier = Modifier.testTag("now_playing_menu_queue")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (audioSettings.eqEnabled) "Ecualizador (${audioSettings.eqPreset.label})"
                                        else "Ecualizador y DSP"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Equalizer, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenEqualizer()
                                },
                                modifier = Modifier.testTag("now_playing_menu_eq")
                            )
                            NowPlayingTimerMenuItem(
                                fallbackRemainingMs = sleepTimerRemainingMs,
                                fallbackStopAfterCurrent = stopAfterCurrentTrack,
                                sleepTimerRemainingFlow = sleepTimerRemainingFlow,
                                stopAfterCurrentFlow = stopAfterCurrentFlow,
                                onClick = {
                                    showMoreMenu = false
                                    onOpenSleepTimer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Guardar en lista") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onAddToPlaylist(track)
                                },
                                modifier = Modifier.testTag("now_playing_menu_add_playlist")
                            )
                            DropdownMenuItem(
                                text = { Text("Editar etiquetas") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onEditTags(track)
                                },
                                modifier = Modifier.testTag("now_playing_menu_edit_tags")
                            )
                            DropdownMenuItem(
                                text = { Text("Metadatos técnicos") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Info, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onShowMetadata(track)
                                },
                                modifier = Modifier.testTag("now_playing_menu_metadata")
                            )
                        }
                    }
                }

                // 2. Upper-Center: Large 1:1 Hero Artwork + Audiophile Badges + Live Lyric Line
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isUltraCompact) 4.dp else 7.dp)
                ) {
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
                                elevation = if (isCompactHeight) 14.dp else 20.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = accentColor,
                                spotColor = accentColor
                            )
                            .testTag("now_playing_artwork")
                    ) {
                        AudioAlbumArtThumbnail(
                            audioUri = track.contentUri,
                            albumArtUri = track.albumArtUri,
                            contentDescription = track.album,
                            cornerRadius = 12.dp,
                            thumbnailSizePx = 720,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Compact Audiophile Badges + Active Output Device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
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
                            color = if (isLossless) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("now_playing_tech_badge")
                        ) {
                            Text(
                                text = fullBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLossless) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        val outputIcon = when {
                            outputDevice.isUsbDac -> Icons.Rounded.Usb
                            outputDevice.isBluetooth -> Icons.Rounded.BluetoothAudio
                            outputDevice.isWired -> Icons.Rounded.Headphones
                            else -> Icons.Rounded.Speaker
                        }
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            border = BorderStroke(
                                0.75.dp,
                                if (outputDevice.isUsbDac) {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier.testTag("now_playing_output_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = outputIcon,
                                    contentDescription = "Salida de audio activa",
                                    tint = if (outputDevice.isUsbDac || outputDevice.isExternal) {
                                        accentColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = outputDevice.shortBadgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !scrobbleStatusNotice.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("now_playing_scrobble_badge")
                        ) {
                            Text(
                                text = scrobbleStatusNotice.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 3. Lower-Center: Track Info, Artist/Album, Favorite, Scrubber & Transport Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(sectionGap * 0.65f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = when {
                                    isTinyHeight -> MaterialTheme.typography.titleMedium
                                    isCompactHeight -> MaterialTheme.typography.titleLarge
                                    else -> MaterialTheme.typography.headlineSmall
                                },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        repeatDelayMillis = 1500,
                                        velocity = 30.dp
                                    )
                            )
                            Spacer(modifier = Modifier.height(if (isTinyHeight) 2.dp else 4.dp))
                            val displayArtist = playbackState.metadata?.artist ?: track.artist
                            val displayAlbum = playbackState.metadata?.album ?: track.album

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
                                            modifier = Modifier.size(13.dp)
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
                                            modifier = Modifier.size(13.dp)
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

                    NowPlayingScrubberSection(
                        fallbackPositionMs = playbackState.positionMs,
                        fallbackDurationMs = playbackState.durationMs,
                        livePositionFlow = livePositionFlow,
                        accentColor = accentColor,
                        onSeekTo = onSeekTo
                    )

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
                                contentColor = ThemePalette.bestContrastingTextColor(accentColor)
                            ),
                            modifier = Modifier
                                .size(if (isUltraCompact) 54.dp else if (isCompactHeight) 58.dp else 64.dp)
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
                }

                // 4. Bottom-Docked YT Music-Inspired 3-Tab Bar (Cola, Letras, Info)
                NowPlayingYtMusicBottomBar(
                    accentColor = accentColor,
                    compact = isTinyHeight,
                    onSelectTab = { tab ->
                        if (onOpenYtMusicTab != null) {
                            onOpenYtMusicTab(tab)
                        } else {
                            when (tab) {
                                NowPlayingTab.QUEUE -> onOpenQueue()
                                NowPlayingTab.LYRICS -> onShowLyrics(track)
                                NowPlayingTab.INFO -> onShowMetadata(track)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NowPlayingYtMusicBottomBar(
    accentColor: Color,
    compact: Boolean = false,
    onSelectTab: (NowPlayingTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 0.dp else 4.dp)
            .testTag("now_playing_yt_bottom_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NowPlayingYtTabButton(
                tab = NowPlayingTab.QUEUE,
                accentColor = accentColor,
                compact = compact,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("now_playing_tab_bar_queue"),
                onClick = { onSelectTab(NowPlayingTab.QUEUE) }
            )
            NowPlayingYtTabButton(
                tab = NowPlayingTab.LYRICS,
                accentColor = accentColor,
                compact = compact,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("now_playing_lyrics_button"),
                onClick = { onSelectTab(NowPlayingTab.LYRICS) }
            )
            NowPlayingYtTabButton(
                tab = NowPlayingTab.INFO,
                accentColor = accentColor,
                compact = compact,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("now_playing_tab_bar_info"),
                onClick = { onSelectTab(NowPlayingTab.INFO) }
            )
        }
    }
}

@Composable
private fun NowPlayingYtTabButton(
    tab: NowPlayingTab,
    accentColor: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (compact) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NowPlayingTimerMenuItem(
    fallbackRemainingMs: Long?,
    fallbackStopAfterCurrent: Boolean,
    sleepTimerRemainingFlow: StateFlow<Long?>?,
    stopAfterCurrentFlow: StateFlow<Boolean>?,
    onClick: () -> Unit
) {
    val liveRemainingMs = sleepTimerRemainingFlow?.collectAsStateWithLifecycle()?.value
    val liveStopAfter = stopAfterCurrentFlow?.collectAsStateWithLifecycle()?.value
    val remainingMs = if (sleepTimerRemainingFlow != null) liveRemainingMs else fallbackRemainingMs
    val stopAfterCurrentTrack = if (stopAfterCurrentFlow != null) (liveStopAfter == true) else fallbackStopAfterCurrent
    val timerLabel = when {
        stopAfterCurrentTrack -> "Temporizador: Al terminar"
        remainingMs != null -> "Temporizador: ${formatDuration(remainingMs)}"
        else -> "Temporizador"
    }
    DropdownMenuItem(
        text = { Text(timerLabel) },
        leadingIcon = {
            Icon(Icons.Rounded.Bedtime, contentDescription = null)
        },
        onClick = onClick,
        modifier = Modifier.testTag("now_playing_menu_timer")
    )
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
            .height(32.dp)
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
        modifier = Modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
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
