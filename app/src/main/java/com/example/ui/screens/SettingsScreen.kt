package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ai.TranslationEngine
import com.example.model.AudioSettings
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan

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
    autoCheckUpdatesEnabled: Boolean = false,
    isCheckingUpdates: Boolean = false,
    updateStatusSubtitle: String? = null,
    githubRepoConfigured: Boolean = false,
    onCheckUpdatesNow: () -> Unit = {},
    onToggleAutoCheckUpdates: (Boolean) -> Unit = {},
    onConfigureGithubRepo: () -> Unit = {}
) {
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
                        iconTint = CopperAmber,
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
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Importar archivos de audio",
                        subtitle = "Añade canciones desde cualquier carpeta del dispositivo",
                        onClick = onImportFiles,
                        testTag = "settings_import_audio_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.SdCard,
                        iconTint = LosslessCyan,
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

            // 2. Audio y Ecualización
            item(key = "section_audio") {
                SettingsSectionCard(title = "Audio y ecualización") {
                    SettingsActionRow(
                        icon = Icons.Rounded.Equalizer,
                        iconTint = LosslessCyan,
                        title = "Ecualizador y DSP",
                        subtitle = if (audioSettings.eqEnabled) {
                            "Activo · ${audioSettings.activeProfileName ?: audioSettings.eqPreset.label}"
                        } else {
                            "10 bandas + Preamp AutoEq, Bass Boost, Virtualizador 3D y balance"
                        },
                        onClick = onOpenEqualizer,
                        testTag = "settings_equalizer_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Bedtime,
                        iconTint = if (isSleepTimerActive) CopperAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Temporizador de apagado",
                        subtitle = if (isSleepTimerActive) {
                            "Temporizador activo"
                        } else {
                            "Detener música automáticamente tras unos minutos"
                        },
                        onClick = onOpenSleepTimer,
                        testTag = "settings_sleep_timer_row"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        iconTint = if (audioSettings.replayGainEnabled) LosslessCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Normalizar volumen",
                        subtitle = "Equilibra automáticamente pistas suaves y fuertes (ReplayGain)",
                        checked = audioSettings.replayGainEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateAudioSettings(audioSettings.copy(replayGainEnabled = enabled))
                        },
                        testTag = "settings_replaygain_switch"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Speed,
                        iconTint = if (audioSettings.gaplessEnabled) CopperAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Reproducción sin pausas (Gapless)",
                        subtitle = "Transición continua entre canciones de álbumes en vivo",
                        checked = audioSettings.gaplessEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateAudioSettings(audioSettings.copy(gaplessEnabled = enabled))
                        },
                        testTag = "settings_gapless_switch"
                    )
                }
            }

            // 3. Traductor de Letras
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
                                tint = LosslessCyan,
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TranslationEngine.entries.forEach { engine ->
                                val selected = preferredTranslationEngine == engine
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectTranslationEngine(engine) },
                                    label = {
                                        Text(
                                            text = engine.displayName,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LosslessCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = LosslessCyan
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
                }
            }

            // 4. Personalización
            item(key = "section_personalization") {
                SettingsSectionCard(title = "Personalización") {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Palette,
                        iconTint = if (audioSettings.dynamicArtworkColors) CopperAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Colores dinámicos de carátula",
                        subtitle = "Adapta los acentos del reproductor al arte del álbum actual",
                        checked = audioSettings.dynamicArtworkColors,
                        onCheckedChange = { enabled ->
                            onUpdateAudioSettings(audioSettings.copy(dynamicArtworkColors = enabled))
                        },
                        testTag = "settings_dynamic_colors_switch"
                    )
                }
            }

            // 5. Créditos, Actualizaciones y Soporte
            item(key = "section_about") {
                SettingsSectionCard(title = "Créditos, código abierto y soporte") {
                    SettingsActionRow(
                        icon = Icons.Rounded.SystemUpdate,
                        iconTint = LosslessCyan,
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
                        iconTint = if (autoCheckUpdatesEnabled) LosslessCyan else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        iconTint = CopperAmber,
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
