package com.team.hyuncha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF), onPrimary = Color.White,
    secondary = Color(0xFF93BFFF),
    background = Color(0xFF111113), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1F), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF2C2C30), onSurfaceVariant = Color(0xFFB6B6BF),
    outlineVariant = Color(0xFF48484F),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF0069D9), onPrimary = Color.White,
    background = Color(0xFFF5F5F7), onBackground = Color(0xFF1D1D1F),
    surface = Color.White, onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFE8E8ED), onSurfaceVariant = Color(0xFF5C5C64),
    outlineVariant = Color(0xFFD2D2D7),
)

@Composable
fun HyunchaTheme(darkTheme: Boolean = true, textScale: Float = 1f, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = remember(textScale) { scaledTypography(textScale) }, content = content)
}
