package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.HumanLyricsTranslationLinks
import com.example.ai.LyricsDisplayMode
import com.example.ai.LyricsTranslationLanguage
import com.example.ai.LyricsTranslationUiState
import com.example.ai.TranslationEngine
import com.example.data.repository.AudioTagParsers
import com.example.data.repository.LrcLibLyricsFetcher
import com.example.data.repository.OnlineLyricsCandidate
import com.example.model.AudioTrack
import com.example.model.FolderGroup
import com.example.model.LyricsData
import com.example.model.SdCardFolder
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan
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
    onOpenHumanTranslationSite: ((url: String, siteName: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showOnlineSearch by remember(track.id) { mutableStateOf(false) }
    var onlineQuery by remember(track.id) {
        mutableStateOf(LrcLibLyricsFetcher.buildDefaultSearchQuery(track.title, track.artist))
    }
    var isSearchingOnline by remember(track.id) { mutableStateOf(false) }
    var onlineResults by remember(track.id) { mutableStateOf<List<OnlineLyricsCandidate>>(emptyList()) }
    var onlineSearchError by remember(track.id) { mutableStateOf<String?>(null) }
    var hasSearchedOnce by remember(track.id) { mutableStateOf(false) }

    var selectedTargetLanguage by remember(track.id) { mutableStateOf(LyricsTranslationLanguage.SPANISH) }
    var showLanguagePicker by remember(track.id) { mutableStateOf(false) }
    var showManualTranslationEditor by remember(track.id) { mutableStateOf(false) }
    var manualTranslationDraft by remember(track.id) { mutableStateOf("") }
    var displayMode by remember(track.id) { mutableStateOf(LyricsDisplayMode.BILINGUAL) }

    val hasNativeBilingual = lyrics?.hasEmbeddedTranslation == true
    val activeTranslationSuccess = (translationState as? LyricsTranslationUiState.Success)
        ?.takeIf { it.trackId == track.id && it.translatedLyrics.lines.size == lyrics?.lines?.size }
    val hasAnyTranslation = hasNativeBilingual || activeTranslationSuccess != null
    val isTranslating = translationState is LyricsTranslationUiState.Loading
    val translationError = translationState as? LyricsTranslationUiState.Error

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

    val activeLineIndex = remember(lyrics, positionMs) {
        if (lyrics == null || !lyrics.isSynced) -1
        else lyrics.lines.indexOfLast { line ->
            val ts = line.timestampMs
            ts != null && ts <= positionMs + 150L
        }
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            runCatching {
                listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("lyrics_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = LosslessCyan)
                    Column {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = lyrics?.sourceLabel ?: "Letras y sincronización (.lrc)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar letras")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_online_lyrics_button")
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showOnlineSearch) "Ocultar búsqueda" else "Descargar letra")
                    }
                }
                OutlinedButton(
                    onClick = onPickLrcFile,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("load_lrc_button")
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Archivo .lrc")
                }
                val isPersistedLyrics = lyrics?.sourceLabel == AudioTagParsers.SAVED_LRC_SOURCE_LABEL ||
                    lyrics?.sourceLabel == AudioTagParsers.ONLINE_LRC_SOURCE_LABEL
                if (onClearSavedLyrics != null && isPersistedLyrics) {
                    IconButton(
                        onClick = onClearSavedLyrics,
                        modifier = Modifier.testTag("clear_saved_lrc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Quitar letra guardada",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val showTranslationControls = lyrics != null &&
                lyrics.lines.isNotEmpty() &&
                !isLoading &&
                (onTranslateLyrics != null || onSaveManualTranslation != null || hasNativeBilingual)

            if (showTranslationControls) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onTranslateLyrics != null || onSaveManualTranslation != null) {
                        if (onTranslateLyrics != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lyrics_engine_selector_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Traductor:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TranslationEngine.entries.forEach { engine ->
                                    val isSelected = selectedEngine == engine
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectEngine?.invoke(engine) },
                                        label = { Text(engine.shortLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = LosslessCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = LosslessCyan
                                        ),
                                        modifier = Modifier.testTag("lyrics_engine_${engine.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onTranslateLyrics != null) {
                                FilledTonalButton(
                                    onClick = {
                                        onTranslateLyrics(lyrics, selectedTargetLanguage, selectedEngine)
                                    },
                                    enabled = !isTranslating,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("translate_lyrics_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Translate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isTranslating) {
                                            "${selectedEngine.shortLabel} (${selectedTargetLanguage.code.uppercase()})..."
                                        } else {
                                            "${selectedEngine.shortLabel} · ${selectedTargetLanguage.displayName}"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showLanguagePicker = !showLanguagePicker },
                                modifier = Modifier.testTag("translation_language_picker_button")
                            ) {
                                Text(selectedTargetLanguage.code.uppercase())
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showLanguagePicker) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Cambiar idioma de traducción",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (onSaveManualTranslation != null) {
                                IconButton(
                                    onClick = {
                                        if (!showManualTranslationEditor && manualTranslationDraft.isBlank()) {
                                            manualTranslationDraft = activeTranslationSuccess?.translatedLyrics
                                                ?.let { AudioTagParsers.serializeTranslationLines(it) }
                                                ?: lyrics.lines.mapNotNull { it.secondaryText }
                                                    .takeIf { it.isNotEmpty() }
                                                    ?.joinToString("\n")
                                                ?: ""
                                        }
                                        showManualTranslationEditor = !showManualTranslationEditor
                                    },
                                    modifier = Modifier.testTag("edit_translation_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EditNote,
                                        contentDescription = "Pegar o editar traducción offline",
                                        tint = if (showManualTranslationEditor) CopperAmber else LosslessCyan
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showLanguagePicker) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("translation_language_list"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(LyricsTranslationLanguage.entries) { language ->
                                val isSelected = language == selectedTargetLanguage
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTargetLanguage = language
                                        showLanguagePicker = false
                                        onTranslateLyrics?.invoke(lyrics, language, selectedEngine)
                                    },
                                    label = { Text(language.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LosslessCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = LosslessCyan
                                    ),
                                    modifier = Modifier.testTag("translation_lang_${language.code}")
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showManualTranslationEditor && onSaveManualTranslation != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, CopperAmber.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_translation_editor_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Traducción humana / edición offline",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CopperAmber
                                )
                                Text(
                                    text = "Pega una traducción línea por línea (o archivo .lrc) y se sincronizará y guardará en tu dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("open_letras_com_button")
                                        ) {
                                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("open_lyricstranslate_button")
                                        ) {
                                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                    maxLines = 5,
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
                                            modifier = Modifier.testTag("clear_saved_translation_button")
                                        ) {
                                            Text("Eliminar")
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            onSaveManualTranslation?.invoke(
                                                lyrics,
                                                manualTranslationDraft,
                                                selectedTargetLanguage
                                            )
                                            showManualTranslationEditor = false
                                        },
                                        enabled = manualTranslationDraft.isNotBlank(),
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

                    if (isTranslating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lyrics_translation_loading"),
                            color = LosslessCyan
                        )
                    }

                    if (translationError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
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
                                    if (onTranslateLyrics != null && translationError.engine == TranslationEngine.DEEPL) {
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
                                    if (onTranslateLyrics != null) {
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

                    if (hasAnyTranslation) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lyrics_display_mode_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(LyricsDisplayMode.entries) { mode ->
                                    FilterChip(
                                        selected = displayMode == mode,
                                        onClick = { displayMode = mode },
                                        label = { Text(mode.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CopperAmber.copy(alpha = 0.2f),
                                            selectedLabelColor = CopperAmber
                                        ),
                                        modifier = Modifier.testTag("lyrics_mode_${mode.name}")
                                    )
                                }
                            }
                            if (hasNativeBilingual || activeTranslationSuccess?.isSavedOffline == true) {
                                Icon(
                                    imageVector = Icons.Rounded.OfflinePin,
                                    contentDescription = "Disponible sin conexión",
                                    tint = LosslessCyan,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(18.dp)
                                        .testTag("translation_offline_badge")
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showOnlineSearch) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, LosslessCyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("online_lyrics_search_panel")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = onlineQuery,
                                onValueChange = { onlineQuery = it },
                                label = { Text("Artista y canción") },
                                singleLine = true,
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
                                    tint = LosslessCyan
                                )
                            }
                        }

                        if (isSearchingOnline) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = LosslessCyan
                            )
                        }

                        if (onlineSearchError != null) {
                            Text(
                                text = onlineSearchError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (onlineResults.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(onlineResults.take(10), key = { _, item -> item.id }) { _, candidate ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
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
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (candidate.isSynced) "Sincronizada (.lrc)" else "Solo texto",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (candidate.isSynced) LosslessCyan else CopperAmber,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = "Guardar letra",
                                                tint = LosslessCyan
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { onOpenLyricsify(onlineQuery.ifBlank { track.title }) },
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                lyrics == null || lyrics.lines.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No se encontró letra para esta canción",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Davoo Player puede descargar letras .lrc directamente desde LRCLIB, abrir Lyricsify en tu navegador o cargar un archivo .lrc local.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    val translatedLines = activeTranslationSuccess?.translatedLyrics?.lines
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == activeLineIndex
                            val resolvedTranslatedText = translatedLines?.getOrNull(index)?.text
                                ?: line.secondaryText
                            val clickableMod = if (line.timestampMs != null) {
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSeekTo(line.timestampMs) }
                            } else Modifier

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(clickableMod)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (line.timestampMs != null) {
                                    Text(
                                        text = formatDuration(line.timestampMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) LosslessCyan
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val primaryText = when {
                                        !resolvedTranslatedText.isNullOrBlank() && displayMode == LyricsDisplayMode.TRANSLATED ->
                                            resolvedTranslatedText
                                        else -> line.text
                                    }
                                    Text(
                                        text = primaryText,
                                        style = if (isActive) MaterialTheme.typography.titleMedium
                                        else MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) LosslessCyan
                                        else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (lyrics.isSynced) 0.78f else 0.94f
                                        )
                                    )
                                    if (!resolvedTranslatedText.isNullOrBlank() &&
                                        displayMode == LyricsDisplayMode.BILINGUAL &&
                                        !resolvedTranslatedText.equals(line.text, ignoreCase = true)
                                    ) {
                                        Text(
                                            text = resolvedTranslatedText,
                                            style = if (isActive) MaterialTheme.typography.bodyMedium
                                            else MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isActive) CopperAmber
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorBottomSheet(
    track: AudioTrack,
    pickedArtworkUri: String?,
    onPickCoverImage: () -> Unit,
    onClearCustomCover: () -> Unit,
    onSave: (
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                Text("Editar etiquetas y carátula", style = MaterialTheme.typography.titleLarge)
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
                enabled = title.isNotBlank(),
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
                border = BorderStroke(1.dp, LosslessCyan.copy(alpha = 0.35f)),
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
                        Icon(Icons.Rounded.SdCard, contentDescription = null, tint = LosslessCyan)
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
                                color = CopperAmber
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
                                tint = CopperAmber,
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
                                BorderStroke(1.dp, CopperAmber.copy(alpha = 0.35f))
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
                                    tint = if (folder.isExcluded) MaterialTheme.colorScheme.onSurfaceVariant else CopperAmber
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
