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
)

@Entity(tableName = "player")
data class Player(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 20, // starter coins so you can test the unlock flow
    val xp: Int = 0,
    val streak: Int = 0,
    val lastCompletionDay: Long = 0L,
)

fun Player.level(): Int = xp / 100 + 1
fun Player.xpIntoLevel(): Int = xp % 100
