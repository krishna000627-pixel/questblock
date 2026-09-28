package com.krishna.questblock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF9B6BFF),
    onPrimary = Color.White,
    secondary = Color(0xFFFFC94D),
    onSecondary = Color(0xFF221B00),
    background = Color(0xFF12101C),
    onBackground = Color(0xFFEDE9FF),
    surface = Color(0xFF1C1930),
    onSurface = Color(0xFFEDE9FF),
    surfaceVariant = Color(0xFF2A2644),
    onSurfaceVariant = Color(0xFFCFC9E8),
)

@Composable
fun QuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
