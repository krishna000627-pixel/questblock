package com.krishna.questblock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Palette ──────────────────────────────────────────────────────────────────
val ColorBg          = Color(0xFF0A0D12)
val ColorSurface     = Color(0xFF111720)
val ColorSurfaceHigh = Color(0xFF1A2230)
val ColorCard        = Color(0xFF16202E)
val ColorPrimary     = Color(0xFF00E5A0)
val ColorOnPrimary   = Color(0xFF001A10)
val ColorAccent      = Color(0xFF7C6EF5)   // purple accent
val ColorCoin        = Color(0xFFFFC857)
val ColorDanger      = Color(0xFFFF5C5C)
val ColorWarning     = Color(0xFFFFAA00)
val ColorText        = Color(0xFFEAEFF5)
val ColorMuted       = Color(0xFF5A6A7E)
val ColorDivider     = Color(0xFF1E2A3A)

// Category colours
val CatColors = mapOf(
    "Study"    to Color(0xFF5C9EFF),
    "Work"     to Color(0xFFFF8C5C),
    "Health"   to Color(0xFF5CFF8C),
    "Personal" to Color(0xFFB45CFF),
    "Custom"   to Color(0xFFFFD05C),
)

private val DarkScheme = darkColorScheme(
    primary          = ColorPrimary,
    onPrimary        = ColorOnPrimary,
    secondary        = ColorCoin,
    background       = ColorBg,
    onBackground     = ColorText,
    surface          = ColorSurface,
    onSurface        = ColorText,
    surfaceVariant   = ColorSurfaceHigh,
    onSurfaceVariant = ColorMuted,
    error            = ColorDanger,
)

@Composable
fun QuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
