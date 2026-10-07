package com.krishna.questblock.data

/** One roadmap idea. [text] is exactly what goes onto the Quest board when you add it. */
data class FeatureIdea(val emoji: String, val title: String, val detail: String) {
    val text: String get() = "✨ Feature: $title"
}

/** The built-in roadmap: ideas for future versions. Roadmap page → "Add to board" puts one on the Quest board. */
val FEATURE_IDEAS: List<FeatureIdea> = listOf(
    FeatureIdea("🧘", "Breathing pause before fun apps open",
        "A 10-second wait with a breathing circle before an unlocked fun app opens. Skipping it costs coins."),
    FeatureIdea("🔁", "Spaced revision quests",
        "Finishing a study quest auto-creates short revision quests 1, 3 and 7 days later."),
    FeatureIdea("📝", "Typed pledge to pause strict mode",
        "To use the daily strict pause you must type a 20-word promise first, so it is never a one-tap escape."),
    FeatureIdea("🏰", "Secret realm weekly dungeon",
        "A weekly timed challenge of 5 linked quests. Clearing it pays big XP and a realm-themed badge."),
    FeatureIdea("❤️", "Dao Heart stat",
        "A consistency meter that grows with unbroken days and gives bonus XP; a missed day cracks it."),
    FeatureIdea("🎨", "Realm accent colours",
        "The app accent changes with your cultivation realm (teal for Qi, blue for Foundation, purple for Core)."),
    FeatureIdea("🗓️", "Focus heatmap on Stats",
        "A calendar grid of focus minutes per day for the last 12 weeks, darker means more."),
    FeatureIdea("🎯", "Your goal on the lock page",
        "The lock screen shows your goal and today's top quest instead of a random quote."),
    FeatureIdea("⚔️", "Coin stake pledges",
        "Stake coins on a promise like 'no Shorts today'. Keep it and get double back, break it and lose the stake."),
    FeatureIdea("🌙", "Bedtime streak",
        "Track nights you stayed off fun apps after your bedtime and build a separate streak with rewards."),
)
