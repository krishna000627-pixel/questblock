package com.krishna.questblock.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val difficulty: Int = 1,           // 1=Easy 2=Medium 3=Hard
    val category: String = "Personal", // Study/Work/Health/Personal/Custom
    val priority: Int = 2,             // 1=High 2=Medium 3=Low
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val dueAt: Long? = null,           // scheduled due timestamp
    val isRecurring: Boolean = false,
    val recurType: String = "",        // "daily" "weekly"
    // Timer Quest — linked app packages (comma-separated)
    val linkedApps: String = "",
    val isTimerQuest: Boolean = false,
    val timerQuestActive: Boolean = false,
    val timerQuestStarted: Long? = null,
    val timerDurationMs: Long = 0L,    // 0 = no timer limit
    // Days-of-week schedule: bit0=Mon … bit6=Sun. 0 = every day / no restriction.
    @ColumnInfo(defaultValue = "0") val daysMask: Int = 0,
    // Hidden from "today" until this time (used after a scheduled quest is completed)
    @ColumnInfo(defaultValue = "0") val availableFrom: Long = 0L,
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
    val cost: Int = 20,
    val unlockMinutes: Int = 15,
    val unlockedUntil: Long = 0L,
    val isStudyApp: Boolean = false,
    // Per-app options (v9)
    @ColumnInfo(defaultValue = "0")   val dailyLimitMin: Int = 0,     // 0 = always locked; >0 = free until N min used per day
    @ColumnInfo(defaultValue = "0")   val usedTodayMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")   val usedDay: Long = 0L,
    @ColumnInfo(defaultValue = "100") val priceMult: Int = 100,       // unlock price in percent
    @ColumnInfo(defaultValue = "-1")  val schedStart: Int = -1,       // minutes from midnight, -1 = lock all day
    @ColumnInfo(defaultValue = "-1")  val schedEnd: Int = -1,
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
    val dob: String = "",
    val xpBoostUntil: Long = 0L,
    val streakShieldUntil: Long = 0L,
    val coinMultiplierUntil: Long = 0L,
    val equippedFrame: String = "",
    val equippedTheme: String = "default",
    val equippedBadge: String = "",
    // Focus mode
    val focusModeUntil: Long = 0L,
    val focusQuestId: Long = -1L,
    @ColumnInfo(defaultValue = "0") val focusTotalMs: Long = 0L,
    // Daily login bonus (day number of last claim)
    @ColumnInfo(defaultValue = "0") val lastLoginDay: Long = 0L,
    // Weekly XP snapshots for simulated leaderboard (7 days, comma-sep)
    val weeklyXpLog: String = "",
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

@Entity(tableName = "unlock_events")
data class UnlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val coinsSpent: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val coinsEarned: Int = 0,
)

@Entity(tableName = "custom_rewards")
data class CustomReward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val price: Int,
    val description: String = "",
    val type: String = "reward",      // "reward" = redeemed on purchase, "consumable" = kept until you redeem it
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val oncePerDay: Boolean = false,
)

@Entity(tableName = "focus_log")
data class FocusLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val minutes: Int,
    val completed: Boolean,
    val questTitle: String = "",
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val unlockedAt: Long? = null,
)

fun Player.level(): Int = xp / 100 + 1
fun Player.xpIntoLevel(): Int = xp % 100
fun Player.hasXpBoost(): Boolean = System.currentTimeMillis() < xpBoostUntil
fun Player.hasCoinMultiplier(): Boolean = System.currentTimeMillis() < coinMultiplierUntil
fun Player.hasStreakShield(): Boolean = System.currentTimeMillis() < streakShieldUntil
fun Player.isFocusMode(): Boolean = System.currentTimeMillis() < focusModeUntil
