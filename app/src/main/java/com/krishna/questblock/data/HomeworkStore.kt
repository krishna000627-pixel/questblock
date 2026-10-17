package com.krishna.questblock.data

import android.content.Context
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * v211: one homework item. [due] is a time in ms. [ch] > 0 links it to chapter number [ch] of the same subject
 * (P C M H E, the Chapters page); [kind] is "study" or "copy": what finishing it ticks there. [est] is the minutes you
 * set yourself (0 = learn it from Focus). [minutes] is the Focus time spent on it so far.
 */
data class HwItem(
    val id: Long,
    val subj: String,
    val text: String,
    val due: Long,
    val ch: Int = 0,
    val kind: String = "",
    val est: Int = 0,
    val addedAt: Long = 0L,
    val doneAt: Long = 0L,
    val onTime: Boolean = false,
    val paid: Boolean = false,
    val carried: Int = 0,
    val minutes: Int = 0,
) {
    val done: Boolean get() = doneAt > 0L
}

class HwBoardRow(val subj: String, val name: String, val due: Int, val overdue: Int, val doneWeek: Int)

class HwWeek(
    val onTime: Int, val late: Int, val open: Int, val overdue: Int, val carried: Int,
    val estMin: Int, val focusMin: Int, val streak: Int, val best: Int,
    val perSubject: List<Triple<String, Int, Int>>,   // subject id, done this week, tracked this week
)

/** The Homework tracker's data, rules and learning. Stored on the phone only (SharedPreferences, no database change). */
object Hw {
    const val ON_TIME_COINS = 3          // handed in before the due time
    const val CARRY_COST = 1             // per item, per day it stays overdue
    const val STREAK_BONUS = 10          // every 7th clean day in a row
    const val DEFAULT_EST_MIN = 25
    const val DUE_SOON_MS = 24L * 60L * 60L * 1000L
    const val SCHOOL_HOUR = 8            // a due day without a time means 08:00 (hand-in at school)
    const val DEFAULT_BED_MIN = 23 * 60

    val SUBJECTS: List<Pair<String, String>> = listOf(
        "P" to "Physics", "C" to "Chemistry", "M" to "Maths", "H" to "Hindi", "E" to "English",
    )

    private val DOW = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("qb_homework", Context.MODE_PRIVATE)

    fun subjectName(id: String): String = SUBJECTS.firstOrNull { it.first == id }?.second ?: id

    fun subjectOf(text: String?): String? = when (text?.trim()?.lowercase()) {
        "p", "phy", "physics" -> "P"
        "c", "chem", "chemistry" -> "C"
        "m", "math", "maths" -> "M"
        "h", "hindi" -> "H"
        "e", "eng", "english" -> "E"
        else -> null
    }

    // ── items ────────────────────────────────────────────────────────────
    fun all(c: Context): List<HwItem> = runCatching {
        val a = JSONArray(sp(c).getString("items", "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            HwItem(
                id = o.getLong("i"), subj = o.getString("s"), text = o.getString("t"), due = o.getLong("d"),
                ch = o.optInt("c"), kind = o.optString("k"), est = o.optInt("e"), addedAt = o.optLong("a"),
                doneAt = o.optLong("x"), onTime = o.optBoolean("o"), paid = o.optBoolean("p"),
                carried = o.optInt("r"), minutes = o.optInt("m"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(c: Context, l: List<HwItem>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("i", it.id).put("s", it.subj).put("t", it.text).put("d", it.due)
                .put("c", it.ch).put("k", it.kind).put("e", it.est).put("a", it.addedAt).put("x", it.doneAt)
                .put("o", it.onTime).put("p", it.paid).put("r", it.carried).put("m", it.minutes))
        }
        sp(c).edit().putString("items", a.toString()).apply()
    }

    fun get(c: Context, id: Long): HwItem? = all(c).firstOrNull { it.id == id }

    fun add(c: Context, subj: String, text: String, due: Long, ch: Int = 0, kind: String = "", est: Int = 0, now: Long = System.currentTimeMillis()): HwItem {
        val s = sp(c)
        val id = s.getLong("seq", 0L) + 1L
        s.edit().putLong("seq", id).apply()
        val item = HwItem(id = id, subj = subj, text = text.trim(), due = due, ch = ch, kind = if (ch > 0) kind.ifEmpty { "study" } else "", est = est, addedAt = now)
        save(c, all(c) + item)
        return item
    }

    fun edit(c: Context, id: Long, f: (HwItem) -> HwItem): HwItem? {
        val l = all(c).toMutableList()
        val i = l.indexOfFirst { it.id == id }
        if (i < 0) return null
        l[i] = f(l[i])
        save(c, l)
        return l[i]
    }

    fun remove(c: Context, id: Long) = save(c, all(c).filter { it.id != id })

    /** Hand in. Learns the Focus time it took when there was some. */
    fun complete(c: Context, id: Long, now: Long = System.currentTimeMillis()): HwItem? {
        val cur = get(c, id) ?: return null
        if (cur.done) return cur
        if (cur.minutes >= 5) learn(c, cur.subj, cur.minutes)
        return edit(c, id) { it.copy(doneAt = now, onTime = now <= it.due) }
    }

    fun reopen(c: Context, id: Long): HwItem? = edit(c, id) { it.copy(doneAt = 0L, onTime = false) }

    // ── time estimates, learned from your Focus sessions ─────────────────
    private fun hist(c: Context): JSONObject = runCatching { JSONObject(sp(c).getString("hist", "{}")) }.getOrDefault(JSONObject())

    private fun learn(c: Context, subj: String, minutes: Int) {
        val h = hist(c)
        val old = h.optJSONArray(subj) ?: JSONArray()
        val l = ArrayList<Int>()
        for (i in 0 until old.length()) l.add(old.optInt(i))
        l.add(minutes)
        val keep = l.takeLast(12)
        h.put(subj, JSONArray(keep))
        sp(c).edit().putString("hist", h.toString()).apply()
    }

    /** Average minutes one finished item of this subject took you in Focus, rounded to 5. Null until there is data. */
    fun learned(c: Context, subj: String): Int? {
        val a = hist(c).optJSONArray(subj) ?: return null
        if (a.length() == 0) return null
        var sum = 0
        for (i in 0 until a.length()) sum += a.optInt(i)
        val avg = sum / a.length()
        return ((avg + 2) / 5 * 5).coerceIn(10, 120)
    }

    fun estimate(c: Context, it: HwItem): Int = if (it.est > 0) it.est else (learned(c, it.subj) ?: DEFAULT_EST_MIN)

    fun estimateIsLearned(c: Context, it: HwItem): Boolean = it.est == 0 && learned(c, it.subj) != null

    // ── Focus sessions started with "hw focus" ───────────────────────────
    fun setPending(c: Context, id: Long, startedAt: Long, plannedMs: Long) {
        sp(c).edit().putLong("pend_id", id).putLong("pend_start", startedAt).putLong("pend_ms", plannedMs).apply()
    }

    /** Adds the minutes of the finished session to its item. Returns a line to show, or null. */
    fun syncFocus(c: Context, logs: List<FocusLog>, now: Long = System.currentTimeMillis()): String? {
        val s = sp(c)
        val id = s.getLong("pend_id", 0L)
        if (id == 0L) return null
        val start = s.getLong("pend_start", 0L)
        val planned = s.getLong("pend_ms", 0L)
        val log = logs.firstOrNull { Math.abs(it.startedAt - start) < 5 * 60_000L }
        if (log == null) {
            if (now > start + planned + 60 * 60_000L) s.edit().remove("pend_id").apply()
            return null
        }
        s.edit().remove("pend_id").apply()
        val item = edit(c, id) { it.copy(minutes = it.minutes + log.minutes) } ?: return null
        return "${log.minutes} min of Focus added to ${subjectName(item.subj)}: ${item.text}"
    }

    // ── planning ─────────────────────────────────────────────────────────
    fun bedMin(c: Context): Int = sp(c).getInt("bed", DEFAULT_BED_MIN)
    fun setBedMin(c: Context, m: Int) { sp(c).edit().putInt("bed", m.coerceIn(0, 24 * 60 - 1)).apply() }

    /** Open items that are overdue or due within the next 24 hours, soonest first. */
    fun dueSoon(c: Context, now: Long = System.currentTimeMillis()): List<HwItem> =
        all(c).filter { !it.done && it.due <= now + DUE_SOON_MS }.sortedBy { it.due }

    /** A line when tonight's list will not fit before bedtime, else null. */
    fun fitWarning(c: Context, now: Long = System.currentTimeMillis()): String? {
        val list = dueSoon(c, now)
        if (list.isEmpty()) return null
        val need = list.sumOf { estimate(c, it) }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val bed = bedMin(c)
        val have = (bed - minuteOfDay).coerceAtLeast(0)
        if (need <= have) return null
        return if (have == 0) "It is past ${hhmm(bed)}: about ${need} min of homework is still open (${list.size} item${if (list.size == 1) "" else "s"})."
        else "Tonight's list is about $need min but only $have min are left before ${hhmm(bed)}. Move something or start now."
    }

    fun hhmm(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    // ── board and week ───────────────────────────────────────────────────
    fun board(c: Context, now: Long = System.currentTimeMillis()): List<HwBoardRow> {
        val items = all(c)
        val weekStart = startOfToday(now) - 6L * 86_400_000L
        return SUBJECTS.map { (id, name) ->
            val mine = items.filter { it.subj == id }
            val open = mine.filter { !it.done }
            HwBoardRow(id, name, open.count { it.due >= now }, open.count { it.due < now }, mine.count { it.done && it.doneAt >= weekStart })
        }
    }

    fun streak(c: Context): Int = sp(c).getInt("streak", 0)
    fun bestStreak(c: Context): Int = sp(c).getInt("best", 0)

    fun week(c: Context, now: Long = System.currentTimeMillis()): HwWeek {
        val items = all(c)
        val weekStart = startOfToday(now) - 6L * 86_400_000L
        val doneWk = items.filter { it.done && it.doneAt >= weekStart }
        val open = items.filter { !it.done }
        val per = SUBJECTS.map { (id, _) ->
            val tracked = items.count { it.subj == id && (it.addedAt >= weekStart || (it.done && it.doneAt >= weekStart) || !it.done) }
            Triple(id, doneWk.count { it.subj == id }, tracked)
        }
        return HwWeek(
            onTime = doneWk.count { it.onTime }, late = doneWk.count { !it.onTime },
            open = open.size, overdue = open.count { it.due < now },
            carried = items.filter { !it.done || it.doneAt >= weekStart }.sumOf { it.carried },
            estMin = doneWk.sumOf { estimate(c, it) }, focusMin = doneWk.sumOf { it.minutes },
            streak = streak(c), best = bestStreak(c), perSubject = per,
        )
    }

    // ── labels ───────────────────────────────────────────────────────────
    fun dueLabel(due: Long, now: Long = System.currentTimeMillis()): String {
        val f = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
        val days = (startOfToday(due) - startOfToday(now)) / 86_400_000L
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(due))
        return when {
            due < now -> {
                val late = (now - due) / 3_600_000L
                if (late < 24) "overdue ${late}h" else "overdue ${late / 24}d"
            }
            days == 0L -> "today $time"
            days == 1L -> "tomorrow $time"
            days < 7L -> f.format(java.util.Date(due)).lowercase()
            else -> java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(due))
        }
    }

    fun parseMinutes(text: String): Int? {
        val t = text.trim().lowercase()
        t.toIntOrNull()?.let { return if (it > 0) it else null }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m?)?$").matchEntire(t) ?: return null
        val total = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        return if (total > 0) total else null
    }

    /**
     * "tomorrow", "today", "fri", "+2d", with an optional time ("fri 18:00"). A day without a time is 08:00
     * (school), except today, which is 23:59 once 08:00 has passed. Null when it can't be read.
     */
    fun parseDue(text: String, now: Long = System.currentTimeMillis()): Long? {
        val parts = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        var minute = -1
        var dayWord = false
        var todayWord = false
        for (p in parts) {
            val t = Regex("^(\\d{1,2}):(\\d{2})$").find(p)
            val plus = Regex("^\\+(\\d{1,2})d$").find(p)
            if (t != null) {
                val h = t.groupValues[1].toInt(); val mi = t.groupValues[2].toInt()
                if (h > 23 || mi > 59) return null
                minute = h * 60 + mi
            } else if (plus != null) {
                cal.add(Calendar.DAY_OF_YEAR, plus.groupValues[1].toInt()); dayWord = true
            } else if (p == "today") {
                dayWord = true; todayWord = true
            } else if (p == "tomorrow" || p == "tmr") {
                cal.add(Calendar.DAY_OF_YEAR, 1); dayWord = true
            } else if (p.length >= 3 && DOW.contains(p.take(3))) {
                val want = DOW.indexOf(p.take(3))
                cal.add(Calendar.DAY_OF_YEAR, (want - dowIndex(cal.timeInMillis) + 7) % 7)
                dayWord = true
            } else return null
        }
        val hadTime = minute >= 0
        if (!hadTime) minute = SCHOOL_HOUR * 60
        cal.set(Calendar.HOUR_OF_DAY, minute / 60)
        cal.set(Calendar.MINUTE, minute % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        var due = cal.timeInMillis
        if (!dayWord && due <= now) {             // only a time was given and it has gone: tomorrow at that time
            cal.add(Calendar.DAY_OF_YEAR, 1)
            due = cal.timeInMillis
        } else if (due <= now && !hadTime && todayWord) {
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            due = cal.timeInMillis
        } else if (due <= now && parts.any { it.length >= 3 && DOW.contains(it.take(3)) } && !parts.contains("today")) {
            cal.add(Calendar.DAY_OF_YEAR, 7)    // that weekday is today and the time has gone: next week
            due = cal.timeInMillis
        }
        return due
    }

    /** "tomorrow 08:00" when nothing was given. */
    fun defaultDue(now: Long = System.currentTimeMillis()): Long = parseDue("tomorrow", now) ?: (now + 24L * 3_600_000L)

    // ── once a day: carry-over cost and the clean-day streak ─────────────
    private fun nextDayStart(dayStart: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        c.add(Calendar.DAY_OF_YEAR, 1)
        return startOfToday(c.timeInMillis)
    }

    /**
     * For every day that has ended since the last check: each item that was overdue at midnight is carried over
     * (costs [CARRY_COST] coin per item, never below 0), and the streak grows by one when nothing was overdue
     * (every 7th day pays [STREAK_BONUS]). Returns the lines to show. Safe to call as often as you like.
     */
    suspend fun dailyTick(c: Context, repo: GameRepository, now: Long = System.currentTimeMillis()): List<String> {
        val s = sp(c)
        val today = startOfToday(now)
        var d = s.getLong("tick_day", 0L)
        if (d == 0L) { s.edit().putLong("tick_day", today).apply(); return emptyList() }
        if (d >= today) return emptyList()
        val notes = ArrayList<String>()
        var guard = 0
        while (d < today && guard < 14) {
            val dayEnd = nextDayStart(d)
            val items = all(c)
            val overdue = items.filter { it.addedAt < dayEnd && it.due < dayEnd && (it.doneAt == 0L || it.doneAt >= dayEnd) }
            // a day only counts for the streak when you were tracking homework that day (an unused tracker earns nothing)
            val tracking = items.any { it.addedAt < dayEnd && (it.doneAt == 0L || it.doneAt >= d) }
            if (overdue.isNotEmpty()) {
                val coins = repo.player.first().coins
                val cost = (overdue.size * CARRY_COST).coerceAtMost(coins)
                overdue.forEach { o -> edit(c, o.id) { it.copy(carried = it.carried + 1) } }
                if (cost > 0) repo.grantBonus(-cost, 0, "Homework", "carried over ${overdue.size}")
                notes.add("${overdue.size} homework item${if (overdue.size == 1) "" else "s"} carried over" + if (cost > 0) ": -$cost coin${if (cost == 1) "" else "s"}" else "")
                s.edit().putInt("streak", 0).apply()
            } else if (tracking) {
                val st = s.getInt("streak", 0) + 1
                val best = maxOf(st, s.getInt("best", 0))
                s.edit().putInt("streak", st).putInt("best", best).apply()
                if (st % 7 == 0) {
                    repo.grantBonus(STREAK_BONUS, 0, "Homework", "$st clean days")
                    notes.add("$st days with nothing overdue: +$STREAK_BONUS coins")
                }
            }
            d = dayEnd
            guard++
        }
        s.edit().putLong("tick_day", today).apply()
        return notes
    }
}
