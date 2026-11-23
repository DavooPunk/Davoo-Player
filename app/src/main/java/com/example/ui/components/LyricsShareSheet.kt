package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.LyricsData
import com.example.share.LyricsShareCardRenderer
import com.example.share.LyricsShareClipCalculator
import com.example.share.LyricsShareClipSpec
import com.example.share.ShareCardStyle
import com.example.share.ShareExportUiState
import com.example.share.ShareOutputFormat
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LyricsShareSheet(
    track: AudioTrack,
    lyrics: LyricsData?,
    translatedLyrics: LyricsData?,
    initialPositionMs: Long,
    initialIncludeTranslation: Boolean = false,
    dynamicArtworkColorsEnabled: Boolean = true,
    dynamicAccentColor: Color? = null,
    exportState: ShareExportUiState = ShareExportUiState.Idle,
    onStartExport: (LyricsShareClipSpec) -> Unit,
    onCancelExport: () -> Unit,
    onExportReady: (ShareExportUiState.Ready) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isLossless = track.format == AudioFormat.FLAC || track.format == AudioFormat.WAV
    val baseAccent = if (isLossless) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

    var resolvedDynamicColor by remember(track.id) { mutableStateOf(dynamicAccentColor) }
    LaunchedEffect(track.id, track.albumArtUri, dynamicArtworkColorsEnabled, dynamicAccentColor) {
        resolvedDynamicColor = when {
            !dynamicArtworkColorsEnabled -> null
            dynamicAccentColor != null -> dynamicAccentColor
            else -> ArtworkColorExtractor.extractAccentColor(
                context = context,
                audioUri = track.contentUri,
                albumArtUri = track.albumArtUri,
                fallbackColor = baseAccent
            )
        }
    }

    val accentColor by animateColorAsState(
        targetValue = if (dynamicArtworkColorsEnabled) {
            resolvedDynamicColor ?: dynamicAccentColor ?: baseAccent
        } else {
            baseAccent
        },
        animationSpec = tween(durationMillis = 400),
        label = "share_accent_color"
    )

    val syncedLinesExist = remember(lyrics) {
        lyrics != null && lyrics.isSynced && lyrics.lines.any {
            it.timestampMs != null && !it.isInstrumental && it.text.isNotBlank()
        }
    }
    val hasTranslationAvailable = remember(lyrics, translatedLyrics) {
        lyrics?.hasEmbeddedTranslation == true ||
            (translatedLyrics != null && translatedLyrics.lines.size == lyrics?.lines?.size)
    }

    var selectedStyle by remember(track.id) { mutableStateOf(ShareCardStyle.DYNAMIC_ARTWORK) }
    var selectedFormat by remember(track.id) { mutableStateOf(ShareOutputFormat.VIDEO_MP4) }
    var includeTranslation by remember(track.id) {
        mutableStateOf(initialIncludeTranslation && hasTranslationAvailable)
    }

    // Synced range state (1-6 consecutive lines)
    var syncedRange by remember(track.id, lyrics) {
        mutableStateOf(
            if (syncedLinesExist && lyrics != null) {
                LyricsShareClipCalculator.initialSyncedSelectionRange(lyrics, initialPositionMs)
            } else {
                0..0
            }
        )
    }

    val effectiveTrackDurationMs = track.durationMs.coerceAtLeast(15_000L)
    val initialUnsyncedWindow = remember(track.id, initialPositionMs, effectiveTrackDurationMs) {
        LyricsShareClipCalculator.computeUnsyncedWindow(
            requestedStartMs = initialPositionMs.coerceAtLeast(0L),
            requestedDurationMs = LyricsShareClipCalculator.DEFAULT_UNSYNCED_DURATION_MS,
            trackDurationMs = effectiveTrackDurationMs
        )
    }
    var unsyncedStartMs by remember(track.id) { mutableLongStateOf(initialUnsyncedWindow.startMs) }
    var unsyncedDurationMs by remember(track.id) { mutableLongStateOf(initialUnsyncedWindow.durationMs) }

    val plainLyricsExcerpt = remember(lyrics, syncedLinesExist) {
        if (!syncedLinesExist && lyrics != null && lyrics.lines.isNotEmpty()) {
            lyrics.lines
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .take(4)
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
        } else null
    }
    var includePlainExcerpt by remember(track.id, plainLyricsExcerpt) {
        mutableStateOf(plainLyricsExcerpt != null)
    }

    val syncedWindow = remember(lyrics, translatedLyrics, syncedRange, includeTranslation, syncedLinesExist, effectiveTrackDurationMs) {
        if (syncedLinesExist && lyrics != null) {
            LyricsShareClipCalculator.computeSyncedClipWindow(
                lyrics = lyrics,
                translatedLyrics = translatedLyrics,
                selectedRange = syncedRange,
                trackDurationMs = effectiveTrackDurationMs,
                includeTranslation = includeTranslation && hasTranslationAvailable
            )
        } else null
    }

    val unsyncedWindow = remember(unsyncedStartMs, unsyncedDurationMs, effectiveTrackDurationMs) {
        LyricsShareClipCalculator.computeUnsyncedWindow(
            requestedStartMs = unsyncedStartMs,
            requestedDurationMs = unsyncedDurationMs,
            trackDurationMs = effectiveTrackDurationMs
        )
    }

    val clipStartMs = syncedWindow?.startMs ?: unsyncedWindow.startMs
    val clipEndMs = syncedWindow?.endMs ?: unsyncedWindow.endMs
    val clipDurationMs = (clipEndMs - clipStartMs).coerceAtLeast(1_000L)

    val clipSpec = remember(
        track,
        clipStartMs,
        clipEndMs,
        syncedLinesExist,
        syncedWindow,
        plainLyricsExcerpt,
        includePlainExcerpt,
        includeTranslation,
        hasTranslationAvailable,
        selectedStyle,
        selectedFormat,
        accentColor
    ) {
        LyricsShareClipSpec(
            track = track,
            startMs = clipStartMs,
            endMs = clipEndMs,
            isSyncedLyrics = syncedLinesExist && (syncedWindow?.verses?.isNotEmpty() == true),
            verses = syncedWindow?.verses ?: emptyList(),
            plainExcerpt = if (includePlainExcerpt) plainLyricsExcerpt else null,
            includeTranslation = includeTranslation && hasTranslationAvailable,
            style = selectedStyle,
            format = selectedFormat,
            dynamicAccentColorInt = accentColor.toArgb()
        )
    }

    // Blurred background bitmap preview for Style 3 (API 24+ compatible)
    val blurredPreviewBitmap by produceState<Bitmap?>(initialValue = null, track.id, selectedStyle) {
        if (selectedStyle != ShareCardStyle.BLURRED_ARTWORK) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val art = LyricsShareCardRenderer.loadArtworkBitmap(context, track, maxSizePx = 320)
                ?: return@withContext null
            try {
                LyricsShareCardRenderer.createBlurredBackgroundBitmap(art, 270, 480)
            } finally {
                if (!art.isRecycled) art.recycle()
            }
        }
    }

    // Isolated loop audio preview player
    var isPreviewPlaying by remember(track.id) { mutableStateOf(true) }
    var previewElapsedMs by remember(track.id, clipStartMs, clipEndMs) { mutableLongStateOf(0L) }

    val previewPlayer = remember(track.id) {
        runCatching {
            if (track.contentUri.isNotBlank()) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(track.contentUri)))
                    prepare()
                }
            } else null
        }.getOrNull()
    }

    DisposableEffect(previewPlayer) {
        onDispose {
            runCatching { previewPlayer?.stop() }
            runCatching { previewPlayer?.release() }
        }
    }

    // Pause loop preview while exporting so audio decoder resources aren't contended
    val isExportingActive = exportState is ShareExportUiState.Preparing || exportState is ShareExportUiState.Exporting
    LaunchedEffect(isExportingActive) {
        if (isExportingActive) {
            isPreviewPlaying = false
        }
    }

    // Loop & animate [clipStartMs, clipEndMs]
    LaunchedEffect(previewPlayer, clipStartMs, clipEndMs, isPreviewPlaying) {
        if (previewPlayer != null) {
            runCatching {
                previewPlayer.seekTo(clipStartMs)
                previewPlayer.playWhenReady = isPreviewPlaying
            }
        }
        var lastTickTime = System.currentTimeMillis()
        while (isActive) {
            delay(65L)
            val now = System.currentTimeMillis()
            val delta = (now - lastTickTime).coerceIn(0L, 250L)
            lastTickTime = now

            if (!isPreviewPlaying) continue

            val playerPos = previewPlayer?.let { p ->
                runCatching {
                    if (p.isPlaying && p.playbackState == Player.STATE_READY) p.currentPosition else null
                }.getOrNull()
            }

            if (playerPos != null) {
                if (playerPos >= clipEndMs || playerPos < clipStartMs - 300L) {
                    runCatching { previewPlayer.seekTo(clipStartMs) }
                    previewElapsedMs = 0L
                } else {
                    previewElapsedMs = (playerPos - clipStartMs).coerceIn(0L, clipDurationMs)
                }
            } else {
                // Smooth fallback clock when preview player is buffering or in Robolectric test env
                val next = previewElapsedMs + delta
                previewElapsedMs = if (next >= clipDurationMs) 0L else next
            }
        }
    }

    LaunchedEffect(exportState) {
        if (exportState is ShareExportUiState.Ready) {
            onExportReady(exportState)
        }
    }

    val handleDismiss: () -> Unit = {
        if (isExportingActive) {
            onCancelExport()
        }
        onDismiss()
    }
    BackHandler(onBack = handleDismiss)

    val cardHighlightAccent = when (selectedStyle) {
        ShareCardStyle.COPPER_AMBER_DARK -> CopperAmber
        ShareCardStyle.DYNAMIC_ARTWORK, ShareCardStyle.BLURRED_ARTWORK -> accentColor
    }
    val onAccentText = ThemePalette.bestContrastingTextColor(cardHighlightAccent)

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("lyrics_share_sheet")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = handleDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("share_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar compartir"
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "Compartir fragmento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.title} · ${track.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = cardHighlightAccent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, cardHighlightAccent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "${(clipDurationMs / 1000L).coerceAtLeast(1L)}s / 30s",
                        style = MaterialTheme.typography.labelMedium,
                        color = cardHighlightAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("share_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Live 9:16 Card Preview + Loop Audio Toggle
                item(key = "preview_card") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShareCardLivePreview(
                            spec = clipSpec,
                            accentColor = cardHighlightAccent,
                            blurredBgBitmap = blurredPreviewBitmap,
                            elapsedMs = previewElapsedMs,
                            isStaticImage = selectedFormat == ShareOutputFormat.IMAGE_PNG
                        )

                        Surface(
                            onClick = { isPreviewPlaying = !isPreviewPlaying },
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, cardHighlightAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("share_preview_play_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPreviewPlaying) "Pausar vista previa" else "Reproducir vista previa",
                                    tint = cardHighlightAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isPreviewPlaying) {
                                        "Bucle en vivo · ${formatDuration(clipStartMs)} – ${formatDuration(clipEndMs)}"
                                    } else {
                                        "Pausado · ${formatDuration(clipStartMs)} – ${formatDuration(clipEndMs)}"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 2. Output Format + Style + Optional Translation Toggle
                item(key = "style_and_format") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Formato de exportación",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShareOutputFormat.entries.forEach { fmt ->
                                val selected = selectedFormat == fmt
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedFormat = fmt },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (fmt == ShareOutputFormat.VIDEO_MP4) {
                                                Icons.Rounded.VideoFile
                                            } else {
                                                Icons.Rounded.Image
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = { Text(fmt.shortLabel) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = cardHighlightAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = cardHighlightAccent,
                                        selectedLeadingIconColor = cardHighlightAccent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag(
                                            if (fmt == ShareOutputFormat.VIDEO_MP4) {
                                                "share_format_mp4"
                                            } else {
                                                "share_format_png"
                                            }
                                        )
                                )
                            }
                        }

                        Text(
                            text = "Estilo visual",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ShareCardStyle.entries) { style ->
                                val selected = selectedStyle == style
                                val tag = when (style) {
                                    ShareCardStyle.DYNAMIC_ARTWORK -> "share_style_dynamic"
                                    ShareCardStyle.COPPER_AMBER_DARK -> "share_style_amber"
                                    ShareCardStyle.BLURRED_ARTWORK -> "share_style_blurred"
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedStyle = style },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    label = { Text(style.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = cardHighlightAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = cardHighlightAccent,
                                        selectedLeadingIconColor = cardHighlightAccent
                                    ),
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }

                        if (syncedLinesExist && hasTranslationAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null,
                                    tint = cardHighlightAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                FilterChip(
                                    selected = !includeTranslation,
                                    onClick = { includeTranslation = false },
                                    label = { Text("Original") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = cardHighlightAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = cardHighlightAccent
                                    ),
                                    modifier = Modifier.testTag("share_lang_original")
                                )
                                FilterChip(
                                    selected = includeTranslation,
                                    onClick = { includeTranslation = true },
                                    label = { Text("Bilingüe") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = cardHighlightAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = cardHighlightAccent
                                    ),
                                    modifier = Modifier.testTag("share_lang_bilingual")
                                )
                            }
                        }
                    }
                }

                // 3. Error feedback if any
                if (exportState is ShareExportUiState.Error) {
                    item(key = "export_error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = exportState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // 4. Smart Selector: Synced LRC 1-6 lines OR Unsynced 15s-30s time range
                if (syncedLinesExist && lyrics != null) {
                    item(key = "synced_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selecciona de 1 a 6 líneas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(syncedRange.last - syncedRange.first + 1).coerceAtLeast(1)}/6 líneas",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cardHighlightAccent
                                )
                            }
                            if (syncedWindow?.wasClampedTo30s == true) {
                                Text(
                                    text = "Clip ajustado automáticamente al máximo de 30 segundos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val transLines = translatedLyrics?.lines
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { idx, line -> "share_lrc_${idx}_${line.timestampMs}" }
                    ) { idx, line ->
                        val isSyncedLine = line.timestampMs != null && !line.isInstrumental && line.text.isNotBlank()
                        if (isSyncedLine) {
                            val isSelected = idx in syncedRange
                            val translatedLine = if (includeTranslation && hasTranslationAvailable) {
                                (transLines?.getOrNull(idx)?.text ?: line.secondaryText)
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() && !it.equals(line.text.trim(), ignoreCase = true) }
                            } else null
                            val adjustedTs = LyricsShareClipCalculator.effectiveTimestampMs(
                                line.timestampMs!!,
                                lyrics.offsetMs
                            )

                            Surface(
                                onClick = {
                                    syncedRange = LyricsShareClipCalculator.updateSyncedSelection(
                                        currentRange = syncedRange,
                                        tappedIndex = idx,
                                        lyrics = lyrics,
                                        trackDurationMs = effectiveTrackDurationMs
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    cardHighlightAccent.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        cardHighlightAccent.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("share_lyric_line_$idx")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            syncedRange = LyricsShareClipCalculator.updateSyncedSelection(
                                                currentRange = syncedRange,
                                                tappedIndex = idx,
                                                lyrics = lyrics,
                                                trackDurationMs = effectiveTrackDurationMs
                                            )
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = line.text.trim(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (translatedLine != null) {
                                            Text(
                                                text = translatedLine,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic,
                                                color = cardHighlightAccent
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatDuration(adjustedTs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "unsynced_range_selector") {
                        UnsyncedClipRangeSelectorCard(
                            trackDurationMs = effectiveTrackDurationMs,
                            startMs = unsyncedWindow.startMs,
                            durationMs = unsyncedWindow.durationMs,
                            accentColor = cardHighlightAccent,
                            hasPlainLyrics = plainLyricsExcerpt != null,
                            includePlainExcerpt = includePlainExcerpt,
                            onTogglePlainExcerpt = { includePlainExcerpt = it },
                            onStartChange = { newStart ->
                                val updated = LyricsShareClipCalculator.computeUnsyncedWindow(
                                    requestedStartMs = newStart,
                                    requestedDurationMs = unsyncedDurationMs,
                                    trackDurationMs = effectiveTrackDurationMs
                                )
                                unsyncedStartMs = updated.startMs
                                unsyncedDurationMs = updated.durationMs
                            },
                            onDurationChange = { newDur ->
                                val updated = LyricsShareClipCalculator.computeUnsyncedWindow(
                                    requestedStartMs = unsyncedStartMs,
                                    requestedDurationMs = newDur,
                                    trackDurationMs = effectiveTrackDurationMs
                                )
                                unsyncedStartMs = updated.startMs
                                unsyncedDurationMs = updated.durationMs
                            }
                        )
                    }
                }
            }

            // Bottom Share CTA
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onStartExport(clipSpec) },
                        enabled = !isExportingActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cardHighlightAccent,
                            contentColor = onAccentText
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("share_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedFormat == ShareOutputFormat.VIDEO_MP4) {
                                "Exportar y compartir Video (.mp4)"
                            } else {
                                "Exportar y compartir Imagen (.png)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Export progress modal overlay
        if (isExportingActive) {
            val progress = (exportState as? ShareExportUiState.Exporting)?.progress ?: 0f
            val stageLabel = (exportState as? ShareExportUiState.Exporting)?.stageLabel
                ?: "Preparando archivo…"

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable(enabled = true, onClick = {})
                    .testTag("share_export_progress_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cardHighlightAccent.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedFormat == ShareOutputFormat.VIDEO_MP4) {
                                "Generando video 9:16…"
                            } else {
                                "Generando tarjeta PNG…"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = cardHighlightAccent,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("share_export_progress_bar")
                        )
                        Text(
                            text = "${(progress * 100f).roundToInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = cardHighlightAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = onCancelExport,
                            modifier = Modifier.testTag("share_cancel_export_button")
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareCardLivePreview(
    spec: LyricsShareClipSpec,
    accentColor: Color,
    blurredBgBitmap: Bitmap?,
    elapsedMs: Long,
    isStaticImage: Boolean
) {
    val cardModifier = Modifier
        .height(240.dp)
        .aspectRatio(9f / 16f)
        .clip(RoundedCornerShape(20.dp))
        .border(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.45f),
            shape = RoundedCornerShape(20.dp)
        )
        .testTag("share_live_preview_card")

    Box(modifier = cardModifier) {
        // Background style layer
        when (spec.style) {
            ShareCardStyle.DYNAMIC_ARTWORK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(ColorUtils.blendARGB(0xFF0B0D12.toInt(), accentColor.toArgb(), 0.48f)),
                                    Color(ColorUtils.blendARGB(0xFF0B0D12.toInt(), accentColor.toArgb(), 0.24f)),
                                    Color(0xFF080A0E)
                                )
                            )
                        )
                )
            }
            ShareCardStyle.COPPER_AMBER_DARK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1A1410),
                                    Color(0xFF0E0F14),
                                    Color(0xFF08090C)
                                )
                            )
                        )
                )
            }
            ShareCardStyle.BLURRED_ARTWORK -> {
                if (blurredBgBitmap != null && !blurredBgBitmap.isRecycled) {
                    Image(
                        bitmap = blurredBgBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.56f))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color(ColorUtils.blendARGB(0xFF0B0D12.toInt(), accentColor.toArgb(), 0.28f))
                            )
                    )
                }
            }
        }

        // Inner card container
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = when (spec.style) {
                ShareCardStyle.COPPER_AMBER_DARK -> Color(0xE614151B)
                ShareCardStyle.BLURRED_ARTWORK -> Color(0xBA0F1219)
                ShareCardStyle.DYNAMIC_ARTWORK -> Color(
                    ColorUtils.setAlphaComponent(
                        ColorUtils.blendARGB(0xFF10131C.toInt(), accentColor.toArgb(), 0.20f),
                        215
                    )
                )
            },
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioAlbumArtThumbnail(
                        audioUri = spec.track.contentUri,
                        albumArtUri = spec.track.albumArtUri,
                        contentDescription = spec.track.title,
                        cornerRadius = 6.dp,
                        thumbnailSizePx = 128,
                        modifier = Modifier.size(30.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spec.track.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp, lineHeight = 11.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = spec.track.artist,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                // Body
                if (spec.isSyncedLyrics && spec.verses.isNotEmpty()) {
                    val absoluteTimeMs = spec.startMs + elapsedMs
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        spec.verses.take(6).forEach { verse ->
                            val isCurrent = isStaticImage ||
                                (absoluteTimeMs >= verse.startMs && absoluteTimeMs < verse.endMs)
                            val isPast = !isStaticImage && absoluteTimeMs >= verse.endMs
                            val alpha = when {
                                isCurrent -> 1f
                                isPast -> 0.72f
                                else -> 0.45f
                            }
                            Text(
                                text = verse.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = if (spec.verses.size <= 3) 9.5.sp else 8.sp,
                                    lineHeight = if (spec.verses.size <= 3) 12.sp else 10.sp
                                ),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White.copy(alpha = alpha),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 1.5.dp)
                            )
                            if (spec.includeTranslation && !verse.secondaryText.isNullOrBlank()) {
                                Text(
                                    text = verse.secondaryText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 7.sp,
                                        lineHeight = 9.sp
                                    ),
                                    fontStyle = FontStyle.Italic,
                                    color = accentColor.copy(alpha = alpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AudioAlbumArtThumbnail(
                            audioUri = spec.track.contentUri,
                            albumArtUri = spec.track.albumArtUri,
                            contentDescription = spec.track.title,
                            cornerRadius = 10.dp,
                            thumbnailSizePx = 220,
                            modifier = Modifier.size(62.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // Animated spectrum bars synced to elapsedMs
                        PreviewSpectrumBars(
                            accentColor = accentColor,
                            elapsedMs = elapsedMs
                        )
                        if (!spec.plainExcerpt.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = spec.plainExcerpt,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, lineHeight = 10.sp),
                                color = Color.White.copy(alpha = 0.88f),
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Footer progress + Davoo Player watermark
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val progress = if (isStaticImage) {
                        0.55f
                    } else {
                        (elapsedMs.toFloat() / spec.clipDurationMs.toFloat()).coerceIn(0f, 1f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(accentColor)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isStaticImage) {
                                formatDuration(spec.startMs)
                            } else {
                                "${formatDuration(elapsedMs)} / ${formatDuration(spec.clipDurationMs)}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.8.sp),
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Text(
                                text = "Davoo Player",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSpectrumBars(
    accentColor: Color,
    elapsedMs: Long
) {
    val barCount = 14
    val center = (barCount - 1) / 2.0
    val timeSec = elapsedMs / 1000.0
    Row(
        modifier = Modifier.height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val env = (1f - (abs(i - center) / center).toFloat() * 0.48f).coerceIn(0.35f, 1f)
            val w1 = sin(timeSec * 5.2 + i * 0.6)
            val w2 = cos(timeSec * 3.4 - i * 0.85)
            val level = (0.48 + 0.32 * w1 + 0.20 * w2).toFloat().coerceIn(0.2f, 1f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .graphicsLayer {
                        scaleY = (level * env).coerceIn(0.18f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun UnsyncedClipRangeSelectorCard(
    trackDurationMs: Long,
    startMs: Long,
    durationMs: Long,
    accentColor: Color,
    hasPlainLyrics: Boolean,
    includePlainExcerpt: Boolean,
    onTogglePlainExcerpt: (Boolean) -> Unit,
    onStartChange: (Long) -> Unit,
    onDurationChange: (Long) -> Unit
) {
    val minDurSec = (minOf(LyricsShareClipCalculator.MIN_UNSYNCED_DURATION_MS, trackDurationMs) / 1000L).coerceAtLeast(1L)
    val maxDurSec = (minOf(LyricsShareClipCalculator.MAX_CLIP_DURATION_MS, trackDurationMs) / 1000L).coerceAtLeast(minDurSec)
    val maxStartMs = (trackDurationMs - durationMs).coerceAtLeast(1L)

    var sliderStartFraction by remember(startMs, maxStartMs) {
        mutableFloatStateOf((startMs.toFloat() / maxStartMs.toFloat()).coerceIn(0f, 1f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unsynced_range_selector_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Selector de fragmento (15s – 30s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Inicio ${formatDuration(startMs)} · Fin ${formatDuration(startMs + durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Posición en la canción",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = sliderStartFraction,
                    onValueChange = { frac ->
                        sliderStartFraction = frac
                        onStartChange((frac * maxStartMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_unsynced_start_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(trackDurationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Duración del clip: ${(durationMs / 1000L).coerceAtLeast(1L)}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15L, 20L, 25L, 30L).forEach { sec ->
                        val clampedSec = sec.coerceIn(minDurSec, maxDurSec)
                        val selected = abs((durationMs / 1000L) - clampedSec) <= 1L
                        FilterChip(
                            selected = selected,
                            onClick = { onDurationChange(clampedSec * 1000L) },
                            label = { Text("${clampedSec}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                selectedLabelColor = accentColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_unsynced_dur_${sec}s")
                        )
                    }
                }
            }

            if (hasPlainLyrics) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mostrar extracto de letra en la tarjeta",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = includePlainExcerpt,
                        onCheckedChange = onTogglePlainExcerpt
                    )
                }
            }
        }
    }
}
