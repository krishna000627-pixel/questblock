package com.krishna.questblock.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Theme mode ───────────────────────────────────────────────────────────────
/** What the user picked: 0 = dark, 1 = light, 2 = follow the phone. Saved in Prefs.themeMode. */
var ThemeMode by mutableIntStateOf(0)
/** What is actually painted right now. Every colour below reads this, so the whole app recolours live. */
var LightMode by mutableStateOf(false)

fun applyThemeMode(mode: Int, systemDark: Boolean) {
    ThemeMode = mode
    LightMode = when (mode) { 1 -> true; 2 -> !systemDark; else -> false }
}

/** Call from every activity before setContent so the first frame is already the right theme. */
fun loadThemeMode(ctx: Context) {
    val mode = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getInt("theme_mode", 0)
    val sysDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    applyThemeMode(mode, sysDark)
}

private fun pick(dark: Long, light: Long) = Color(if (LightMode) light else dark)

// ── Palette (same names as before; each one switches between dark and light) ──
val ColorBg: Color          get() = pick(0xFF0A0D12, 0xFFEEF2F7)
val ColorSurface: Color     get() = pick(0xFF111720, 0xFFFFFFFF)
val ColorSurfaceHigh: Color get() = pick(0xFF1A2230, 0xFFE3E9F1)
val ColorCard: Color        get() = pick(0xFF16202E, 0xFFFFFFFF)

/** Accent colour. A state, so changing it in Settings recolours the app live. Darkened a little on light so it stays readable. */
private var accentRaw by mutableStateOf(Color(0xFF00E5A0))
var ColorPrimary: Color
    get() = if (LightMode) Color(accentRaw.red * 0.75f, accentRaw.green * 0.75f, accentRaw.blue * 0.75f, accentRaw.alpha) else accentRaw
    set(value) { accentRaw = value }

val ColorOnPrimary: Color   get() = pick(0xFF001A10, 0xFFFFFFFF)
val ColorAccent: Color      get() = pick(0xFF7C6EF5, 0xFF6552E8)   // purple accent
val ColorCoin: Color        get() = pick(0xFFFFC857, 0xFFB8800A)
val ColorDanger: Color      get() = pick(0xFFFF5C5C, 0xFFD93636)
val ColorWarning: Color     get() = pick(0xFFFFAA00, 0xFFC47A00)
val ColorText: Color        get() = pick(0xFFEAEFF5, 0xFF121A24)
val ColorMuted: Color       get() = pick(0xFF5A6A7E, 0xFF6B7A8D)
val ColorDivider: Color     get() = pick(0xFF1E2A3A, 0xFFDDE4EE)

// Category colours
val CatColors: Map<String, Color>
    get() = if (LightMode) mapOf(
        "Study"    to Color(0xFF2F6FD6),
        "Work"     to Color(0xFFD9642A),
        "Health"   to Color(0xFF1E9E4A),
        "Personal" to Color(0xFF8E3FD6),
        "Custom"   to Color(0xFFB8860B),
    ) else mapOf(
        "Study"    to Color(0xFF5C9EFF),
        "Work"     to Color(0xFFFF8C5C),
        "Health"   to Color(0xFF5CFF8C),
        "Personal" to Color(0xFFB45CFF),
        "Custom"   to Color(0xFFFFD05C),
    )

val ACCENTS: List<Pair<String, Long>> = listOf(
    "Mint"   to 0xFF00E5A0,
    "Sky"    to 0xFF4DA3FF,
    "Violet" to 0xFF9B7CFF,
    "Rose"   to 0xFFFF6B9A,
    "Amber"  to 0xFFFFB020,
    "Coral"  to 0xFFFF7A59,
)

@Composable
fun QuestTheme(content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val light = when (ThemeMode) { 1 -> true; 2 -> !sysDark; else -> false }
    SideEffect { if (LightMode != light) LightMode = light }

    val primary = ColorPrimary
    val scheme = remember(primary, light) {
        if (light) lightColorScheme(
            primary          = primary,
            onPrimary        = ColorOnPrimary,
            secondary        = ColorCoin,
            background       = ColorBg,
            onBackground     = ColorText,
            surface          = ColorSurface,
            onSurface        = ColorText,
            surfaceVariant   = ColorSurfaceHigh,
            onSurfaceVariant = ColorMuted,
            error            = ColorDanger,
        ) else darkColorScheme(
            primary          = primary,
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
    }

    // status/nav bar icon colours + window background follow the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = light
                c.isAppearanceLightNavigationBars = light
                window.setBackgroundDrawable(ColorDrawable(ColorBg.toArgb()))
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
