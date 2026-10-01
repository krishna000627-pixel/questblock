package com.krishna.questblock.data

data class QuestTypeInfo(val id: String, val emoji: String, val name: String, val blurb: String)

/** Order here = order of the picker page and of the groups on the Quests tab. */
val QUEST_TYPES = listOf(
    QuestTypeInfo("daily",     "🔥", "Daily",     "Every day. Builds a streak."),
    QuestTypeInfo("weekly",    "📅", "Weekly",    "Only on the days you pick."),
    QuestTypeInfo("challenge", "🏆", "Challenge", "Do it every day for N days in a row."),
    QuestTypeInfo("routine",   "📋", "Routine",   "A checklist of steps. Tick them all to finish."),
    QuestTypeInfo("focus",     "🎯", "Focus",     "A timed session that unlocks the apps you choose."),
    QuestTypeInfo("onetime",   "✅", "One-time",  "A plain to-do. Done once."),
)

fun questTypeInfo(id: String): QuestTypeInfo = QUEST_TYPES.firstOrNull { it.id == id } ?: QUEST_TYPES.last()
