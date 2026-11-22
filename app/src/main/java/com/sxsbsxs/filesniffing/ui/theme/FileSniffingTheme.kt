package com.sxsbsxs.filesniffing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1769E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF1FF),
    onPrimaryContainer = Color(0xFF102F68),
    secondary = Color(0xFF353B45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F2F5),
    onSecondaryContainer = Color(0xFF232832),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF15181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15181D),
    surfaceVariant = Color(0xFFF4F5F7),
    onSurfaceVariant = Color(0xFF626A75),
    outline = Color(0xFF9AA2AD),
    outlineVariant = Color(0xFFE3E6EA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEBE9),
    onErrorContainer = Color(0xFF5F1410)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DBBFF),
    onPrimary = Color(0xFF082B68),
    primaryContainer = Color(0xFF174A9D),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFC2CAD6),
    onSecondary = Color(0xFF252B35),
    secondaryContainer = Color(0xFF363E4B),
    onSecondaryContainer = Color(0xFFDCE2EC),
    background = Color(0xFF101216),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF242A33),
    onSurfaceVariant = Color(0xFFB7C0CC),
    outline = Color(0xFF7C8795),
    outlineVariant = Color(0xFF343C47),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun FileSniffingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
