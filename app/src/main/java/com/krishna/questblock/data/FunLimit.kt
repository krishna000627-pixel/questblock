package com.krishna.questblock.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which fun app is on screen right now (set by the accessibility service). Used for the notification line. */
object FunSignal {
    @Volatile var pkg: String? = null
    /** True when the lock page is shown because of quiet/study hours (not because the daily limit ran out). */
    @Volatile var quiet: Boolean = false
    /** True while YouTube Shorts is on screen (so the time-left bubble can show there too). */
    @Volatile var shortsOn: Boolean = false
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
        val baseMs = prefs.effectiveFunLimitMin * 60_000L
        // "+5 min" bought with coins today is added on top (only when a limit is set)
        val limitMs = if (baseMs > 0L) baseMs + prefs.funBonusTodayMs() else 0L
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
            val from = UsageTracker.dayStart(0)
            val res = UsageTracker.foreground(ctx, from, now)
            val per = res.perPkg
            val onScreen = per.entries.filter { AppCategorizer.categoryOf(prefs, pm, it.key) == "fun" }.sumOf { it.value }
            val ghostMs = if (prefs.ghostCountsOn) runCatching { UsageTracker.ghost(ctx, prefs, from, now, res.spans).values.sum() }.getOrDefault(0L) else 0L
            // Shorts time counts as fun time, unless YouTube itself is already a Fun & Games app (then it is counted above)
            val shortsMs = if (AppCategorizer.categoryOf(prefs, pm, "com.google.android.youtube") == "fun") 0L else prefs.shortsMsToday()
            onScreen + ghostMs + shortsMs
        }
        val s = State(used, limitMs, now)
        last = s
        return s
    }
}
