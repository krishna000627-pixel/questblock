package com.krishna.questblock.data

/** v260: one group of the "What the app does" list (page opened from About me > Why I built QuestBlock, terminal: about features). */
class FeatureGroup(val title: String, val items: List<String>)

object AppFeatures {
    val INTRO = "Screen time you earn: finish quests, collect coins, and spend them to open the apps you locked."

    val GROUPS: List<FeatureGroup> = listOf(
        FeatureGroup("Quests and rewards", listOf(
            "Six quest types: Daily, Weekly, One-time, Challenge, Routine (with steps) and Focus (a timed session). Each has its own form",
            "Every quest has a category (Study, Work, Health, Personal or Custom), a difficulty, a priority and optional notes",
            "Due dates, repeat every day or on chosen days, challenges that run for a set number of days",
            "Finishing a quest gives XP and coins. Undo is one tap away",
            "Home shows today's quests, sorted by priority and due time",
        )),
        FeatureGroup("Locking and coins", listOf(
            "Lock any app, website or Shorts and Reels. A lock screen appears when a locked app opens, and it works with any launcher",
            "Hold to unlock: pay coins for minutes of time. Short on coins? Do a quest instead",
            "Free opens (a daily-limit app, a study app, a Focus quest, outside lock hours) and a Coin log that names the reason for every earn and spend",
            "Unlock timers page, opened from the unlock notification",
            "Daily Fun and Games limit, app categories and fun rules (loosening a rule waits until midnight) and a Usage page",
            "YouTube gate: study videos pass, fun videos cost, with a Shorts counter and a study-first page for chapters you have not studied",
            "Reliability page: one-tap shortcuts that help Android keep the blocker alive",
        )),
        FeatureGroup("Focus", listOf(
            "Focus sessions with a ring timer, the apps each session needs and background sound (Off, Rain, Brown, Pink or White noise)",
            "Strict focus: only the Focus tab opens and every other app is locked",
            "Focus lock page for apps outside the session, and homework due soon on the Focus tab",
        )),
        FeatureGroup("School", listOf(
            "Attendance for school (once a day) and coaching (two classes a day): present, absent or leave, with coins",
            "Homework tracker with subject chips, a board per subject, due soon, estimates and a weekly report",
            "Chapters: study progress per subject, with coins paid once per chapter",
            "Marks feeder: MT 1, Quarterly, MT 2, Half-yearly and MT 3 for every subject",
            "Weekly review of the last 7 days with one suggestion, and a Mood check-in",
        )),
        FeatureGroup("Player", listOf(
            "Level, XP and rank, a hero profile (name, goal, date of birth) and line style avatars",
            "Badges and achievements, Stats with a daily leaderboard, and the Hub with today's numbers, goals, badges, rules, experiments and sites",
            "Inventory and shop: themes, power-ups, frames and badges bought with coins",
        )),
        FeatureGroup("Looks", listOf(
            "Themes recolour the whole app. Nine style themes (System, Ninja Scroll, Hashira Mist, Shadow Monarch, Gothic Halo, Guild Board, Pixel Arcade, Handheld and Blueprint) also change shapes, fonts and borders",
            "Theme effects: Focus ring, XP bar, level up, quest done pop and lock screens for every theme, with a switch per theme and an effect preview page under Flags",
            "Claymorphism, grayscale look, SVG icons and a picture from your gallery as the background",
            "Termux theme: the whole app as one terminal. Quest Home: QuestBlock as your launcher",
        )),
        FeatureGroup("Tools", listOf(
            "Encrypted backup and a safe restore",
            "Ideas page: write what you want in the next update and copy it into a chat",
            "Version info with the notes of every version, Self test, Debug log and the Flags page",
            "About me with the developer's page and links",
        )),
    )
}
