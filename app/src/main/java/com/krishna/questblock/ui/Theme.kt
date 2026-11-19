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
// #svg-logo  NEW THEME CHECKLIST: 1 SHOP_THEME_LIGHT (here) 2 SHOP_PALETTES 3 ShopCatalog item + its SVG logo in SvgIcons.EMOJI 4 terminal: theme set / help / chips / strict list.
private val SHOP_THEME_LIGHT = mapOf(
    "theme_sunset" to true, "theme_paper" to true, "theme_rose" to true,
    "theme_ocean" to false, "theme_forest" to false, "theme_neon" to false, "theme_scifi" to false,
    "theme_amoled" to false, "theme_terminal" to false, "theme_indigo" to false, 
    
    
    "theme_linen" to true, "theme_sage" to true, "theme_sky" to true, "theme_lilac" to true, "theme_chalk" to true,
    "theme_clay" to true, "theme_system" to false, "theme_ninja" to true, "theme_hashira" to false, "theme_monarch" to false, "theme_gothic" to false,
    "theme_guild" to true, "theme_pixel" to false, "theme_handheld" to true, "theme_blueprint" to false,
    // v277: Whitespace is always white; Carnival, Baroque and Vaporwave are always dark. Mono and Zen are NOT listed on purpose:
    // they follow Dark / Light / System (pure black or white, dark or light stone).
    "theme_whitespace" to true, "theme_carnival" to false, "theme_baroque" to false, "theme_vaporwave" to false,
    "theme_habitica" to true,
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
    ThemeFonts.load(ctx)
    // v268: the poster background was removed: delete its leftover picture and settings
    runCatching {
        java.io.File(ctx.filesDir, "poster_user.jpg").delete()
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().remove("poster_on").remove("poster_tint").remove("poster_dim").apply()
    }
    // v273: Quest Home was removed: delete its leftover pictures and settings
    runCatching {
        java.io.File(ctx.filesDir, "qh_wall.jpg").delete()
        java.io.File(ctx.filesDir, "qh_icons").deleteRecursively()
        ctx.getSharedPreferences("qb_launcher", Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().remove("questhome_theme").remove("flag_theme_home").apply()
    }
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
    // v222: five plain light themes in the Paper & Ink style (flat paper colours, one calm accent)
    "theme_linen" to ShopPalette(0xFF5B6B7F,
        longArrayOf(0xFF111214, 0xFF1A1C1F, 0xFF24272B, 0xFF1E2023, 0xFF30343A),
        longArrayOf(0xFFF1F2F4, 0xFFFFFFFF, 0xFFE4E6EA, 0xFFFFFFFF, 0xFFD2D6DC),
        longArrayOf(0xFFECEEF1, 0xFF8A929C, 0xFFE0A15A, 0xFFD9B26A, 0xFF8FB0D9),
        longArrayOf(0xFF1B1F24, 0xFF6A727C, 0xFFB5651D, 0xFF9C6B1E, 0xFF3F6FA6)),
    "theme_sage" to ShopPalette(0xFF6B8F71,
        longArrayOf(0xFF0F1410, 0xFF161D17, 0xFF1F2921, 0xFF1A231C, 0xFF2A362D),
        longArrayOf(0xFFEFF3EA, 0xFFFAFCF6, 0xFFDFE7D8, 0xFFFAFCF6, 0xFFCAD6C0),
        longArrayOf(0xFFE8F0E6, 0xFF84977F, 0xFFE0A15A, 0xFFC9C46A, 0xFF7FBFA0),
        longArrayOf(0xFF1C261D, 0xFF6A7A66, 0xFFB5651D, 0xFF8A7A1E, 0xFF2F7A5A)),
    "theme_sky" to ShopPalette(0xFF4A86C5,
        longArrayOf(0xFF0D1218, 0xFF141B23, 0xFF1D2733, 0xFF18212B, 0xFF263342),
        longArrayOf(0xFFEDF3F9, 0xFFFAFCFF, 0xFFDCE7F2, 0xFFFAFCFF, 0xFFC6D6E6),
        longArrayOf(0xFFE6EEF7, 0xFF7C8FA6, 0xFFE0A15A, 0xFFD9B26A, 0xFF8FC0FF),
        longArrayOf(0xFF16202B, 0xFF5E7186, 0xFFB5651D, 0xFF9C6B1E, 0xFF2A6FB8)),
    "theme_lilac" to ShopPalette(0xFF8E78C8,
        longArrayOf(0xFF120F18, 0xFF1A1623, 0xFF251F31, 0xFF1F1A2B, 0xFF30293F),
        longArrayOf(0xFFF3EFF8, 0xFFFCFAFF, 0xFFE6DFF1, 0xFFFCFAFF, 0xFFD5CBE5),
        longArrayOf(0xFFEFEAF7, 0xFF8D83A6, 0xFFE0A15A, 0xFFD9B26A, 0xFFC7A8FF),
        longArrayOf(0xFF1E1728, 0xFF72688A, 0xFFB5651D, 0xFF9C6B1E, 0xFF6A4FB0)),
    "theme_system" to ShopPalette(0xFF2EA8FF,
        longArrayOf(0xFF050B16, 0xFF0A1424, 0xFF102038, 0xFF0C1A30, 0xFF16294A),
        longArrayOf(0xFFE8F2FB, 0xFFFFFFFF, 0xFFD8E8F7, 0xFFFFFFFF, 0xFFC9DBEC),
        longArrayOf(0xFFD6EBFF, 0xFF6F8CB0, 0xFFFFB02E, 0xFFFFD24D, 0xFF3DF2C0),
        longArrayOf(0xFF0F1D2B, 0xFF5F7C99, 0xFFB5651D, 0xFF9C6B1E, 0xFF0A8CA0)),
    "theme_ninja" to ShopPalette(0xFFC62828,
        longArrayOf(0xFF15110C, 0xFF1D1812, 0xFF2A231A, 0xFF231D15, 0xFF332A1E),
        longArrayOf(0xFFF3EBDD, 0xFFFBF6EA, 0xFFE8DCC5, 0xFFFFFBF0, 0xFFD9C9A8),
        longArrayOf(0xFFF2E8D5, 0xFFA89880, 0xFFE0A15A, 0xFFD9B26A, 0xFF7FBF8F),
        longArrayOf(0xFF2A1F14, 0xFF7A6A55, 0xFFB5651D, 0xFF9C6B1E, 0xFF3F7F4F)),
    "theme_hashira" to ShopPalette(0xFF4FD6C4,
        longArrayOf(0xFF06151A, 0xFF0B2229, 0xFF123239, 0xFF0E2A31, 0xFF1B4048),
        longArrayOf(0xFFE6F6F4, 0xFFFFFFFF, 0xFFD2ECE8, 0xFFFFFFFF, 0xFFBFDDD8),
        longArrayOf(0xFFDDF6F2, 0xFF7FAAA6, 0xFFFFB86B, 0xFFFFD27A, 0xFF5CF0B0),
        longArrayOf(0xFF0F2B2B, 0xFF5A807C, 0xFFB5651D, 0xFF9C6B1E, 0xFF0A8C6A)),
    "theme_monarch" to ShopPalette(0xFF9B5CFF,
        longArrayOf(0xFF07050F, 0xFF0F0B1C, 0xFF191232, 0xFF140F28, 0xFF241A44),
        longArrayOf(0xFFF1EAFF, 0xFFFFFFFF, 0xFFE2D6FA, 0xFFFFFFFF, 0xFFCFC2EC),
        longArrayOf(0xFFE9DEFF, 0xFF8F80B5, 0xFFFFB02E, 0xFFFFD24D, 0xFF5CF0C0),
        longArrayOf(0xFF1E1233, 0xFF6F5F96, 0xFFB5651D, 0xFF9C6B1E, 0xFF0A8C6A)),
    "theme_gothic" to ShopPalette(0xFFD3122F,
        longArrayOf(0xFF0A0A0A, 0xFF121212, 0xFF1C1C1C, 0xFF161616, 0xFF2A2A2A),
        longArrayOf(0xFFF2F2F2, 0xFFFFFFFF, 0xFFE4E4E4, 0xFFFFFFFF, 0xFFD2D2D2),
        longArrayOf(0xFFEDEDED, 0xFF8E8E8E, 0xFFE0A15A, 0xFFD9B26A, 0xFF8FBF8F),
        longArrayOf(0xFF141414, 0xFF6B6B6B, 0xFFB5651D, 0xFF9C6B1E, 0xFF2F7F4F)),
    "theme_guild" to ShopPalette(0xFF8C5A1E,
        longArrayOf(0xFF1A140B, 0xFF231B10, 0xFF30251A, 0xFF2A2014, 0xFF3D2F1E),
        longArrayOf(0xFFE9D8B4, 0xFFF4E7C8, 0xFFDCC79B, 0xFFF8EDD2, 0xFFC9B084),
        longArrayOf(0xFFEFE0BE, 0xFFA8946A, 0xFFE0A15A, 0xFFD9B26A, 0xFF8FBF7F),
        longArrayOf(0xFF3A2A14, 0xFF7A6540, 0xFFA8561B, 0xFF8A6A12, 0xFF4A7A3A)),
    "theme_pixel" to ShopPalette(0xFFFFD400,
        longArrayOf(0xFF0B0B1E, 0xFF14143A, 0xFF1E1E52, 0xFF191945, 0xFF2B2B6B),
        longArrayOf(0xFFF2F2FF, 0xFFFFFFFF, 0xFFDEDEFA, 0xFFFFFFFF, 0xFFC8C8EE),
        longArrayOf(0xFFF4F4FF, 0xFF8C8CC8, 0xFFFF8A3D, 0xFFFFD400, 0xFF3DFF8C),
        longArrayOf(0xFF14143A, 0xFF6B6BA6, 0xFFB5651D, 0xFF9C6B1E, 0xFF0A9C4C)),
    "theme_handheld" to ShopPalette(0xFF306230,
        longArrayOf(0xFF0F380F, 0xFF17481A, 0xFF1F5A22, 0xFF1B501E, 0xFF2B6B2E),
        longArrayOf(0xFFBDD07A, 0xFFCBDC8C, 0xFFA9BF63, 0xFFC9DA89, 0xFF8FA84F),
        longArrayOf(0xFF9BBC0F, 0xFF6A8F1F, 0xFFC8A00F, 0xFFE0D020, 0xFF8BEF4F),
        longArrayOf(0xFF0F380F, 0xFF3D5A27, 0xFF6B3A0F, 0xFF4A4A0F, 0xFF1F5F1F)),
    "theme_blueprint" to ShopPalette(0xFF9BE1FF,
        longArrayOf(0xFF0B2F66, 0xFF103A7A, 0xFF184B94, 0xFF0F3570, 0xFF2A5FAF),
        longArrayOf(0xFFE6F0FF, 0xFFFFFFFF, 0xFFD2E2FA, 0xFFFFFFFF, 0xFFBCD2F2),
        longArrayOf(0xFFEAF4FF, 0xFF8FB4E8, 0xFFFFC857, 0xFFFFE08A, 0xFF7FFFD4),
        longArrayOf(0xFF0B2F66, 0xFF5A7DB0, 0xFFB5651D, 0xFF9C6B1E, 0xFF0A8C6A)),
    "theme_clay" to ShopPalette(0xFFEF5D8F,
        longArrayOf(0xFF1D1830, 0xFF282240, 0xFF352D52, 0xFF2D2748, 0xFF40375E),
        longArrayOf(0xFFE9E4F5, 0xFFF6F2FC, 0xFFDDD6EF, 0xFFF8F5FD, 0xFFD3CAE8),
        longArrayOf(0xFFF1ECFF, 0xFF9C92BC, 0xFFFFA24D, 0xFFFFC066, 0xFF6FDCC8),
        longArrayOf(0xFF2B2440, 0xFF7A7094, 0xFFC8661A, 0xFFB87414, 0xFF2FA58F)),
    "theme_chalk" to ShopPalette(0xFF444B55,
        longArrayOf(0xFF0E0E0E, 0xFF171717, 0xFF222222, 0xFF1C1C1C, 0xFF2E2E2E),
        longArrayOf(0xFFFAFAF8, 0xFFFFFFFF, 0xFFECECE8, 0xFFFFFFFF, 0xFFDADAD4),
        longArrayOf(0xFFF0F0EE, 0xFF8A8A86, 0xFFE0A15A, 0xFFD9B26A, 0xFFA9B4C2),
        longArrayOf(0xFF1A1A1A, 0xFF6B6B66, 0xFFB5651D, 0xFF9C6B1E, 0xFF2F5F8F)),
    "theme_rose" to ShopPalette(0xFFFF7BAC,
        longArrayOf(0xFF160B12, 0xFF21111B, 0xFF301A28, 0xFF281520, 0xFF3B2133),
        longArrayOf(0xFFFFF0F5, 0xFFFFFFFF, 0xFFFFDDE8, 0xFFFFFFFF, 0xFFF2C9D8),
        longArrayOf(0xFFFDEEF4, 0xFFA07A8C, 0xFFFF8FA3, 0xFFFFC2D6, 0xFFC77DFF),
        longArrayOf(0xFF2A1220, 0xFF8C6478, 0xFFC93C65, 0xFFB0486E, 0xFF8E3FD6)),
        "theme_habitica" to ShopPalette(0xFF6133B4,
        longArrayOf(0xFF161122, 0xFF211A33, 0xFF2E2447, 0xFF261E3B, 0xFF3B3059),
        longArrayOf(0xFFEDEAF6, 0xFFFFFFFF, 0xFFE1DCF0, 0xFFFFFFFF, 0xFFD2CBE6),
        longArrayOf(0xFFF5F2FA, 0xFFA99EC2, 0xFFFF6165, 0xFFFFBE5D, 0xFF50B5E9),
        longArrayOf(0xFF34313A, 0xFF686274, 0xFFFF6165, 0xFFFFA623, 0xFF50B5E9)),
"theme_indigo" to ShopPalette(0xFF7C83FF,
        longArrayOf(0xFF090A1A, 0xFF11122A, 0xFF1B1D40, 0xFF151736, 0xFF262A5A),
        longArrayOf(0xFFEEF0FF, 0xFFFFFFFF, 0xFFDDE0FF, 0xFFFFFFFF, 0xFFC9CEF5),
        longArrayOf(0xFFE8EAFF, 0xFF7479B0, 0xFF9BE7FF, 0xFFC7B9FF, 0xFFFF7BD5),
        longArrayOf(0xFF12143A, 0xFF5F649A, 0xFF2A6FD6, 0xFF5A4FCF, 0xFFC23FA0)),
    // v277: three minimalist themes (Mono, Whitespace, Zen) and three maximalist ones (Carnival, Baroque, Vaporwave)
    "theme_mono" to ShopPalette(0xFFFF3B30,
        longArrayOf(0xFF000000, 0xFF050505, 0xFF111111, 0xFF000000, 0xFF262626),
        longArrayOf(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF2F2F2, 0xFFFFFFFF, 0xFFE0E0E0),
        longArrayOf(0xFFFFFFFF, 0xFF8A8A8A, 0xFFFF3B30, 0xFFE6E6E6, 0xFFFF6B5E),
        longArrayOf(0xFF000000, 0xFF6B6B6B, 0xFFD62B1F, 0xFF1A1A1A, 0xFFD62B1F)),
    "theme_whitespace" to ShopPalette(0xFF1A1A1A,
        longArrayOf(0xFF0B0B0B, 0xFF111111, 0xFF1A1A1A, 0xFF0B0B0B, 0xFF262626),
        longArrayOf(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F7F5, 0xFFFFFFFF, 0xFFEAEAE6),
        longArrayOf(0xFFF2F2F2, 0xFF8C8C8C, 0xFFE0A15A, 0xFFD9B26A, 0xFFBDBDBD),
        longArrayOf(0xFF1A1A1A, 0xFF9A9A96, 0xFFB5651D, 0xFF6B6B6B, 0xFF444444)),
    "theme_zen" to ShopPalette(0xFF8C8A82,
        longArrayOf(0xFF1B1B1A, 0xFF232322, 0xFF2C2C2A, 0xFF252524, 0xFF353533),
        longArrayOf(0xFFE6E4DF, 0xFFEFEDE8, 0xFFDAD8D2, 0xFFEFEDE8, 0xFFCFCDC6),
        longArrayOf(0xFFE4E2DC, 0xFF8E8C85, 0xFFC9A27A, 0xFFCDBF9A, 0xFF9DA89A),
        longArrayOf(0xFF2A2927, 0xFF7C7A73, 0xFF9A6A3A, 0xFF8A7A52, 0xFF6F7C6A)),
    "theme_carnival" to ShopPalette(0xFFFF2D95,
        longArrayOf(0xFF14052E, 0xFF1E0A42, 0xFF2A1058, 0xFF220B4A, 0xFF3C1A7A),
        longArrayOf(0xFFFFF0F8, 0xFFFFFFFF, 0xFFFFD9EE, 0xFFFFFFFF, 0xFFF5BFE0),
        longArrayOf(0xFFFFFFFF, 0xFFB89AE0, 0xFFFFE600, 0xFFFFE600, 0xFF00E5FF),
        longArrayOf(0xFF2A0A3A, 0xFF8A5AA8, 0xFFC47A00, 0xFFB8800A, 0xFF0A8FB0)),
    "theme_baroque" to ShopPalette(0xFFD4AF37,
        longArrayOf(0xFF16070A, 0xFF20090E, 0xFF2E0E15, 0xFF260B11, 0xFF4A1A24),
        longArrayOf(0xFFF6EBD7, 0xFFFFF8E8, 0xFFEAD9B5, 0xFFFFF8E8, 0xFFD9C08A),
        longArrayOf(0xFFF3E6C8, 0xFFA58E6A, 0xFFE07A5F, 0xFFF0CF65, 0xFFB5324A),
        longArrayOf(0xFF2B1810, 0xFF7A6540, 0xFFA8561B, 0xFF8A6A12, 0xFF8E1F36)),
    "theme_vaporwave" to ShopPalette(0xFFFF71CE,
        longArrayOf(0xFF140A2B, 0xFF1D0F3D, 0xFF2A1659, 0xFF22124A, 0xFF3D2380),
        longArrayOf(0xFFFFEFFA, 0xFFFFFFFF, 0xFFFFD6F0, 0xFFFFFFFF, 0xFFF2BDE0),
        longArrayOf(0xFFF4E9FF, 0xFF9F86C9, 0xFFFFFB96, 0xFFFFFB96, 0xFF01CDFE),
        longArrayOf(0xFF2A0F46, 0xFF8A62B0, 0xFFC47A00, 0xFFB8800A, 0xFF0A8FB8)),
)
private fun tone(i: Int): Color? = SHOP_PALETTES[ShopTheme]?.let { Color(if (LightMode) it.lTone[i] else it.dTone[i]) }
private fun themed(i: Int): Color? = SHOP_PALETTES[ShopTheme]?.let { Color(if (LightMode) it.light[i] else it.dark[i]) }

private fun pick(dark: Long, light: Long) = Color(if (LightMode) light else dark)

// ── Palette (same names as before; each one switches between dark and light) ──
val ColorBg: Color          get() = (themed(0) ?: pick(0xFF050E0C, 0xFFEEF2F7))
val ColorSurface: Color     get() = (themed(1) ?: pick(0xFF0A1A15, 0xFFFFFFFF))
val ColorSurfaceHigh: Color get() = (themed(2) ?: pick(0xFF12261F, 0xFFE3E9F1))
val ColorCard: Color        get() = (themed(3) ?: pick(0xFF0B1D17, 0xFFFFFFFF))

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
val ColorText: Color        get() = tone(0) ?: pick(0xFFE6F5EE, 0xFF121A24)
val ColorMuted: Color       get() = tone(1) ?: pick(0xFF6E9487, 0xFF6B7A8D)
val ColorDivider: Color     get() = themed(4) ?: pick(0xFF15362B, 0xFFDDE4EE)

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

/** The window background: the plain theme colour. */
fun windowBackgroundFor(ctx: Context): Drawable = ColorDrawable(ColorBg.toArgb())

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
    // v270: the font of the theme in use (ThemeFonts.kt). Typography covers styled text; LocalTextStyle covers Text with no style
    val appFont = AppFontNow
    val typo = remember(appFont) { androidx.compose.material3.Typography().withFamily(appFont) }
    MaterialTheme(colorScheme = scheme, typography = typo) {
        if (appFont != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.merge(androidx.compose.ui.text.TextStyle(fontFamily = appFont)),
            ) { content() }
        } else content()
    }
}
