package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.LyricsDisplayMode
import com.example.ai.LyricsTranslationLanguage
import com.example.ai.LyricsTranslationUiState
import com.example.ai.TranslationEngine
import com.example.data.repository.OnlineLyricsCandidate
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.LyricsData
import com.example.playback.PlaybackPositionState
import com.example.playback.PlaybackUiState
import com.example.playback.rememberAudioOutputDeviceInfo
import kotlinx.coroutines.flow.StateFlow

enum class NowPlayingTab(
    val label: String,
    val tag: String,
    val icon: ImageVector
) {
    QUEUE("Cola", "now_playing_tab_queue", Icons.AutoMirrored.Rounded.QueueMusic),
    LYRICS("Letras", "now_playing_tab_lyrics", Icons.Rounded.Lyrics),
    INFO("Info", "now_playing_tab_info", Icons.Rounded.Info)
}

@Composable
fun NowPlayingTabsOverlay(
    track: AudioTrack,
    activeTab: NowPlayingTab,
    onSelectTab: (NowPlayingTab) -> Unit,
    onCollapse: () -> Unit,
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    lyrics: LyricsData?,
    isLoadingLyrics: Boolean,
    onSeekTo: (Long) -> Unit,
    onPickLrcFile: () -> Unit,
    metadata: AudioTrackMetadata?,
    isLoadingMetadata: Boolean,
    dynamicArtworkColorsEnabled: Boolean = true,
    dynamicAccentColor: Color? = null,
    onClearSavedLyrics: (() -> Unit)? = null,
    onSearchOnlineLyrics: (suspend (customQuery: String?) -> Result<List<OnlineLyricsCandidate>>)? = null,
    onSaveOnlineLyrics: (suspend (OnlineLyricsCandidate) -> Unit)? = null,
    onOpenLyricsify: ((query: String) -> Unit)? = null,
    translationState: LyricsTranslationUiState = LyricsTranslationUiState.Idle,
    selectedEngine: TranslationEngine = TranslationEngine.DEEPL,
    onSelectEngine: ((TranslationEngine) -> Unit)? = null,
    onTranslateLyrics: ((LyricsData, LyricsTranslationLanguage, TranslationEngine) -> Unit)? = null,
    onSaveManualTranslation: ((LyricsData, String, LyricsTranslationLanguage) -> Unit)? = null,
    onClearSavedTranslation: (() -> Unit)? = null,
    onOpenHumanTranslationSite: ((url: String, siteName: String) -> Unit)? = null,
    onAdjustOffset: ((deltaMs: Long) -> Unit)? = null,
    onResetOffset: (() -> Unit)? = null,
    onExportSaveLrc: ((includeTranslation: Boolean) -> Unit)? = null,
    onExportShareLrc: ((includeTranslation: Boolean) -> Unit)? = null,
    livePositionFlow: StateFlow<PlaybackPositionState>? = null,
    onShareSnippet: ((includeTranslation: Boolean) -> Unit)? = null,
    hasDeepSeekApiKey: Boolean = true,
    onOpenTranslatorSettings: (() -> Unit)? = null,
    openTranslateMenuOnStart: Boolean = false,
    onEditTags: ((AudioTrack) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null
) {
    BackHandler(onBack = onCollapse)

    val palette = rememberLyricsAdaptivePalette(
        track = track,
        dynamicArtworkColorsEnabled = dynamicArtworkColorsEnabled,
        dynamicAccentColor = dynamicAccentColor
    )
    var displayMode by remember(track.id) { mutableStateOf(LyricsDisplayMode.BILINGUAL) }
    val queueListState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.sheetTopGlow, palette.sheetBaseBg, palette.sheetBottomBg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("now_playing_tabs_overlay")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed YT Music-style top mini player header (44.dp art + marquee title + artist + Play/Pause + Collapse ⌄)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("now_playing_tabs_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AudioAlbumArtThumbnail(
                    audioUri = track.contentUri,
                    albumArtUri = track.albumArtUri,
                    contentDescription = track.album,
                    cornerRadius = 10.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_tabs_mini_art")
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryFg,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1500,
                            velocity = 30.dp
                        )
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = palette.accentColor,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_tabs_play_pause")
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                            tint = palette.onAccentContent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_tabs_collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Colapsar vista",
                        tint = palette.primaryFg,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Fixed 3-tab strip (Cola, Letras, Info)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("now_playing_tabs_tab_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NowPlayingTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Surface(
                        onClick = { onSelectTab(tab) },
                        shape = RoundedCornerShape(100.dp),
                        color = if (selected) {
                            palette.accentColor.copy(alpha = 0.24f)
                        } else {
                            Color.Transparent
                        },
                        border = if (selected) {
                            BorderStroke(1.dp, palette.accentColor.copy(alpha = 0.55f))
                        } else {
                            BorderStroke(1.dp, palette.primaryFg.copy(alpha = 0.14f))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag(tab.tag)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (selected) palette.brightAccentText else palette.secondaryFg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.primaryFg else palette.secondaryFg,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = palette.primaryFg.copy(alpha = 0.12f)
            )

            // Free-scrolling body per tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    NowPlayingTab.QUEUE -> {
                        QueueLazyList(
                            playbackState = playbackState,
                            onSelectIndex = onSelectQueueIndex,
                            onRemoveIndex = onRemoveQueueIndex,
                            onMoveItem = onMoveQueueItem,
                            listState = queueListState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            primaryTextColor = palette.primaryFg,
                            secondaryTextColor = palette.secondaryFg,
                            accentColor = palette.brightAccentText,
                            currentHighlightColor = palette.accentColor.copy(alpha = 0.22f),
                            draggingCardColor = palette.panelSurfaceBg,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    NowPlayingTab.LYRICS -> {
                        LyricsContentPanel(
                            track = track,
                            lyrics = lyrics,
                            isLoading = isLoadingLyrics,
                            positionMs = playbackState.positionMs,
                            onSeekTo = onSeekTo,
                            onPickLrcFile = onPickLrcFile,
                            palette = palette,
                            displayMode = displayMode,
                            onDisplayModeChange = { displayMode = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            onClearSavedLyrics = onClearSavedLyrics,
                            onSearchOnlineLyrics = onSearchOnlineLyrics,
                            onSaveOnlineLyrics = onSaveOnlineLyrics,
                            onOpenLyricsify = onOpenLyricsify,
                            translationState = translationState,
                            selectedEngine = selectedEngine,
                            onSelectEngine = onSelectEngine,
                            onTranslateLyrics = onTranslateLyrics,
                            onSaveManualTranslation = onSaveManualTranslation,
                            onClearSavedTranslation = onClearSavedTranslation,
                            onOpenHumanTranslationSite = onOpenHumanTranslationSite,
                            isPlaying = playbackState.isPlaying,
                            onAdjustOffset = onAdjustOffset,
                            onResetOffset = onResetOffset,
                            onExportSaveLrc = onExportSaveLrc,
                            onExportShareLrc = onExportShareLrc,
                            livePositionFlow = livePositionFlow,
                            onShareSnippet = onShareSnippet,
                            showShareButtonInBottomBar = true,
                            hasDeepSeekApiKey = hasDeepSeekApiKey,
                            onOpenTranslatorSettings = onOpenTranslatorSettings,
                            openTranslateMenuOnStart = openTranslateMenuOnStart
                        )
                    }

                    NowPlayingTab.INFO -> {
                        NowPlayingInfoTabBody(
                            track = track,
                            metadata = metadata,
                            isLoading = isLoadingMetadata,
                            playbackState = playbackState,
                            palette = palette,
                            onEditTags = onEditTags,
                            onOpenAlbum = onOpenAlbum,
                            onOpenArtist = onOpenArtist
                        )
                    }
                }
            }
        }
    }
}

private data class InfoDetailEntry(
    val key: String,
    val label: String,
    val value: String
)

@Composable
private fun NowPlayingInfoTabBody(
    track: AudioTrack,
    metadata: AudioTrackMetadata?,
    isLoading: Boolean,
    playbackState: PlaybackUiState,
    palette: LyricsAdaptivePalette,
    onEditTags: ((AudioTrack) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?,
    onOpenArtist: ((String) -> Unit)?
) {
    val outputDevice = rememberAudioOutputDeviceInfo()
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
    val techBadge = metadata?.techSpecs?.formatBadge(track.format)
        ?: playbackState.techSpecs?.formatBadge(track.format)
        ?: track.format.label
    val sizeStr = formatFileSize(track.sizeBytes)

    val infoEntries = remember(
        track.id,
        resolvedTitle,
        resolvedArtist,
        resolvedAlbum,
        resolvedAlbumArtist,
        resolvedGenre,
        resolvedYear,
        resolvedTrackNum,
        resolvedDuration,
        techBadge,
        sizeStr,
        metadata?.hasEmbeddedArtwork,
        outputDevice.shortBadgeLabel,
        outputDevice.supportedSampleRatesHz,
        outputDevice.maxBitDepthLabel
    ) {
        buildList {
            add(InfoDetailEntry("title", "Título", resolvedTitle))
            add(InfoDetailEntry("artist", "Artista", resolvedArtist))
            add(InfoDetailEntry("album", "Álbum", resolvedAlbum))
            add(InfoDetailEntry("album_artist", "Artista del álbum", resolvedAlbumArtist))
            if (resolvedGenre.isNotBlank()) add(InfoDetailEntry("genre", "Género", resolvedGenre))
            if (resolvedYear != null) add(InfoDetailEntry("year", "Año", resolvedYear))
            if (resolvedTrackNum != null) add(InfoDetailEntry("track_num", "N.º de pista", resolvedTrackNum))
            add(InfoDetailEntry("duration", "Duración", resolvedDuration))
            add(InfoDetailEntry("format", "Calidad y formato", techBadge))
            if (sizeStr.isNotEmpty()) add(InfoDetailEntry("size", "Tamaño", sizeStr))
            add(InfoDetailEntry("folder", "Carpeta", track.folderName))
            add(
                InfoDetailEntry(
                    "embedded_art",
                    "Carátula incrustada",
                    if (metadata?.hasEmbeddedArtwork == true || track.albumArtUri != null) {
                        "Disponible"
                    } else "No detectada"
                )
            )
            add(InfoDetailEntry("output_device", "Salida activa", outputDevice.shortBadgeLabel))
            if (outputDevice.supportedSampleRatesHz.isNotEmpty()) {
                val ratesSummary = outputDevice.supportedSampleRatesHz
                    .takeLast(4)
                    .joinToString(" / ") { hz ->
                        if (hz % 1000 == 0) "${hz / 1000} kHz" else "${hz / 1000.0} kHz"
                    }
                add(InfoDetailEntry("dac_rates", "Frecuencias DAC", ratesSummary))
            }
            outputDevice.maxBitDepthLabel?.let { depth ->
                add(InfoDetailEntry("bit_depth", "Profundidad salida", depth))
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("now_playing_info_lazy_column"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "info_audio_summary_card", contentType = "info_card") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = palette.panelSurfaceBg,
                border = BorderStroke(1.dp, palette.accentColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = palette.brightAccentText,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = techBadge,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryFg
                        )
                        Text(
                            text = "Salida: ${outputDevice.shortBadgeLabel}${if (sizeStr.isNotEmpty()) " · $sizeStr" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryFg
                        )
                    }
                }
            }
        }

        if (onEditTags != null || onOpenArtist != null || onOpenAlbum != null) {
            item(key = "info_quick_actions", contentType = "info_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onEditTags != null) {
                        OutlinedButton(
                            onClick = { onEditTags(track) },
                            border = BorderStroke(1.dp, palette.accentColor.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryFg),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("info_tab_edit_tags_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Etiquetas", maxLines = 1)
                        }
                    }
                    if (onOpenArtist != null && resolvedArtist.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onOpenArtist(resolvedArtist) },
                            border = BorderStroke(1.dp, palette.primaryFg.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryFg),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("info_tab_artist_button")
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Artista", maxLines = 1)
                        }
                    }
                    if (onOpenAlbum != null && resolvedAlbum.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onOpenAlbum(resolvedAlbum) },
                            border = BorderStroke(1.dp, palette.primaryFg.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryFg),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("info_tab_album_button")
                        ) {
                            Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Álbum", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (isLoading && metadata == null) {
            item(key = "info_loading", contentType = "info_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.brightAccentText, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            items(
                items = infoEntries,
                key = { it.key },
                contentType = { "info_detail_row" }
            ) { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.resultCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryFg,
                            modifier = Modifier.weight(0.40f)
                        )
                        Text(
                            text = entry.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.primaryFg,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.60f)
                        )
                    }
                }
            }
        }
    }
}
