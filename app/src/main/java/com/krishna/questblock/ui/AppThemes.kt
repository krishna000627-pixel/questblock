package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent

/**
 * v219: the two app themes (Quest Phone = the normal pages, Termux) as free inventory items,
 * and the flags that turn each one on or off (flags page: 7 taps on the newest entry in Version info; terminal: flags).
 */
object AppThemes {
    val IDS = listOf("phone", "termux")

    fun label(id: String) = when (id) { "phone" -> "Quest Phone"; else -> "Termux" }
    fun itemId(id: String) = "app_$id"
    /** phone | termux | home from a word typed in the terminal (also questphone, questhome, default) */
    fun parse(w: String?): String? = when (w?.lowercase()) {
        "phone", "questphone", "default", "normal" -> "phone"
        "termux", "terminal" -> "termux"
        else -> null
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
    fun enabled(ctx: Context, id: String) = sp(ctx).getBoolean("flag_theme_$id", true)
    fun enabledList(ctx: Context) = IDS.filter { enabled(ctx, it) }
    fun active(): String = if (TermuxOn) "termux" else "phone"

    /** Turn a theme on or off. Returns an error text, or null when done. The one theme left on cannot be turned off. */
    fun setEnabled(ctx: Context, id: String, on: Boolean): String? {
        if (TermuxMode.strictNow(ctx)) return "refused: a strict Focus session is running"
        if (!on && enabled(ctx, id) && enabledList(ctx).size <= 1) return "keep at least one theme on"
        sp(ctx).edit().putBoolean("flag_theme_$id", on).apply()
        if (!on && active() == id) switchTo(ctx, enabledList(ctx).first())
        return null
    }

    /** Equip a theme. Returns an error text, or null when done. */
    fun apply(ctx: Context, id: String): String? {
        if (!enabled(ctx, id)) return "${label(id)} is turned off in flags"
        if (TermuxMode.strictNow(ctx)) return "refused: a strict Focus session is running"
        switchTo(ctx, id)
        return null
    }

    private fun switchTo(ctx: Context, id: String) {
        when (id) {
            "termux" -> TermuxMode.set(ctx, true)
            else -> TermuxMode.set(ctx, false)
        }
    }

    /** If the theme in use was turned off, move to one that is on. */
    fun enforce(ctx: Context) {
        if (!enabled(ctx, active())) enabledList(ctx).firstOrNull()?.let { switchTo(ctx, it) }
    }
}
