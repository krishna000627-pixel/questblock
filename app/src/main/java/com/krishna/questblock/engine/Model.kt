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

data class Mission(val headline: String, val steps: List<String>, val suggestions: List<SuggestedQuest>, val source: String)

/** A quest the engine proposes. The user taps + to add it. */
data class SuggestedQuest(val title: String, val difficulty: Int, val category: String, val note: String = "")

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
    suspend fun mission(s: Snapshot): Mission
    suspend fun weekly(s: Snapshot): WeeklyReport
}

class FallbackAdvisor(private val primary: Advisor, private val fallback: Advisor) : Advisor {
    override suspend fun mission(s: Snapshot): Mission = runCatching { primary.mission(s) }.getOrElse { fallback.mission(s) }
    override suspend fun weekly(s: Snapshot): WeeklyReport = runCatching { primary.weekly(s) }.getOrElse { fallback.weekly(s) }
}

object AdvisorProvider {
    /** Offline today. Later: return the Gemini advisor here when a key is saved and internet is allowed. */
    fun get(): Advisor = RulesAdvisor
}
