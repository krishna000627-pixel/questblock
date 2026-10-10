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
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import android.view.View

// ── Theme mode ───────────────────────────────────────────────────────────────
/** What the user picked: 0 = dark, 1 = light, 2 = follow the phone. Saved in Prefs.themeMode. */
var ThemeMode by mutableIntStateOf(2)
/** What is actually painted right now. Every colour below reads this, so the whole app recolours live. */
var LightMode by mutableStateOf(false)

fun applyThemeMode(mode: Int, systemDark: Boolean) {
    ThemeMode = mode
    LightMode = when (mode) { 1 -> true; 2 -> !systemDark; else -> false }
}

// ── Grayscale look ───────────────────────────────────────────────────────────
/** Optional (Hub → Experiments). On = the whole window is drawn through a zero-saturation colour filter, like the phone's own grayscale: emojis, icons and images go gray too, at full sharpness. */
var GrayMode by mutableStateOf(false)

private val GRAY_PAINT: android.graphics.Paint by lazy {
    android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
    }
}

/** Puts the filter on (or takes it off) the window that [view] lives in. Works for activities and for dialog windows. */
fun setWindowGray(view: View, on: Boolean) {
    val root = view.rootView ?: return
    if (on) root.setLayerType(View.LAYER_TYPE_HARDWARE, GRAY_PAINT) else if (root.layerType != View.LAYER_TYPE_NONE) root.setLayerType(View.LAYER_TYPE_NONE, null)
}

/** Add to a dialog (modifier = Modifier.grayWindow()) so its own window follows the grayscale switch too. */
fun Modifier.grayWindow(): Modifier = composed {
    val gray = GrayMode
    val v = LocalView.current
    SideEffect { setWindowGray(v, gray) }
    Modifier
}

/** Call from every activity before setContent so the first frame is already the right theme. */
fun loadThemeMode(ctx: Context) {
    val mode = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getInt("theme_mode", 2)
    val sysDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    applyThemeMode(mode, sysDark)
    GrayMode = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean("gray_mode", false)
    // paint the chosen background before the first frame so the XML window colour never shows
    (ctx as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(ColorBg.toArgb()))
}

/** Shop theme equipped right now ("default", "theme_sunset", ...). Recolours the whole app live. */
var ShopTheme by mutableStateOf("default")

/** accent + 5 colours each for dark and light: background, surface, surface-high, card, divider */
private class ShopPalette(val accent: Long, val dark: LongArray, val light: LongArray)
private val SHOP_PALETTES = mapOf(
    "theme_sunset" to ShopPalette(0xFFFF7A59,
        longArrayOf(0xFF1A0F12, 0xFF251519, 0xFF331D22, 0xFF2B191E, 0xFF3D2329),
        longArrayOf(0xFFFFF1EA, 0xFFFFFFFF, 0xFFFFE0D2, 0xFFFFFFFF, 0xFFF3D3C6)),
    "theme_ocean" to ShopPalette(0xFF3FA7FF,
        longArrayOf(0xFF08121C, 0xFF0E1C2B, 0xFF16293D, 0xFF12243A, 0xFF1D3350),
        longArrayOf(0xFFE8F2FB, 0xFFFFFFFF, 0xFFD8E8F7, 0xFFFFFFFF, 0xFFC9DBEC)),
    "theme_forest" to ShopPalette(0xFF6BCB77,
        longArrayOf(0xFF0B140E, 0xFF121F16, 0xFF1B2D21, 0xFF16261B, 0xFF22372A),
        longArrayOf(0xFFEDF5EE, 0xFFFFFFFF, 0xFFDDEBDF, 0xFFFFFFFF, 0xFFCFE0D2)),
    "theme_neon" to ShopPalette(0xFFC86BFF,
        longArrayOf(0xFF0D0716, 0xFF160C26, 0xFF21133A, 0xFF1B0F31, 0xFF2D1A4F),
        longArrayOf(0xFFF3ECFB, 0xFFFFFFFF, 0xFFE6D9F6, 0xFFFFFFFF, 0xFFD8C8EE)),
    "theme_scifi" to ShopPalette(0xFF00E5FF,
        longArrayOf(0xFF050B14, 0xFF0A1422, 0xFF11213A, 0xFF0D1A2E, 0xFF173052),
        longArrayOf(0xFFE9F6FB, 0xFFFFFFFF, 0xFFD3EAF3, 0xFFFFFFFF, 0xFFBFDDE9)),
)
private fun themed(i: Int): Color? = SHOP_PALETTES[ShopTheme]?.let { Color(if (LightMode) it.light[i] else it.dark[i]) }

private fun pick(dark: Long, light: Long) = Color(if (LightMode) light else dark)

// ── Palette (same names as before; each one switches between dark and light) ──
val ColorBg: Color          get() = themed(0) ?: pick(0xFF0A0D12, 0xFFEEF2F7)
val ColorSurface: Color     get() = themed(1) ?: pick(0xFF111720, 0xFFFFFFFF)
val ColorSurfaceHigh: Color get() = themed(2) ?: pick(0xFF1A2230, 0xFFE3E9F1)
val ColorCard: Color        get() = themed(3) ?: pick(0xFF16202E, 0xFFFFFFFF)

/** Accent colour. A state, so changing it in Settings recolours the app live. Darkened a little on light so it stays readable. */
private var accentRaw by mutableStateOf(Color(0xFF00E5A0))
var ColorPrimary: Color
    get() {
        val base = SHOP_PALETTES[ShopTheme]?.let { Color(it.accent) } ?: accentRaw
        return if (LightMode) Color(base.red * 0.75f, base.green * 0.75f, base.blue * 0.75f, base.alpha) else base
    }
    set(value) { accentRaw = value }

val ColorOnPrimary: Color   get() = pick(0xFF001A10, 0xFFFFFFFF)
val ColorAccent: Color      get() = pick(0xFF7C6EF5, 0xFF6552E8)   // purple accent
val ColorCoin: Color        get() = pick(0xFFFFC857, 0xFFB8800A)
val ColorDanger: Color      get() = pick(0xFFFF5C5C, 0xFFD93636)
val ColorWarning: Color     get() = pick(0xFFFFAA00, 0xFFC47A00)
val ColorText: Color        get() = pick(0xFFEAEFF5, 0xFF121A24)
val ColorMuted: Color       get() = pick(0xFF5A6A7E, 0xFF6B7A8D)
val ColorDivider: Color     get() = themed(4) ?: pick(0xFF1E2A3A, 0xFFDDE4EE)

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
    val gray = GrayMode
    if (!view.isInEditMode) {
        SideEffect {
            setWindowGray(view, gray)
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
