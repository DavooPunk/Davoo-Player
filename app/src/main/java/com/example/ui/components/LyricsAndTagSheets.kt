package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.ai.HumanLyricsTranslationLinks
import com.example.ai.LyricsDisplayMode
import com.example.ai.LyricsTranslationLanguage
import com.example.ai.LyricsTranslationUiState
import com.example.ai.TranslationEngine
import com.example.data.repository.AudioTagParsers
import com.example.data.repository.LrcLibLyricsFetcher
import com.example.data.repository.OnlineLyricsCandidate
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.FolderGroup
import com.example.model.LyricsData
import com.example.model.SdCardFolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playback.PlaybackPositionState
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    track: AudioTrack,
    lyrics: LyricsData?,
    isLoading: Boolean,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    onPickLrcFile: () -> Unit,
    onDismiss: () -> Unit,
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
    durationMs: Long = track.durationMs,
    isPlaying: Boolean = false,
    onTogglePlayPause: (() -> Unit)? = null,
    dynamicArtworkColorsEnabled: Boolean = true,
    dynamicAccentColor: Color? = null,
    onAdjustOffset: ((deltaMs: Long) -> Unit)? = null,
    onResetOffset: (() -> Unit)? = null,
    onExportSaveLrc: ((includeTranslation: Boolean) -> Unit)? = null,
    onExportShareLrc: ((includeTranslation: Boolean) -> Unit)? = null,
    livePositionFlow: StateFlow<PlaybackPositionState>? = null,
    onShareSnippet: ((includeTranslation: Boolean) -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isLossless = track.format == AudioFormat.FLAC || track.format == AudioFormat.WAV
    val baseAccent = if (isLossless) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    var extractedDynamicColor by remember(track.id) { mutableStateOf(dynamicAccentColor) }
    LaunchedEffect(track.id, track.albumArtUri, dynamicArtworkColorsEnabled, dynamicAccentColor, baseAccent) {
        extractedDynamicColor = when {
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
            extractedDynamicColor ?: dynamicAccentColor ?: baseAccent
        } else {
            baseAccent
        },
        animationSpec = tween(durationMillis = 550),
        label = "lyrics_accent_color"
    )

    val sheetBaseBg = if (dynamicArtworkColorsEnabled) {
        lerp(Color(0xFF0C0E13), accentColor, 0.20f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val sheetTopGlow = if (dynamicArtworkColorsEnabled) {
        lerp(Color(0xFF0C0E13), accentColor, 0.36f)
    } else {
        lerp(MaterialTheme.colorScheme.background, accentColor, 0.14f)
    }
    val sheetBottomBg = if (dynamicArtworkColorsEnabled) {
        lerp(Color(0xFF090B10), accentColor, 0.12f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val panelSurfaceBg = if (dynamicArtworkColorsEnabled) {
        lerp(Color(0xFF151923), accentColor, 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val menuSurfaceBg = if (dynamicArtworkColorsEnabled) {
        lerp(Color(0xFF171C26), accentColor, 0.20f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val resultCardBg = if (dynamicArtworkColorsEnabled) {
        lerp(Color(0xFF10131B), accentColor, 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val primaryFg = if (dynamicArtworkColorsEnabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val secondaryFg = if (dynamicArtworkColorsEnabled) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val brightAccentText = if (dynamicArtworkColorsEnabled) {
        lerp(accentColor, Color.White, 0.28f)
    } else {
        ThemePalette.ensureMinimumContrast(accentColor, sheetBaseBg, minRatio = 3.5f)
    }
    val onAccentContent = ThemePalette.bestContrastingTextColor(accentColor)

    var showOnlineSearch by remember(track.id) { mutableStateOf(false) }
    var onlineQuery by remember(track.id) {
        mutableStateOf(LrcLibLyricsFetcher.buildDefaultSearchQuery(track.title, track.artist))
    }
    var isSearchingOnline by remember(track.id) { mutableStateOf(false) }
    var onlineResults by remember(track.id) { mutableStateOf<List<OnlineLyricsCandidate>>(emptyList()) }
    var onlineSearchError by remember(track.id) { mutableStateOf<String?>(null) }
    var hasSearchedOnce by remember(track.id) { mutableStateOf(false) }

    var selectedTargetLanguage by remember(track.id) { mutableStateOf(LyricsTranslationLanguage.SPANISH) }
    var showTranslateMenu by remember(track.id) { mutableStateOf(false) }
    var showManageMenu by remember(track.id) { mutableStateOf(false) }
    var showManualTranslationEditor by remember(track.id) { mutableStateOf(false) }
    var manualTranslationDraft by remember(track.id) { mutableStateOf("") }
    var displayMode by remember(track.id) { mutableStateOf(LyricsDisplayMode.BILINGUAL) }
    var showExportDialog by remember(track.id) { mutableStateOf(false) }
    var exportIncludeTranslation by remember(track.id) { mutableStateOf(false) }

    var isAutoScrollPaused by remember(track.id) { mutableStateOf(false) }
    var isDraggingLyrics by remember(track.id) { mutableStateOf(false) }
    var isProgrammaticScrolling by remember(track.id) { mutableStateOf(false) }
    var manualInteractionNonce by remember(track.id) { mutableIntStateOf(0) }

    val hasLoadedLyrics = !isLoading && lyrics != null && lyrics.lines.isNotEmpty()
    val hasNativeBilingual = lyrics?.hasEmbeddedTranslation == true
    val activeTranslationSuccess = (translationState as? LyricsTranslationUiState.Success)
        ?.takeIf { it.trackId == track.id && it.translatedLyrics.lines.size == lyrics?.lines?.size }
    val hasAnyTranslation = hasNativeBilingual || activeTranslationSuccess != null
    val isTranslating = translationState is LyricsTranslationUiState.Loading
    val translationError = translationState as? LyricsTranslationUiState.Error
    val isPersistedLyrics = lyrics?.sourceLabel == AudioTagParsers.SAVED_LRC_SOURCE_LABEL ||
        lyrics?.sourceLabel == AudioTagParsers.ONLINE_LRC_SOURCE_LABEL

    LaunchedEffect(activeTranslationSuccess?.language) {
        if (activeTranslationSuccess != null) {
            selectedTargetLanguage = activeTranslationSuccess.language
            if (displayMode == LyricsDisplayMode.ORIGINAL) {
                displayMode = LyricsDisplayMode.BILINGUAL
            }
        }
    }

    val triggerOnlineSearch: (String?) -> Unit = { overrideQuery ->
        val searcher = onSearchOnlineLyrics
        if (searcher != null) {
            showOnlineSearch = true
            isSearchingOnline = true
            onlineSearchError = null
            coroutineScope.launch {
                val res = searcher(overrideQuery)
                isSearchingOnline = false
                hasSearchedOnce = true
                res.onSuccess { list ->
                    onlineResults = list
                    if (list.isEmpty()) {
                        onlineSearchError = "No se encontraron coincidencias en LRCLIB. Prueba editar el texto o buscar en Lyricsify."
                    }
                }.onFailure {
                    onlineResults = emptyList()
                    onlineSearchError = "No se pudo conectar con LRCLIB. Revisa tu conexión o busca en Lyricsify."
                }
            }
        }
    }

    val staticPositionMs = if (livePositionFlow == null) positionMs else 0L
    var activeLineIndex by remember(track.id, lyrics, staticPositionMs) {
        val initPos = if (livePositionFlow == null) positionMs else livePositionFlow.value.positionMs
        val initEffective = (initPos + (lyrics?.offsetMs ?: 0L)).coerceAtLeast(0L)
        mutableIntStateOf(computeActiveLyricLineIndex(lyrics, initEffective))
    }
    LaunchedEffect(livePositionFlow, lyrics) {
        if (livePositionFlow == null) return@LaunchedEffect
        val offset = lyrics?.offsetMs ?: 0L
        livePositionFlow.collect { pos ->
            val nextIdx = computeActiveLyricLineIndex(
                lyrics = lyrics,
                effectivePositionMs = (pos.positionMs + offset).coerceAtLeast(0L)
            )
            if (nextIdx != activeLineIndex) {
                activeLineIndex = nextIdx
            }
        }
    }

    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isDraggingLyrics = true
                    isAutoScrollPaused = true
                    manualInteractionNonce++
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    isDraggingLyrics = false
                    manualInteractionNonce++
                }
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isProgrammaticScrolling) {
            isAutoScrollPaused = true
            manualInteractionNonce++
        }
    }

    LaunchedEffect(isAutoScrollPaused, manualInteractionNonce, listState.isScrollInProgress, isDraggingLyrics) {
        if (isAutoScrollPaused && !listState.isScrollInProgress && !isDraggingLyrics) {
            delay(4_500L)
            isAutoScrollPaused = false
        }
    }

    LaunchedEffect(activeLineIndex, isAutoScrollPaused) {
        if (activeLineIndex >= 0 && !isAutoScrollPaused) {
            isProgrammaticScrolling = true
            try {
                runCatching {
                    listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
                }
            } finally {
                isProgrammaticScrolling = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(sheetTopGlow, sheetBaseBg, sheetBottomBg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("lyrics_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Clean Spotify-style full-screen header: collapse arrow on left, centered bold title + artist
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cerrar letras",
                        tint = primaryFg,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryFg,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasLoadedLyrics) {
                            track.artist.ifBlank { lyrics?.sourceLabel.orEmpty() }
                        } else {
                            track.artist.ifBlank { lyrics?.sourceLabel ?: "Letras y sincronización (.lrc)" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryFg,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onShareSnippet != null) {
                    IconButton(
                        onClick = {
                            val includeTrans = hasAnyTranslation && displayMode != LyricsDisplayMode.ORIGINAL
                            onShareSnippet(includeTrans)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lyrics_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Compartir letra o clip",
                            tint = primaryFg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Show top action buttons ONLY when no lyrics are loaded yet (clean view once lyrics load)
            if (!hasLoadedLyrics && !isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_empty_top_actions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSearchOnlineLyrics != null) {
                        FilledTonalButton(
                            onClick = {
                                if (showOnlineSearch && hasSearchedOnce) {
                                    showOnlineSearch = false
                                } else {
                                    triggerOnlineSearch(null)
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accentColor.copy(alpha = 0.24f),
                                contentColor = primaryFg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_online_lyrics_button")
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = brightAccentText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showOnlineSearch) "Ocultar búsqueda" else "Descargar letra")
                        }
                    }
                    OutlinedButton(
                        onClick = onPickLrcFile,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryFg),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_lrc_button")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = brightAccentText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Archivo .lrc")
                    }
                }
            }

            if (isTranslating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_translation_loading"),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.22f)
                )
            }

            if (translationError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_translation_error")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = translationError.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (lyrics != null && onTranslateLyrics != null && translationError.engine == TranslationEngine.DEEPL) {
                                TextButton(
                                    onClick = {
                                        onSelectEngine?.invoke(TranslationEngine.GOOGLE)
                                        onTranslateLyrics(lyrics, translationError.language, TranslationEngine.GOOGLE)
                                    },
                                    modifier = Modifier.testTag("error_switch_to_google_button")
                                ) {
                                    Text("Usar Google")
                                }
                            }
                            if (onSaveManualTranslation != null) {
                                TextButton(
                                    onClick = { showManualTranslationEditor = true },
                                    modifier = Modifier.testTag("error_paste_translation_button")
                                ) {
                                    Text("Pegar traducción")
                                }
                            }
                            if (lyrics != null && onTranslateLyrics != null) {
                                TextButton(
                                    onClick = {
                                        onTranslateLyrics(lyrics, translationError.language, translationError.engine)
                                    },
                                    modifier = Modifier.testTag("retry_translation_button")
                                ) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showManualTranslationEditor && onSaveManualTranslation != null && lyrics != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = panelSurfaceBg,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_translation_editor_panel")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Traducción humana / edición offline",
                                style = MaterialTheme.typography.titleSmall,
                                color = brightAccentText
                            )
                            IconButton(
                                onClick = { showManualTranslationEditor = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cerrar editor", tint = primaryFg.copy(alpha = 0.8f))
                            }
                        }
                        Text(
                            text = "Pega una traducción línea por línea (o archivo .lrc) y se sincronizará y guardará en tu dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryFg
                        )

                        if (onOpenHumanTranslationSite != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenHumanTranslationSite(
                                            HumanLyricsTranslationLinks.buildLetrasSearchUrl(track.title, track.artist),
                                            "Letras.com"
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryFg),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_letras_com_button")
                                ) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = brightAccentText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Letras.com", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onOpenHumanTranslationSite(
                                            HumanLyricsTranslationLinks.buildLyricsTranslateSearchUrl(track.title, track.artist),
                                            "LyricsTranslate"
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryFg),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_lyricstranslate_button")
                                ) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = brightAccentText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("LyricsTranslate", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = manualTranslationDraft,
                            onValueChange = { manualTranslationDraft = it },
                            label = { Text("Traducción (${selectedTargetLanguage.displayName})") },
                            placeholder = { Text("Pega aquí la letra traducida, una línea por verso…") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = primaryFg,
                                unfocusedTextColor = primaryFg,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = accentColor.copy(alpha = 0.35f),
                                focusedLabelColor = brightAccentText,
                                unfocusedLabelColor = secondaryFg,
                                cursorColor = accentColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_translation_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onClearSavedTranslation != null && activeTranslationSuccess != null) {
                                TextButton(
                                    onClick = {
                                        onClearSavedTranslation()
                                        manualTranslationDraft = ""
                                        showManualTranslationEditor = false
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = brightAccentText),
                                    modifier = Modifier.testTag("clear_saved_translation_button")
                                ) {
                                    Text("Eliminar")
                                }
                            }
                            Button(
                                onClick = {
                                    lyrics?.let { currentLyrics ->
                                        onSaveManualTranslation?.invoke(
                                            currentLyrics,
                                            manualTranslationDraft,
                                            selectedTargetLanguage
                                        )
                                    }
                                    showManualTranslationEditor = false
                                },
                                enabled = manualTranslationDraft.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = onAccentContent
                                ),
                                modifier = Modifier.testTag("save_manual_translation_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar offline")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showOnlineSearch) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = panelSurfaceBg,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("online_lyrics_search_panel")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = onlineQuery,
                                onValueChange = { onlineQuery = it },
                                label = { Text("Artista y canción") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = primaryFg,
                                    unfocusedTextColor = primaryFg,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = accentColor.copy(alpha = 0.35f),
                                    focusedLabelColor = brightAccentText,
                                    unfocusedLabelColor = secondaryFg,
                                    cursorColor = accentColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("online_lyrics_search_input")
                            )
                            IconButton(
                                onClick = { triggerOnlineSearch(onlineQuery) },
                                enabled = onlineQuery.isNotBlank() && !isSearchingOnline,
                                modifier = Modifier.testTag("online_lyrics_search_submit")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Buscar letra en línea",
                                    tint = brightAccentText
                                )
                            }
                            IconButton(
                                onClick = { showOnlineSearch = false },
                                modifier = Modifier.testTag("close_online_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Ocultar búsqueda",
                                    tint = primaryFg.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (isSearchingOnline) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = accentColor,
                                trackColor = accentColor.copy(alpha = 0.22f)
                            )
                        }

                        if (onlineSearchError != null) {
                            Text(
                                text = onlineSearchError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryFg
                            )
                        }

                        if (onlineResults.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(onlineResults.take(10), key = { _, item -> item.id }) { _, candidate ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = resultCardBg,
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val saver = onSaveOnlineLyrics ?: return@clickable
                                                coroutineScope.launch {
                                                    saver(candidate)
                                                    showOnlineSearch = false
                                                }
                                            }
                                            .testTag("online_lyrics_result_${candidate.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = candidate.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = primaryFg,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val metaLine = buildList {
                                                    add(candidate.artist)
                                                    if (candidate.album.isNotBlank()) add(candidate.album)
                                                    candidate.durationSec?.let { add(formatDuration(it * 1000L)) }
                                                }.joinToString(" · ")
                                                Text(
                                                    text = metaLine,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = secondaryFg,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (candidate.isSynced) "Sincronizada (.lrc)" else "Solo texto",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (candidate.isSynced) brightAccentText else secondaryFg,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = "Guardar letra",
                                                tint = brightAccentText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (onOpenLyricsify != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "¿Prefieres Lyricsify?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = secondaryFg
                                )
                                TextButton(
                                    onClick = { onOpenLyricsify(onlineQuery.ifBlank { track.title }) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = brightAccentText),
                                    modifier = Modifier.testTag("open_lyricsify_browser_button")
                                ) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buscar en Lyricsify")
                                }
                            }
                        }
                    }
                }
            }

            // Main Lyrics Display Area (fills nearly the entire view cleanly with high contrast)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = brightAccentText)
                        }
                    }

                    lyrics == null || lyrics.lines.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No se encontró letra para esta canción",
                                style = MaterialTheme.typography.titleSmall,
                                color = primaryFg,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Davoo Player puede descargar letras .lrc directamente desde LRCLIB, abrir Lyricsify en tu navegador o cargar un archivo .lrc local.",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryFg,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        val translatedLines = activeTranslationSuccess?.translatedLyrics?.lines
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("spotify_lyrics_list"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = lyrics.lines,
                                key = { index, line -> "${index}_${line.timestampMs ?: -1L}_${line.isInstrumental}" },
                                contentType = { _, line -> if (line.isInstrumental) "instrumental_note" else "lyric_line" }
                            ) { index, line ->
                                val isActive = index == activeLineIndex
                                val lineTs = line.timestampMs
                                val clickableMod = if (lineTs != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            isAutoScrollPaused = false
                                            onSeekTo((lineTs - lyrics.offsetMs).coerceAtLeast(0L))
                                        }
                                } else Modifier

                                if (line.isInstrumental) {
                                    InstrumentalLyricNoteRow(
                                        index = index,
                                        isActive = isActive,
                                        isPlaying = isPlaying,
                                        isSynced = lyrics.isSynced,
                                        accentTint = brightAccentText,
                                        activeTint = primaryFg,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(clickableMod)
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                } else {
                                    val resolvedTranslatedText = translatedLines?.getOrNull(index)?.text
                                        ?: line.secondaryText
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(clickableMod)
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val primaryText = when {
                                            !resolvedTranslatedText.isNullOrBlank() && displayMode == LyricsDisplayMode.TRANSLATED ->
                                                resolvedTranslatedText
                                            else -> line.text
                                        }
                                        Text(
                                            text = primaryText,
                                            style = if (isActive) {
                                                MaterialTheme.typography.titleLarge
                                            } else {
                                                MaterialTheme.typography.titleMedium
                                            },
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) {
                                                primaryFg
                                            } else {
                                                primaryFg.copy(alpha = if (lyrics.isSynced) 0.44f else 0.86f)
                                            }
                                        )
                                        if (!resolvedTranslatedText.isNullOrBlank() &&
                                            displayMode == LyricsDisplayMode.BILINGUAL &&
                                            !resolvedTranslatedText.equals(line.text, ignoreCase = true)
                                        ) {
                                            Text(
                                                text = resolvedTranslatedText,
                                                style = if (isActive) {
                                                    MaterialTheme.typography.bodyLarge
                                                } else {
                                                    MaterialTheme.typography.bodyMedium
                                                },
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isActive) {
                                                    brightAccentText
                                                } else {
                                                    brightAccentText.copy(alpha = if (lyrics.isSynced) 0.45f else 0.72f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating "Sincronizar" pill while user is manually browsing lyrics
                this@Column.AnimatedVisibility(
                    visible = hasLoadedLyrics && lyrics?.isSynced == true && isAutoScrollPaused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            isAutoScrollPaused = false
                            if (activeLineIndex >= 0) {
                                coroutineScope.launch {
                                    isProgrammaticScrolling = true
                                    try {
                                        runCatching {
                                            listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
                                        }
                                    } finally {
                                        isProgrammaticScrolling = false
                                    }
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor,
                            contentColor = onAccentContent
                        ),
                        modifier = Modifier.testTag("lyrics_sync_resume_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sincronizar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Minimalist Spotify-style Bottom Corner Bar: ONLY 2 discrete corner icons directly below lyrics
            if (hasLoadedLyrics) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spotify_lyrics_bottom_bar"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bottom-Left: Compact Translate Menu Icon (文A)
                    Box {
                        val isTranslationShown = hasAnyTranslation && displayMode != LyricsDisplayMode.ORIGINAL
                        Surface(
                            onClick = {
                                val canTranslateOrToggle = onTranslateLyrics != null ||
                                    onSaveManualTranslation != null ||
                                    hasAnyTranslation
                                if (canTranslateOrToggle) {
                                    showTranslateMenu = true
                                }
                            },
                            shape = CircleShape,
                            color = if (isTranslationShown) {
                                accentColor.copy(alpha = 0.24f)
                            } else {
                                accentColor.copy(alpha = 0.12f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isTranslationShown) accentColor.copy(alpha = 0.55f) else accentColor.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lyrics_translate_menu_button")
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = "Traducir o cambiar idioma de letra",
                                    tint = if (isTranslationShown) {
                                        brightAccentText
                                    } else {
                                        primaryFg.copy(alpha = 0.9f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTranslateMenu,
                            onDismissRequest = { showTranslateMenu = false },
                            containerColor = menuSurfaceBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                            modifier = Modifier
                                .widthIn(min = 250.dp, max = 310.dp)
                                .testTag("lyrics_translate_floating_menu")
                        ) {
                            if (onTranslateLyrics != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isTranslating) {
                                                "Traduciendo con ${selectedEngine.shortLabel}…"
                                            } else if (hasAnyTranslation) {
                                                "Volver a traducir (${selectedEngine.shortLabel} · ${selectedTargetLanguage.displayName})"
                                            } else {
                                                "Traducir letra (${selectedEngine.shortLabel} · ${selectedTargetLanguage.displayName})"
                                            },
                                            color = primaryFg,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Translate, contentDescription = null, tint = brightAccentText)
                                    },
                                    enabled = !isTranslating,
                                    onClick = {
                                        showTranslateMenu = false
                                        onTranslateLyrics(lyrics, selectedTargetLanguage, selectedEngine)
                                    },
                                    modifier = Modifier.testTag("translate_lyrics_button")
                                )
                            }

                            if (hasAnyTranslation) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Vista de letra",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = secondaryFg
                                        )
                                        if (hasNativeBilingual || activeTranslationSuccess?.isSavedOffline == true) {
                                            Icon(
                                                imageVector = Icons.Rounded.OfflinePin,
                                                contentDescription = "Disponible sin conexión",
                                                tint = brightAccentText,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .testTag("translation_offline_badge")
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .width(268.dp)
                                            .horizontalScroll(rememberScrollState())
                                            .testTag("lyrics_display_mode_row"),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LyricsDisplayMode.entries.forEach { mode ->
                                            val isModeSelected = displayMode == mode
                                            FilterChip(
                                                selected = isModeSelected,
                                                onClick = { displayMode = mode },
                                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = primaryFg.copy(alpha = 0.06f),
                                                    labelColor = primaryFg.copy(alpha = 0.85f),
                                                    selectedContainerColor = accentColor.copy(alpha = 0.26f),
                                                    selectedLabelColor = brightAccentText
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isModeSelected,
                                                    borderColor = primaryFg.copy(alpha = 0.16f),
                                                    selectedBorderColor = accentColor.copy(alpha = 0.65f)
                                                ),
                                                modifier = Modifier.testTag("lyrics_mode_${mode.name}")
                                            )
                                        }
                                    }
                                }
                            }

                            if (onTranslateLyrics != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = accentColor.copy(alpha = 0.18f)
                                )
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Motor de traducción",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = secondaryFg
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("lyrics_engine_selector_row"),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TranslationEngine.entries.forEach { engine ->
                                            val isSelected = selectedEngine == engine
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSelectEngine?.invoke(engine) },
                                                label = { Text(engine.shortLabel, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = primaryFg.copy(alpha = 0.06f),
                                                    labelColor = primaryFg.copy(alpha = 0.85f),
                                                    selectedContainerColor = accentColor.copy(alpha = 0.26f),
                                                    selectedLabelColor = brightAccentText
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isSelected,
                                                    borderColor = primaryFg.copy(alpha = 0.16f),
                                                    selectedBorderColor = accentColor.copy(alpha = 0.65f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("lyrics_engine_${engine.name.lowercase()}")
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Idioma destino",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = secondaryFg
                                    )
                                    Row(
                                        modifier = Modifier
                                            .width(268.dp)
                                            .horizontalScroll(rememberScrollState())
                                            .testTag("translation_language_list"),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LyricsTranslationLanguage.entries.forEach { language ->
                                            val isSelected = language == selectedTargetLanguage
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedTargetLanguage = language
                                                    showTranslateMenu = false
                                                    onTranslateLyrics.invoke(lyrics, language, selectedEngine)
                                                },
                                                label = { Text(language.displayName, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = primaryFg.copy(alpha = 0.06f),
                                                    labelColor = primaryFg.copy(alpha = 0.85f),
                                                    selectedContainerColor = accentColor.copy(alpha = 0.26f),
                                                    selectedLabelColor = brightAccentText
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isSelected,
                                                    borderColor = primaryFg.copy(alpha = 0.16f),
                                                    selectedBorderColor = accentColor.copy(alpha = 0.65f)
                                                ),
                                                modifier = Modifier.testTag("translation_lang_${language.code}")
                                            )
                                        }
                                    }
                                }
                            }

                            if (onSaveManualTranslation != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = accentColor.copy(alpha = 0.18f)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Editar / pegar traducción offline",
                                            color = primaryFg
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.EditNote, contentDescription = null, tint = brightAccentText)
                                    },
                                    onClick = {
                                        if (manualTranslationDraft.isBlank()) {
                                            manualTranslationDraft = activeTranslationSuccess?.translatedLyrics
                                                ?.let { AudioTagParsers.serializeTranslationLines(it) }
                                                ?: lyrics.lines.mapNotNull { it.secondaryText }
                                                    .takeIf { it.isNotEmpty() }
                                                    ?.joinToString("\n")
                                                ?: ""
                                        }
                                        showTranslateMenu = false
                                        showManualTranslationEditor = true
                                    },
                                    modifier = Modifier.testTag("edit_translation_button")
                                )
                            }
                        }
                    }

                    // Subtle centered translation status indicator only when translated
                    if (hasAnyTranslation && displayMode != LyricsDisplayMode.ORIGINAL) {
                        Text(
                            text = "${selectedTargetLanguage.code.uppercase()} · ${displayMode.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = brightAccentText
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Bottom-Right: Compact Download / File Management Icon
                    Box {
                        Surface(
                            onClick = { showManageMenu = true },
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lyrics_manage_menu_button")
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = "Opciones de descarga y archivo .lrc",
                                    tint = primaryFg.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showManageMenu,
                            onDismissRequest = { showManageMenu = false },
                            containerColor = menuSurfaceBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                            modifier = Modifier.testTag("lyrics_manage_floating_menu")
                        ) {
                            if (onSearchOnlineLyrics != null) {
                                DropdownMenuItem(
                                    text = { Text("Descargar nueva letra (LRCLIB)", color = primaryFg) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = brightAccentText)
                                    },
                                    onClick = {
                                        showManageMenu = false
                                        triggerOnlineSearch(null)
                                    },
                                    modifier = Modifier.testTag("menu_download_online_lyrics_button")
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Seleccionar archivo .lrc local", color = primaryFg) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = brightAccentText)
                                },
                                onClick = {
                                    showManageMenu = false
                                    onPickLrcFile()
                                },
                                modifier = Modifier.testTag("menu_load_lrc_button")
                            )
                            if (onOpenLyricsify != null) {
                                DropdownMenuItem(
                                    text = { Text("Buscar en Lyricsify", color = primaryFg) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = primaryFg.copy(alpha = 0.85f))
                                    },
                                    onClick = {
                                        showManageMenu = false
                                        onOpenLyricsify(onlineQuery.ifBlank { track.title })
                                    },
                                    modifier = Modifier.testTag("menu_open_lyricsify_button")
                                )
                            }
                            if (lyrics?.isSynced == true && onAdjustOffset != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = accentColor.copy(alpha = 0.18f)
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("lyrics_offset_controls"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Timer,
                                            contentDescription = null,
                                            tint = brightAccentText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Sincronización: ${formatLyricsOffsetLabel(lyrics.offsetMs)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primaryFg,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { onAdjustOffset(-500L) },
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryFg),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("lyrics_offset_minus_button")
                                        ) {
                                            Text("-0.5s", style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(
                                            onClick = { onResetOffset?.invoke() },
                                            enabled = lyrics.offsetMs != 0L,
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = brightAccentText),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("lyrics_offset_reset_button")
                                        ) {
                                            Text("0.0s", style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(
                                            onClick = { onAdjustOffset(500L) },
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryFg),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("lyrics_offset_plus_button")
                                        ) {
                                            Text("+0.5s", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                            if (hasLoadedLyrics && (onExportSaveLrc != null || onExportShareLrc != null)) {
                                DropdownMenuItem(
                                    text = { Text("Exportar / Compartir .lrc", color = primaryFg) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Share, contentDescription = null, tint = brightAccentText)
                                    },
                                    onClick = {
                                        showManageMenu = false
                                        exportIncludeTranslation = hasAnyTranslation && displayMode != LyricsDisplayMode.ORIGINAL
                                        showExportDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_export_lrc_button")
                                )
                            }
                            if (onClearSavedLyrics != null && isPersistedLyrics) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = accentColor.copy(alpha = 0.18f)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Eliminar letra actual",
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
                                        showManageMenu = false
                                        onClearSavedLyrics()
                                    },
                                    modifier = Modifier.testTag("clear_saved_lrc_button")
                                )
                            }
                        }
                    }
                }
            }

            // Spotify-style Playback Progress Bar right below the two corner icons (isolated recomposition scope)
            LyricsScrubberSection(
                trackId = track.id,
                fallbackPositionMs = positionMs,
                fallbackDurationMs = durationMs.coerceAtLeast(track.durationMs),
                livePositionFlow = livePositionFlow,
                isPlaying = isPlaying,
                accentColor = accentColor,
                onAccentContent = onAccentContent,
                secondaryFg = secondaryFg,
                onSeekTo = onSeekTo,
                onTogglePlayPause = onTogglePlayPause
            )
        }
    }

    if (showExportDialog && lyrics != null) {
        val suggestedName = remember(track.id) {
            AudioTagParsers.buildSafeLrcFileName(track.artist, track.title)
        }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar letra .lrc") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Archivo: $suggestedName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lyrics.offsetMs != 0L) {
                        Text(
                            text = "Se incluirá el ajuste de sincronización (${formatLyricsOffsetLabel(lyrics.offsetMs)}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (hasAnyTranslation) {
                        Text(
                            text = "Contenido a exportar",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = !exportIncludeTranslation,
                                onClick = { exportIncludeTranslation = false },
                                label = { Text("Original") },
                                modifier = Modifier.testTag("export_mode_original")
                            )
                            FilterChip(
                                selected = exportIncludeTranslation,
                                onClick = { exportIncludeTranslation = true },
                                label = { Text("Bilingüe") },
                                modifier = Modifier.testTag("export_mode_bilingual")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onExportShareLrc != null) {
                        TextButton(
                            onClick = {
                                showExportDialog = false
                                onExportShareLrc(exportIncludeTranslation)
                            },
                            modifier = Modifier.testTag("export_share_lrc_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartir")
                        }
                    }
                    if (onExportSaveLrc != null) {
                        Button(
                            onClick = {
                                showExportDialog = false
                                onExportSaveLrc(exportIncludeTranslation)
                            },
                            modifier = Modifier.testTag("export_save_lrc_button")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar .lrc")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancelar")
                }
            },
            modifier = Modifier.testTag("lyrics_export_dialog")
        )
    }
}

private fun formatLyricsOffsetLabel(offsetMs: Long): String {
    if (offsetMs == 0L) return "0.0 s"
    val seconds = offsetMs / 1000.0
    val sign = if (offsetMs > 0) "+" else ""
    return String.format("%s%.1f s", sign, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorBottomSheet(
    track: AudioTrack,
    pickedArtworkUri: String?,
    onPickCoverImage: () -> Unit,
    onClearCustomCover: () -> Unit,
    onSave: (
        fileNameBase: String,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        customArtworkUri: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fileParts = remember(track.id, track.filePath, track.contentUri) {
        com.example.data.repository.AudioTagWriter.splitFileName(track, context)
    }
    var fileNameBase by remember(track.id) { mutableStateOf(fileParts.baseName) }
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var albumArtist by remember(track.id) { mutableStateOf(track.albumArtist) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var yearStr by remember(track.id) { mutableStateOf(if (track.year > 0) track.year.toString() else "") }
    var trackNumStr by remember(track.id) {
        mutableStateOf(if (track.trackNumber > 0) track.trackNumber.toString() else "")
    }
    val effectiveArtwork = pickedArtworkUri ?: track.albumArtUri

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
                .testTag("tag_editor_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Editar etiquetas y archivo", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar editor")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AudioAlbumArtThumbnail(
                    audioUri = track.contentUri,
                    albumArtUri = effectiveArtwork,
                    contentDescription = album,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(72.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onPickCoverImage,
                        modifier = Modifier.testTag("pick_cover_button")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar carátula")
                    }
                    if (pickedArtworkUri != null) {
                        TextButton(onClick = onClearCustomCover) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Usar carátula original")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = fileNameBase,
                onValueChange = { raw ->
                    fileNameBase = raw.replace('/', '_').replace('\\', '_')
                },
                label = { Text("Nombre físico del archivo") },
                suffix = {
                    Text(
                        text = fileParts.extensionWithDot,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingText = {
                    Text("Se conservará automáticamente la extensión ${fileParts.extensionWithDot}")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tag_filename_input")
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tag_title_input")
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tag_artist_input")
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Álbum") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tag_album_input")
            )
            OutlinedTextField(
                value = albumArtist,
                onValueChange = { albumArtist = it },
                label = { Text("Artista del álbum") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Género") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = yearStr,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) yearStr = it },
                    label = { Text("Año") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f)
                )
                OutlinedTextField(
                    value = trackNumStr,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) trackNumStr = it },
                    label = { Text("N.º") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f)
                )
            }

            Button(
                onClick = {
                    onSave(
                        fileNameBase.ifBlank { fileParts.baseName },
                        title.ifBlank { track.title },
                        artist.ifBlank { track.artist },
                        album.ifBlank { track.album },
                        albumArtist,
                        genre,
                        yearStr.toIntOrNull() ?: 0,
                        trackNumStr.toIntOrNull() ?: 0,
                        effectiveArtwork
                    )
                    onDismiss()
                },
                enabled = title.isNotBlank() && fileNameBase.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_tags_button")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar cambios")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersExclusionSheet(
    folders: List<FolderGroup>,
    onToggleExclude: (folderName: String, currentlyExcluded: Boolean) -> Unit,
    onDismiss: () -> Unit,
    sdCardFolders: List<SdCardFolder> = emptyList(),
    removingSdFolders: Map<String, String> = emptyMap(),
    onAddSdCardFolder: () -> Unit = {},
    onRemoveSdCardFolder: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("folders_exclusion_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Carpetas y Tarjeta SD", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Vincula tu tarjeta SD u oculta carpetas que no quieras ver",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar carpetas")
                }
            }

            // SD Card / SAF folder section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tarjeta SD / Carpetas externas", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Selecciona tu tarjeta SD para leer música directamente sin duplicar archivos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onAddSdCardFolder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_sd_folder_button")
                    ) {
                        Icon(Icons.Rounded.SdCard, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vincular carpeta o tarjeta SD")
                    }

                    removingSdFolders.forEach { (uri, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .testTag("removing_sd_folder_row_$uri"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("removing_sd_folder_indicator_$uri"),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Quitando «$name» en segundo plano…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    sdCardFolders.forEach { sd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sd.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${sd.trackCount} pistas indexadas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onRemoveSdCardFolder(sd.treeUri) },
                                modifier = Modifier.testTag("remove_sd_folder_${sd.displayName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Desvincular ${sd.displayName}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Visibilidad de carpetas detectadas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (folders.isEmpty()) {
                Text(
                    text = "Aún no se han detectado carpetas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(folders) { _, folder ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = if (folder.isExcluded) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (folder.isExcluded) Icons.Rounded.FolderOff else Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = if (folder.isExcluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.folderName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folder.tracks.size} pistas · ${if (folder.isExcluded) "Oculta" else "Visible"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = !folder.isExcluded,
                                    onCheckedChange = {
                                        onToggleExclude(folder.folderName, folder.isExcluded)
                                    },
                                    modifier = Modifier.testTag("folder_toggle_${folder.folderName}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computeActiveLyricLineIndex(
    lyrics: LyricsData?,
    effectivePositionMs: Long
): Int {
    if (lyrics == null || !lyrics.isSynced) return -1
    return lyrics.lines.indexOfLast { line ->
        val ts = line.timestampMs
        ts != null && ts <= effectivePositionMs + 150L
    }
}

@Composable
private fun InstrumentalLyricNoteRow(
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    isSynced: Boolean,
    accentTint: Color,
    activeTint: Color,
    modifier: Modifier = Modifier
) {
    val pulseScale: Float
    val pulseAlpha: Float
    if (isActive && isPlaying) {
        val transition = rememberInfiniteTransition(label = "instrumental_pulse")
        val s by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "note_scale"
        )
        val a by transition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "note_alpha"
        )
        pulseScale = s
        pulseAlpha = a
    } else {
        pulseScale = if (isActive) 1.08f else 1.0f
        pulseAlpha = 1f
    }

    val tint = if (isActive) {
        activeTint
    } else {
        accentTint.copy(alpha = if (isSynced) 0.42f else 0.75f)
    }

    Row(
        modifier = modifier.testTag("lyrics_instrumental_note_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = "Interludio instrumental",
            tint = tint,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
        )
    }
}

@Composable
private fun LyricsScrubberSection(
    trackId: String,
    fallbackPositionMs: Long,
    fallbackDurationMs: Long,
    livePositionFlow: StateFlow<PlaybackPositionState>?,
    isPlaying: Boolean,
    accentColor: Color,
    onAccentContent: Color,
    secondaryFg: Color,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: (() -> Unit)?
) {
    val livePos = livePositionFlow?.collectAsStateWithLifecycle()?.value
    val resolvedPositionMs = livePos?.positionMs ?: fallbackPositionMs
    val totalDurationMs = (livePos?.durationMs?.takeIf { it > 0L } ?: fallbackDurationMs).coerceAtLeast(1L)

    var isScrubbing by remember(trackId) { mutableStateOf(false) }
    var scrubFraction by remember(trackId) { mutableFloatStateOf(0f) }
    val progressFraction = if (isScrubbing) {
        scrubFraction
    } else {
        (resolvedPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lyrics_playback_progress_bar")
    ) {
        Slider(
            value = progressFraction,
            onValueChange = { fraction ->
                isScrubbing = true
                scrubFraction = fraction
            },
            onValueChangeFinished = {
                onSeekTo((scrubFraction * totalDurationMs).toLong())
                isScrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.24f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lyrics_playback_slider")
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayedPos = if (isScrubbing) (scrubFraction * totalDurationMs).toLong() else resolvedPositionMs
            Text(
                text = formatDuration(displayedPos),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryFg
            )
            if (onTogglePlayPause != null) {
                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = accentColor,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("lyrics_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = onAccentContent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Text(
                text = formatDuration(totalDurationMs),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryFg
            )
        }
    }
}

