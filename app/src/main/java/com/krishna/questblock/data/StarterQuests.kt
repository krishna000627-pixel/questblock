package com.krishna.questblock.data

/** Ready-made first quests for a student starting from the basics (Class 12 Boards + JEE, PCM). */
data class StarterQuest(
    val emoji: String,
    val title: String,
    val note: String,
    val difficulty: Int = 1,   // 1 = Easy
)

val STARTER_QUESTS: List<StarterQuest> = listOf(
    StarterQuest("📐", "Units & dimensions: learn the 7 base units", "Physics basics · 20 min"),
    StarterQuest("🏃", "Kinematics: 3 formulas + 10 easy questions", "Physics · 30 min"),
    StarterQuest("🧲", "Read NCERT Electrostatics, note 5 key points", "Physics (Boards) · 30 min"),
    StarterQuest("⚗️", "Mole concept: understand it, solve 10 problems", "Chemistry basics · 30 min"),
    StarterQuest("🧪", "Periodic table: write the 3 main trends", "Chemistry · 20 min"),
    StarterQuest("📗", "NCERT Chemistry: read one chapter, highlight 5 lines", "Chemistry (Boards) · 30 min"),
    StarterQuest("➗", "Quadratic equations: 15 basic problems", "Maths basics · 30 min"),
    StarterQuest("📏", "Trigonometry: write all identities on one sheet", "Maths · 20 min"),
    StarterQuest("📈", "Functions & graphs: draw 5 basic graphs", "Maths · 25 min"),
    StarterQuest("♾️", "Limits: read the idea, solve 10 easy ones", "Maths · 30 min"),
    StarterQuest("✍️", "Write a 150-word letter or essay", "English (Boards) · 20 min"),
    StarterQuest("📓", "Formula notebook: write 10 formulas", "Any subject · 15 min"),
    StarterQuest("📄", "Solve one section of a previous-year board paper", "Boards · 45 min", 2),
    StarterQuest("🎯", "Do 20 mixed basic questions (PCM)", "JEE warm-up · 30 min"),
    StarterQuest("🔁", "Revise yesterday's notes, no new topic", "Any subject · 15 min"),
    StarterQuest("📅", "Make a simple weekly study plan", "Planning · 15 min"),
)
