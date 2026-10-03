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
    private const val EV_STOPPED = 23       // ACTIVITY_STOPPED
    private const val EV_SHUTDOWN = 26      // DEVICE_SHUTDOWN
    private const val EV_STARTUP = 27       // DEVICE_STARTUP
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
        // Activities currently resumed in the foreground package (only one package at a time).
        var fgPkg: String? = null
        val fgActs = HashSet<String>()

        fun close(at: Long) {
            val p = open ?: return
            val s = maxOf(since, from)
            val e = minOf(at, to)
            if (e > s) out[p] = (out[p] ?: 0L) + (e - s)
            open = null
        }
        fun clearAll(at: Long) { close(at); fgPkg = null; fgActs.clear() }
        fun sync(at: Long) {
            val want = if (screenOn && fgActs.isNotEmpty()) fgPkg else null
            if (want != open) {
                close(at)
                if (want != null) { open = want; since = at }
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            val pkg = ev.packageName ?: ""
            val cls = ev.className ?: ""
            when (ev.eventType) {
                EV_RESUMED -> {
                    if (fgPkg != pkg) { fgActs.clear(); fgPkg = pkg }
                    fgActs += cls
                    sync(t)
                }
                EV_PAUSED, EV_STOPPED -> if (fgPkg == pkg) {
                    fgActs -= cls
                    sync(t)
                }
                EV_SCREEN_OFF -> { screenOn = false; clearAll(t) }
                EV_SCREEN_ON -> { screenOn = true; sync(t) }
                EV_KEYGUARD_SHOWN, EV_SHUTDOWN, EV_STARTUP -> clearAll(t)
            }
        }

        val openAtEnd = if (screenOn) open else null
        if (openAtEnd != null) {
            val s = maxOf(since, from)
            if (to > s) out[openAtEnd] = (out[openAtEnd] ?: 0L) + (to - s)
        }
        return Result(out, openAtEnd)
    }

    /**
     * Android's own pre-computed daily totals (the same numbers Digital Wellbeing is built on).
     * Used for the App usage page so it matches the phone's own screen time.
     */
    fun systemTotals(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val list = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from - 36 * 60 * 60_000L, to) }
            .getOrNull() ?: return emptyMap()
        val tz = java.util.TimeZone.getDefault()
        val day = 24 * 60 * 60_000L
        // Local calendar day number of a timestamp. Buckets can start a bit off midnight, so
        // classify each one by the day its middle falls in, not by its start.
        fun dayNo(t: Long) = Math.floorDiv(t + tz.getOffset(t), day)
        val firstDay = dayNo(from)
        val lastDay = dayNo(to - 1)
        // Android can hold several overlapping records for the same app and day.
        // Take the biggest one per app per day instead of adding them together.
        val perDay = HashMap<Pair<String, Long>, Long>()
        for (u in list) {
            val t = u.totalTimeInForeground
            if (t <= 0) continue
            val d = dayNo(u.firstTimeStamp + 12 * 60 * 60_000L)
            if (d < firstDay || d > lastDay) continue
            val k = u.packageName to d
            if (t > (perDay[k] ?: 0L)) perDay[k] = t
        }
        val out = HashMap<String, Long>()
        for ((k, t) in perDay) out[k.first] = (out[k.first] ?: 0L) + t
        return out
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
