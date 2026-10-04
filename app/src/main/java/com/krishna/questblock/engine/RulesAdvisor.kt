package com.krishna.questblock.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain rules and averages. Works offline, same input gives same output. */
object RulesAdvisor : Advisor {

    private fun round15(v: Double): Int = (Math.round(v / 15.0) * 15).toInt()

    /** The difficulty to suggest: the highest one the user finishes at least 70% of the time (needs 3+ samples). */
    fun suggestDifficulty(s: Snapshot): Int {
        var best = 0
        for (d in 1..3) { val r = s.difficultyRate[d] ?: -1.0; if (r >= 0.7) best = d }
        return if (best == 0) (if (s.difficultyRate.values.any { it >= 0 }) 1 else 2) else best
    }

    override suspend fun mission(s: Snapshot): Mission {
        val steps = ArrayList<String>()
        val sugg = ArrayList<SuggestedQuest>()
        val avgStudy = s.thisWeek.map { it.studyMin }.average().let { if (it.isNaN()) 0.0 else it }
        val studyTarget = if (s.examMode) 360 else round15(avgStudy * 1.1).coerceIn(60, 240)
        val studyLeft = (studyTarget - s.today.studyMin).coerceAtLeast(0)
        val avgFocus = s.thisWeek.map { it.focusMin }.average().let { if (it.isNaN()) 0.0 else it }
        val focusSessions = if (s.examMode) 4 else if (avgFocus < 30) 1 else 2
        val dyn = suggestDifficulty(s)

        val open = s.openToday.sortedWith(compareByDescending<QuestBrief> { it.overdue }.thenByDescending { it.difficulty })
        open.take(3).forEach { steps += "Finish: ${it.title}${if (it.overdue) " (overdue)" else ""}" }
        if (studyLeft > 0) steps += "Study $studyLeft more min (target $studyTarget min)"
        else steps += "Study target of $studyTarget min reached"
        steps += "Do $focusSessions focus session${if (focusSessions > 1) "s" else ""}"
        if (s.funLimitMin > 0) steps += "Keep Fun & Games under ${s.funLimitMin} min (used ${s.today.funMin})"
        else {
            val avgFun = s.thisWeek.map { it.funMin }.average().let { if (it.isNaN()) 0.0 else it }
            if (avgFun > 90) steps += "Try to keep Fun & Games under ${round15(avgFun * 0.8)} min"
        }

        if (open.isEmpty()) {
            val m = if (studyLeft > 0) studyLeft.coerceAtLeast(30) else 30
            sugg += SuggestedQuest("Study $m min", dyn, "Study", "Added by the mission planner")
            sugg += SuggestedQuest("Deep focus 25 min", dyn, "Study", "Added by the mission planner")
            if (s.funLimitMin > 0 || s.thisWeek.map { it.funMin }.average() > 90)
                sugg += SuggestedQuest("Fun apps under limit today", 2, "Personal", "Self-check, tick when the day ends")
        }
        val head = when {
            s.examMode -> "Exam mode mission"
            s.streak > 0 -> "Protect your ${s.streak}-day streak"
            else -> "Start a streak today"
        }
        return Mission(head, steps, sugg, "Offline rules")
    }

    private fun pct(now: Int, before: Int): String? =
        if (before <= 0) null else { val p = (now - before) * 100 / before; (if (p >= 0) "+$p%" else "$p%") }

    override suspend fun weekly(s: Snapshot): WeeklyReport {
        val a = s.thisWeek; val b = s.lastWeek
        fun sum(l: List<DayStats>, f: (DayStats) -> Int) = l.sumOf(f)
        val lines = ArrayList<String>()
        fun line(label: String, now: Int, before: Int, unit: String) {
            val p = pct(now, before)
            lines += "$label: $now$unit" + (if (p != null) " ($p vs last week)" else "")
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
            funShare >= 40 -> { weakness = "Fun & Games is $funShare% of your screen time"; rec = "Set or lower the Fun limit, and do study quests before opening fun apps." }
            avgStudy < 60 -> { weakness = "Study averages only $avgStudy min a day"; rec = "Add one 45 min study quest every day and finish it early in the day." }
            sum(a) { it.focusMin } < 60 -> { weakness = "Very little focus time this week"; rec = "Try one 25 min focus session a day." }
            else -> { weakness = "Nothing stands out"; rec = "Keep the routine and raise the study target a little." }
        }
        return WeeklyReport(lines, bestName, weakness, rec, "Offline rules")
    }
}
