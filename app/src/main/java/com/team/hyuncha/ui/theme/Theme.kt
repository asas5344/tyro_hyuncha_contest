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

// Pleos Connect Colors: neutral scale for the app window and control surfaces.
private val PleosDarkColors = DarkColors.copy(
    primary = Color(0xFFE0E1E6), onPrimary = Color(0xFF131417),
    primaryContainer = Color(0xFF44464E), onPrimaryContainer = Color.White,
    secondary = Color(0xFFC7C9CE),
    secondaryContainer = Color(0xFF44464E), onSecondaryContainer = Color.White,
    background = Color(0xFF131417), onBackground = Color.White,
    surface = Color(0xFF1D1E21), onSurface = Color.White,
    surfaceVariant = Color(0xFF313236), onSurfaceVariant = Color(0xFFC7C9CE),
    surfaceContainerLow = Color(0xFF1D1E21), surfaceContainer = Color(0xFF313236),
    surfaceContainerHigh = Color(0xFF313236),
    outline = Color(0xFF686A72), outlineVariant = Color(0xFF44464E),
)
private val PleosLightColors = LightColors.copy(
    primary = Color(0xFF44464E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E1E6), onPrimaryContainer = Color(0xFF131417),
    secondary = Color(0xFF44464E),
    secondaryContainer = Color(0xFFE0E1E6), onSecondaryContainer = Color(0xFF131417),
    background = Color(0xFFF7F8FA), onBackground = Color(0xFF131417),
    surface = Color.White, onSurface = Color(0xFF131417),
    surfaceVariant = Color(0xFFEDEEF2), onSurfaceVariant = Color(0xFF44464E),
    surfaceContainerLow = Color(0xFFF7F8FA), surfaceContainer = Color(0xFFEDEEF2),
    surfaceContainerHigh = Color(0xFFE0E1E6),
    outline = Color(0xFF686A72), outlineVariant = Color(0xFFC7C9CE),
)

// Surfaces over the camera keep their own contrast in both app themes.
internal val CameraOverlay = Color(0xEB131417)
internal val CameraControl = Color(0xFF313236)
internal val CameraControlOutline = Color(0xFF686A72)
internal val CameraControlSelected = Color(0xFFE0E1E6)
internal val CameraControlSelectedText = Color(0xFF131417)
internal val CameraSecondaryText = Color(0xFFC7C9CE)
internal val PracticeAmber = Color(0xFFFFCB70)

@Composable
fun HyunchaTheme(
    darkTheme: Boolean = true,
    textScale: Float = 1f,
    pleosVisuals: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        pleosVisuals -> if (darkTheme) PleosDarkColors else PleosLightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors,
        typography = remember(textScale) { scaledTypography(textScale) }, content = content)
}
