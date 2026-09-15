package com.example.milklog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MilkColors = lightColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF0B3C6B),
    secondary = Color(0xFF4FA8FE),
    onSecondary = Color.White,
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF10161D),
    surface = Color.White,
    onSurface = Color(0xFF10161D),
    surfaceVariant = Color(0xFFE9EFF6),
    onSurfaceVariant = Color(0xFF4A5560),
    outline = Color(0xFFB9C3CE),
    error = Color(0xFFD32F2F)
)

@Composable
fun MilkLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MilkColors, content = content)
}
