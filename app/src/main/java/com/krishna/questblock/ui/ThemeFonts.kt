package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.krishna.questblock.R

/**
 * v270: fonts. [kind] is only a label on the picker (System, Serif, Sans, Mono, Display, Hand). The bundled fonts live in res/font and are
 * all free under the SIL Open Font License (texts in assets/font_licenses). A family with one file gets a synthetic bold.
 * New font: copy the .ttf into res/font (lowercase name), add one line here. It shows up in the picker and in the terminal command font.
 */
class AppFont(val id: String, val label: String, val kind: String, val family: FontFamily?)

private fun ff(reg: Int, bold: Int? = null): FontFamily =
    if (bold == null) FontFamily(Font(reg, FontWeight.Normal)) else FontFamily(Font(reg, FontWeight.Normal), Font(bold, FontWeight.Bold))

val APP_FONTS: List<AppFont> = listOf(
    AppFont("system", "System font", "System", null),
    AppFont("serif", "Serif", "System", FontFamily.Serif),
    AppFont("mono", "Monospace", "System", FontFamily.Monospace),
    AppFont("cursive", "Cursive", "System", FontFamily.Cursive),
    AppFont("lora", "Lora", "Serif", ff(R.font.lora_regular, R.font.lora_bold)),
    AppFont("crimson", "Crimson Pro", "Serif", ff(R.font.crimsonpro_regular, R.font.crimsonpro_bold)),
    AppFont("plexserif", "IBM Plex Serif", "Serif", ff(R.font.plexserif_regular, R.font.plexserif_bold)),
    AppFont("baskerville", "Libre Baskerville", "Serif", ff(R.font.librebaskerville_regular)),
    AppFont("instrumentserif", "Instrument Serif", "Serif", ff(R.font.instrumentserif_regular)),
    AppFont("instrumentsans", "Instrument Sans", "Sans", ff(R.font.instrumentsans_regular, R.font.instrumentsans_bold)),
    AppFont("outfit", "Outfit", "Sans", ff(R.font.outfit_regular, R.font.outfit_bold)),
    AppFont("poppins", "Poppins", "Sans", ff(R.font.poppins_regular, R.font.poppins_bold)),
    AppFont("bricolage", "Bricolage Grotesque", "Sans", ff(R.font.bricolage_regular, R.font.bricolage_bold)),
    AppFont("nationalpark", "National Park", "Sans", ff(R.font.nationalpark_regular, R.font.nationalpark_bold)),
    AppFont("jura", "Jura", "Sans", ff(R.font.jura_medium)),
    AppFont("gloock", "Gloock", "Display", ff(R.font.gloock_regular)),
    AppFont("italiana", "Italiana", "Display", ff(R.font.italiana_regular)),
    AppFont("poiret", "Poiret One", "Display", ff(R.font.poiretone_regular)),
    AppFont("bigshoulders", "Big Shoulders", "Display", ff(R.font.bigshoulders_regular, R.font.bigshoulders_bold)),
    AppFont("arsenalsc", "Arsenal SC", "Display", ff(R.font.arsenalsc_regular)),
    AppFont("ericaone", "Erica One", "Display", ff(R.font.ericaone_regular)),
    AppFont("boldonse", "Boldonse", "Display", ff(R.font.boldonse_regular)),
    AppFont("pixelify", "Pixelify Sans", "Display", ff(R.font.pixelify_medium)),
    AppFont("nothing", "Nothing You Could Do", "Hand", ff(R.font.nothingyoucoulddo_regular)),
    AppFont("jetbrains", "JetBrains Mono", "Mono", ff(R.font.jetbrainsmono_regular, R.font.jetbrainsmono_bold)),
    AppFont("plexmono", "IBM Plex Mono", "Mono", ff(R.font.plexmono_regular, R.font.plexmono_bold)),
    AppFont("dmmono", "DM Mono", "Mono", ff(R.font.dmmono_regular)),
    AppFont("geistmono", "Geist Mono", "Mono", ff(R.font.geistmono_regular, R.font.geistmono_bold)),
    AppFont("redhatmono", "Red Hat Mono", "Mono", ff(R.font.redhatmono_regular, R.font.redhatmono_bold)),
)

/** Each theme has its own font. Until you pick one the theme gets its suggested font (DEFAULTS; themes not listed keep the system font). */
object ThemeFonts {
    /** theme key (the shop theme id without "theme_", and "default") to its label. Same order as the pages. */
    val THEMES: List<Pair<String, String>> = listOf(
        "default" to "Default", "system" to "System", "ninja" to "Ninja Scroll", "hashira" to "Hashira Mist", "monarch" to "Shadow Monarch",
        "gothic" to "Gothic Halo", "guild" to "Guild Board", "pixel" to "Pixel Arcade", "handheld" to "Handheld", "blueprint" to "Blueprint",
        "sunset" to "Sunset", "ocean" to "Ocean", "forest" to "Forest", "neon" to "Neon", "scifi" to "Sci-Fi", "amoled" to "AMOLED",
        "terminal" to "Terminal", "paper" to "Paper", "linen" to "Linen", "sage" to "Sage", "sky" to "Sky", "lilac" to "Lilac",
        "chalk" to "Chalk", "clay" to "Clay", "rose" to "Rose", "indigo" to "Indigo",
    )

    val DEFAULTS: Map<String, String> = mapOf(
        "system" to "jura", "ninja" to "italiana", "hashira" to "outfit", "monarch" to "bigshoulders", "gothic" to "gloock",
        "guild" to "baskerville", "pixel" to "pixelify", "handheld" to "dmmono", "blueprint" to "plexmono", "neon" to "bricolage",
        "scifi" to "geistmono", "terminal" to "jetbrains", "paper" to "lora", "rose" to "instrumentserif", "clay" to "poppins",
    )

    private var overrides by mutableStateOf<Map<String, String>>(emptyMap())

    fun keyOf(shopTheme: String): String = shopTheme.removePrefix("theme_")
    fun find(id: String): AppFont? = APP_FONTS.firstOrNull { it.id == id }
    fun chosen(key: String): String? = overrides[key]
    fun defaultId(key: String): String = DEFAULTS[key] ?: "system"
    fun resolvedId(key: String): String = overrides[key] ?: defaultId(key)
    fun familyFor(key: String): FontFamily? = find(resolvedId(key))?.family
    /** True when the theme has a font of its own (picked or suggested), so the old per-theme fallback (Monospace, Serif) is not used. */
    fun hasChoice(key: String): Boolean = overrides.containsKey(key) || DEFAULTS.containsKey(key)
    fun keyFor(word: String?): String? {
        val w = word?.lowercase()?.removePrefix("theme_") ?: return null
        return THEMES.firstOrNull { it.first == w }?.first
    }

    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        overrides = sp.all.entries
            .filter { it.key.startsWith("font_") && it.value is String && find(it.value as String) != null }
            .associate { it.key.removePrefix("font_") to (it.value as String) }
    }

    /** [id] null = back to the theme's suggested font. */
    fun set(ctx: Context, key: String, id: String?) {
        val ed = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit()
        if (id == null) ed.remove("font_$key") else ed.putString("font_$key", id)
        ed.apply()
        overrides = if (id == null) overrides - key else overrides + (key to id)
    }

    fun resetAll(ctx: Context) {
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        val ed = sp.edit()
        sp.all.keys.filter { it.startsWith("font_") }.forEach { ed.remove(it) }
        ed.apply()
        overrides = emptyMap()
    }
}

/** The font of the theme in use now (null = the system font). Reads state, so the app redraws when it changes. */
val AppFontNow: FontFamily? get() = ThemeFonts.familyFor(ThemeFonts.keyOf(ShopTheme))

internal fun Typography.withFamily(f: FontFamily?): Typography = if (f == null) this else Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)
