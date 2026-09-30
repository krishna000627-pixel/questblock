package com.krishna.questblock.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, SubTask::class, BlockedApp::class, Player::class,
                UnlockEvent::class, InventoryItem::class, StudySession::class, Achievement::class,
                CustomReward::class],
    version = 6, exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subTaskDao(): SubTaskDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun playerDao(): PlayerDao
    abstract fun unlockEventDao(): UnlockEventDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun achievementDao(): AchievementDao
    abstract fun customRewardDao(): CustomRewardDao
}
