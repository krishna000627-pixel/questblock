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
 * v270: fonts (v272: one app-wide font + on/off flag). [kind] is only a label on the picker (System, Serif, Sans, Mono, Display, Hand). The bundled fonts live in res/font and are
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

/**
 * v272: one font for the whole app (not one per theme). [on] is the Flags switch; [chosenId] null = the default (system) font.
 * Off, or nothing picked, means the theme keeps its normal font exactly as before v270.
 */
object ThemeFonts {
    private var onState by mutableStateOf(true)
    private var pick by mutableStateOf<String?>(null)

    val on: Boolean get() = onState
    val chosenId: String? get() = pick
    fun find(id: String): AppFont? = APP_FONTS.firstOrNull { it.id == id }
    /** The font in use now, or null for the default font (switch off, nothing picked, or the system font picked). */
    fun activeFamily(): FontFamily? = if (!onState) null else pick?.let { find(it)?.family }
    fun hasActive(): Boolean = activeFamily() != null

    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        // v272: the old per-theme picks (font_<theme>) are dropped; the app goes back to the default font
        val old = sp.all.keys.filter { it.startsWith("font_") }
        if (old.isNotEmpty()) { val ed = sp.edit(); old.forEach { ed.remove(it) }; ed.apply() }
        onState = sp.getBoolean("fontx_on", true)
        pick = sp.getString("fontx_pick", null)?.takeIf { find(it) != null }
    }

    fun setOn(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putBoolean("fontx_on", v).apply()
        onState = v
    }

    /** [id] null = back to the default font. */
    fun set(ctx: Context, id: String?) {
        val ed = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit()
        if (id == null) ed.remove("fontx_pick") else ed.putString("fontx_pick", id)
        ed.apply()
        pick = id
    }
}

/** The font in use now (null = the default font). Reads state, so the app redraws when it changes. */
val AppFontNow: FontFamily? get() = ThemeFonts.activeFamily()

internal fun Typography.withFamily(f: FontFamily?): Typography = if (f == null) this else Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)
