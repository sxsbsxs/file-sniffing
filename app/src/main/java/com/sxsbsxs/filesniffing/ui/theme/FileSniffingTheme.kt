package com.sxsbsxs.filesniffing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared semantic palette for every screen in File Sniffing.
 * Pages should consume MaterialTheme.colorScheme instead of defining local colors.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF315E59),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EFEB),
    onPrimaryContainer = Color(0xFF173B36),
    secondary = Color(0xFF596B68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EFEC),
    onSecondaryContainer = Color(0xFF263B37),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF202826),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202826),
    surfaceVariant = Color(0xFFEEF2EF),
    onSurfaceVariant = Color(0xFF64706C),
    outline = Color(0xFFD5DEDA),
    outlineVariant = Color(0xFFE6ECE8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4C9C1),
    onPrimary = Color(0xFF123731),
    primaryContainer = Color(0xFF2C514B),
    onPrimaryContainer = Color(0xFFD0E8E2),
    secondary = Color(0xFFB7CBC5),
    onSecondary = Color(0xFF263B37),
    secondaryContainer = Color(0xFF3C514C),
    onSecondaryContainer = Color(0xFFD3E4DE),
    background = Color(0xFF111715),
    onBackground = Color(0xFFE1E9E5),
    surface = Color(0xFF171E1B),
    onSurface = Color(0xFFE1E9E5),
    surfaceVariant = Color(0xFF26312D),
    onSurfaceVariant = Color(0xFFB8C5BF),
    outline = Color(0xFF84918B),
    outlineVariant = Color(0xFF35423C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun FileSniffingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
