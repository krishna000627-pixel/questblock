package com.krishna.questblock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val difficulty: Int = 1,
    val category: String = "Personal",
    val priority: Int = 2,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val dueAt: Long? = null,
    val isRecurring: Boolean = false,
    val recurType: String = "daily",
    // Focus/Timer quest (merged)
    val linkedApps: String = "",
    val isFocusQuest: Boolean = false,
    val focusActive: Boolean = false,
    val focusStarted: Long? = null,
    val focusDurationMs: Long = 25 * 60_000L,
)

@Entity(tableName = "subtasks")
data class SubTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
)

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val unlockedUntil: Long = 0L,
    val isStudyApp: Boolean = false,
)

@Entity(tableName = "player")
data class Player(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 20,
    val xp: Int = 0,
    val streak: Int = 0,
    val lastCompletionDay: Long = 0L,
    val lastLoginDay: Long = 0L,
    val name: String = "",
    val goal: String = "",
    val dob: String = "",
    val xpBoostUntil: Long = 0L,
    val streakShieldUntil: Long = 0L,
    val coinMultiplierUntil: Long = 0L,
    val focusModeUntil: Long = 0L,
    val focusQuestId: Long = -1L,
    val equippedFrame: String = "",
    val equippedTheme: String = "default",
    val equippedBadge: String = "",
    val weeklyXpLog: String = "",
    val totalCoinsEarned: Int = 0,
)

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val category: String,
    val name: String,
    val emoji: String,
    val acquiredAt: Long = System.currentTimeMillis(),
    val isEquipped: Boolean = false,
    val usedAt: Long? = null,
)

// Flexible unlock session (replaces fixed cost per app)
@Entity(tableName = "unlock_sessions")
data class UnlockSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val coinsSpent: Int,
    val durationMs: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
)

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val startedAt: Long = System.currentTimeMillis(),
    val coinsEarned: Int = 0,
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val unlockedAt: Long? = null,
)

// Custom shop items created by the user
@Entity(tableName = "custom_shop_items")
data class CustomShopItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val description: String,
    val price: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

// Log of custom item purchases
@Entity(tableName = "custom_purchases")
data class CustomPurchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemName: String,
    val coinsSpent: Int,
    val purchasedAt: Long = System.currentTimeMillis(),
    val redeemed: Boolean = false,
)

fun Player.level(): Int = xp / 100 + 1
fun Player.xpIntoLevel(): Int = xp % 100
fun Player.hasXpBoost(): Boolean = System.currentTimeMillis() < xpBoostUntil
fun Player.hasCoinMultiplier(): Boolean = System.currentTimeMillis() < coinMultiplierUntil
fun Player.hasStreakShield(): Boolean = System.currentTimeMillis() < streakShieldUntil
fun Player.isFocusMode(): Boolean = System.currentTimeMillis() < focusModeUntil

// Unlock duration options
data class UnlockOption(val label: String, val durationMs: Long, val cost: Int)
val UNLOCK_OPTIONS = listOf(
    UnlockOption("15 min",  15 * 60_000L,       20),
    UnlockOption("30 min",  30 * 60_000L,       35),
    UnlockOption("1 hour",  60 * 60_000L,       60),
    UnlockOption("3 hours", 3 * 60 * 60_000L,  120),
    UnlockOption("10 hours",10 * 60 * 60_000L, 300),
)
