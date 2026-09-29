package com.krishna.questblock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val difficulty: Int = 1, // 1 easy, 2 medium, 3 hard
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val cost: Int = 20,
    val unlockMinutes: Int = 15,
    val unlockedUntil: Long = 0L,
    val isStudyApp: Boolean = false, // earns coins when used
)

@Entity(tableName = "player")
data class Player(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 20,
    val xp: Int = 0,
    val streak: Int = 0,
    val lastCompletionDay: Long = 0L,
    val name: String = "",
    val goal: String = "",
    val dob: String = "",  // stored as "dd/MM/yyyy"
    // Active power-ups (expiry timestamps, 0 = inactive)
    val xpBoostUntil: Long = 0L,
    val streakShieldUntil: Long = 0L,
    val coinMultiplierUntil: Long = 0L,
    // Equipped cosmetic IDs
    val equippedFrame: String = "",
    val equippedTheme: String = "default",
    val equippedBadge: String = "",
)

// Inventory item owned by player
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,       // e.g. "xp_boost_1", "frame_fire"
    val category: String,     // "powerup", "cosmetic", "pass"
    val name: String,
    val emoji: String,
    val acquiredAt: Long = System.currentTimeMillis(),
    val isEquipped: Boolean = false,
    val usedAt: Long? = null, // null = not used yet (for consumables)
)

// Unlock event log for Stats screen
@Entity(tableName = "unlock_events")
data class UnlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val coinsSpent: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

// Study session tracking (5 min = 1 coin)
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val coinsEarned: Int = 0,
)

fun Player.level(): Int = xp / 100 + 1
fun Player.xpIntoLevel(): Int = xp % 100
fun Player.hasXpBoost(): Boolean = System.currentTimeMillis() < xpBoostUntil
fun Player.hasCoinMultiplier(): Boolean = System.currentTimeMillis() < coinMultiplierUntil
fun Player.hasStreakShield(): Boolean = System.currentTimeMillis() < streakShieldUntil
