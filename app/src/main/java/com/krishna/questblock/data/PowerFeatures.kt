package com.krishna.questblock.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * v297: Persistent store powering the 55 new Focus, RPG, Pet, Study, Wellness, and System features.
 * Zero Room migrations required; all state is persisted cleanly in SharedPreferences.
 */
object PowerFeatures {
    var focusPresetMin by mutableIntStateOf(25)
    var breakMin by mutableIntStateOf(5)
    var dailyFocusGoalMin by mutableIntStateOf(120)
    var focusDoneTodayMin by mutableIntStateOf(0)
    var sessionIntention by mutableStateOf("")
    var subjectTag by mutableStateOf("Physics")
    var urgesResisted by mutableIntStateOf(0)
    var ambientLevel by mutableIntStateOf(1) // 0=Low, 1=Med, 2=High
    var autoChime10m by mutableStateOf(false)

    // RPG & Economy
    var bossHp by mutableIntStateOf(500)
    var bossMaxHp by mutableIntStateOf(500)
    var bossDamageTotal by mutableIntStateOf(0)
    var lastLootDay by mutableStateOf(-1L)
    var lastLootReward by mutableStateOf("")
    var heroTitle by mutableStateOf("Initiate")
    var vaultedCoins by mutableIntStateOf(0)
    var dailyQuestQuota by mutableIntStateOf(5)
    var critChancePct by mutableIntStateOf(15)

    // Study & Wellness
    var waterGlasses by mutableIntStateOf(0)
    var eyeBreaks by mutableIntStateOf(0)
    var scratchpad by mutableStateOf("")
    var examName by mutableStateOf("Board / JEE")
    var examDaysLeft by mutableIntStateOf(60)
    var weakTopics by mutableStateOf("Electrostatics|Organic Mechanisms|Integration")
    var dailyWins by mutableStateOf("")
    var flashcardsRaw by mutableStateOf("Gauss Law|Flux = Q_in / eps0;;Ohm Law|V = I * R;;Integration|d/dx(sin x) = cos x")
    var morningChecks by mutableIntStateOf(0) // bitmask 0..7
    var nightChecks by mutableIntStateOf(0)   // bitmask 0..7

    // Pet extras
    var petEnergy by mutableIntStateOf(85)
    var petCrownVisible by mutableStateOf(true)
    var petVoiceShift by mutableIntStateOf(0) // -2..+2

    // System presets
    var audioPreset by mutableIntStateOf(0)   // 0=Crisp, 1=Warm, 2=Retro
    var hapticPreset by mutableIntStateOf(1)  // 0=Light, 1=Medium, 2=Strong

    val HERO_TITLES = listOf("Initiate", "Scholar", "Vanguard", "Archivist", "Sovereign", "Apex")
    val SUBJECTS = listOf("Physics", "Chemistry", "Math", "Code", "Reading", "Revision")
    val FORTUNES = listOf(
        "Discipline compounds faster than motivation.",
        "One clean 25-minute block changes the whole day.",
        "Guard your focus; the reward will follow.",
        "Small daily wins build an unbreakable streak.",
        "Clarity comes from starting, not waiting.",
        "Your future self is watching today's choices.",
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("questblock_power", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val s = sp(ctx)
        focusPresetMin = s.getInt("pf_focus_min", 25)
        breakMin = s.getInt("pf_break_min", 5)
        dailyFocusGoalMin = s.getInt("pf_focus_goal", 120)
        focusDoneTodayMin = s.getInt("pf_focus_done", 0)
        sessionIntention = s.getString("pf_intent", "") ?: ""
        subjectTag = s.getString("pf_subject", "Physics") ?: "Physics"
        urgesResisted = s.getInt("pf_urges", 0)
        ambientLevel = s.getInt("pf_amb_lvl", 1)
        autoChime10m = s.getBoolean("pf_chime10", false)

        bossHp = s.getInt("pf_boss_hp", 500)
        bossMaxHp = s.getInt("pf_boss_max", 500)
        bossDamageTotal = s.getInt("pf_boss_dmg", 0)
        lastLootDay = s.getLong("pf_loot_day", -1L)
        lastLootReward = s.getString("pf_loot_msg", "") ?: ""
        heroTitle = s.getString("pf_title", "Initiate") ?: "Initiate"
        vaultedCoins = s.getInt("pf_vault_coins", 0)
        dailyQuestQuota = s.getInt("pf_quota", 5)
        critChancePct = s.getInt("pf_crit", 15)

        waterGlasses = s.getInt("pf_water", 0)
        eyeBreaks = s.getInt("pf_eye", 0)
        scratchpad = s.getString("pf_scratch", "") ?: ""
        examName = s.getString("pf_exam_name", "Board / JEE") ?: "Board / JEE"
        examDaysLeft = s.getInt("pf_exam_days", 60)
        weakTopics = s.getString("pf_weak", "Electrostatics|Organic Mechanisms|Integration") ?: ""
        dailyWins = s.getString("pf_wins", "") ?: ""
        flashcardsRaw = s.getString("pf_cards", flashcardsRaw) ?: flashcardsRaw
        morningChecks = s.getInt("pf_morning", 0)
        nightChecks = s.getInt("pf_night", 0)

        petEnergy = s.getInt("pf_pet_energy", 85)
        petCrownVisible = s.getBoolean("pf_pet_crown", true)
        petVoiceShift = s.getInt("pf_pet_voice", 0)

        audioPreset = s.getInt("pf_audio_preset", 0)
        hapticPreset = s.getInt("pf_haptic_preset", 1)
    }

    fun save(ctx: Context) {
        sp(ctx).edit()
            .putInt("pf_focus_min", focusPresetMin)
            .putInt("pf_break_min", breakMin)
            .putInt("pf_focus_goal", dailyFocusGoalMin)
            .putInt("pf_focus_done", focusDoneTodayMin)
            .putString("pf_intent", sessionIntention)
            .putString("pf_subject", subjectTag)
            .putInt("pf_urges", urgesResisted)
            .putInt("pf_amb_lvl", ambientLevel)
            .putBoolean("pf_chime10", autoChime10m)
            .putInt("pf_boss_hp", bossHp)
            .putInt("pf_boss_max", bossMaxHp)
            .putInt("pf_boss_dmg", bossDamageTotal)
            .putLong("pf_loot_day", lastLootDay)
            .putString("pf_loot_msg", lastLootReward)
            .putString("pf_title", heroTitle)
            .putInt("pf_vault_coins", vaultedCoins)
            .putInt("pf_quota", dailyQuestQuota)
            .putInt("pf_crit", critChancePct)
            .putInt("pf_water", waterGlasses)
            .putInt("pf_eye", eyeBreaks)
            .putString("pf_scratch", scratchpad)
            .putString("pf_exam_name", examName)
            .putInt("pf_exam_days", examDaysLeft)
            .putString("pf_weak", weakTopics)
            .putString("pf_wins", dailyWins)
            .putString("pf_cards", flashcardsRaw)
            .putInt("pf_morning", morningChecks)
            .putInt("pf_night", nightChecks)
            .putInt("pf_pet_energy", petEnergy)
            .putBoolean("pf_pet_crown", petCrownVisible)
            .putInt("pf_pet_voice", petVoiceShift)
            .putInt("pf_audio_preset", audioPreset)
            .putInt("pf_haptic_preset", hapticPreset)
            .apply()
    }

    fun logUrgeResisted(ctx: Context) {
        urgesResisted += 1
        dealBossDamage(ctx, 25)
        save(ctx)
    }

    fun dealBossDamage(ctx: Context, dmg: Int) {
        bossDamageTotal += dmg
        val next = bossHp - dmg
        bossHp = if (next <= 0) bossMaxHp else next
        save(ctx)
    }

    fun rollDailyChest(ctx: Context): String {
        val today = System.currentTimeMillis() / 86_400_000L
        if (lastLootDay == today) return "Already claimed today: $lastLootReward"
        lastLootDay = today
        val rewards = listOf("+15 Bonus XP", "+5 Focus Points", "+25 Boss Damage", "Critical Streak Charm", "Companion Energy Full")
        lastLootReward = rewards[Random.nextInt(rewards.size)]
        if (lastLootReward == "Companion Energy Full") petEnergy = 100
        if (lastLootReward == "+25 Boss Damage") dealBossDamage(ctx, 25)
        save(ctx)
        return "Loot Chest: $lastLootReward"
    }

    fun dailyFortune(): String {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return FORTUNES[kotlin.math.abs(day) % FORTUNES.size]
    }

    fun napPet(ctx: Context) {
        petEnergy = (petEnergy + 30).coerceAtMost(100)
        save(ctx)
    }
}
