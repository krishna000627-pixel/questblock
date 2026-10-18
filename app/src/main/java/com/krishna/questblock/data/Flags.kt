package com.krishna.questblock.data

/** v215: switches that cut a feature loose without deleting it. v219: the homework switch is a flag (flags page / terminal flags), off by default. */
object Flags {
    var HOMEWORK_ENABLED: Boolean = false
        private set

    fun load(ctx: android.content.Context) {
        HOMEWORK_ENABLED = ctx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE).getBoolean("flag_homework", false)
    }

    fun setHomework(ctx: android.content.Context, on: Boolean) {
        ctx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE).edit().putBoolean("flag_homework", on).apply()
        HOMEWORK_ENABLED = on
    }
}
