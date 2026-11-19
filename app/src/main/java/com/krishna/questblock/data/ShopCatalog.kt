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
        // #svg-logo  NEW THEME RULE: every theme's emoji must draw as an SVG icon. When you add a theme here, also add its emoji
        // to SvgIcons.EMOJI (and a new icon to ICONS if none fits). Hub > Self test, "Every theme has an SVG logo", fails if you forget.
        ShopItem("theme_sunset", "Sunset Theme",      "SUN", "Warm orange & pink UI",           4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_ocean",  "Ocean Theme",       "SEA", "Deep blue calm UI",               4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_forest", "Forest Theme",      "PINE", "Earthy green UI",                 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_neon",   "Neon Theme",        "NEON", "Cyberpunk neon purple UI",        6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_scifi",  "Sci-Fi Theme",      "SCI", "Deep-space black with cyan glow", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_amoled", "AMOLED Black",      "OLED", "Pure black, soft ice-blue accent", 3, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_terminal","Terminal Theme",   "TERM", "Green on black hacker screen",    5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_paper",  "Paper & Ink",       "INK", "Warm sepia, easy on the eyes",    4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_linen",  "Linen Grey",        "GRAY", "Light: soft grey paper, slate accent",   4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_sage",   "Sage Paper",        "SAGE", "Light: pale green paper, sage accent",   4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_sky",    "Sky Paper",         "SKY", "Light: pale blue paper, calm blue accent", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_lilac",  "Lilac Paper",       "LILAC", "Light: pale lilac paper, soft violet accent", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_chalk",  "Chalk & Charcoal",  "CHALK", "Light: clean white, charcoal accent",    4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_clay",   "Claymorphism",      "CLAY", "Light: soft pastel clay, puffy cards and buttons", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_system", "System Window",     "SYS", "Dark: blue RPG system windows, cut corners, mono text, HUD grid", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_ninja",     "Ninja Scroll",     "NINJA", "Light: washi paper, serif ink text, red hanko seals on cards", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_hashira",   "Hashira Mist",     "MIST", "Dark: frosted teal glass cards, drifting mist, round buttons", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_monarch",   "Shadow Monarch",   "SHAD", "Dark: black and violet, glowing edges, rising shadow motes", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_gothic",    "Gothic Halo",      "HALO", "Dark: black and white, one red accent, serif text, double borders", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_guild",     "Guild Board",      "GUILD", "Light: parchment, brass studs, serif titles, a Guild Board strip", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_pixel",     "Pixel Arcade",     "8BIT", "Dark: chunky corners, hard shadows, mono text, INSERT COIN strip", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_handheld",  "Handheld",         "LCD", "Light: green LCD screen, dot matrix, thick borders, mono text", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_blueprint", "Blueprint",        "CAD", "Dark: white lines on blueprint blue, grid sheet, corner marks", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_mono",       "Mono",             "MONO", "Minimal: pure white or black, one hairline accent, a 2 px XP rule, a quiet Done.", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_whitespace", "Whitespace",       "PURE", "Minimal: light, huge margins, no card borders, only dividers, the lock page shows the time", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_zen",        "Zen",              "ZEN", "Minimal: stone grey, an ens\u014D brush circle for Focus, no sounds, one soft haptic", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_carnival",   "Carnival",         "SHOW", "Maximal: clashing neon, striped cards, confetti, fireworks level up, marquee lights", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_baroque",    "Baroque",          "GOLD", "Maximal: gold-leaf frames, ornate dividers, a wax-seal level up, velvet red", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_vaporwave",  "Vaporwave Arcade", "1984", "Maximal: pink and cyan, a sun grid, chrome text, a glitchy XP bar, INSERT COIN", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_rose",   "Rose Quartz",       "ROSE", "Soft pink and plum",              5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_indigo", "Midnight Indigo",   "STAR", "Deep indigo night sky",           6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_habitica", "Tavern RPG", "RPG", "Open-source RPG style: royal purple, HP/XP/MP bars, pixel borders, gold coins", 4, "cosmetic_theme", isConsumable = false),
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
