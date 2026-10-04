package com.krishna.questblock.engine

import java.text.SimpleDateFormat
import java.util.Calendar
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

    private val DIFF_NAMES = listOf("", "Easy", "Medium", "Hard")

    override suspend fun mission(s: Snapshot): Mission {
        val cal = Calendar.getInstance()
        val minNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val availableMin = (22 * 60 - minNow).coerceAtLeast(0)
        var budget = (availableMin - 60).coerceAtLeast(0)      // keep an hour free

        val steps = ArrayList<String>()
        val sugg = ArrayList<SuggestedQuest>()
        val avgStudy = s.thisWeek.map { it.studyMin }.average().let { if (it.isNaN()) 0.0 else it }
        val studyTarget = if (s.examMode) 360 else round15(avgStudy * 1.1).coerceIn(60, 240)
        val studyLeft = (studyTarget - s.today.studyMin).coerceAtLeast(0)
        val avgFocus = s.thisWeek.map { it.focusMin }.average().let { if (it.isNaN()) 0.0 else it }
        val focusSessions = if (s.examMode) 4 else if (avgFocus < 30) 1 else 2
        val dyn = suggestDifficulty(s)

        steps += if (availableMin > 0) "Time left until 22:00: ${availableMin / 60}h ${availableMin % 60}m" else "It is past 22:00, wind down"
        val open = s.openToday.sortedWith(compareByDescending<QuestBrief> { it.overdue }.thenByDescending { it.difficulty })
        open.take(3).forEach { steps += "Finish: ${it.title}${if (it.overdue) " (overdue)" else ""}" }
        steps += if (studyLeft > 0) "Study $studyLeft more min (target $studyTarget min)" else "Study target of $studyTarget min reached"
        steps += "Do $focusSessions focus session${if (focusSessions > 1) "s" else ""}"
        val avgFun = s.thisWeek.map { it.funMin }.average().let { if (it.isNaN()) 0.0 else it }
        if (s.funLimitMin > 0) steps += "Keep Fun & Games under ${s.funLimitMin} min (used ${s.today.funMin})"
        else if (avgFun > 90) steps += "Try to keep Fun & Games under ${round15(avgFun * 0.8)} min"
        if (s.examMode) steps += "Aim for 8 quests today (exam mode)"
        val rates = (1..3).filter { (s.difficultyRate[it] ?: -1.0) >= 0 }
            .joinToString(", ") { "${DIFF_NAMES[it]} ${(s.difficultyRate[it]!! * 100).toInt()}%" }
        steps += "Quest difficulty tuned to ${DIFF_NAMES[dyn]}" + if (rates.isNotEmpty()) " (you finish: $rates)" else " (not enough history yet)"

        // Adaptive suggestions: fill the time that is left, weakest area first.
        val wanted = if (s.examMode) 8 else 3
        if (open.size < wanted && budget >= 20) {
            if (studyLeft > 0 && budget >= 30) {
                val m = minOf(round15(studyLeft.toDouble()).coerceAtLeast(30), budget, 90)
                sugg += SuggestedQuest("Study $m min", dyn, "Study", "Added by the mission planner"); budget -= m
            }
            if (s.today.focusMin < 25 && budget >= 25) {
                sugg += SuggestedQuest("Focus session 25 min", dyn, "Personal", "Start it from the Focus tab"); budget -= 25
            }
            if (budget >= 20 && sugg.size < 3) {
                sugg += SuggestedQuest("Revise yesterday's notes 20 min", maxOf(1, dyn - 1), "Study", "Added by the mission planner"); budget -= 20
            }
            if ((s.funLimitMin > 0 || avgFun > 90) && sugg.size < 3) {
                val lim = if (s.funLimitMin > 0) s.funLimitMin else round15(avgFun * 0.8).coerceAtLeast(30)
                sugg += SuggestedQuest("Keep Fun under $lim min", 2, "Personal", "Ticks itself after 22:00 if you stay under")
            }
        }
        val head = when {
            s.examMode -> "Exam mode mission"
            s.streak > 0 -> "Protect your ${s.streak}-day streak"
            else -> "Start a streak today"
        }
        return Mission(head, steps, sugg, "Offline rules", availableMin)
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
