package com.chrizzzzilla.self.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.chrizzzzilla.self.core.settings.AppTheme

/** Tatsaechlich angezeigter Modus, nachdem "System" aufgeloest wurde. */
enum class ResolvedTheme { LIGHT, DARK, SEPIA }

@Composable
fun AppTheme.resolve(): ResolvedTheme = when (this) {
    AppTheme.SYSTEM -> if (isSystemInDarkTheme()) ResolvedTheme.DARK else ResolvedTheme.LIGHT
    AppTheme.LIGHT -> ResolvedTheme.LIGHT
    AppTheme.DARK -> ResolvedTheme.DARK
    AppTheme.SEPIA -> ResolvedTheme.SEPIA
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E6F3),
    onPrimaryContainer = Color(0xFF0E2A3B),
    secondary = Color(0xFF5E7A6E),
    background = Color(0xFFFBFBF9),
    onBackground = Color(0xFF1B1C1C),
    surface = Color(0xFFFBFBF9),
    onSurface = Color(0xFF1B1C1C),
    surfaceVariant = Color(0xFFE6E8E8),
    onSurfaceVariant = Color(0xFF454849),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC9E6),
    onPrimary = Color(0xFF0E2A3B),
    primaryContainer = Color(0xFF26506A),
    onPrimaryContainer = Color(0xFFD3E6F3),
    secondary = Color(0xFFA9C6B8),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE3E3E1),
    surface = Color(0xFF121416),
    onSurface = Color(0xFFE3E3E1),
    surfaceVariant = Color(0xFF2B2F31),
    onSurfaceVariant = Color(0xFFC1C6C8),
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBD5B8),
    onPrimaryContainer = Color(0xFF3B2611),
    secondary = Color(0xFF7A6A4F),
    background = Color(0xFFF4ECD8),
    onBackground = Color(0xFF5B4636),
    surface = Color(0xFFF4ECD8),
    onSurface = Color(0xFF5B4636),
    surfaceVariant = Color(0xFFE8DCC2),
    onSurfaceVariant = Color(0xFF6B5846),
)

fun ResolvedTheme.colorScheme(): ColorScheme = when (this) {
    ResolvedTheme.LIGHT -> LightColors
    ResolvedTheme.DARK -> DarkColors
    ResolvedTheme.SEPIA -> SepiaColors
}

@Composable
fun SelfTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.resolve().colorScheme(), content = content)
}
