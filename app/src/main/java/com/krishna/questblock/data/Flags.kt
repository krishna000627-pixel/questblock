package com.krishna.questblock.data

import androidx.compose.runtime.mutableStateMapOf

/** v215: switches that cut a feature loose without deleting it. v219: the homework switch is a flag (flags page / terminal flags), off by default. v225: more flags (EXTRA), on by default. */
object Flags {
    class Def(val id: String, val label: String, val desc: String, val default: Boolean = true)

    /** v225: flags that hide one feature on the normal pages and in the terminal. */
    val EXTRA: List<Def> = listOf(
        // v282: switches for the v281 UI additions (all on by default)
        Def("ui_swipe_hint", "Swipe hint", "The one-time line on Quests that explains the swipes", false),
        Def("ui_empty_quick", "One-tap suggestion", "The one-tap button on Home's Nothing planned card", false),
        Def("ui_streak_cal", "Streak calendar", "The 5-week calendar on Profile (off = a small Streak map link instead)", false),
        Def("ui_daily_ring", "Daily ring", "The ring in the Quests tile on Home (off = the old check tile)", false),
        Def("ui_skeleton", "Loading placeholder", "The shimmering placeholder on Home during the first load"),
        Def("ui_haptic_ticks", "Tap ticks", "The light tick on filter pills and the Home shortcut pills (also needs the Haptics flag)"),
        Def("a11y_bits", "Accessibility extras", "48 dp plus and minus buttons in the quest creator and spoken labels on icons"),
        Def("mood", "Mood check-in", "The Mood section on the Attendance page, terminal mood"),
        Def("leaderboard", "Daily leaderboard", "The leaderboard card on Stats, terminal leaderboard"),
        Def("experiments", "Experiments card", "The Experiments card in the Hub"),
        Def("themeicon", "Theme app icon", "The app icon on the home screen follows the equipped theme (Ninja, System, Paper, Gothic shields; Mono, Whitespace, Zen, Carnival, Baroque, Vaporwave have their own logos). Off = always the gold one. Choose picks one icon yourself"),
        Def("themefx", "Theme extras (all)", "Master switch for the own words and effects of the style themes. Each theme also has its own switch below"),
        Def("fx_system", "System extras", "System Window: HUD Focus ring, XP blocks, bracket level up, [SYSTEM] words and quest done pop, lock screen"),
        Def("fx_ninja", "Ninja Scroll extras", "Ninja Scroll: ink brush Focus ring and XP bar, red seal level up, DONE pop, lock screen"),
        Def("fx_hashira", "Hashira Mist extras", "Hashira Mist: breathing mist Focus ring, pill XP bar, mist level up, Form 1 to 3 words, lock screen"),
        Def("fx_monarch", "Shadow Monarch extras", "Shadow Monarch: violet Focus ring with motes, glow XP bar, ARISE level up, shadow army counter, lock screen"),
        Def("fx_gothic", "Gothic Halo extras", "Gothic Halo: double ring and diamond Focus ring, line XP bar, red halo level up, days left, rite words, lock screen"),
        Def("fx_guild", "Guild Board extras", "Guild Board: brass stud Focus ring, plank XP bar, wax seal level up, tilted notices, Copper Silver Gold words, lock screen"),
        Def("fx_pixel", "Pixel Arcade extras", "Pixel Arcade: pixel Focus ring, block XP bar, confetti level up, GAME OVER, STAGE words, lock screen"),
        Def("fx_handheld", "Handheld extras", "Handheld: dot matrix Focus ring, LCD XP bar, LEVEL UP box, QUESTBOY boot screen, SAVING box, lock screen"),
        Def("fx_blueprint", "Blueprint extras", "Blueprint: drafting Focus ring, dimension line XP bar, REVISION ACCEPTED stamp, dimension lines, TYPE words, lock screen"),
        Def("fx_sunset", "Sunset extras", "Sunset: sun ray Focus ring, sun XP bar, rising sun level up, Golden hour pop, header strip, quest cards, lock screen"),
        Def("fx_ocean", "Ocean extras", "Ocean: ripple Focus ring, wave XP bar, ripple level up, Tide is in pop, header strip, quest cards, lock screen"),
        Def("fx_forest", "Forest extras", "Forest: leaf Focus ring, vine XP bar, falling leaves level up, Grown pop, header strip, quest cards, lock screen"),
        Def("fx_neon", "Neon extras", "Neon: glow tube Focus ring and XP bar, flickering sign level up, NICE pop, header strip, quest cards, lock screen"),
        Def("fx_scifi", "Sci-Fi extras", "Sci-Fi: segment Focus ring and XP bar, hex scan level up, SYNC COMPLETE pop, grid background, header strip, quest cards, lock screen"),
        Def("fx_amoled", "AMOLED extras", "AMOLED: hairline Focus ring and XP bar, thin ring level up, Done pop, header strip, quest cards, lock screen"),
        Def("fx_terminal", "Terminal extras", "Terminal: tick Focus ring with cursor, text XP bar, typed level up, exit 0 pop, scan lines, header strip, quest cards, lock screen"),
        Def("fx_paper", "Paper extras", "Paper: pencil sketch Focus ring and XP bar, NEW CHAPTER stamp, Noted pop, paper specks, header strip, quest cards, lock screen"),
        Def("fx_linen", "Linen extras", "Linen: stitched Focus ring and XP bar, stitched ring level up, Stitched pop, header strip, quest cards, lock screen"),
        Def("fx_sage", "Sage extras", "Sage: dotted Focus ring with sprout, sprout XP bar, leaf burst level up, Sprouted pop, header strip, quest cards, lock screen"),
        Def("fx_sky", "Sky extras", "Sky: sun and cloud Focus ring and XP bar, sun rays level up, Clear skies pop, header strip, quest cards, lock screen"),
        Def("fx_lilac", "Lilac extras", "Lilac: petal Focus ring, flower bead XP bar, petal burst level up, In bloom pop, header strip, quest cards, lock screen"),
        Def("fx_chalk", "Chalk extras", "Chalk: chalk Focus ring and XP bar, A+ level up, Marked pop, chalk dust, header strip, quest cards, lock screen"),
        Def("fx_clay", "Clay extras", "Clay: puffy Focus ring and XP bar, bouncing blobs level up, Squish pop, header strip, quest cards, lock screen"),
        Def("fx_rose", "Rose extras", "Rose: heart Focus ring and XP bar, floating hearts level up, Lovely pop, header strip, quest cards, lock screen"),
        Def("fx_indigo", "Indigo extras", "Indigo: star Focus ring and XP bar, twinkling stars level up, Shooting star pop, star background, header strip, quest cards, lock screen"),
        Def("fx_mono", "Mono extras", "Mono: thin line Focus ring, 2 px XP rule, the level number fading in, a small Done. pop, hairline strip and cards, lock screen"),
        Def("fx_whitespace", "Whitespace extras", "Whitespace: hairline Focus ring and XP rule, two quiet lines level up, divider-only cards, light header strip, lock page with the time"),
        Def("fx_zen", "Zen extras", "Zen: ens\u014D brush Focus ring and XP stroke, the number in a brush circle level up, light line strip and card line, lock screen"),
        Def("fx_carnival", "Carnival extras", "Carnival: marquee bulb Focus ring, candy stripe XP bar, fireworks level up with a light border, confetti pop, striped cards, bunting strip, lock screen"),
        Def("fx_baroque", "Baroque extras", "Baroque: gold double ring Focus ring, gold frame XP bar, wax seal level up, Gilded pop, ornate frames and dividers, lock screen"),
        Def("fx_vaporwave", "Vaporwave extras", "Vaporwave: gradient Focus ring, glitch XP bar, sun grid level up and background, chrome text, INSERT COIN words, lock screen"),
    )

    var HOMEWORK_ENABLED: Boolean = false
        private set

    private val state = mutableStateMapOf<String, Boolean>()

    private fun sp(ctx: android.content.Context) = ctx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE)

    fun load(ctx: android.content.Context) {
        HOMEWORK_ENABLED = sp(ctx).getBoolean("flag_homework", false)
        EXTRA.forEach { state[it.id] = sp(ctx).getBoolean("flag_" + it.id, it.default) }
    }

    fun setHomework(ctx: android.content.Context, on: Boolean) {
        sp(ctx).edit().putBoolean("flag_homework", on).apply()
        HOMEWORK_ENABLED = on
    }

    /** Whether one of the EXTRA flags is on (reads the saved switch, recomposes when it changes). */
    fun get(id: String): Boolean = state[id] ?: EXTRA.firstOrNull { it.id == id }?.default ?: true

    fun set(ctx: android.content.Context, id: String, on: Boolean) {
        sp(ctx).edit().putBoolean("flag_$id", on).apply()
        state[id] = on
        if (id == "themeicon") com.krishna.questblock.ui.AppName.applyAll(ctx)
    }
}
