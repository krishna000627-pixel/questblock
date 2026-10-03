package com.krishna.questblock.data

import java.util.Calendar

val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
const val WEEKDAYS_MASK = 0b0011111
const val WEEKEND_MASK  = 0b1100000
const val ALL_DAYS_MASK = 0b1111111

/** Monday = 0 … Sunday = 6 */
fun dowIndex(millis: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

fun maskHas(mask: Int, dow: Int): Boolean = ((mask shr dow) and 1) == 1

fun TaskEntity.runsOn(dow: Int): Boolean = daysMask == 0 || maskHas(daysMask, dow)

/** Not done, not waiting for its next scheduled day, and scheduled for today. */
fun TaskEntity.isAvailableNow(now: Long = System.currentTimeMillis()): Boolean =
    !done && availableFrom <= now && runsOn(dowIndex(now))

fun daysLabel(mask: Int): String = when (mask) {
    0, ALL_DAYS_MASK -> ""
    WEEKDAYS_MASK    -> "Weekdays"
    WEEKEND_MASK     -> "Weekends"
    else -> DAY_LABELS.filterIndexed { i, _ -> maskHas(mask, i) }.joinToString(" ")
}

private fun startOfDay(c: Calendar) {
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
}

/** 00:00 of the next day (strictly after today) that is in [mask]. */
fun nextRunDayStart(mask: Int, now: Long): Long {
    val c = Calendar.getInstance()
    for (i in 1..7) {
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_YEAR, i)
        startOfDay(c)
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (mask == 0 || maskHas(mask, dow)) return c.timeInMillis
    }
    return now
}

/** [dayStart] with the hour/minute of [timeSource]. */
fun withTimeOfDay(dayStart: Long, timeSource: Long): Long {
    val src = Calendar.getInstance(); src.timeInMillis = timeSource
    val c = Calendar.getInstance(); c.timeInMillis = dayStart
    c.set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
    c.set(Calendar.MINUTE, src.get(Calendar.MINUTE))
    return c.timeInMillis
}

fun startOfToday(now: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance(); c.timeInMillis = now; startOfDay(c); return c.timeInMillis
}

/** Is the lock enforced at this minute-of-day? (no schedule = always) */
fun BlockedApp.enforcedAt(minuteOfDay: Int): Boolean {
    if (schedStart < 0 || schedEnd < 0 || schedStart == schedEnd) return true
    return if (schedStart < schedEnd) minuteOfDay in schedStart until schedEnd
    else (minuteOfDay >= schedStart || minuteOfDay < schedEnd)
}

fun fmtMinute(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

// ── v10: streaks, quest types, time formatting ──────────────────────────────

/** Monday = 0 … Sunday = 6 for a [dayNumber]. (Day 0 = 1970-01-01, a Thursday.) */
fun dowOfDay(day: Long): Int = (((day + 3) % 7 + 7) % 7).toInt()

/**
 * True if a streak last extended on [lastDoneDay] is still alive on [today]: no scheduled day
 * (per [mask], 0 = every day) went by without it being done.
 */
fun streakContinues(mask: Int, lastDoneDay: Long, today: Long): Boolean {
    if (lastDoneDay <= 0L) return false
    if (lastDoneDay >= today) return true
    for (d in (lastDoneDay + 1) until today) if (mask == 0 || maskHas(mask, dowOfDay(d))) return false
    return true
}

/** Streak to show right now (0 once a scheduled day has been missed). */
fun TaskEntity.streakNow(today: Long): Int =
    if (streak > 0 && streakContinues(daysMask, lastDoneDay, today)) streak else 0

/** "12:30" under an hour, "1:05:30" over. */
fun fmtLeft(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) + 999L) / 1000L   // round up so it never shows 00:00 while still unlocked
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun agoText(ms: Long): String {
    val m = ms / 60_000L
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        else -> "${m / (60 * 24)} d ago"
    }
}

/** Unlock carry-over: true while the user is away from an app that still had unlock time left (same day). */
fun BlockedApp.frozenAt(awayAt: Long, now: Long): Boolean =
    awayAt > 0L && unlockedUntil > awayAt && dayNumber(awayAt) == dayNumber(now)

/** Unlock time left; frozen at the moment the user left while they are away. */
fun BlockedApp.leftMs(awayAt: Long, now: Long): Long =
    if (frozenAt(awayAt, now)) unlockedUntil - awayAt else (unlockedUntil - now).coerceAtLeast(0L)
