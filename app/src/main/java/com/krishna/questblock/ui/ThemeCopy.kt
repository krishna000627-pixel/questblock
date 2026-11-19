package com.krishna.questblock.ui

import com.krishna.questblock.data.Flags

/**
 * v295: Per-theme UI/UX strings for all themes (Tavern/Habitica excluded as requested).
 * Toggleable via Flag "theme_strings".
 */
object ThemeCopy {
    private val MAP: Map<String, Map<String, String>> = mapOf(
        "theme_system" to mapOf("homeTab" to "STATUS", "questsTab" to "QUESTS", "focusTab" to "DUNGEON", "vaultTab" to "GATE", "profileTab" to "HUNTER", "coinsLabel" to "CREDITS", "streakLabel" to "STREAK"),
        "theme_monarch" to mapOf("homeTab" to "THRONE", "questsTab" to "EXTRACTS", "focusTab" to "ABYSS", "vaultTab" to "GATES", "profileTab" to "MONARCH", "coinsLabel" to "SOULS", "streakLabel" to "REIGN"),
        "theme_ninja" to mapOf("homeTab" to "Scroll", "questsTab" to "Missions", "focusTab" to "Chakra", "vaultTab" to "Seals", "profileTab" to "Shinobi", "coinsLabel" to "Ryo", "streakLabel" to "Bond"),
        "theme_hashira" to mapOf("homeTab" to "Estate", "questsTab" to "Forms", "focusTab" to "Breath", "vaultTab" to "Mist", "profileTab" to "Slayer", "coinsLabel" to "Yen", "streakLabel" to "Forms"),
        "theme_gothic" to mapOf("homeTab" to "Sanctum", "questsTab" to "Rites", "focusTab" to "Vigil", "vaultTab" to "Crypt", "profileTab" to "Knight", "coinsLabel" to "Marks", "streakLabel" to "Oath"),
        "theme_guild" to mapOf("homeTab" to "Hall", "questsTab" to "Contracts", "focusTab" to "Watch", "vaultTab" to "Strongbox", "profileTab" to "Adventurer", "coinsLabel" to "Gold", "streakLabel" to "Days"),
        "theme_pixel" to mapOf("homeTab" to "1P", "questsTab" to "STAGES", "focusTab" to "BONUS", "vaultTab" to "LOCK", "profileTab" to "PLAYER", "coinsLabel" to "COINS", "streakLabel" to "CHAIN"),
        "theme_handheld" to mapOf("homeTab" to "ROM", "questsTab" to "SAVE", "focusTab" to "TIMER", "vaultTab" to "CART", "profileTab" to "HERO", "coinsLabel" to "PTS", "streakLabel" to "RUN"),
        "theme_blueprint" to mapOf("homeTab" to "SHEET 1", "questsTab" to "SPECS", "focusTab" to "BUILD", "vaultTab" to "LOCKED", "profileTab" to "DRAFTER", "coinsLabel" to "UNITS", "streakLabel" to "REV"),
        "theme_sunset" to mapOf("homeTab" to "Horizon", "questsTab" to "Goals", "focusTab" to "Golden", "vaultTab" to "Shade", "profileTab" to "Traveler", "coinsLabel" to "Sun", "streakLabel" to "Days"),
        "theme_ocean" to mapOf("homeTab" to "Harbor", "questsTab" to "Tides", "focusTab" to "Dive", "vaultTab" to "Anchor", "profileTab" to "Voyager", "coinsLabel" to "Pearls", "streakLabel" to "Waves"),
        "theme_forest" to mapOf("homeTab" to "Camp", "questsTab" to "Trails", "focusTab" to "Canopy", "vaultTab" to "Hollow", "profileTab" to "Ranger", "coinsLabel" to "Acorns", "streakLabel" to "Roots"),
        "theme_neon" to mapOf("homeTab" to "GRID", "questsTab" to "GIGS", "focusTab" to "OVERDRIVE", "vaultTab" to "ICE", "profileTab" to "RUNNER", "coinsLabel" to "EDDIES", "streakLabel" to "HEAT"),
        "theme_scifi" to mapOf("homeTab" to "BRIDGE", "questsTab" to "LOGS", "focusTab" to "HYPER", "vaultTab" to "SHIELD", "profileTab" to "PILOT", "coinsLabel" to "CREDITS", "streakLabel" to "CYCLE"),
        "theme_amoled" to mapOf("homeTab" to "Home", "questsTab" to "Tasks", "focusTab" to "Focus", "vaultTab" to "Lock", "profileTab" to "You", "coinsLabel" to "Coins", "streakLabel" to "Streak"),
        "theme_terminal" to mapOf("homeTab" to "~/", "questsTab" to "jobs", "focusTab" to "cron", "vaultTab" to "chmod", "profileTab" to "whoami", "coinsLabel" to "CRED", "streakLabel" to "UPTIME"),
        "theme_paper" to mapOf("homeTab" to "Desk", "questsTab" to "Notes", "focusTab" to "Study", "vaultTab" to "Drawer", "profileTab" to "Author", "coinsLabel" to "Seals", "streakLabel" to "Pages"),
        "theme_linen" to mapOf("homeTab" to "Loom", "questsTab" to "Threads", "focusTab" to "Weave", "vaultTab" to "Chest", "profileTab" to "Tailor", "coinsLabel" to "Spools", "streakLabel" to "Rows"),
        "theme_sage" to mapOf("homeTab" to "Garden", "questsTab" to "Seeds", "focusTab" to "Bloom", "vaultTab" to "Shed", "profileTab" to "Keeper", "coinsLabel" to "Leaves", "streakLabel" to "Days"),
        "theme_sky" to mapOf("homeTab" to "Deck", "questsTab" to "Flights", "focusTab" to "Glide", "vaultTab" to "Hangar", "profileTab" to "Aviator", "coinsLabel" to "Miles", "streakLabel" to "Skies"),
        "theme_lilac" to mapOf("homeTab" to "Arbor", "questsTab" to "Petals", "focusTab" to "Calm", "vaultTab" to "Gate", "profileTab" to "Dreamer", "coinsLabel" to "Gems", "streakLabel" to "Blooms"),
        "theme_chalk" to mapOf("homeTab" to "Board", "questsTab" to "Drills", "focusTab" to "Period", "vaultTab" to "Locker", "profileTab" to "Scholar", "coinsLabel" to "Marks", "streakLabel" to "Days"),
        "theme_clay" to mapOf("homeTab" to "Studio", "questsTab" to "Blocks", "focusTab" to "Mold", "vaultTab" to "Box", "profileTab" to "Maker", "coinsLabel" to "Tokens", "streakLabel" to "Days"),
        "theme_rose" to mapOf("homeTab" to "Lounge", "questsTab" to "Wishes", "focusTab" to "Glow", "vaultTab" to "Case", "profileTab" to "Muse", "coinsLabel" to "Charms", "streakLabel" to "Days"),
        "theme_indigo" to mapOf("homeTab" to "Observatory", "questsTab" to "Charts", "focusTab" to "Orbit", "vaultTab" to "Eclipse", "profileTab" to "Watcher", "coinsLabel" to "Stars", "streakLabel" to "Nights"),
        "theme_mono" to mapOf("homeTab" to "01", "questsTab" to "02", "focusTab" to "03", "vaultTab" to "04", "profileTab" to "05", "coinsLabel" to "PTS", "streakLabel" to "DAYS"),
        "theme_whitespace" to mapOf("homeTab" to "Space", "questsTab" to "List", "focusTab" to "Time", "vaultTab" to "Hold", "profileTab" to "Self", "coinsLabel" to "Coins", "streakLabel" to "Days"),
        "theme_zen" to mapOf("homeTab" to "Now", "questsTab" to "Path", "focusTab" to "Still", "vaultTab" to "Rest", "profileTab" to "Self", "coinsLabel" to "Coins", "streakLabel" to "Days"),
        "theme_carnival" to mapOf("homeTab" to "Tent", "questsTab" to "Acts", "focusTab" to "Show", "vaultTab" to "Booth", "profileTab" to "Star", "coinsLabel" to "Tickets", "streakLabel" to "Shows"),
        "theme_baroque" to mapOf("homeTab" to "Salon", "questsTab" to "Duties", "focusTab" to "Vigil", "vaultTab" to "Treasury", "profileTab" to "Patron", "coinsLabel" to "Ducats", "streakLabel" to "Reign"),
        "theme_vaporwave" to mapOf("homeTab" to "P L A Z A", "questsTab" to "T A P E S", "focusTab" to "D R I V E", "vaultTab" to "V H S", "profileTab" to "A V A T A R", "coinsLabel" to "CREDITS", "streakLabel" to "DAYS"),
    )

    fun word(key: String, default: String): String {
        if (!Flags.get("theme_strings") || TermuxOn) return default
        if (ShopTheme == "theme_habitica") return default
        return MAP[ShopTheme]?.get(key) ?: default
    }
}
