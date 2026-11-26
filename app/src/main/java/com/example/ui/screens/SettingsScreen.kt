package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale
import kotlin.math.roundToInt
import com.example.BuildConfig
import com.example.ai.DeepSeekTestUiState
import com.example.ai.TranslationEngine
import com.example.model.AppThemeMode
import com.example.model.AppearancePreferences
import com.example.model.AudioSettings
import com.example.model.ThemePreset

@Composable
fun SettingsScreen(
    totalTracksCount: Int,
    isScanning: Boolean,
    excludedFoldersCount: Int,
    sdCardFoldersCount: Int,
    audioSettings: AudioSettings,
    isSleepTimerActive: Boolean,
    preferredTranslationEngine: TranslationEngine,
    onBack: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenFoldersManager: () -> Unit,
    onAddSdCardFolder: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onUpdateAudioSettings: (AudioSettings) -> Unit,
    onSelectTranslationEngine: (TranslationEngine) -> Unit,
    onOpenAboutAndLegal: () -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    appearancePreferences: AppearancePreferences = AppearancePreferences(),
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    onOpenAppearanceCustomization: () -> Unit = {},
    onToggleDynamicArtworkColors: (Boolean) -> Unit = { enabled ->
        onUpdateAudioSettings(audioSettings.copy(dynamicArtworkColors = enabled))
    },
    isBackupBusy: Boolean = false,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    autoCheckUpdatesEnabled: Boolean = true,
    isCheckingUpdates: Boolean = false,
    updateStatusSubtitle: String? = null,
    githubRepoConfigured: Boolean = false,
    onCheckUpdatesNow: () -> Unit = {},
    onToggleAutoCheckUpdates: (Boolean) -> Unit = {},
    onConfigureGithubRepo: () -> Unit = {},
    lastFmUsername: String? = null,
    lastFmHasCredentials: Boolean = false,
    onOpenLastFmProfileOrConnect: () -> Unit = {},
    onConfigureLastFmCredentials: () -> Unit = {},
    onOpenBatchAutoTag: () -> Unit = {},
    onOpenBatchLyrics: () -> Unit = {},
    replayGainCachedCount: Int = 0,
    onOpenReplayGainScanner: () -> Unit = {},
    deepSeekApiKey: String = "",
    deepSeekTestState: DeepSeekTestUiState = DeepSeekTestUiState.Idle,
    onSaveDeepSeekApiKey: (String) -> Unit = {},
    onTestDeepSeekConnection: (String) -> Unit = {},
    focusTranslatorSection: Boolean = false,
    onConsumedTranslatorFocus: () -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val listState = rememberLazyListState()

    LaunchedEffect(focusTranslatorSection) {
        if (focusTranslatorSection) {
            runCatching { listState.animateScrollToItem(3) }
            onConsumedTranslatorFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Clean Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver a Tu Música"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Opciones",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Davoo Player v${BuildConfig.VERSION_NAME} · Audio ligero y abierto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Memoria y Biblioteca
            item(key = "section_storage") {
                SettingsSectionCard(title = "Memoria y biblioteca") {
                    SettingsActionRow(
                        icon = Icons.Rounded.Refresh,
                        iconTint = primaryColor,
                        title = "Actualizar biblioteca",
                        subtitle = if (isScanning) {
                            "Escaneando canciones en memoria…"
                        } else {
                            "$totalTracksCount canciones detectadas en el dispositivo"
                        },
                        onClick = onRefreshLibrary,
                        testTag = "settings_refresh_library_row",
                        trailing = {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.FolderOpen,
                        iconTint = primaryColor,
                        title = "Importar archivos de audio",
                        subtitle = "Añade canciones desde cualquier carpeta del dispositivo",
                        onClick = onImportFiles,
                        testTag = "settings_import_audio_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.SdCard,
                        iconTint = secondaryColor,
                        title = "Vincular carpeta de tarjeta SD",
                        subtitle = if (sdCardFoldersCount > 0) {
                            "$sdCardFoldersCount ${if (sdCardFoldersCount == 1) "carpeta vinculada" else "carpetas vinculadas"}"
                        } else {
                            "Autoriza lectura de música en almacenamiento externo"
                        },
                        onClick = onAddSdCardFolder,
                        testTag = "settings_sd_card_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.FolderOff,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Gestionar carpetas y exclusiones",
                        subtitle = if (excludedFoldersCount > 0) {
                            "$excludedFoldersCount ${if (excludedFoldersCount == 1) "carpeta oculta" else "carpetas ocultas"}"
                        } else {
                            "Oculta audios de WhatsApp, grabaciones o carpetas específicas"
                        },
                        onClick = onOpenFoldersManager,
                        testTag = "settings_folders_manager_row"
                    )
                }
            }

            // 1b. Etiquetas y letras en línea (lotes)
            item(key = "section_enrichment") {
                SettingsSectionCard(title = "Etiquetas y letras (.lrc)") {
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoFixHigh,
                        iconTint = primaryColor,
                        title = "Auto-etiquetar biblioteca (Deezer)",
                        subtitle = "Autocompleta carátulas HD, álbum, año y pista en MP3/FLAC locales",
                        onClick = onOpenBatchAutoTag,
                        testTag = "settings_batch_autotag_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Lyrics,
                        iconTint = secondaryColor,
                        title = "Descargar letras sincronizadas (.lrc)",
                        subtitle = "Busca y guarda letras .lrc por lotes desde LRCLIB",
                        onClick = onOpenBatchLyrics,
                        testTag = "settings_batch_lyrics_row"
                    )
                }
            }

            // 2. Audio y Ecualización (Crossfade, Gapless & ReplayGain live in Ecualizador y DSP; Scanner & Fade-on-Pause live only here in Opciones)
            item(key = "section_audio") {
                SettingsSectionCard(title = "Audio y ecualización") {
                    val eqSummary = buildList {
                        if (audioSettings.eqEnabled) {
                            add("EQ ${audioSettings.activeProfileName ?: audioSettings.eqPreset.label}")
                        } else {
                            add("10 bandas + AutoEq")
                        }
                        if (audioSettings.crossfadeSeconds > 0) add("Crossfade ${audioSettings.crossfadeSeconds}s")
                        if (audioSettings.replayGainEnabled) add("Normalizar volumen")
                        if (audioSettings.gaplessEnabled) add("Gapless")
                    }.joinToString(" · ")
                    SettingsActionRow(
                        icon = Icons.Rounded.Equalizer,
                        iconTint = secondaryColor,
                        title = "Ecualizador y DSP",
                        subtitle = if (audioSettings.eqEnabled) {
                            eqSummary
                        } else {
                            "10 bandas, Preamp, Crossfade, Gapless y Normalizar volumen"
                        },
                        onClick = onOpenEqualizer,
                        testTag = "settings_equalizer_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.GraphicEq,
                        iconTint = primaryColor,
                        title = "Escáner de sonoridad (ReplayGain)",
                        subtitle = if (replayGainCachedCount > 0) {
                            "$replayGainCachedCount canciones medidas localmente (-18 dBFS) para normalización"
                        } else {
                            "Calcula ganancia ReplayGain para MP3/FLAC locales sin etiqueta previa"
                        },
                        onClick = onOpenReplayGainScanner,
                        testTag = "settings_replaygain_scanner_row"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        iconTint = if (audioSettings.fadeOnPauseEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Fade suave al pausar y reanudar",
                        subtitle = "Atenúa suavemente el volumen (~180 ms) en pausas y reanudaciones manuales",
                        checked = audioSettings.fadeOnPauseEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateAudioSettings(audioSettings.copy(fadeOnPauseEnabled = enabled))
                        },
                        testTag = "settings_fade_on_pause_switch"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Bedtime,
                        iconTint = if (isSleepTimerActive) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Temporizador de apagado",
                        subtitle = if (isSleepTimerActive) {
                            "Temporizador activo"
                        } else {
                            "Detener música automáticamente tras unos minutos"
                        },
                        onClick = onOpenSleepTimer,
                        testTag = "settings_sleep_timer_row"
                    )
                }
            }

            // 2b. Preamp ReplayGain (3.5 mm / DAC) — exclusivo en Opciones
            item(key = "section_replaygain_preamp") {
                var draftTaggedPreamp by remember(audioSettings.replayGainPreampDb) {
                    mutableFloatStateOf(audioSettings.replayGainPreampDb.coerceIn(-6f, 12f))
                }
                var draftUntaggedPreamp by remember(audioSettings.replayGainUntaggedPreampDb) {
                    mutableFloatStateOf(audioSettings.replayGainUntaggedPreampDb.coerceIn(-12f, 0f))
                }
                SettingsSectionCard(title = "Preamp ReplayGain (3.5 mm / DAC)") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("settings_replaygain_preamp_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Compensación de sonoridad",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (audioSettings.replayGainEnabled) {
                                        "Compensa volumen base sin sacrificar rango dinámico"
                                    } else {
                                        "Activa Normalizar volumen en Ecualizador y DSP para aplicar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    draftTaggedPreamp = 3.0f
                                    draftUntaggedPreamp = -4.0f
                                    onUpdateAudioSettings(
                                        audioSettings.copy(
                                            replayGainPreampDb = 3.0f,
                                            replayGainUntaggedPreampDb = -4.0f
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("settings_replaygain_preamp_reset_button")
                            ) {
                                Text("Restablecer", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pistas con etiqueta / medidas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", draftTaggedPreamp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor,
                                modifier = Modifier.testTag("settings_replaygain_preamp_value")
                            )
                        }
                        Slider(
                            value = draftTaggedPreamp,
                            onValueChange = { raw ->
                                val stepped = ((raw * 2f).roundToInt() / 2f).coerceIn(-6f, 12f)
                                draftTaggedPreamp = stepped
                            },
                            onValueChangeFinished = {
                                onUpdateAudioSettings(audioSettings.copy(replayGainPreampDb = draftTaggedPreamp))
                            },
                            valueRange = -6f..12f,
                            steps = 35,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_replaygain_preamp_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pistas sin etiqueta ReplayGain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", draftUntaggedPreamp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryColor,
                                modifier = Modifier.testTag("settings_replaygain_untagged_preamp_value")
                            )
                        }
                        Slider(
                            value = draftUntaggedPreamp,
                            onValueChange = { raw ->
                                val stepped = ((raw * 2f).roundToInt() / 2f).coerceIn(-12f, 0f)
                                draftUntaggedPreamp = stepped
                            },
                            onValueChangeFinished = {
                                onUpdateAudioSettings(audioSettings.copy(replayGainUntaggedPreampDb = draftUntaggedPreamp))
                            },
                            valueRange = -12f..0f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = secondaryColor,
                                activeTrackColor = secondaryColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_replaygain_untagged_preamp_slider")
                        )
                    }
                }
            }

            // 3. Apariencia (Tema Oscuro / Claro / Sistema + Personalización manual + Colores dinámicos)
            item(key = "section_personalization") {
                SettingsSectionCard(title = "Apariencia") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (appearancePreferences.themeMode) {
                                    AppThemeMode.DARK -> Icons.Rounded.DarkMode
                                    AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                },
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo de tema",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = appearancePreferences.themeMode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val selected = appearancePreferences.themeMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectThemeMode(mode) },
                                    label = {
                                        Text(
                                            text = mode.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                AppThemeMode.DARK -> Icons.Rounded.DarkMode
                                                AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                                                AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_theme_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    SettingsDivider()
                    val presetDescription = if (appearancePreferences.preset == ThemePreset.CUSTOM) {
                        "Personalizado · 5 colores editables"
                    } else {
                        "Preset: ${appearancePreferences.preset.label}"
                    }
                    SettingsActionRow(
                        icon = Icons.Rounded.ColorLens,
                        iconTint = primaryColor,
                        title = "Personalizar colores y tema",
                        subtitle = "$presetDescription · Presets y selector RGB/Hex",
                        onClick = onOpenAppearanceCustomization,
                        testTag = "settings_customize_colors_row",
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Palette,
                        iconTint = if (appearancePreferences.dynamicArtworkColors) secondaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Usar colores dinámicos de la carátula",
                        subtitle = "Adapta reproductor, letras y mini barra al álbum actual; desactívalo para usar solo tus colores",
                        checked = appearancePreferences.dynamicArtworkColors,
                        onCheckedChange = onToggleDynamicArtworkColors,
                        testTag = "settings_dynamic_colors_switch"
                    )
                }
            }

            // 4. Traductor de Letras
            item(key = "section_translator") {
                SettingsSectionCard(title = "Traductor de letras") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = secondaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Motor de traducción preferido",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = preferredTranslationEngine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TranslationEngine.entries.forEach { engine ->
                                val selected = preferredTranslationEngine == engine
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectTranslationEngine(engine) },
                                    label = {
                                        Text(
                                            text = engine.shortLabel,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_engine_${engine.name.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "Todas las traducciones se guardan sin conexión en tu dispositivo. También puedes cambiar de motor directamente desde el visor de letras.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (preferredTranslationEngine == TranslationEngine.DEEPSEEK) {
                        var deepSeekDraft by remember(deepSeekApiKey) { mutableStateOf(deepSeekApiKey) }
                        var revealKey by rememberSaveable { mutableStateOf(false) }
                        val hasSavedKey = deepSeekApiKey.isNotBlank()
                        val hasUnsavedChanges = deepSeekDraft.trim() != deepSeekApiKey.trim()
                        val isTesting = deepSeekTestState is DeepSeekTestUiState.Testing

                        SettingsDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_deepseek_credentials_panel"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = if (hasSavedKey) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Credenciales API de DeepSeek",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (hasSavedKey) {
                                            "API Key guardada localmente · Excluida de respaldos .json"
                                        } else {
                                            "Ingresa tu API Key de DeepSeek (sk-...) · Solo en este dispositivo"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = deepSeekDraft,
                                onValueChange = { deepSeekDraft = it },
                                label = { Text("API Key de DeepSeek") },
                                placeholder = { Text("sk-...") },
                                singleLine = true,
                                visualTransformation = if (revealKey) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { revealKey = !revealKey },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("settings_deepseek_visibility_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (revealKey) {
                                                Icons.Rounded.VisibilityOff
                                            } else {
                                                Icons.Rounded.Visibility
                                            },
                                            contentDescription = if (revealKey) {
                                                "Ocultar API Key"
                                            } else {
                                                "Mostrar API Key"
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_deepseek_api_key_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onTestDeepSeekConnection(deepSeekDraft) },
                                    enabled = !isTesting && deepSeekDraft.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_deepseek_test_button")
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Probando…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        Text("Probar conexión", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }

                                Button(
                                    onClick = { onSaveDeepSeekApiKey(deepSeekDraft) },
                                    enabled = hasUnsavedChanges || (deepSeekDraft.isBlank() && hasSavedKey),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_deepseek_save_button")
                                ) {
                                    Text(
                                        text = if (deepSeekDraft.isBlank() && hasSavedKey) "Borrar clave" else "Guardar",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            when (deepSeekTestState) {
                                is DeepSeekTestUiState.Success -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("settings_deepseek_test_success")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = deepSeekTestState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                                is DeepSeekTestUiState.Error -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("settings_deepseek_test_error")
                                    ) {
                                        Text(
                                            text = deepSeekTestState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                DeepSeekTestUiState.Idle, DeepSeekTestUiState.Testing -> Unit
                            }
                        }
                    }
                }
            }

            // 5. Last.fm y Scrobbling
            item(key = "section_lastfm") {
                SettingsSectionCard(title = "Last.fm y Scrobbling") {
                    SettingsActionRow(
                        icon = Icons.Rounded.Person,
                        iconTint = if (lastFmUsername != null) secondaryColor else primaryColor,
                        title = if (lastFmUsername != null) {
                            "Last.fm conectado (@$lastFmUsername)"
                        } else {
                            "Conectar con Last.fm"
                        },
                        subtitle = when {
                            lastFmUsername != null -> "Scrobbling en vivo y Favoritas sincronizadas · Toca para abrir Perfil"
                            lastFmHasCredentials -> "Inicia sesión para scrobbling en vivo, Top Canciones y previews Deezer"
                            else -> "Configura API Key / Secret e inicia sesión con Last.fm"
                        },
                        onClick = onOpenLastFmProfileOrConnect,
                        testTag = "settings_lastfm_connect_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Key,
                        iconTint = if (lastFmHasCredentials) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Credenciales API de Last.fm",
                        subtitle = if (lastFmHasCredentials) {
                            "API Key y Shared Secret guardados · Toca para editar"
                        } else {
                            "Ingresa LASTFM_API_KEY y Shared Secret manualmente"
                        },
                        onClick = onConfigureLastFmCredentials,
                        testTag = "settings_lastfm_credentials_row"
                    )
                }
            }

            // 6. Copia de Seguridad y Restauración (.json)
            item(key = "section_backup") {
                SettingsSectionCard(title = "Copia de seguridad (.json)") {
                    SettingsActionRow(
                        icon = Icons.Rounded.Upload,
                        iconTint = primaryColor,
                        title = "Exportar copia de seguridad (.json)",
                        subtitle = "Guarda tema, colores, ecualizador, listas de reproducción, favoritas y ajustes",
                        onClick = { if (!isBackupBusy) onExportBackup() },
                        testTag = "settings_export_backup_row",
                        trailing = {
                            if (isBackupBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Download,
                        iconTint = secondaryColor,
                        title = "Importar copia de seguridad (.json)",
                        subtitle = "Restaura tu configuración visual, perfiles EQ, estadísticas y listas desde un archivo",
                        onClick = { if (!isBackupBusy) onImportBackup() },
                        testTag = "settings_import_backup_row"
                    )
                }
            }

            // 7. Créditos, Actualizaciones y Soporte
            item(key = "section_about") {
                SettingsSectionCard(title = "Créditos, código abierto y soporte") {
                    SettingsActionRow(
                        icon = Icons.Rounded.SystemUpdate,
                        iconTint = secondaryColor,
                        title = "Comprobar actualizaciones",
                        subtitle = when {
                            isCheckingUpdates -> "Consultando GitHub…"
                            !updateStatusSubtitle.isNullOrBlank() -> updateStatusSubtitle
                            githubRepoConfigured -> "Buscar nuevas versiones en GitHub (Davoo-Player)"
                            else -> "Toca para comprobar o vincular tu repositorio de GitHub"
                        },
                        onClick = onCheckUpdatesNow,
                        testTag = "settings_check_updates_row",
                        trailing = {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Refresh,
                        iconTint = if (autoCheckUpdatesEnabled) secondaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Aviso automático de actualizaciones",
                        subtitle = if (githubRepoConfigured) {
                            "Comprobación ligera diaria al abrir (Davoo-Player)"
                        } else {
                            "Opcional: activa avisos al vincular tu repositorio GitHub"
                        },
                        checked = autoCheckUpdatesEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !githubRepoConfigured) {
                                onConfigureGithubRepo()
                            }
                            onToggleAutoCheckUpdates(enabled)
                        },
                        testTag = "settings_auto_updates_switch"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Info,
                        iconTint = primaryColor,
                        title = "Acerca de Davoo Player",
                        subtitle = "v${BuildConfig.VERSION_NAME} · DavooPunk · Licencias MIT, legal y cambios",
                        onClick = onOpenAboutAndLegal,
                        testTag = "settings_about_legal_row"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
