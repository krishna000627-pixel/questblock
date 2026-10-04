package com.krishna.questblock.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which fun app is on screen right now (set by the accessibility service). Used for the notification line. */
object FunSignal {
    @Volatile var pkg: String? = null
    fun enter(p: String) { pkg = p }
    fun leave() { pkg = null }
}

/**
 * Daily time limit for the Fun & Games category.
 * Time comes from Android's usage record (screen-on only), the same numbers as the App usage page.
 */
object FunLimit {
    class State(val usedMs: Long, val limitMs: Long, val at: Long) {
        val enabled: Boolean get() = limitMs > 0L
        val reached: Boolean get() = enabled && usedMs >= limitMs
    }

    @Volatile var last: State? = null

    /** Today's fun time. Cached for 45 s unless [force]. Without Usage access the limit is simply off. */
    suspend fun state(ctx: Context, prefs: Prefs, force: Boolean = false): State {
        val limitMs = prefs.funLimitMin * 60_000L
        val now = System.currentTimeMillis()
        val c = last
        if (!force && c != null && c.limitMs == limitMs && now - c.at < 45_000L && dayNumber(c.at) == dayNumber(now)) return c
        if (limitMs <= 0L || !UsageTracker.hasAccess(ctx)) {
            val off = State(0L, 0L, now)
            last = off
            return off
        }
        val used = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            UsageTracker.foreground(ctx, UsageTracker.dayStart(0), now).perPkg.entries
                .filter { AppCategorizer.categoryOf(prefs, pm, it.key) == "fun" }
                .sumOf { it.value }
        }
        val s = State(used, limitMs, now)
        last = s
        return s
    }
}
