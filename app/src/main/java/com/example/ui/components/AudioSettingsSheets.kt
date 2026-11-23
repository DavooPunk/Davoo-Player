package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioSettings
import com.example.model.AutoEqProfilesCatalog
import com.example.model.EqBandInfo
import com.example.model.EqPreset
import com.example.model.HeadphoneEqProfile
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerAndDspSheet(
    settings: AudioSettings,
    bands: List<EqBandInfo>,
    onUpdateSettings: (AudioSettings) -> Unit,
    onDismiss: () -> Unit,
    onUpdateSettingsThrottled: (AudioSettings, Boolean) -> Unit = { s, _ -> onUpdateSettings(s) },
    customProfiles: List<HeadphoneEqProfile> = emptyList(),
    onSaveCustomProfile: (name: String, AudioSettings) -> Unit = { _, _ -> },
    onDeleteCustomProfile: (profileId: String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftSettings by remember { mutableStateOf(settings) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var headphoneQuery by remember { mutableStateOf("") }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var customProfileNameDraft by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        if (!isDraggingSlider) {
            draftSettings = settings
        }
    }

    val commitImmediate: (AudioSettings) -> Unit = { next ->
        isDraggingSlider = false
        draftSettings = next
        onUpdateSettingsThrottled(next, true)
    }

    val commitDrag: (AudioSettings) -> Unit = { next ->
        isDraggingSlider = true
        draftSettings = next
        onUpdateSettingsThrottled(next, false)
    }

    val allProfiles = remember(customProfiles) {
        customProfiles + AutoEqProfilesCatalog.builtInProfiles
    }
    val filteredProfiles = remember(allProfiles, headphoneQuery) {
        val q = headphoneQuery.trim().lowercase()
        if (q.isEmpty()) {
            allProfiles
        } else {
            allProfiles.filter { p ->
                p.model.lowercase().contains(q) ||
                    p.brand.lowercase().contains(q) ||
                    p.formFactor.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isDraggingSlider) {
                isDraggingSlider = false
                onUpdateSettingsThrottled(draftSettings, true)
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("equalizer_dsp_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            text = "Ecualizador y DSP",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "10 bandas paramétricas + AutoEq + Preamp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar ecualizador")
                }
            }

            // Master EQ switch
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (draftSettings.eqEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ecualizador de 10 bandas",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val activeLabel = draftSettings.activeProfileName ?: draftSettings.eqPreset.label
                        Text(
                            text = if (draftSettings.eqEnabled) {
                                "Activo: $activeLabel · Preamp ${String.format(Locale.US, "%+.1f dB", draftSettings.preampDb)}"
                            } else {
                                "Desactivado (respuesta plana)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (draftSettings.eqEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = draftSettings.eqEnabled,
                        onCheckedChange = { enabled ->
                            commitImmediate(draftSettings.copy(eqEnabled = enabled))
                        },
                        modifier = Modifier.testTag("eq_enabled_switch")
                    )
                }
            }

            // Dedicated Preamp slider (clipping prevention)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (draftSettings.eqEnabled) 0.35f else 0.16f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preamp (Preamplificador)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Compensa picos positivos para evitar distorsión (clipping)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", draftSettings.preampDb),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("preamp_value_label")
                            )
                            IconButton(
                                onClick = {
                                    commitImmediate(draftSettings.copy(preampDb = 0f))
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("preamp_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "Restablecer preamp a 0 dB",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Slider(
                        value = draftSettings.preampDb.coerceIn(-12f, 12f),
                        onValueChange = { value ->
                            val quantized = (value * 10f).roundToInt() / 10f
                            commitDrag(draftSettings.copy(preampDb = quantized.coerceIn(-12f, 12f)))
                        },
                        onValueChangeFinished = { commitImmediate(draftSettings) },
                        valueRange = -12f..12f,
                        enabled = draftSettings.eqEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("preamp_slider")
                    )
                }
            }

            // AutoEq Headphone & Custom Profiles Selector
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Perfiles de audífonos (AutoEq)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                customProfileNameDraft = draftSettings.activeProfileName ?: ""
                                showSaveProfileDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("save_custom_eq_profile_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar perfil", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedTextField(
                        value = headphoneQuery,
                        onValueChange = { headphoneQuery = it },
                        placeholder = {
                            Text(
                                text = "Buscar modelo (Sony, AirPods, HD 600, KZ…)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (headphoneQuery.isNotEmpty()) {
                                IconButton(onClick = { headphoneQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Limpiar filtro de audífonos")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eq_profile_search_input")
                    )

                    if (filteredProfiles.isEmpty()) {
                        Text(
                            text = "No se encontraron perfiles para «$headphoneQuery»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(filteredProfiles, key = { it.id }) { profile ->
                                val isSelected = draftSettings.activeProfileId == profile.id
                                Surface(
                                    onClick = {
                                        commitImmediate(
                                            draftSettings.copy(
                                                eqEnabled = true,
                                                eqPreset = EqPreset.CUSTOM,
                                                bandGainsMb = EqPreset.normalizeToTenBands(profile.bandGainsMb),
                                                preampDb = profile.preampDb,
                                                activeProfileId = profile.id,
                                                activeProfileName = profile.fullName
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .width(178.dp)
                                        .testTag("headphone_profile_chip_${profile.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (profile.isCustom) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.BookmarkAdded,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Text(
                                                    text = profile.brand,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (profile.isCustom) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (profile.isCustom) {
                                                IconButton(
                                                    onClick = { onDeleteCustomProfile(profile.id) },
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .testTag("delete_custom_eq_profile_${profile.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Eliminar perfil ${profile.model}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = profile.model,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${profile.formFactor} · ${String.format(Locale.US, "%+.1f dB", profile.preampDb)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Classic preset selector
            LazyRow(
                modifier = Modifier.testTag("eq_preset_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(EqPreset.entries) { preset ->
                    val selected = draftSettings.eqPreset == preset && draftSettings.activeProfileId == null
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newGains = if (preset == EqPreset.CUSTOM) {
                                EqPreset.normalizeToTenBands(draftSettings.bandGainsMb)
                            } else {
                                preset.gainsMb
                            }
                            val nextPreamp = if (preset == EqPreset.CUSTOM) {
                                draftSettings.preampDb
                            } else {
                                preset.recommendedPreampDb
                            }
                            commitImmediate(
                                draftSettings.copy(
                                    eqEnabled = true,
                                    eqPreset = preset,
                                    bandGainsMb = newGains,
                                    preampDb = nextPreamp,
                                    activeProfileId = null,
                                    activeProfileName = null
                                )
                            )
                        },
                        label = { Text(preset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("eq_preset_${preset.name.lowercase()}")
                    )
                }
            }

            // 10-band visual response curve + sliders
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EqFrequencyCurvePreview(
                        bandGainsMb = EqPreset.normalizeToTenBands(draftSettings.bandGainsMb),
                        eqEnabled = draftSettings.eqEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .testTag("eq_frequency_curve_canvas")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    bands.forEach { band ->
                        val minF = band.minLevelMb.toFloat()
                        val maxF = band.maxLevelMb.toFloat().coerceAtLeast(minF + 1f)
                        val liveLevelMb = draftSettings.bandGainsMb
                            .getOrElse(band.index) { band.currentLevelMb }
                            .coerceIn(band.minLevelMb, band.maxLevelMb.coerceAtLeast(band.minLevelMb + 1))
                        val db = liveLevelMb / 100f
                        val freqLabel = if (band.centerFreqHz >= 1000) {
                            val khz = band.centerFreqHz / 1000.0
                            if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", khz)
                        } else {
                            "${band.centerFreqHz} Hz"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp)
                            )
                            Slider(
                                value = liveLevelMb.toFloat().coerceIn(minF, maxF),
                                onValueChange = { newVal ->
                                    val updated = EqPreset.normalizeToTenBands(draftSettings.bandGainsMb).toMutableList()
                                    if (band.index in updated.indices) {
                                        updated[band.index] = newVal.roundToInt().coerceIn(band.minLevelMb, band.maxLevelMb)
                                    }
                                    commitDrag(
                                        draftSettings.copy(
                                            eqEnabled = true,
                                            eqPreset = EqPreset.CUSTOM,
                                            bandGainsMb = updated,
                                            activeProfileId = null,
                                            activeProfileName = null
                                        )
                                    )
                                },
                                onValueChangeFinished = {
                                    commitImmediate(draftSettings)
                                },
                                valueRange = minF..maxF,
                                enabled = draftSettings.eqEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("eq_band_slider_${band.index}")
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", db),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (draftSettings.eqEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp)
                            )
                        }
                    }
                }
            }

            // Volume Normalization integrated directly in Equalizer & DSP menu
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (draftSettings.replayGainEnabled) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (draftSettings.replayGainEnabled) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier.testTag("volume_normalization_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (draftSettings.replayGainEnabled) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "Normalizar volumen",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (draftSettings.replayGainEnabled) {
                                    "Activo · Equilibra pistas automáticamente (ReplayGain + nivelador)"
                                } else {
                                    "Desactivado · Volumen original de cada canción"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (draftSettings.replayGainEnabled) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = draftSettings.replayGainEnabled,
                        onCheckedChange = { enabled ->
                            commitImmediate(draftSettings.copy(replayGainEnabled = enabled))
                        },
                        modifier = Modifier.testTag("replaygain_switch")
                    )
                }
            }

            // Bass Boost & Virtualizer
            Text(
                text = "Refuerzo y espacialidad",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Refuerzo de graves (Bass Boost)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${draftSettings.bassBoostStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = draftSettings.bassBoostStrength.toFloat().coerceIn(0f, 1000f),
                        onValueChange = { v ->
                            commitDrag(draftSettings.copy(bassBoostStrength = v.roundToInt().coerceIn(0, 1000)))
                        },
                        onValueChangeFinished = { commitImmediate(draftSettings) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("bass_boost_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Virtualizador envolvente (3D)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${draftSettings.virtualizerStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = draftSettings.virtualizerStrength.toFloat().coerceIn(0f, 1000f),
                        onValueChange = { v ->
                            commitDrag(draftSettings.copy(virtualizerStrength = v.roundToInt().coerceIn(0, 1000)))
                        },
                        onValueChangeFinished = { commitImmediate(draftSettings) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary, activeTrackColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.testTag("virtualizer_slider")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Crossfade & Speed & Stereo Balance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Transición, velocidad y balance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crossfade entre canciones", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (draftSettings.crossfadeSeconds > 0) {
                                "Mezcla continua con solapamiento real de ${draftSettings.crossfadeSeconds} s"
                            } else {
                                "Desactivado · Cambio directo según modo Gapless"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (draftSettings.crossfadeSeconds == 0) "Off" else "${draftSettings.crossfadeSeconds} s",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (draftSettings.crossfadeSeconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = draftSettings.crossfadeSeconds.toFloat().coerceIn(0f, 12f),
                    onValueChange = { raw ->
                        val sec = raw.roundToInt().coerceIn(0, 12)
                        commitDrag(draftSettings.copy(crossfadeSeconds = sec))
                    },
                    onValueChangeFinished = { commitImmediate(draftSettings) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("crossfade_slider")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0, 2, 5, 8, 12)) { sec ->
                        FilterChip(
                            selected = draftSettings.crossfadeSeconds == sec,
                            onClick = { commitImmediate(draftSettings.copy(crossfadeSeconds = sec)) },
                            label = { Text(if (sec == 0) "Off" else "${sec} s") },
                            modifier = Modifier.testTag("crossfade_chip_$sec")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Velocidad de reproducción", style = MaterialTheme.typography.bodyMedium)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { spd ->
                        val isSel = kotlin.math.abs(draftSettings.playbackSpeed - spd) < 0.03f
                        FilterChip(
                            selected = isSel,
                            onClick = { commitImmediate(draftSettings.copy(playbackSpeed = spd)) },
                            label = { Text("${spd}x") },
                            modifier = Modifier.testTag("speed_chip_${spd}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Balance estéreo (L / R)", style = MaterialTheme.typography.bodyMedium)
                    val balText = when {
                        draftSettings.stereoBalance < -0.05f -> "Izquierda ${(kotlin.math.abs(draftSettings.stereoBalance) * 100).roundToInt()}%"
                        draftSettings.stereoBalance > 0.05f -> "Derecha ${(draftSettings.stereoBalance * 100).roundToInt()}%"
                        else -> "Centro"
                    }
                    Text(balText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = draftSettings.stereoBalance.coerceIn(-1f, 1f),
                    onValueChange = { bal ->
                        val snapped = if (kotlin.math.abs(bal) < 0.06f) 0f else bal
                        commitDrag(draftSettings.copy(stereoBalance = snapped))
                    },
                    onValueChangeFinished = { commitImmediate(draftSettings) },
                    valueRange = -1f..1f,
                    modifier = Modifier.testTag("stereo_balance_slider")
                )
            }

            // Gapless Playback
            SettingToggleRow(
                title = "Reproducción sin pausas (Gapless)",
                subtitle = "Encadena álbumes en vivo o conceptuales sin silencios",
                checked = draftSettings.gaplessEnabled,
                onCheckedChange = { commitImmediate(draftSettings.copy(gaplessEnabled = it)) },
                testTag = "gapless_switch"
            )
        }
    }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("Guardar perfil de ecualización") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("custom_eq_profile_dialog")
                ) {
                    Text(
                        text = "Guarda la curva actual de 10 bandas y el preamp (${String.format(Locale.US, "%+.1f dB", draftSettings.preampDb)}) como perfil reutilizable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customProfileNameDraft,
                        onValueChange = { customProfileNameDraft = it },
                        label = { Text("Nombre del perfil o audífonos") },
                        placeholder = { Text("Ej. Mis IEMs, Studio Nocturno…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_eq_profile_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customProfileNameDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSaveCustomProfile(trimmed, draftSettings)
                            showSaveProfileDialog = false
                        }
                    },
                    enabled = customProfileNameDraft.trim().isNotEmpty(),
                    modifier = Modifier.testTag("confirm_save_eq_profile_button")
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun EqFrequencyCurvePreview(
    bandGainsMb: List<Int>,
    eqEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (eqEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val glowColor = if (eqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val padX = 12f
            val padY = 10f
            val usableW = (w - padX * 2).coerceAtLeast(1f)
            val usableH = (h - padY * 2).coerceAtLeast(1f)
            val zeroY = padY + usableH / 2f

            // Zero dB baseline and ±6 dB guides
            drawLine(gridColor, Offset(padX, zeroY), Offset(w - padX, zeroY), strokeWidth = 1.5f)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(padX, padY + usableH * 0.25f), Offset(w - padX, padY + usableH * 0.25f), strokeWidth = 1f)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(padX, padY + usableH * 0.75f), Offset(w - padX, padY + usableH * 0.75f), strokeWidth = 1f)

            val points = (0 until 10).map { idx ->
                val gainMb = bandGainsMb.getOrElse(idx) { 0 }.coerceIn(-1200, 1200)
                val x = padX + (usableW * idx / 9f)
                val normY = (1f - (gainMb + 1200f) / 2400f).coerceIn(0f, 1f)
                val y = padY + normY * usableH
                Offset(x, y)
            }

            if (points.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.lastIndex) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, zeroY)
                    lineTo(points.first().x, zeroY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), glowColor.copy(alpha = 0.04f))
                    )
                )
                drawPath(
                    path = strokePath,
                    brush = Brush.horizontalGradient(listOf(glowColor, lineColor)),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            points.forEach { pt ->
                drawCircle(color = lineColor, radius = 4.5f, center = pt)
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    stopAfterCurrentTrack: Boolean,
    onSelectMinutes: (Int) -> Unit,
    onSelectAfterTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("sleep_timer_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Temporizador de apagado", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar temporizador")
                }
            }

            if (remainingMs != null || stopAfterCurrentTrack) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (stopAfterCurrentTrack) {
                                "Se detendrá al terminar esta canción"
                            } else {
                                "Tiempo restante: ${formatDuration(remainingMs ?: 0L)}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                onCancelTimer()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("cancel_sleep_timer_button")
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }

            Text(
                text = "Detener reproducción automáticamente después de:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 30).forEach { mins ->
                    OutlinedButton(
                        onClick = {
                            onSelectMinutes(mins)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sleep_timer_${mins}m")
                    ) {
                        Text("$mins min")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(45, 60, 90).forEach { mins ->
                    OutlinedButton(
                        onClick = {
                            onSelectMinutes(mins)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sleep_timer_${mins}m")
                    ) {
                        Text("$mins min")
                    }
                }
            }

            Button(
                onClick = {
                    onSelectAfterTrack()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sleep_timer_after_track")
            ) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Al finalizar la canción actual")
            }
        }
    }
}
