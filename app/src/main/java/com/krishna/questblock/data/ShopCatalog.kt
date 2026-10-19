package com.krishna.questblock.data

data class ShopItem(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val price: Int,
    val category: String,       // "powerup", "cosmetic_frame", "cosmetic_theme", "cosmetic_badge"
    val durationMs: Long = 0L,  // for timed power-ups; 0 = permanent
    val isConsumable: Boolean = true,
)

object ShopCatalog {
    val items: List<ShopItem> = listOf(
        // ─── Power-ups ────────────────────────────────────────────────────
        ShopItem("xp_boost_1h",  "XP Boost",         "⚡", "Double XP for 1 hour",         60,  "powerup", 3_600_000L),
        ShopItem("xp_boost_3h",  "XP Surge",         "⚡", "Double XP for 3 hours",        150, "powerup", 10_800_000L),
        ShopItem("coin_mult_1h", "Coin Magnet",       "🧲", "1.5× coins for 1 hour",        80,  "powerup", 3_600_000L),
        ShopItem("coin_mult_3h", "Coin Storm",        "🧲", "1.5× coins for 3 hours",       200, "powerup", 10_800_000L),
        ShopItem("streak_shield","Streak Shield",     "🛡️", "Protect streak for 1 day",     100, "powerup", 86_400_000L),
        ShopItem("streak_repair","Streak Repair",     "🩹", "Restore a streak broken in the last 3 days", 150, "powerup"),
        ShopItem("streak_freeze","Streak Freeze",     "🧊", "Saves your streak once if you miss a day", 120, "powerup"),
        // ─── Avatar Frames ────────────────────────────────────────────────
        ShopItem("frame_fire",   "Fire Frame",        "🔥", "Blazing avatar frame",          150, "cosmetic_frame", isConsumable = false),
        ShopItem("frame_ice",    "Ice Frame",         "❄️", "Cool icy avatar frame",         150, "cosmetic_frame", isConsumable = false),
        ShopItem("frame_gold",   "Gold Frame",        "✨", "Legendary golden frame",        300, "cosmetic_frame", isConsumable = false),
        ShopItem("frame_galaxy", "Galaxy Frame",      "🌌", "Cosmic galaxy frame",           500, "cosmetic_frame", isConsumable = false),
        // ─── Themes: the price is coins PER DAY while the theme is equipped (no one-time buy). Not enough coins = back to default ───
        ShopItem("theme_sunset", "Sunset Theme",      "🌅", "Warm orange & pink UI",           4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_ocean",  "Ocean Theme",       "🌊", "Deep blue calm UI",               4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_forest", "Forest Theme",      "🌲", "Earthy green UI",                 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_neon",   "Neon Theme",        "💜", "Cyberpunk neon purple UI",        6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_scifi",  "Sci-Fi Theme",      "🛸", "Deep-space black with cyan glow", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_amoled", "AMOLED Black",      "🖤", "Pure black, soft ice-blue accent", 3, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_terminal","Terminal Theme",   "🖥️", "Green on black hacker screen",    5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_paper",  "Paper & Ink",       "📜", "Warm sepia, easy on the eyes",    4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_linen",  "Linen Grey",        "🩶", "Light: soft grey paper, slate accent",   4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_sage",   "Sage Paper",        "🌿", "Light: pale green paper, sage accent",   4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_sky",    "Sky Paper",         "🌤️", "Light: pale blue paper, calm blue accent", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_lilac",  "Lilac Paper",       "🪻", "Light: pale lilac paper, soft violet accent", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_chalk",  "Chalk & Charcoal",  "⚪", "Light: clean white, charcoal accent",    4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_clay",   "Claymorphism",      "\uD83C\uDFA8", "Light: soft pastel clay, puffy cards and buttons", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_system", "System Window",     "\uD83D\uDCA0", "Dark: blue RPG system windows, cut corners, mono text, HUD grid", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_rose",   "Rose Quartz",       "🌸", "Soft pink and plum",              5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_indigo", "Midnight Indigo",   "🌌", "Deep indigo night sky",           6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_mist",   "Mist Hashira (demo)", "🌫️", "Teal mist poster behind every screen", 0, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_itachi", "Crow Shadow",       "🪶", "Light theme: ink-and-crows poster, crimson accent", 0, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_angel",  "Dark Angel",        "🌑", "Light theme: black wings under a red halo poster", 0, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_naruto", "Red Eyes Hokage",   "🦊", "Black and red Hokage poster",     0, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_katana", "Ink Katana (light)", "🗡️", "Light theme: ink sword and cherry blossom poster", 0, "cosmetic_theme", isConsumable = false),
        // ─── Badges ───────────────────────────────────────────────────────
        ShopItem("badge_star",   "Star Badge",        "⭐", "Show off your star badge",      150, "cosmetic_badge", isConsumable = false),
        ShopItem("badge_crown",  "Crown Badge",       "👑", "You are royalty",               250, "cosmetic_badge", isConsumable = false),
        ShopItem("badge_sword",  "Warrior Badge",     "⚔️", "For the fearless",             300, "cosmetic_badge", isConsumable = false),
        ShopItem("badge_dragon", "Dragon Badge",      "🐉", "Legendary dragon slayer",       500, "cosmetic_badge", isConsumable = false),
    )

    val categories = listOf("All", "Power-ups", "Frames", "Themes", "Badges")

    fun filter(category: String): List<ShopItem> = when (category) {
        "Power-ups" -> items.filter { it.category == "powerup" }
        "Frames"    -> items.filter { it.category == "cosmetic_frame" }
        "Themes"    -> items.filter { it.category == "cosmetic_theme" }
        "Badges"    -> items.filter { it.category == "cosmetic_badge" }
        else        -> items
    }
}
