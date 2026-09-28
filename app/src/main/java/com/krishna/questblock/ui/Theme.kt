package com.krishna.questblock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Emerald Night palette
val ColorBg          = Color(0xFF0E1116)
val ColorSurface     = Color(0xFF151A21)
val ColorSurfaceHigh = Color(0xFF1D242D)
val ColorPrimary     = Color(0xFF3DDC97)
val ColorOnPrimary   = Color(0xFF00271A)
val ColorCoin        = Color(0xFFFFC857)
val ColorText        = Color(0xFFE8ECF1)
val ColorMuted       = Color(0xFF9AA5B1)
val ColorDanger      = Color(0xFFFF6B6B)
val ColorSuccess     = Color(0xFF3DDC97)

private val scheme = darkColorScheme(
    primary          = ColorPrimary,
    onPrimary        = ColorOnPrimary,
    secondary        = ColorCoin,
    onSecondary      = Color(0xFF3A2A00),
    background       = ColorBg,
    onBackground     = ColorText,
    surface          = ColorSurface,
    onSurface        = ColorText,
    surfaceVariant   = ColorSurfaceHigh,
    onSurfaceVariant = ColorMuted,
    error            = ColorDanger,
    onError          = Color.White,
    outline          = Color(0xFF2E3A47),
)

@Composable
fun QuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
