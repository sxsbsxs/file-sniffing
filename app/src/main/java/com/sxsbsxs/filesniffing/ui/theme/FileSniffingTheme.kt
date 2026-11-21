package com.sxsbsxs.filesniffing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Neutral, high-contrast utility palette. Components consume semantic Material 3 roles.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF315FC8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5ECFF),
    onPrimaryContainer = Color(0xFF132A61),
    secondary = Color(0xFF53627E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EDF5),
    onSecondaryContainer = Color(0xFF25324A),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF171B23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171B23),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF616B7A),
    outline = Color(0xFFBFC7D3),
    outlineVariant = Color(0xFFE0E5EC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC4FF),
    onPrimary = Color(0xFF172F69),
    primaryContainer = Color(0xFF284785),
    onPrimaryContainer = Color(0xFFDFE7FF),
    secondary = Color(0xFFBEC9E0),
    onSecondary = Color(0xFF29354C),
    secondaryContainer = Color(0xFF3A465E),
    onSecondaryContainer = Color(0xFFDCE3F2),
    background = Color(0xFF101319),
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF181D25),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF252C37),
    onSurfaceVariant = Color(0xFFB8C1D0),
    outline = Color(0xFF788394),
    outlineVariant = Color(0xFF343D4A),
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
