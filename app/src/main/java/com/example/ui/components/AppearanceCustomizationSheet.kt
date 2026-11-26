package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppearancePreferences
import com.example.model.CustomizableColorSlot
import com.example.model.ThemePreset
import com.example.ui.theme.ThemePalette
import java.util.Locale

private val quickColorSwatches = listOf(
    0xFFF5A623L, // Ámbar Cobre
    0xFF38BDF8L, // Cian Lossless
    0xFF10B981L, // Esmeralda
    0xFFA855F7L, // Violeta Neón
    0xFFF43F5EL, // Carmesí
    0xFFEC4899L, // Magenta
    0xFF0C0E13L, // Obsidiana
    0xFF14171FL, // Superficie Oscura
    0xFFF8F7F4L, // Marfil Claro
    0xFFFFFFFFL  // Blanco Puro
)

@Composable
fun AppearanceCustomizationSheet(
    appearance: AppearancePreferences,
    onUpdateAppearance: (AppearancePreferences) -> Unit,
    onSelectPreset: (ThemePreset) -> Unit,
    onResetToDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        val systemDark = isSystemInDarkTheme()
        val resolvedPalette = remember(appearance, systemDark) {
            ThemePalette.resolve(appearance, systemDark)
        }
        val activeColors = remember(appearance, resolvedPalette) {
            if (appearance.preset == ThemePreset.CUSTOM) {
                appearance.customColors
            } else {
                ThemePalette.seedCustomColorsFromPreset(appearance.preset, resolvedPalette.isDark)
            }
        }

        var selectedSlot by remember { mutableStateOf(CustomizableColorSlot.PRIMARY) }

        val currentSlotArgb = remember(activeColors, selectedSlot, resolvedPalette.primaryText) {
            ThemePalette.slotValueArgb(activeColors, selectedSlot, resolvedPalette.primaryText)
        }
        val currentSlotColor = remember(currentSlotArgb) { Color(currentSlotArgb.toInt()) }

        var hue by remember { mutableFloatStateOf(0f) }
        var saturation by remember { mutableFloatStateOf(1f) }
        var brightness by remember { mutableFloatStateOf(1f) }
        var hexDraft by remember { mutableStateOf(ThemePalette.argbToHexString(currentSlotArgb)) }
        var isHexError by remember { mutableStateOf(false) }

        LaunchedEffect(selectedSlot, currentSlotArgb) {
            val hsv = ThemePalette.colorToHsv(Color(currentSlotArgb.toInt()))
            hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
            hexDraft = ThemePalette.argbToHexString(currentSlotArgb)
            isHexError = false
        }

        val applyColorChange: (Color) -> Unit = { newColor ->
            val newArgb = newColor.toArgb().toLong() and 0xFFFFFFFFL
            hexDraft = ThemePalette.argbToHexString(newArgb)
            isHexError = false
            val baseCustom = if (appearance.preset == ThemePreset.CUSTOM) {
                appearance.customColors
            } else {
                ThemePalette.seedCustomColorsFromPreset(appearance.preset, resolvedPalette.isDark)
            }
            val updatedCustom = ThemePalette.updateSlot(baseCustom, selectedSlot, newArgb)
            onUpdateAppearance(
                appearance.copy(
                    preset = ThemePreset.CUSTOM,
                    customColors = updatedCustom
                )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("appearance_customization_sheet"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_appearance_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Cerrar personalización",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Personalizar colores",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (appearance.preset == ThemePreset.CUSTOM) {
                                "Paleta personalizada activa"
                            } else {
                                "Preset: ${appearance.preset.label}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onResetToDefaults,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("appearance_header_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Restablecer valores por defecto",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Live Interactive Preview Card
                    Text(
                        text = "VISTA PREVIA EN VIVO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("appearance_live_preview")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Pista Hi-Res de ejemplo",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "FLAC",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "24-bit / 96 kHz",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Acento primario",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Acento secundario",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Presets Row
                    Text(
                        text = "TEMAS PREDEFINIDOS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.testTag("theme_presets_row")
                    ) {
                        items(ThemePreset.entries, key = { it.name }, contentType = { "theme_preset" }) { preset ->
                            val isSelected = appearance.preset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectPreset(preset) },
                                leadingIcon = {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (preset == ThemePreset.CUSTOM) {
                                                        Color(activeColors.primaryArgb.toInt())
                                                    } else {
                                                        Color(preset.primaryPreviewArgb.toInt())
                                                    }
                                                )
                                                .border(0.75.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (preset == ThemePreset.CUSTOM) {
                                                        Color(activeColors.secondaryArgb.toInt())
                                                    } else {
                                                        Color(preset.secondaryPreviewArgb.toInt())
                                                    }
                                                )
                                                .border(0.75.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("theme_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    // 3. Editable Color Slots
                    Text(
                        text = "ELEMENTO A EDITAR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("color_slots_row")
                    ) {
                        items(CustomizableColorSlot.entries, key = { it.name }) { slot ->
                            val isSlotSelected = selectedSlot == slot
                            val slotArgb = ThemePalette.slotValueArgb(
                                activeColors,
                                slot,
                                resolvedPalette.primaryText
                            )
                            FilterChip(
                                selected = isSlotSelected,
                                onClick = { selectedSlot = slot },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(slotArgb.toInt()))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                },
                                label = { Text(slot.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("color_slot_${slot.name.lowercase()}")
                            )
                        }
                    }

                    // 4. Interactive HSV Picker + Hex Input
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedSlot.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = selectedSlot.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(currentSlotColor)
                                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }

                            if (selectedSlot == CustomizableColorSlot.TEXT) {
                                val isAutoText = appearance.preset != ThemePreset.CUSTOM ||
                                    appearance.customColors.textArgb == null
                                val bgColor = Color(activeColors.backgroundArgb.toInt())
                                val ratio = ThemePalette.wcagContrastRatio(currentSlotColor, bgColor)
                                val ratioLabel = String.format(Locale.US, "%.1f:1", ratio)
                                val gradeLabel = when {
                                    ratio >= 7.0f -> "WCAG AAA"
                                    ratio >= 4.5f -> "WCAG AA"
                                    else -> "Bajo contraste"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = isAutoText,
                                        onClick = {
                                            val baseCustom = if (appearance.preset == ThemePreset.CUSTOM) {
                                                appearance.customColors
                                            } else {
                                                ThemePalette.seedCustomColorsFromPreset(
                                                    appearance.preset,
                                                    resolvedPalette.isDark
                                                )
                                            }
                                            onUpdateAppearance(
                                                appearance.copy(
                                                    preset = ThemePreset.CUSTOM,
                                                    customColors = baseCustom.copy(textArgb = null)
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Automático (WCAG)") },
                                        modifier = Modifier.testTag("text_color_auto_wcag_chip")
                                    )

                                    Text(
                                        text = "$ratioLabel · $gradeLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (ratio >= 4.5f) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("text_wcag_ratio_badge")
                                    )
                                }
                            }

                            // 2D Saturation / Brightness (SV) Box
                            SvColorSquare(
                                hue = hue,
                                saturation = saturation,
                                brightness = brightness,
                                onSatBrightnessChanged = { newSat, newBright ->
                                    saturation = newSat
                                    brightness = newBright
                                    applyColorChange(ThemePalette.hsvToColor(hue, newSat, newBright))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(172.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .testTag("color_picker_sv_box")
                            )

                            // Rainbow Hue Bar
                            HueSliderBar(
                                hue = hue,
                                onHueChanged = { newHue ->
                                    hue = newHue
                                    applyColorChange(ThemePalette.hsvToColor(newHue, saturation, brightness))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .testTag("color_picker_hue_bar")
                            )

                            // Quick swatches
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(quickColorSwatches) { swatchArgb ->
                                    val swatchColor = Color(swatchArgb.toInt())
                                    val isMatch = (currentSlotArgb and 0x00FFFFFFL) == (swatchArgb and 0x00FFFFFFL)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                width = if (isMatch) 2.5.dp else 1.dp,
                                                color = if (isMatch) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                                },
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                val hsv = ThemePalette.colorToHsv(swatchColor)
                                                hue = hsv[0]
                                                saturation = hsv[1]
                                                brightness = hsv[2]
                                                applyColorChange(swatchColor)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isMatch) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = ThemePalette.bestContrastingTextColor(swatchColor),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Hex input row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = hexDraft,
                                    onValueChange = { raw ->
                                        val filtered = raw.uppercase()
                                            .filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
                                            .take(9)
                                        hexDraft = filtered
                                        val parsed = ThemePalette.hexStringToArgb(filtered)
                                        if (parsed != null) {
                                            isHexError = false
                                            val c = Color(parsed.toInt())
                                            val hsv = ThemePalette.colorToHsv(c)
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            brightness = hsv[2]
                                            applyColorChange(c)
                                        } else {
                                            isHexError = filtered.removePrefix("#").isNotEmpty()
                                        }
                                    },
                                    label = { Text("Código Hex (#RRGGBB)") },
                                    singleLine = true,
                                    isError = isHexError,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Palette,
                                            contentDescription = null,
                                            tint = currentSlotColor
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("color_picker_hex_input")
                                )
                            }
                        }
                    }

                    // Reset to Defaults & Done buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResetToDefaults,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("appearance_reset_defaults_button")
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restablecer valores por defecto", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("appearance_done_button")
                        ) {
                            Text("Listo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SvColorSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onSatBrightnessChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val huePureColor = remember(hue) { ThemePalette.hsvToColor(hue, 1f, 1f) }
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val s = (offset.x / w).coerceIn(0f, 1f)
                    val v = (1f - offset.y / h).coerceIn(0f, 1f)
                    onSatBrightnessChanged(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val s = (change.position.x / w).coerceIn(0f, 1f)
                    val v = (1f - change.position.y / h).coerceIn(0f, 1f)
                    onSatBrightnessChanged(s, v)
                }
            }
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, huePureColor)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black)
            )
        )
        val cx = saturation.coerceIn(0f, 1f) * size.width
        val cy = (1f - brightness.coerceIn(0f, 1f)) * size.height
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = 12.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@Composable
private fun HueSliderBar(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = remember {
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFFFF00FF),
            Color(0xFFFF0000)
        )
    }
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val newHue = ((offset.x / w).coerceIn(0f, 1f) * 360f)
                    onHueChanged(newHue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val newHue = ((change.position.x / w).coerceIn(0f, 1f) * 360f)
                    onHueChanged(newHue)
                }
            }
    ) {
        drawRect(brush = Brush.horizontalGradient(rainbowColors))
        val cx = ((hue.coerceIn(0f, 360f) / 360f) * size.width)
            .coerceIn(size.height / 2f, (size.width - size.height / 2f).coerceAtLeast(size.height / 2f))
        val cy = size.height / 2f
        drawCircle(
            color = Color.White,
            radius = (size.height * 0.42f),
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
