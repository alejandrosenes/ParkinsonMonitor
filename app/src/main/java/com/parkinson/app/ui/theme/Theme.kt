package com.parkinson.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.parkinson.app.util.AppSettings

private fun colorSchemeFor(palette: String): ColorScheme {
    val primary = AppSettings.colorFor(palette)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        secondary = ParkinsonTeal,
        tertiary = ParkinsonOrange,
        background = ParkinsonBg,
        surface = ParkinsonSurface,
        error = ParkinsonRed
    )
}

@Composable
fun ParkinsonTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(
        colorScheme = colorSchemeFor(AppSettings.colorPalette.value),
        typography = ParkinsonTypography,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(density.density, AppSettings.textScale.value)
            ) {
                content()
            }
        }
    )
}
