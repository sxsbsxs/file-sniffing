package com.sxsbsxs.filesniffing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared visual language for every screen in File Sniffing.
 * Keep semantic colors here so pages do not grow their own palettes.
 */
private val FileSniffingColorScheme = lightColorScheme(
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

@Composable
fun FileSniffingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FileSniffingColorScheme,
        content = content
    )
}
