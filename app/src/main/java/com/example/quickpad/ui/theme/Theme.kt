package com.example.quickpad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF6A1A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2D1),
    onPrimaryContainer = Color(0xFF3B1700),
    secondary = Color(0xFF2A2A2A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7DE),
    onSecondaryContainer = Color(0xFF25190F),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF1F1B17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1B17),
    surfaceVariant = Color(0xFFF7EDE5),
    onSurfaceVariant = Color(0xFF5D534C)
)

@Composable
fun QuickPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
