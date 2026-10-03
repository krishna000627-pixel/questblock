package com.krishna.questblock.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.Calendar

/**
 * Reads Android's own record of which app was on screen (Usage access).
 * Only time with the screen ON is counted, so a locked phone never earns anything.
 */
object UsageTracker {
    // Event ids (written as numbers so older API levels don't complain)
    private const val EV_RESUMED = 1        // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val EV_PAUSED = 2         // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val EV_SCREEN_ON = 15     // SCREEN_INTERACTIVE
    private const val EV_SCREEN_OFF = 16    // SCREEN_NON_INTERACTIVE
    private const val EV_KEYGUARD_SHOWN = 17
    private const val LOOKBACK_MS = 3 * 60 * 60_000L

    class Result(val perPkg: Map<String, Long>, val openPkg: String?)

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground milliseconds per package between [from] and [to] (screen-on only) + the app still open at [to]. */
    fun foreground(ctx: Context, from: Long, to: Long): Result {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Result(emptyMap(), null)
        val events = runCatching { usm.queryEvents(from - LOOKBACK_MS, to) }.getOrNull()
            ?: return Result(emptyMap(), null)

        val ev = UsageEvents.Event()
        val out = HashMap<String, Long>()
        var open: String? = null
        var since = 0L
        var screenOn = true

        fun close(at: Long) {
            val p = open ?: return
            val s = maxOf(since, from)
            val e = minOf(at, to)
            if (e > s) out[p] = (out[p] ?: 0L) + (e - s)
            open = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            when (ev.eventType) {
                EV_RESUMED -> {
                    val p = ev.packageName
                    if (open != p) {
                        close(t)
                        if (screenOn) { open = p; since = t }
                    }
                }
                EV_PAUSED -> if (open == ev.packageName) close(t)
                EV_SCREEN_OFF -> { close(t); screenOn = false }
                EV_SCREEN_ON -> screenOn = true
                EV_KEYGUARD_SHOWN -> close(t)
            }
        }

        val openAtEnd = if (screenOn) open else null
        if (openAtEnd != null) {
            val s = maxOf(since, from)
            if (to > s) out[openAtEnd] = (out[openAtEnd] ?: 0L) + (to - s)
        }
        return Result(out, openAtEnd)
    }

    /** 00:00 of the day [daysAgo] days back. */
    fun dayStart(daysAgo: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 0 = today, 1 = yesterday, 2 = last 7 days (including today). */
    fun rangeFor(range: Int): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (range) {
            1 -> dayStart(1) to dayStart(0)
            2 -> dayStart(6) to now
            else -> dayStart(0) to now
        }
    }

    /** Home screen(s) and system UI are not "apps you used". */
    fun hiddenPackages(ctx: Context): Set<String> {
        val set = HashSet<String>()
        set += "com.android.systemui"
        set += "android"
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching {
            ctx.packageManager.queryIntentActivities(home, 0).forEach { set += it.activityInfo.packageName }
        }
        return set
    }

    fun labelOf(pm: PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}
