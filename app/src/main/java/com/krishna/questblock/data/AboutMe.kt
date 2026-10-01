package com.krishna.questblock.data

data class AboutLink(val emoji: String, val label: String, val shown: String, val url: String)

/** Text of the "About me" page (the developer). Edit this file and rebuild to change it. */
object AboutMe {
    const val NAME = "Krishna"
    const val TAGLINE = "Solo Android developer"
    const val BIO = "I build QuestBlock entirely on my phone, with Termux, GitHub Actions and Claude. " +
        "No Android Studio, no laptop."
    const val WHY = "Screen time should be something you earn, not something that happens to you."

    val LINKS = listOf(
        AboutLink("🐙", "GitHub", "krishna000627-pixel/questblock", "https://github.com/krishna000627-pixel/questblock"),
        AboutLink("✉️", "Email", "Krishna000627@gmail.com", "mailto:Krishna000627@gmail.com"),
    )

    const val CREDITS = "Built with Kotlin, Jetpack Compose, Room and Hilt."
}
