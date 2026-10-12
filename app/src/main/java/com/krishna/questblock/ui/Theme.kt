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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.krishna.questblock.R

// ── Theme mode ───────────────────────────────────────────────────────────────
/** What the user picked: 0 = dark, 1 = light, 2 = follow the phone. Saved in Prefs.themeMode. */
var ThemeMode by mutableIntStateOf(2)
/** What is actually painted right now. Every colour below reads this, so the whole app recolours live. */
var LightMode by mutableStateOf(false)

fun applyThemeMode(mode: Int, systemDark: Boolean) {
    ThemeMode = mode
    LightMode = themeForcedLight() ?: when (mode) { 1 -> true; 2 -> !systemDark; else -> false }
}

/** Each shop theme has its own type: true = a light theme (white), false = a dark theme (black). It picks that by itself, whatever Dark / Light / System says. Default look: null = follow the setting. */
private val SHOP_THEME_LIGHT = mapOf(
    "theme_sunset" to true, "theme_paper" to true, "theme_rose" to true,
    "theme_ocean" to false, "theme_forest" to false, "theme_neon" to false, "theme_scifi" to false,
    "theme_amoled" to false, "theme_terminal" to false, "theme_indigo" to false, "theme_mist" to false,
)
fun themeForcedLight(id: String = ShopTheme): Boolean? = SHOP_THEME_LIGHT[id]

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
    ShopTheme = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getString("shop_theme", "default") ?: "default"
    val mode = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getInt("theme_mode", 2)
    val sysDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    applyThemeMode(mode, sysDark)
    GrayMode = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean("gray_mode", false)
    // paint the chosen background before the first frame so the XML window colour never shows
    (ctx as? Activity)?.window?.setBackgroundDrawable(windowBackgroundFor(ctx))
}

/** Shop theme equipped right now ("default", "theme_sunset", ...). Recolours the whole app live. */
var ShopTheme by mutableStateOf("default")

/** accent + 5 colours each for dark and light: background, surface, surface-high, card, divider.
 *  Tones (also one set each for dark and light): text, muted text, warning (fun limit lines), coin, second accent. */
private class ShopPalette(val accent: Long, val dark: LongArray, val light: LongArray, val dTone: LongArray, val lTone: LongArray)
private val SHOP_PALETTES = mapOf(
    "theme_sunset" to ShopPalette(0xFFFF7A59,
        longArrayOf(0xFF1A0F12, 0xFF251519, 0xFF331D22, 0xFF2B191E, 0xFF3D2329),
        longArrayOf(0xFFFFF1EA, 0xFFFFFFFF, 0xFFFFE0D2, 0xFFFFFFFF, 0xFFF3D3C6),
        longArrayOf(0xFFFCEEE8, 0xFFA07A6C, 0xFFFF9A59, 0xFFFFC27A, 0xFFFF5C8A),
        longArrayOf(0xFF2A1812, 0xFF8A6A5E, 0xFFC8561F, 0xFFB4651A, 0xFFD43F6B)),
    "theme_ocean" to ShopPalette(0xFF3FA7FF,
        longArrayOf(0xFF08121C, 0xFF0E1C2B, 0xFF16293D, 0xFF12243A, 0xFF1D3350),
        longArrayOf(0xFFE8F2FB, 0xFFFFFFFF, 0xFFD8E8F7, 0xFFFFFFFF, 0xFFC9DBEC),
        longArrayOf(0xFFE6F1FB, 0xFF6A87A6, 0xFF4DD0E1, 0xFF8FD3FF, 0xFF5C8CFF),
        longArrayOf(0xFF0F1D2B, 0xFF5F7C99, 0xFF0A8CA0, 0xFF2A7FC4, 0xFF3A5FD6)),
    "theme_forest" to ShopPalette(0xFF6BCB77,
        longArrayOf(0xFF0B140E, 0xFF121F16, 0xFF1B2D21, 0xFF16261B, 0xFF22372A),
        longArrayOf(0xFFEDF5EE, 0xFFFFFFFF, 0xFFDDEBDF, 0xFFFFFFFF, 0xFFCFE0D2),
        longArrayOf(0xFFEAF5EC, 0xFF6F9078, 0xFFB6D957, 0xFFD4E27A, 0xFF3FB68B),
        longArrayOf(0xFF122018, 0xFF5E7E68, 0xFF5E8A12, 0xFF7A8F1E, 0xFF1F8A66)),
    "theme_neon" to ShopPalette(0xFFC86BFF,
        longArrayOf(0xFF0D0716, 0xFF160C26, 0xFF21133A, 0xFF1B0F31, 0xFF2D1A4F),
        longArrayOf(0xFFF3ECFB, 0xFFFFFFFF, 0xFFE6D9F6, 0xFFFFFFFF, 0xFFD8C8EE),
        longArrayOf(0xFFF3ECFB, 0xFF8470A8, 0xFFFF5CD1, 0xFFE29BFF, 0xFF5CE1FF),
        longArrayOf(0xFF1D1030, 0xFF7C6A9E, 0xFFC4199A, 0xFF9A3FD0, 0xFF0A8FB0)),
    "theme_scifi" to ShopPalette(0xFF00E5FF,
        longArrayOf(0xFF050B14, 0xFF0A1422, 0xFF11213A, 0xFF0D1A2E, 0xFF173052),
        longArrayOf(0xFFE9F6FB, 0xFFFFFFFF, 0xFFD3EAF3, 0xFFFFFFFF, 0xFFBFDDE9),
        longArrayOf(0xFFE3F4FB, 0xFF5F7C96, 0xFF4DFFC3, 0xFF7DF9FF, 0xFF6C8CFF),
        longArrayOf(0xFF0C1B27, 0xFF547088, 0xFF0A9A78, 0xFF0A8FA6, 0xFF4A62D6)),
    "theme_amoled" to ShopPalette(0xFF9CC2FF,
        longArrayOf(0xFF000000, 0xFF0A0A0A, 0xFF151515, 0xFF0F0F0F, 0xFF242424),
        longArrayOf(0xFFF4F4F4, 0xFFFFFFFF, 0xFFE6E6E6, 0xFFFFFFFF, 0xFFD6D6D6),
        longArrayOf(0xFFF2F2F2, 0xFF7C7C7C, 0xFFC7B6FF, 0xFFE8E8E8, 0xFFFF9CC2),
        longArrayOf(0xFF111111, 0xFF6E6E6E, 0xFF6B4FD6, 0xFF4A4A4A, 0xFFC23F78)),
    "theme_terminal" to ShopPalette(0xFF39FF14,
        longArrayOf(0xFF000A00, 0xFF031203, 0xFF072007, 0xFF051505, 0xFF0F3A0F),
        longArrayOf(0xFFEEF7EC, 0xFFFFFFFF, 0xFFDDEBD9, 0xFFFFFFFF, 0xFFC7DCC2),
        longArrayOf(0xFFC8F7C8, 0xFF4E8A4E, 0xFF7DFFB0, 0xFFB6FF6B, 0xFF00FFA3),
        longArrayOf(0xFF0A1F0A, 0xFF4A6B4A, 0xFF1F8A3F, 0xFF5A8A00, 0xFF008F5A)),
    "theme_paper" to ShopPalette(0xFFD9A066,
        longArrayOf(0xFF17110C, 0xFF211812, 0xFF2D2118, 0xFF261C14, 0xFF3A2B1E),
        longArrayOf(0xFFF6EEDC, 0xFFFFFBF0, 0xFFEADFC6, 0xFFFFFBF0, 0xFFDCCFB0),
        longArrayOf(0xFFF3E9D8, 0xFF9A8670, 0xFFE07A5F, 0xFFD9A066, 0xFF81B29A),
        longArrayOf(0xFF2B2118, 0xFF7A6A55, 0xFFB5482A, 0xFF9C6B1E, 0xFF3F8A6A)),
    "theme_rose" to ShopPalette(0xFFFF7BAC,
        longArrayOf(0xFF160B12, 0xFF21111B, 0xFF301A28, 0xFF281520, 0xFF3B2133),
        longArrayOf(0xFFFFF0F5, 0xFFFFFFFF, 0xFFFFDDE8, 0xFFFFFFFF, 0xFFF2C9D8),
        longArrayOf(0xFFFDEEF4, 0xFFA07A8C, 0xFFFF8FA3, 0xFFFFC2D6, 0xFFC77DFF),
        longArrayOf(0xFF2A1220, 0xFF8C6478, 0xFFC93C65, 0xFFB0486E, 0xFF8E3FD6)),
    "theme_indigo" to ShopPalette(0xFF7C83FF,
        longArrayOf(0xFF090A1A, 0xFF11122A, 0xFF1B1D40, 0xFF151736, 0xFF262A5A),
        longArrayOf(0xFFEEF0FF, 0xFFFFFFFF, 0xFFDDE0FF, 0xFFFFFFFF, 0xFFC9CEF5),
        longArrayOf(0xFFE8EAFF, 0xFF7479B0, 0xFF9BE7FF, 0xFFC7B9FF, 0xFFFF7BD5),
        longArrayOf(0xFF12143A, 0xFF5F649A, 0xFF2A6FD6, 0xFF5A4FCF, 0xFFC23FA0)),
    "theme_mist" to ShopPalette(0xFF5CE0D0,
        longArrayOf(0x8006100F, 0xE60B1A1A, 0xEB12292A, 0xE60F2222, 0xFF1E3A3A),
        longArrayOf(0x8006100F, 0xE60B1A1A, 0xEB12292A, 0xE60F2222, 0xFF1E3A3A),
        longArrayOf(0xFFE6F7F5, 0xFF7FA3A0, 0xFF7FFFD4, 0xFFA8F0E6, 0xFF8FB8FF),
        longArrayOf(0xFFE6F7F5, 0xFF7FA3A0, 0xFF7FFFD4, 0xFFA8F0E6, 0xFF8FB8FF)),
)
private fun tone(i: Int): Color? = SHOP_PALETTES[ShopTheme]?.let { Color(if (LightMode) it.lTone[i] else it.dTone[i]) }
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
val ColorAccent: Color      get() = tone(4) ?: pick(0xFF7C6EF5, 0xFF6552E8)   // purple accent
val ColorCoin: Color        get() = tone(3) ?: pick(0xFFFFC857, 0xFFB8800A)
val ColorDanger: Color      get() = pick(0xFFFF5C5C, 0xFFD93636)
val ColorWarning: Color     get() = tone(2) ?: pick(0xFFFFAA00, 0xFFC47A00)
val ColorText: Color        get() = tone(0) ?: pick(0xFFEAEFF5, 0xFF121A24)
val ColorMuted: Color       get() = tone(1) ?: pick(0xFF5A6A7E, 0xFF6B7A8D)
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

// ── Poster themes ────────────────────────────────────────────────────────────
/** A theme with a picture: it is drawn as the window background, tinted and dimmed, and the screens above it are see-through. id → (picture, tint colour, dim 0-255) */
private class Poster(val res: Int, val tint: Long, val dim: Int)
private val SHOP_POSTERS = mapOf("theme_mist" to Poster(R.drawable.poster_mist, 0xFF5CE0D0, 0x99))
private var posterKey: String? = null
private var posterBmp: Bitmap? = null

/** The window background for the equipped theme: the tinted poster, or the plain theme colour. */
fun windowBackgroundFor(ctx: Context): Drawable {
    val p = SHOP_POSTERS[ShopTheme] ?: return ColorDrawable(ColorBg.toArgb())
    val dm = ctx.resources.displayMetrics
    val key = "$ShopTheme ${dm.widthPixels}x${dm.heightPixels}"
    if (posterKey != key || posterBmp == null) {
        runCatching {
            val src = BitmapFactory.decodeResource(ctx.resources, p.res)
            // centre-crop to the screen shape so nothing is stretched
            val want = dm.widthPixels.toFloat() / dm.heightPixels
            val have = src.width.toFloat() / src.height
            val w = if (have > want) (src.height * want).toInt() else src.width
            val h = if (have > want) src.height else (src.width / want).toInt()
            posterBmp = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
            posterKey = key
        }
    }
    val bmp = posterBmp ?: return ColorDrawable(ColorBg.toArgb())
    val pic = BitmapDrawable(ctx.resources, bmp)
    // one-colour look: gray picture multiplied by the tint, then dimmed so text stays readable
    val cm = ColorMatrix().apply { setSaturation(0f) }
    val t = Color(p.tint)
    cm.postConcat(ColorMatrix(floatArrayOf(t.red, 0f, 0f, 0f, 0f, 0f, t.green, 0f, 0f, 0f, 0f, 0f, t.blue, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    pic.colorFilter = ColorMatrixColorFilter(cm)
    return LayerDrawable(arrayOf(ColorDrawable(0xFF000000.toInt()), pic, ColorDrawable((p.dim shl 24) or 0x000000)))
}

@Composable
fun QuestTheme(content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val light = themeForcedLight(ShopTheme) ?: when (ThemeMode) { 1 -> true; 2 -> !sysDark; else -> false }
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
                window.setBackgroundDrawable(windowBackgroundFor(view.context))
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
