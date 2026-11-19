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
        ShopItem("theme_ninja",     "Ninja Scroll",     "\uD83E\uDD77", "Light: washi paper, serif ink text, red hanko seals on cards", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_hashira",   "Hashira Mist",     "\uD83D\uDCA8", "Dark: frosted teal glass cards, drifting mist, round buttons", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_monarch",   "Shadow Monarch",   "\uD83D\uDD2E", "Dark: black and violet, glowing edges, rising shadow motes", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_gothic",    "Gothic Halo",      "\u26EA", "Dark: black and white, one red accent, serif text, double borders", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_guild",     "Guild Board",      "\uD83C\uDFF0", "Light: parchment, brass studs, serif titles, a Guild Board strip", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_pixel",     "Pixel Arcade",     "\uD83D\uDC7E", "Dark: chunky corners, hard shadows, mono text, INSERT COIN strip", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_handheld",  "Handheld",         "\uD83D\uDD79", "Light: green LCD screen, dot matrix, thick borders, mono text", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_blueprint", "Blueprint",        "\uD83D\uDCD0", "Dark: white lines on blueprint blue, grid sheet, corner marks", 5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_mono",       "Mono",             "\u26AB", "Minimal: pure white or black, one hairline accent, a 2 px XP rule, a quiet Done.", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_whitespace", "Whitespace",       "\u2B1C", "Minimal: light, huge margins, no card borders, only dividers, the lock page shows the time", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_zen",        "Zen",              "\u2B55", "Minimal: stone grey, an ens\u014D brush circle for Focus, no sounds, one soft haptic", 4, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_carnival",   "Carnival",         "\uD83C\uDFAA", "Maximal: clashing neon, striped cards, confetti, fireworks level up, marquee lights", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_baroque",    "Baroque",          "\uD83D\uDD6F", "Maximal: gold-leaf frames, ornate dividers, a wax-seal level up, velvet red", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_vaporwave",  "Vaporwave Arcade", "\uD83C\uDF34", "Maximal: pink and cyan, a sun grid, chrome text, a glitchy XP bar, INSERT COIN", 6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_rose",   "Rose Quartz",       "🌸", "Soft pink and plum",              5, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_indigo", "Midnight Indigo",   "🌌", "Deep indigo night sky",           6, "cosmetic_theme", isConsumable = false),
        ShopItem("theme_habitica", "Habitica RPG", "RPG", "Open-source RPG style: royal purple, HP/XP/MP bars, pixel borders, gold coins", 4, "cosmetic_theme", isConsumable = false),
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
