package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.model.AudioFormat
import com.example.playback.PlaybackController
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Floating bottom playback bar bound directly to a Media3 [Player] ([androidx.media3.exoplayer.ExoPlayer]
 * or [androidx.media3.session.MediaController]). Displays the currently selected track's embedded artwork,
 * title, artist, album metadata, playback progress, and Play / Pause / Skip Previous / Skip Next controls.
 */
@Composable
fun Media3FloatingBottomBar(
    player: Player,
    modifier: Modifier = Modifier,
    accentOverride: Color? = null,
    onOpenNowPlaying: (() -> Unit)? = null
) {
    var hasCurrentTrack by remember(player) { mutableStateOf(player.currentMediaItem != null) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying || player.playWhenReady) }
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var title by remember(player) { mutableStateOf("") }
    var artist by remember(player) { mutableStateOf("") }
    var album by remember(player) { mutableStateOf("") }
    var audioUri by remember(player) { mutableStateOf("") }
    var artworkUri by remember(player) { mutableStateOf<String?>(null) }
    var format by remember(player) { mutableStateOf<AudioFormat?>(null) }

    fun syncFromPlayer() {
        val currentItem = player.currentMediaItem
        hasCurrentTrack = currentItem != null
        isPlaying = player.isPlaying || player.playWhenReady
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)

        val meta = currentItem?.mediaMetadata ?: player.mediaMetadata
        val extras = meta.extras
        title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: "Sin pista seleccionada"
        artist = meta.artist?.toString().orEmpty()
        album = meta.albumTitle?.toString().orEmpty()
        audioUri = currentItem?.localConfiguration?.uri?.toString().orEmpty()
        artworkUri = meta.artworkUri?.toString()
        val formatName = extras?.getString(PlaybackController.EXTRA_FORMAT)
        val lowerUri = audioUri.lowercase()
        format = when {
            formatName != null -> AudioFormat.entries.firstOrNull { it.name == formatName }
            lowerUri.endsWith(".flac") -> AudioFormat.FLAC
            lowerUri.endsWith(".wav") -> AudioFormat.WAV
            lowerUri.endsWith(".m4a") || lowerUri.endsWith(".aac") || lowerUri.endsWith(".alac") -> AudioFormat.AAC
            lowerUri.endsWith(".ogg") || lowerUri.endsWith(".opus") -> AudioFormat.OGG
            lowerUri.endsWith(".mp3") -> AudioFormat.MP3
            else -> null
        }
    }

    DisposableEffect(player) {
        syncFromPlayer()
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                syncFromPlayer()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncFromPlayer()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                syncFromPlayer()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing || pPlayWhenReady(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = player.isPlaying || playWhenReady
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying, hasCurrentTrack) {
        if (!hasCurrentTrack) return@LaunchedEffect
        positionMs = player.currentPosition.coerceAtLeast(0L)
        val initialDur = player.duration
        if (initialDur > 0L) durationMs = initialDur
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            delay(500L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val pDur = player.duration
            if (pDur > 0L) durationMs = pDur
        }
    }

    if (!hasCurrentTrack) return

    val defaultAccent = if (format == AudioFormat.FLAC || format == AudioFormat.WAV) LosslessCyan else CopperAmber
    val accentColor = accentOverride ?: defaultAccent
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val subtitle = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = accentColor.copy(alpha = 0.35f),
                spotColor = accentColor.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(22.dp))
            .let { if (onOpenNowPlaying != null) it.clickable(onClick = onOpenNowPlaying) else it }
            .testTag("floating_bottom_bar"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioAlbumArtThumbnail(
                    audioUri = audioUri.ifBlank { artworkUri.orEmpty() },
                    albumArtUri = artworkUri,
                    contentDescription = title,
                    cornerRadius = 12.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("floating_bar_artwork")
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        format?.let { currentFormat ->
                            FormatBadge(format = currentFormat)
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (player.currentPosition > 3500L) {
                            player.seekTo(0L)
                        } else if (player.hasPreviousMediaItem()) {
                            player.seekToPreviousMediaItem()
                        } else {
                            player.seekTo(0L)
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("floating_bar_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Pista anterior",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledIconButton(
                    onClick = {
                        if (player.isPlaying || player.playWhenReady) {
                            player.pause()
                            isPlaying = false
                        } else {
                            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                                player.prepare()
                            }
                            player.play()
                            isPlaying = true
                        }
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF0C0E13)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("floating_bar_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                    )
                }

                IconButton(
                    onClick = {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("floating_bar_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente pista",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        }
    }
}

/**
 * Self-contained playback control bar bound directly to a Media3 [Player] / [androidx.media3.exoplayer.ExoPlayer]
 * instance. Observes player state via [Player.Listener] and invokes `play()`, `pause()`, `seekTo()`,
 * and track skip commands directly on the player.
 */
@Composable
fun ExoPlayerControlBar(
    player: Player,
    modifier: Modifier = Modifier,
    seekIncrementMs: Long = 10_000L,
    onOpenNowPlaying: (() -> Unit)? = null
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying || player.playWhenReady) }
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var title by remember(player) {
        mutableStateOf(player.mediaMetadata.title?.toString() ?: "Sin pista")
    }
    var artist by remember(player) {
        mutableStateOf(player.mediaMetadata.artist?.toString().orEmpty())
    }
    var artworkUri by remember(player) {
        mutableStateOf(player.mediaMetadata.artworkUri?.toString())
    }

    fun syncPlayerSnapshot() {
        isPlaying = player.isPlaying || player.playWhenReady
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
        title = player.mediaMetadata.title?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Sin pista"
        artist = player.mediaMetadata.artist?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.artist?.toString()
            ?: ""
        artworkUri = (player.mediaMetadata.artworkUri
            ?: player.currentMediaItem?.mediaMetadata?.artworkUri)?.toString()
    }

    DisposableEffect(player) {
        syncPlayerSnapshot()
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                syncPlayerSnapshot()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing || pPlayWhenReady(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = player.isPlaying || playWhenReady
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncPlayerSnapshot()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying) {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            delay(350L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
        }
    }

    PlaybackControlBar(
        title = title,
        artist = artist,
        artworkUri = artworkUri,
        format = null,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        onPlay = {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
            player.play()
            isPlaying = true
        },
        onPause = {
            player.pause()
            isPlaying = false
        },
        onSeekTo = { targetMs ->
            val maxDur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            val clamped = targetMs.coerceIn(0L, maxDur)
            player.seekTo(clamped)
            positionMs = clamped
        },
        onSeekBackward = {
            val clamped = (player.currentPosition - seekIncrementMs).coerceAtLeast(0L)
            player.seekTo(clamped)
            positionMs = clamped
        },
        onSeekForward = {
            val maxDur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            val clamped = (player.currentPosition + seekIncrementMs).coerceIn(0L, maxDur)
            player.seekTo(clamped)
            positionMs = clamped
        },
        onSkipPrevious = {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else player.seekTo(0L)
        },
        onSkipNext = {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        },
        onHeaderClick = onOpenNowPlaying,
        modifier = modifier
    )
}

private fun pPlayWhenReady(player: Player): Boolean = player.playWhenReady

/**
 * Audiophile transport control bar featuring Play, Pause, Seek Back (-10s), Seek Forward (+10s),
 * Previous/Next track buttons, and an interactive Seek Slider.
 */
@Composable
fun PlaybackControlBar(
    title: String,
    artist: String,
    artworkUri: String?,
    format: AudioFormat?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null
) {
    val accentColor = if (format == AudioFormat.FLAC) LosslessCyan else CopperAmber
    val safeDuration = durationMs.coerceAtLeast(1L)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val sliderFraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("playback_control_bar"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Track Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .let { if (onHeaderClick != null) it.clickable(onClick = onHeaderClick) else it }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtwork(
                    artworkUri = artworkUri,
                    contentDescription = title,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (format != null) {
                            FormatBadge(format = format)
                        }
                    }
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "${formatDuration(if (isDragging) (dragFraction * safeDuration).toLong() else positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Interactive Seek Bar
            Slider(
                value = sliderFraction,
                onValueChange = { fraction ->
                    isDragging = true
                    dragFraction = fraction
                },
                onValueChangeFinished = {
                    onSeekTo((dragFraction * safeDuration).toLong())
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("control_bar_seek_slider")
            )

            // Transport Buttons: Prev, Seek -10s, Play, Pause, Seek +10s, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("control_bar_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Pista anterior",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("control_bar_seek_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = "Retroceder 10 segundos",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledIconButton(
                    onClick = onPlay,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (!isPlaying) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (!isPlaying) Color(0xFF0C0E13) else accentColor
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("control_bar_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir"
                    )
                }

                FilledIconButton(
                    onClick = onPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (isPlaying) Color(0xFF0C0E13) else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("control_bar_pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pausar"
                    )
                }

                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("control_bar_seek_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward10,
                        contentDescription = "Adelantar 10 segundos",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("control_bar_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente pista",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
