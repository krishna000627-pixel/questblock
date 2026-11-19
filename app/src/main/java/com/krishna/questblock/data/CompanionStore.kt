package com.krishna.questblock.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class CompanionSpec(
    val id: String,
    val baseName: String,
    val stages: List<String>,      // Stage 1, Stage 2 (Lv 5+), Stage 3 (Lv 12+)
    val emojis: List<String>,
    val cost: Int,                 // 0 = free starter
    val colorHex: Long,
    val perk: String,
)

data class CompanionState(
    val activeId: String = "kitsune",
    val adoptedIds: Set<String> = setOf("kitsune"),
    val customName: String = "",
    val xp: Int = 0,
    val hunger: Int = 80,
    val happiness: Int = 85,
    val careStreak: Int = 1,
    val totalPets: Int = 0,
    val lastFreeSnackDay: Long = -1L,
) {
    val level: Int get() = 1 + (xp / 60)
    val levelXp: Int get() = xp % 60
    val stageIndex: Int get() = when {
        level >= 12 -> 2
        level >= 5 -> 1
        else -> 0
    }
    val mood: String get() = when {
        happiness >= 80 && hunger >= 65 -> "Thriving"
        happiness >= 50 && hunger >= 40 -> "Happy"
        hunger < 30 -> "Hungry"
        else -> "Sleepy"
    }
}

/**
 * v292: Adopt & Companion progression store (SharedPreferences-backed, zero Room migration needed).
 */
object CompanionStore {
    val CATALOG: List<CompanionSpec> = listOf(
        CompanionSpec(
            id = "kitsune",
            baseName = "Kitsune Ember",
            stages = listOf("Ember Kit", "Nine-Tail Spark", "Solar Kitsune"),
            emojis = listOf("\uD83E\uDD8A", "\uD83D\uDD25", "\u2728"),
            cost = 0,
            colorHex = 0xFFFF7A59,
            perk = "Starter spirit · cheers on every daily streak",
        ),
        CompanionSpec(
            id = "wolf",
            baseName = "Shadow Wolf",
            stages = listOf("Shadow Pup", "Dusk Fang", "Monarch Hound"),
            emojis = listOf("\uD83D\uDC3A", "\uD83C\uDF11", "\uD83D\uDD2E"),
            cost = 25,
            colorHex = 0xFF9C6BFF,
            perk = "Discipline guardian · thrives during Focus blocks",
        ),
        CompanionSpec(
            id = "owl",
            baseName = "Owl Scholar",
            stages = listOf("Owlet", "Arcane Owl", "Grand Archivist"),
            emojis = listOf("\uD83E\uDD89", "\uD83D\uDCD6", "\uD83C\uDF93"),
            cost = 20,
            colorHex = 0xFF4A86C5,
            perk = "Study sage · loves chapter & homework clears",
        ),
        CompanionSpec(
            id = "cat",
            baseName = "Cyber Cat",
            stages = listOf("Pixel Kitty", "Neon Lynx", "Quantum Panther"),
            emojis = listOf("\uD83D\uDC31", "\u26A1", "\uD83D\uDC06"),
            cost = 25,
            colorHex = 0xFF00E5FF,
            perk = "Arcade ally · purrs on quest combos",
        ),
        CompanionSpec(
            id = "dragon",
            baseName = "Jade Dragon",
            stages = listOf("Jade Hatchling", "Mist Wyrm", "Celestial Dragon"),
            emojis = listOf("\uD83D\uDC09", "\uD83C\uDF0A", "\uD83D\uDC32"),
            cost = 40,
            colorHex = 0xFF6BCB77,
            perk = "Mythic partner · symbol of iron willpower",
        ),
        CompanionSpec(
            id = "frog",
            baseName = "Zen Frog",
            stages = listOf("Pond Tadpole", "Lotus Toad", "Sage Guardian"),
            emojis = listOf("\uD83D\uDC38", "\uD83E\uDEB7", "\u2B55"),
            cost = 15,
            colorHex = 0xFF6B8F71,
            perk = "Calm anchor · keeps Focus sessions steady",
        ),
    )

    var state by mutableStateOf(CompanionState())
        private set
    var revision by mutableIntStateOf(0)
        private set

    private fun sp(ctx: Context) = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    fun specOf(id: String): CompanionSpec = CATALOG.firstOrNull { it.id == id } ?: CATALOG[0]

    fun load(ctx: Context) {
        val s = sp(ctx)
        val adopted = s.getStringSet("comp_adopted", setOf("kitsune"))?.toSet() ?: setOf("kitsune")
        state = CompanionState(
            activeId = s.getString("comp_active", "kitsune") ?: "kitsune",
            adoptedIds = if (adopted.isEmpty()) setOf("kitsune") else adopted,
            customName = s.getString("comp_name", "") ?: "",
            xp = s.getInt("comp_xp", 0),
            hunger = s.getInt("comp_hunger", 80).coerceIn(0, 100),
            happiness = s.getInt("comp_happy", 85).coerceIn(0, 100),
            careStreak = s.getInt("comp_streak", 1).coerceAtLeast(1),
            totalPets = s.getInt("comp_pets", 0),
            lastFreeSnackDay = s.getLong("comp_snack_day", -1L),
        )
        revision++
    }

    private fun save(ctx: Context, next: CompanionState) {
        state = next
        revision++
        sp(ctx).edit()
            .putString("comp_active", next.activeId)
            .putStringSet("comp_adopted", next.adoptedIds)
            .putString("comp_name", next.customName)
            .putInt("comp_xp", next.xp)
            .putInt("comp_hunger", next.hunger)
            .putInt("comp_happy", next.happiness)
            .putInt("comp_streak", next.careStreak)
            .putInt("comp_pets", next.totalPets)
            .putLong("comp_snack_day", next.lastFreeSnackDay)
            .apply()
    }

    fun select(ctx: Context, id: String) {
        if (id !in state.adoptedIds) return
        save(ctx, state.copy(activeId = id, customName = ""))
    }

    fun markAdopted(ctx: Context, id: String) {
        save(ctx, state.copy(adoptedIds = state.adoptedIds + id, activeId = id, customName = ""))
    }

    fun rename(ctx: Context, name: String) {
        save(ctx, state.copy(customName = name.trim().take(24)))
    }

    fun pet(ctx: Context) {
        save(
            ctx,
            state.copy(
                happiness = (state.happiness + 8).coerceAtMost(100),
                xp = state.xp + 6,
                totalPets = state.totalPets + 1,
            ),
        )
    }

    fun feed(ctx: Context, freeDaily: Boolean) {
        val today = System.currentTimeMillis() / 86_400_000L
        val nextStreak = if (state.lastFreeSnackDay == today - 1L) state.careStreak + 1
        else if (state.lastFreeSnackDay == today) state.careStreak else 1
        save(
            ctx,
            state.copy(
                hunger = (state.hunger + 25).coerceAtMost(100),
                happiness = (state.happiness + 10).coerceAtMost(100),
                xp = state.xp + 18,
                careStreak = nextStreak,
                lastFreeSnackDay = if (freeDaily) today else state.lastFreeSnackDay,
            ),
        )
    }

    fun train(ctx: Context) {
        save(
            ctx,
            state.copy(
                hunger = (state.hunger - 6).coerceAtLeast(10),
                happiness = (state.happiness + 12).coerceAtMost(100),
                xp = state.xp + 24,
            ),
        )
    }

    fun grantXp(ctx: Context?, amount: Int) {
        if (ctx == null || amount <= 0) return
        if (!Flags.get("adopt")) return
        if (revision == 0) load(ctx)
        save(ctx, state.copy(xp = state.xp + amount, happiness = (state.happiness + 2).coerceAtMost(100)))
    }

    fun hasFreeSnackToday(): Boolean {
        val today = System.currentTimeMillis() / 86_400_000L
        return state.lastFreeSnackDay != today
    }
}
