package com.metrolist.music.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.metrolist.music.ui.theme.AppTypography

private val tvColorScheme = darkColorScheme(
    primary = Color(0xFF89BEFF),
    onPrimary = Color(0xFF09213D),
    primaryContainer = Color(0xFF24476D),
    onPrimaryContainer = Color(0xFFF5F9FF),
    background = Color(0xFF0B1422),
    onBackground = Color(0xFFF5F9FF),
    surface = Color(0xFF111F31),
    onSurface = Color(0xFFF5F9FF),
    surfaceVariant = Color(0xFF203149),
    onSurfaceVariant = Color(0xFFC3D1E3),
    surfaceContainer = Color(0xFF18283D),
    outline = Color(0xFF9AB9DC),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = tvColorScheme, typography = AppTypography, content = content)
}
