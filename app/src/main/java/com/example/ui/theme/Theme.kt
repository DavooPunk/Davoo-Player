package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SonoraDarkColorScheme = darkColorScheme(
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

private val SonoraLightColorScheme = lightColorScheme(
    primary = DeepBronze,
    onPrimary = WarmIvorySurface,
    primaryContainer = DeepBronzeContainer,
    onPrimaryContainer = OnCopperAmber,
    secondary = Color(0xFF0284C7),
    onSecondary = WarmIvorySurface,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    background = WarmIvoryBg,
    onBackground = InkCharcoal,
    surface = WarmIvorySurface,
    onSurface = InkCharcoal,
    surfaceVariant = WarmIvoryContainer,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFD6D3CB)
)

private val SonoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SonoraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SonoraDarkColorScheme else SonoraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonoraTypography,
        shapes = SonoraShapes,
        content = content
    )
}
