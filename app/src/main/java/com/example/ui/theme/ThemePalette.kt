package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.model.AppThemeMode
import com.example.model.AppearancePreferences
import com.example.model.CustomThemeColors
import com.example.model.CustomizableColorSlot
import com.example.model.ThemePreset
import kotlin.math.max
import kotlin.math.min

@Immutable
data class ResolvedThemePalette(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val primaryText: Color,
    val isDark: Boolean,
    val isCopperDefaultDark: Boolean = false
)

object ThemePalette {

    fun wcagContrastRatio(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    fun bestContrastingTextColor(
        background: Color,
        preferDark: Color = InkCharcoal,
        preferLight: Color = CreamWhite
    ): Color {
        val darkRatio = wcagContrastRatio(preferDark, background)
        val lightRatio = wcagContrastRatio(preferLight, background)
        return if (darkRatio >= lightRatio) preferDark else preferLight
    }

    fun ensureMinimumContrast(
        desiredFg: Color,
        background: Color,
        minRatio: Float = 4.5f
    ): Color {
        if (wcagContrastRatio(desiredFg, background) >= minRatio) return desiredFg
        val targetInk = bestContrastingTextColor(background)
        var step = 0.15f
        while (step <= 1f) {
            val candidate = lerp(desiredFg, targetInk, step)
            if (wcagContrastRatio(candidate, background) >= minRatio) {
                return candidate
            }
            step += 0.15f
        }
        return targetInk
    }

    fun resolveDarkFlag(mode: AppThemeMode, isSystemDark: Boolean): Boolean {
        return when (mode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> isSystemDark
        }
    }

    fun presetPalette(preset: ThemePreset, isDark: Boolean): ResolvedThemePalette {
        return when (preset) {
            ThemePreset.COPPER_AMBER -> if (isDark) {
                ResolvedThemePalette(
                    primary = CopperAmber,
                    secondary = LosslessCyan,
                    background = ObsidianBg,
                    surface = ObsidianSurface,
                    primaryText = CreamWhite,
                    isDark = true,
                    isCopperDefaultDark = true
                )
            } else {
                ResolvedThemePalette(
                    primary = DeepBronze,
                    secondary = Color(0xFF0284C7),
                    background = WarmIvoryBg,
                    surface = WarmIvorySurface,
                    primaryText = InkCharcoal,
                    isDark = false
                )
            }

            ThemePreset.LOSSLESS_CYAN -> if (isDark) {
                ResolvedThemePalette(
                    primary = LosslessCyan,
                    secondary = CopperAmber,
                    background = Color(0xFF091118),
                    surface = Color(0xFF101C26),
                    primaryText = Color(0xFFF1F5F9),
                    isDark = true
                )
            } else {
                ResolvedThemePalette(
                    primary = Color(0xFF0284C7),
                    secondary = Color(0xFFD97706),
                    background = Color(0xFFF3F8FC),
                    surface = Color(0xFFFFFFFF),
                    primaryText = Color(0xFF0F172A),
                    isDark = false
                )
            }

            ThemePreset.EMERALD -> if (isDark) {
                ResolvedThemePalette(
                    primary = Color(0xFF10B981),
                    secondary = Color(0xFF38BDF8),
                    background = Color(0xFF091310),
                    surface = Color(0xFF101F1A),
                    primaryText = Color(0xFFECFDF5),
                    isDark = true
                )
            } else {
                ResolvedThemePalette(
                    primary = Color(0xFF047857),
                    secondary = Color(0xFF0284C7),
                    background = Color(0xFFF3FAF7),
                    surface = Color(0xFFFFFFFF),
                    primaryText = Color(0xFF0D211A),
                    isDark = false
                )
            }

            ThemePreset.NEON_VIOLET -> if (isDark) {
                ResolvedThemePalette(
                    primary = Color(0xFFA855F7),
                    secondary = Color(0xFF22D3EE),
                    background = Color(0xFF100C18),
                    surface = Color(0xFF191326),
                    primaryText = Color(0xFFF5F3FF),
                    isDark = true
                )
            } else {
                ResolvedThemePalette(
                    primary = Color(0xFF7C3AED),
                    secondary = Color(0xFF0891B2),
                    background = Color(0xFFF7F5FC),
                    surface = Color(0xFFFFFFFF),
                    primaryText = Color(0xFF1C142B),
                    isDark = false
                )
            }

            ThemePreset.CRIMSON -> if (isDark) {
                ResolvedThemePalette(
                    primary = Color(0xFFF43F5E),
                    secondary = Color(0xFFFB923C),
                    background = Color(0xFF150B0F),
                    surface = Color(0xFF211218),
                    primaryText = Color(0xFFFFF1F2),
                    isDark = true
                )
            } else {
                ResolvedThemePalette(
                    primary = Color(0xFFBE123C),
                    secondary = Color(0xFFC2410C),
                    background = Color(0xFFFCF5F6),
                    surface = Color(0xFFFFFFFF),
                    primaryText = Color(0xFF271017),
                    isDark = false
                )
            }

            ThemePreset.MONOCHROME -> if (isDark) {
                ResolvedThemePalette(
                    primary = Color(0xFFE5E7EB),
                    secondary = Color(0xFF9CA3AF),
                    background = Color(0xFF0A0A0C),
                    surface = Color(0xFF141418),
                    primaryText = Color(0xFFF9FAFB),
                    isDark = true
                )
            } else {
                ResolvedThemePalette(
                    primary = Color(0xFF1F2937),
                    secondary = Color(0xFF4B5563),
                    background = Color(0xFFF5F5F6),
                    surface = Color(0xFFFFFFFF),
                    primaryText = Color(0xFF111827),
                    isDark = false
                )
            }

            ThemePreset.CUSTOM -> presetPalette(ThemePreset.COPPER_AMBER, isDark)
        }
    }

    fun resolve(
        appearance: AppearancePreferences,
        isSystemDark: Boolean
    ): ResolvedThemePalette {
        val requestedDark = resolveDarkFlag(appearance.themeMode, isSystemDark)
        if (appearance.preset != ThemePreset.CUSTOM) {
            return presetPalette(appearance.preset, requestedDark)
        }
        val bg = Color(appearance.customColors.backgroundArgb.toInt())
        val surface = Color(appearance.customColors.surfaceArgb.toInt())
        val primary = Color(appearance.customColors.primaryArgb.toInt())
        val secondary = Color(appearance.customColors.secondaryArgb.toInt())
        val isBgDark = bg.luminance() <= 0.45f
        val textColor = appearance.customColors.textArgb?.let { Color(it.toInt()) }
            ?: bestContrastingTextColor(bg)
        return ResolvedThemePalette(
            primary = primary,
            secondary = secondary,
            background = bg,
            surface = surface,
            primaryText = textColor,
            isDark = isBgDark,
            isCopperDefaultDark = false
        )
    }

    fun seedCustomColorsFromPreset(
        preset: ThemePreset,
        isDark: Boolean
    ): CustomThemeColors {
        val base = presetPalette(
            preset = if (preset == ThemePreset.CUSTOM) ThemePreset.COPPER_AMBER else preset,
            isDark = isDark
        )
        return CustomThemeColors(
            primaryArgb = base.primary.toArgb().toLong() and 0xFFFFFFFFL,
            secondaryArgb = base.secondary.toArgb().toLong() and 0xFFFFFFFFL,
            backgroundArgb = base.background.toArgb().toLong() and 0xFFFFFFFFL,
            surfaceArgb = base.surface.toArgb().toLong() and 0xFFFFFFFFL,
            textArgb = null
        )
    }

    fun adaptCustomSurfacesToMode(
        current: CustomThemeColors,
        targetDark: Boolean
    ): CustomThemeColors {
        val currentBgDark = Color(current.backgroundArgb.toInt()).luminance() <= 0.45f
        if (currentBgDark == targetDark) return current
        val base = presetPalette(ThemePreset.COPPER_AMBER, targetDark)
        return current.copy(
            backgroundArgb = base.background.toArgb().toLong() and 0xFFFFFFFFL,
            surfaceArgb = base.surface.toArgb().toLong() and 0xFFFFFFFFL,
            textArgb = null
        )
    }

    fun slotValueArgb(
        colors: CustomThemeColors,
        slot: CustomizableColorSlot,
        resolvedFallbackTextColor: Color
    ): Long {
        return when (slot) {
            CustomizableColorSlot.PRIMARY -> colors.primaryArgb
            CustomizableColorSlot.SECONDARY -> colors.secondaryArgb
            CustomizableColorSlot.BACKGROUND -> colors.backgroundArgb
            CustomizableColorSlot.SURFACE -> colors.surfaceArgb
            CustomizableColorSlot.TEXT -> colors.textArgb ?: (resolvedFallbackTextColor.toArgb().toLong() and 0xFFFFFFFFL)
        }
    }

    fun updateSlot(
        colors: CustomThemeColors,
        slot: CustomizableColorSlot,
        newArgb: Long?
    ): CustomThemeColors {
        val safeArgb = newArgb?.let { (it and 0x00FFFFFFL) or 0xFF000000L }
        return when (slot) {
            CustomizableColorSlot.PRIMARY -> colors.copy(primaryArgb = safeArgb ?: colors.primaryArgb)
            CustomizableColorSlot.SECONDARY -> colors.copy(secondaryArgb = safeArgb ?: colors.secondaryArgb)
            CustomizableColorSlot.BACKGROUND -> colors.copy(backgroundArgb = safeArgb ?: colors.backgroundArgb)
            CustomizableColorSlot.SURFACE -> colors.copy(surfaceArgb = safeArgb ?: colors.surfaceArgb)
            CustomizableColorSlot.TEXT -> colors.copy(textArgb = safeArgb)
        }
    }

    fun buildColorScheme(palette: ResolvedThemePalette): ColorScheme {
        if (palette.isCopperDefaultDark) {
            return darkColorScheme(
                primary = CopperAmber,
                onPrimary = OnCopperAmber,
                primaryContainer = CopperAmberContainer,
                onPrimaryContainer = CopperAmberBright,
                secondary = LosslessCyan,
                onSecondary = ObsidianBg,
                secondaryContainer = LosslessCyanContainer,
                onSecondaryContainer = LosslessCyan,
                tertiary = CopperAmberBright,
                background = ObsidianBg,
                onBackground = CreamWhite,
                surface = ObsidianSurface,
                onSurface = CreamWhite,
                surfaceVariant = ObsidianSurfaceElevated,
                onSurfaceVariant = SlateMuted,
                surfaceContainerLowest = ObsidianBg,
                surfaceContainerLow = ObsidianSurface,
                surfaceContainer = ObsidianSurfaceElevated,
                surfaceContainerHigh = ObsidianSurfaceHighest,
                surfaceContainerHighest = ObsidianSurfaceHighest,
                outline = ObsidianBorder,
                outlineVariant = ObsidianBorder.copy(alpha = 0.6f),
                error = CoralError,
                onError = ObsidianBg
            )
        }

        val bg = palette.background
        val surface = palette.surface
        val primary = palette.primary
        val secondary = palette.secondary
        val textOnBg = ensureMinimumContrast(palette.primaryText, bg, minRatio = 4.5f)
        val textOnSurface = ensureMinimumContrast(palette.primaryText, surface, minRatio = 4.5f)
        val mutedText = lerp(textOnSurface, surface, 0.36f)
        val onPrimary = bestContrastingTextColor(primary)
        val onSecondary = bestContrastingTextColor(secondary)

        val primaryContainer = if (palette.isDark) {
            lerp(surface, primary, 0.24f)
        } else {
            lerp(Color.White, primary, 0.16f)
        }
        val onPrimaryContainer = ensureMinimumContrast(
            desiredFg = if (palette.isDark) lerp(primary, Color.White, 0.32f) else lerp(primary, InkCharcoal, 0.45f),
            background = primaryContainer,
            minRatio = 4.5f
        )

        val secondaryContainer = if (palette.isDark) {
            lerp(surface, secondary, 0.22f)
        } else {
            lerp(Color.White, secondary, 0.16f)
        }
        val onSecondaryContainer = ensureMinimumContrast(
            desiredFg = if (palette.isDark) lerp(secondary, Color.White, 0.28f) else lerp(secondary, InkCharcoal, 0.45f),
            background = secondaryContainer,
            minRatio = 4.5f
        )

        val elevated = if (palette.isDark) {
            lerp(surface, Color.White, 0.055f)
        } else {
            lerp(surface, bg, 0.45f)
        }
        val highest = if (palette.isDark) {
            lerp(surface, Color.White, 0.11f)
        } else {
            lerp(surface, bg, 0.72f)
        }
        val outline = if (palette.isDark) {
            lerp(surface, Color.White, 0.18f)
        } else {
            lerp(bg, InkCharcoal, 0.18f)
        }

        return if (palette.isDark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = lerp(primary, Color.White, 0.22f),
                background = bg,
                onBackground = textOnBg,
                surface = surface,
                onSurface = textOnSurface,
                surfaceVariant = elevated,
                onSurfaceVariant = mutedText,
                surfaceContainerLowest = bg,
                surfaceContainerLow = surface,
                surfaceContainer = elevated,
                surfaceContainerHigh = highest,
                surfaceContainerHighest = highest,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.6f),
                error = CoralError,
                onError = ObsidianBg
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = lerp(primary, InkCharcoal, 0.18f),
                background = bg,
                onBackground = textOnBg,
                surface = surface,
                onSurface = textOnSurface,
                surfaceVariant = elevated,
                onSurfaceVariant = mutedText,
                surfaceContainerLowest = surface,
                surfaceContainerLow = lerp(surface, bg, 0.25f),
                surfaceContainer = elevated,
                surfaceContainerHigh = highest,
                surfaceContainerHighest = highest,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.55f),
                error = Color(0xFFDC2626),
                onError = Color.White
            )
        }
    }

    fun argbToHexString(argb: Long): String {
        val rgb = (argb and 0x00FFFFFFL).toInt()
        return String.format("#%06X", rgb)
    }

    fun hexStringToArgb(raw: String): Long? {
        val cleaned = raw.trim().removePrefix("#").uppercase()
        val rgbHex = when (cleaned.length) {
            6 -> cleaned
            8 -> cleaned.substring(2)
            else -> return null
        }
        val parsed = rgbHex.toLongOrNull(16) ?: return null
        return 0xFF000000L or parsed
    }

    fun colorToHsv(color: Color): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
        val argb = android.graphics.Color.HSVToColor(
            floatArrayOf(
                hue.coerceIn(0f, 360f),
                saturation.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f)
            )
        )
        return Color(argb)
    }
}
