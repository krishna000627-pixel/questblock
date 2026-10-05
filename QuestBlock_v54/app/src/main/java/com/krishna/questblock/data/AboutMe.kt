package com.krishna.questblock.data

data class AboutLink(val emoji: String, val label: String, val shown: String, val url: String)

/** Text of the "About me" page (the developer). Edit this file and rebuild to change it. */
object AboutMe {
    const val NAME = "Krishna"
    const val TAGLINE_FULL = "Class 12 student · Solo Android developer"
    const val TAGLINE = "Class 12 student · Solo Android developer"
    const val BIO =
        "I’m Krishna, a Class 12 PCM student and solo Android developer. I build and maintain two apps " +
        "— QuestPhone (a gamified Android launcher) and QuestBlock (this app) — entirely from my phone. " +
        "No Android Studio. No laptop. Just Termux, GitHub Actions and Claude.

" +
        "I’ve been writing code since school, mostly Kotlin for Android. I read code structurally and " +
        "catch bugs by pattern recognition rather than writing everything from scratch."
    const val WHY =
        "Screen time should be something you earn, not something that happens to you. I built QuestBlock " +
        "because I needed it myself: a blocker that doesn’t just lock apps, but makes you pay for " +
        "distraction with coins you earn from studying."
    const val OTHER_PROJECTS = "QuestPhone — a custom Android launcher with gamified habits, app blocking, " +
        "an RPG stats system, a cosmetic theme store and an AI companion called Kai. Package: neth.iecal.questphone"
    const val TOOLS = "Kotlin · Jetpack Compose · Hilt · Room · Termux · GitHub Actions · Claude"
    const val CREDITS = TOOLS
    val LINKS = listOf(
        AboutLink("🐙", "GitHub", "krishna000627-pixel/questblock", "https://github.com/krishna000627-pixel/questblock"),
        AboutLink("✉️", "Email", "Krishna000627@gmail.com", "mailto:Krishna000627@gmail.com"),
    )

    const val CREDITS = "Built with Kotlin, Jetpack Compose, Room and Hilt."
}
