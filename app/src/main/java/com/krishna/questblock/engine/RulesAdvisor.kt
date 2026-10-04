package com.krishna.questblock.engine

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Plain rules and averages. Works offline, same input gives same output. */
object RulesAdvisor : Advisor {

    private fun pct(now: Int, before: Int): String? =
        if (before <= 0) null else { val p = (now - before) * 100 / before; (if (p >= 0) "+$p%" else "$p%") }

    override suspend fun weekly(s: Snapshot): WeeklyReport {
        val a = s.thisWeek; val b = s.lastWeek
        fun sum(l: List<DayStats>, f: (DayStats) -> Int) = l.sumOf(f)
        val lines = ArrayList<String>()
        fun line(label: String, now: Int, before: Int, unit: String) {
            val p = pct(now, before)
            lines += "$label: $now$unit" + (if (p != null) "  $p" else "")
        }
        line("Study", sum(a) { it.studyMin }, sum(b) { it.studyMin }, " min")
        line("Focus", sum(a) { it.focusMin }, sum(b) { it.focusMin }, " min")
        line("Fun & Games", sum(a) { it.funMin }, sum(b) { it.funMin }, " min")
        line("Quests done", sum(a) { it.questsDone }, sum(b) { it.questsDone }, "")

        val best = a.maxByOrNull { it.questsDone * 10 + it.studyMin + it.focusMin }
        val bestName = if (best != null && (best.questsDone + best.studyMin + best.focusMin) > 0)
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(best.dayStart)) else "none yet"
        val screen = sum(a) { it.screenMin }.coerceAtLeast(1)
        val funShare = sum(a) { it.funMin } * 100 / screen
        val avgStudy = sum(a) { it.studyMin } / 7
        val weakness: String
        val rec: String
        when {
            funShare >= 40 -> { weakness = "Fun is $funShare% of screen time"; rec = "Lower the Fun limit, study first" }
            avgStudy < 60 -> { weakness = "Study only $avgStudy min a day"; rec = "Add a daily 45 min study quest" }
            sum(a) { it.focusMin } < 60 -> { weakness = "Little focus time"; rec = "One 25 min focus session a day" }
            else -> { weakness = "None"; rec = "Keep going" }
        }
        return WeeklyReport(lines, bestName, weakness, rec, "Offline rules")
    }
}
