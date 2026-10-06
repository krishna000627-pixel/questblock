package com.krishna.questblock.data

data class VersionEntry(val version: String, val headline: String, val items: List<String>)

val VERSION_LOG: List<VersionEntry> = listOf(
    VersionEntry("62.0.0", "Website unlock, debug page, ideas",
        listOf(
            "Blocked websites can be unlocked with coins, with the same timer and notification as apps",
            "Home/Back on a blocked site moves the browser off it so other sites keep working",
            "Debug log has its own page",
            "Notification permission and notification access are in the permission window",
            "About me: new page and widget to write update prompts and ideas, with Copy",
        )),
    VersionEntry("53.0.0", "Commitment, health check, Backup 2.0, breaks",
        listOf(
            "Fun limit is now a commitment: tightening applies now, loosening waits until midnight",
            "Anti-bypass health check: Home warns you and a notification fires if Accessibility, Usage access or battery settings are switched off while a fun rule is on",
            "Backup 2.0: backup now includes all settings (fun limit, categories, shields, theme). A daily copy is saved automatically to Download/QuestBlock (keeps newest 3)",
            "Streak Repair: new 150-coin shop item that restores a streak broken in the last 3 days",
            "Focus break cycles: 5-min break after every session, 15-min after every 4th. A card shows on the Focus page with a Skip button",
        )),
    VersionEntry("52.0.0", "Player Hub rebuilt",
        listOf(
            "Hub split into sub-pages (Goals, Reports, Badges, Rules)",
            "Attributes now come from completed quests by category, not raw app time. Fitness attribute added",
            "Unlocked badges stay unlocked permanently",
            "Hub loads fast: finished days are cached, page shows instantly",
            "Quiet hours and Study hours lock: fun apps stay locked in windows you set",
            "Exam mode now actually locks Fun apps (08:00–20:00)",
            "Context-aware blocking, seasons with reward goals, adaptive mission planner",
        )),
    VersionEntry("46.0.0", "Build fix",
        listOf("Fixed JVM clash (setAutoVerify) in HubViewModel that caused a CI build failure")),
    VersionEntry("45.0.0", "Hub, Focus engine, economy",
        listOf(
            "Player Hub: ranks, season, discipline score, attributes, badges, challenges, weekly report, economy summary",
            "Advanced Focus engine: modes, streak, score, give-up penalty, nudge",
            "Economy: earn/spend overview, streak bonuses, hard-quest XP bonus",
            "Auto-verify: Study quests with a time in the title tick from study-app usage",
            "Context-aware and adaptive mission planner (offline rules)",
        )),
    VersionEntry("43.0.0", "Fun time limit",
        listOf(
            "Daily cap for all Fun & Games apps. Set it in Settings → Apps",
            "Free Fire (and Free Fire Max) are never cut mid-match. They lock the next time you open them after the limit, with a 5-minute rejoin window",
            "Free time resets at midnight",
        )),
    VersionEntry("42.0.0", "Usage accuracy",
        listOf(
            "App usage page now records screen on/off events from a broadcast receiver so apps left 'open' overnight no longer cause a 13-hour overcount",
            "60-minute no-event cap prevents counting long offline videos indefinitely",
            "Today's numbers match Digital Wellbeing to within 15-20 minutes",
        )),
    VersionEntry("36.0.0", "Single clock in notification",
        listOf("Removed the second ticking time from the notification title/text. The system countdown in the header is the only clock, so they can't disagree")),
    VersionEntry("35.0.0", "Instant study bar",
        listOf(
            "Study bar appears the moment a study app opens (accessibility signal), not after the next 20-second check",
            "Coins are still paid from Android's usage record so a locked screen earns nothing",
            "Checks run every 10 s while a study app is open, 20 s otherwise",
        )),
    VersionEntry("34.0.0", "Notification priority fix",
        listOf("Study line only shows while a study app is on screen. Unlock line wins otherwise")),
    VersionEntry("33.0.0", "App categories and daily usage page",
        listOf(
            "Study coins now come from Android's usage record (Usage access). Leftover minutes carry over",
            "App categories page: sort every installed app into Study / Coding / Neutral / Fun & Games",
            "Daily app usage page: screen time split by category with a coloured bar",
            "Unused unlock time carries over (time away or screen-off is not charged)",
        )),
    VersionEntry("32.0.0", "Suggested quests and startup tint",
        listOf(
            "Suggested tab on the Quests screen: 16 Class 12 / JEE basics. Opens automatically when nothing is planned today",
            "Home fills empty space with suggested quests when today's list is done",
            "No dark startup flash even when the phone is in dark mode but the app is in Light",
        )),
    VersionEntry("29.0.0", "Focus page redesign and lock page cleanup",
        listOf(
            "Focus page: large circular dial, Today strip, recent sessions, full-screen active timer",
            "Lock page: stepper hidden while a timer runs, Open button is green, streak line shows only when you have a streak, build label removed",
            "Home: slim Shop and Stats pills instead of two large cards",
        )),
    VersionEntry("28.0.0", "Light theme",
        listOf("Dark / Light / System theme choice in Settings → Look. Applies to every screen including the lock page")),
    VersionEntry("27.0.0", "Open only on tap",
        listOf("After paying to unlock, the app opens only when you tap Open. It no longer opens automatically")),
    VersionEntry("26.0.0", "Unlock crash fix",
        listOf("Vibrate permission was missing and the call was uncaught, causing the app to crash every time the hold bar finished. Added VIBRATE permission and guarded the call")),
    VersionEntry("25.0.0", "Debug log",
        listOf("Persistent debug log in Settings (Refresh, Copy, Clear). Used to find the unlock crash")),
    VersionEntry("24.0.0", "Version label",
        listOf("Version now shown on the lock page to confirm which build is installed. Stale 'paying' state reset on each new lock page")),
    VersionEntry("22.0.0", "Atomic coin spend",
        listOf("Coin deduction is now a single atomic database command (UPDATE WHERE coins >= cost). Old read-modify-write could be overwritten by another writer")),
    VersionEntry("20.0.0", "Single unlock notification",
        listOf("One persistent notification replaces the old 'QuestBlock is on' line. Shows the unlock countdown with a progress bar and 5-minute block tracker. Quiet/idle when nothing is unlocked")),
    VersionEntry("19.0.0", "Notification redesign",
        listOf("Unlock timer notification with live countdown and 5-minute block tracker")),
    VersionEntry("18.0.0", "Unlock hold",
        listOf("Hold-to-unlock gesture, vibration on complete, unlock cost stepper")),
    VersionEntry("11.0.0", "Foundation",
        listOf(
            "No double charge on unlock",
            "Coins per 5 min in study apps",
            "Live unlock timer",
            "Coin log",
            "Reliability improvements",
            "Quest types",
        )),
)

const val NEXT_UP = """
• Exam countdown with daily study-time pace
• PCM chapter tracker (feeds Knowledge attribute)
• Mistake log with spaced revision reminders
• Mock test score tracker
"""
