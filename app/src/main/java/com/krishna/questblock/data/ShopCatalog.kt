package com.krishna.questblock.data

data class ShopItem(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val price: Int,
    val category: String,       // "powerup", "cosmetic_frame", "cosmetic_theme", "cosmetic_badge", "pass"
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
        ShopItem("focus_boost",  "Focus Mode",        "🎯", "No distractions for 30 min",   50,  "powerup", 1_800_000L),
        // ─── App Unlock Passes ────────────────────────────────────────────
        ShopItem("pass_15",      "Quick Pass",        "🎫", "Unlock any app for 15 min",    20,  "pass"),
        ShopItem("pass_30",      "Standard Pass",     "🎟️", "Unlock any app for 30 min",    35,  "pass"),
        ShopItem("pass_60",      "Hour Pass",         "🏷️", "Unlock any app for 60 min",    60,  "pass"),
        // ─── Avatar Frames ────────────────────────────────────────────────
        ShopItem("frame_fire",   "Fire Frame",        "🔥", "Blazing avatar frame",          150, "cosmetic_frame", isConsumable = false),
        ShopItem("frame_ice",    "Ice Frame",         "❄️", "Cool icy avatar frame",         150, "cosmetic_frame", isConsumable = false),
        ShopItem("frame_gold",   "Gold Frame",        "✨", "Legendary golden frame",        300, "cosmetic_frame", isConsumable = false),
        ShopItem("frame_galaxy", "Galaxy Frame",      "🌌", "Cosmic galaxy frame",           500, "cosmetic_frame", isConsumable = false),
        // ─── Themes ───────────────────────────────────────────────────────
        ShopItem("theme_sunset", "Sunset Theme",      "🌅", "Warm orange & pink UI",         200, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_ocean",  "Ocean Theme",       "🌊", "Deep blue calm UI",             200, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_forest", "Forest Theme",      "🌲", "Earthy green UI",               200, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_neon",   "Neon Theme",        "💜", "Cyberpunk neon purple UI",      400, "cosmetic_theme", isConsumable = false),
        // ─── Badges ───────────────────────────────────────────────────────
        ShopItem("badge_star",   "Star Badge",        "⭐", "Show off your star badge",      100, "cosmetic_badge", isConsumable = false),
        ShopItem("badge_crown",  "Crown Badge",       "👑", "You are royalty",               250, "cosmetic_badge", isConsumable = false),
        ShopItem("badge_sword",  "Warrior Badge",     "⚔️", "For the fearless",             300, "cosmetic_badge", isConsumable = false),
        ShopItem("badge_dragon", "Dragon Badge",      "🐉", "Legendary dragon slayer",       500, "cosmetic_badge", isConsumable = false),
    )

    val categories = listOf("All", "Power-ups", "Passes", "Frames", "Themes", "Badges")

    fun filter(category: String): List<ShopItem> = when (category) {
        "Power-ups" -> items.filter { it.category == "powerup" }
        "Passes"    -> items.filter { it.category == "pass" }
        "Frames"    -> items.filter { it.category == "cosmetic_frame" }
        "Themes"    -> items.filter { it.category == "cosmetic_theme" }
        "Badges"    -> items.filter { it.category == "cosmetic_badge" }
        else        -> items
    }
}
