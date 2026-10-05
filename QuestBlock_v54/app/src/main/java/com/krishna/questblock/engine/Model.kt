package com.krishna.questblock.engine

/** One day of numbers. Index 0 in [Snapshot.days] is today, 1 is yesterday, and so on. */
data class DayStats(
    val dayStart: Long,
    val studyMin: Int,
    val codingMin: Int,
    val neutralMin: Int,
    val funMin: Int,
    val questsDone: Int,
    val focusMin: Int,
) { val screenMin: Int get() = studyMin + codingMin + neutralMin + funMin }

/** Everything about finished quests. Levels and the score on the Hub come from these, not from screen time. */
data class QuestStats(
    val study: Int = 0,
    val work: Int = 0,
    val health: Int = 0,
    val hard: Int = 0,
    val timer: Int = 0,
    val activeDays: Int = 0,
    val weekDone: Int = 0,
    val weekHard: Int = 0,
    val weekActiveDays: Int = 0,
)

data class QuestBrief(val title: String, val category: String, val difficulty: Int, val overdue: Boolean)

/** Everything the offline engine (and later an AI advisor) is allowed to see. Totals only, no app names. */
data class Snapshot(
    val days: List<DayStats>,
    val streak: Int,
    val xp: Int,
    val level: Int,
    val totalQuestsDone: Int,
    val totalFocusMin: Int,
    val longestFocusMin: Int,
    val openToday: List<QuestBrief>,
    val funLimitMin: Int,
    val examMode: Boolean,
    val usageAccess: Boolean,
    /** Completion rate per difficulty (1..3) from finished vs overdue quests; -1 = not enough data. */
    val difficultyRate: Map<Int, Double>,
    val quests: QuestStats = QuestStats(),
) {
    val today: DayStats get() = days.first()
    val thisWeek: List<DayStats> get() = days.take(7)
    val lastWeek: List<DayStats> get() = days.drop(7).take(7)

    /** Compact summary meant to be sent to an AI later. Never contains app names or raw logs. */
    fun toPromptJson(): String {
        val sb = StringBuilder("{")
        sb.append("\"streak\":$streak,\"level\":$level,\"examMode\":$examMode,\"funLimitMin\":$funLimitMin,")
        sb.append("\"days\":[")
        thisWeek.forEachIndexed { i, d ->
            if (i > 0) sb.append(',')
            sb.append("{\"ago\":$i,\"study\":${d.studyMin},\"coding\":${d.codingMin},\"neutral\":${d.neutralMin},")
            sb.append("\"fun\":${d.funMin},\"quests\":${d.questsDone},\"focus\":${d.focusMin}}")
        }
        sb.append("],\"openQuests\":${openToday.size}}")
        return sb.toString()
    }
}

data class WeeklyReport(
    val lines: List<String>,
    val bestDay: String,
    val weakness: String,
    val recommendation: String,
    val source: String,
)

/**
 * The one place intelligence plugs in. v44 ships [RulesAdvisor] (offline). To add Gemini later:
 * write a class that implements [Advisor] (send [Snapshot.toPromptJson], parse the reply) and return
 * `FallbackAdvisor(GeminiAdvisor(key), RulesAdvisor)` from [AdvisorProvider.get]. Nothing else changes.
 */
interface Advisor {
    suspend fun weekly(s: Snapshot): WeeklyReport
}

class FallbackAdvisor(private val primary: Advisor, private val fallback: Advisor) : Advisor {
    override suspend fun weekly(s: Snapshot): WeeklyReport = runCatching { primary.weekly(s) }.getOrElse { fallback.weekly(s) }
}

object AdvisorProvider {
    /** Offline today. Later: return the Gemini advisor here when a key is saved and internet is allowed. */
    fun get(): Advisor = RulesAdvisor
}
