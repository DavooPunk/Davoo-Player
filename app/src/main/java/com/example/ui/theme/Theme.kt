package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.AppThemeMode
import com.example.model.AppearancePreferences

private val SonoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SonoraTheme(
    appearance: AppearancePreferences = AppearancePreferences(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedPalette = remember(appearance, systemDark) {
        ThemePalette.resolve(appearance, systemDark)
    }
    val colorScheme = remember(resolvedPalette) {
        ThemePalette.buildColorScheme(resolvedPalette)
    }
    val isLightBackground = resolvedPalette.background.luminance() > 0.45f

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = isLightBackground
                insetsController.isAppearanceLightNavigationBars = isLightBackground
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonoraTypography,
        shapes = SonoraShapes,
        content = content
    )
}

@Composable
fun SonoraTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val mode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT
    SonoraTheme(
        appearance = AppearancePreferences(themeMode = mode),
        content = content
    )
}
