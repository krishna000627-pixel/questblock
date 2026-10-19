package com.krishna.questblock.data

import androidx.compose.runtime.mutableStateMapOf

/** v215: switches that cut a feature loose without deleting it. v219: the homework switch is a flag (flags page / terminal flags), off by default. v225: more flags (EXTRA), on by default. */
object Flags {
    class Def(val id: String, val label: String, val desc: String, val default: Boolean = true)

    /** v225: flags that hide one feature on the normal pages and in the terminal. */
    val EXTRA: List<Def> = listOf(
        Def("mood", "Mood check-in", "The Mood section on the Attendance page, terminal mood"),
        Def("leaderboard", "Daily leaderboard", "The leaderboard card on Stats, terminal leaderboard"),
        Def("experiments", "Experiments card", "The Experiments card in the Hub"),
        Def("themefx", "Theme extras (all)", "Master switch for the own words and effects of the style themes. Each theme also has its own switch below"),
        Def("fx_system", "System extras", "System Window: result window, [SYSTEM] words"),
        Def("fx_hashira", "Hashira Mist extras", "Hashira Mist: Form 1 to 3 words, mist puff after a quest"),
        Def("fx_monarch", "Shadow Monarch extras", "Shadow Monarch: shadow army counter, SHADOW EXTRACTED burst, rank words"),
        Def("fx_gothic", "Gothic Halo extras", "Gothic Halo: red halo rings, days left in the strip, rite words"),
        Def("fx_guild", "Guild Board extras", "Guild Board: tilted notices, wax seal, Copper Silver Gold words"),
        Def("fx_pixel", "Pixel Arcade extras", "Pixel Arcade: +coins pop, GAME OVER, STAGE words"),
        Def("fx_handheld", "Handheld extras", "Handheld: QUESTBOY boot screen, SAVING box"),
        Def("fx_blueprint", "Blueprint extras", "Blueprint: APPROVED stamp, dimension lines, TYPE words"),
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
    }
}
