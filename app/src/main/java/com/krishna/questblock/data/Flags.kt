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
        Def("themefx", "Theme extras", "Own words and effects of each style theme: quest done stamps, rank names, empty page texts, the shadow army, the boot screen"),
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
