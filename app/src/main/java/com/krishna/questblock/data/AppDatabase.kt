package com.krishna.questblock.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, SubTask::class, BlockedApp::class, Player::class,
                UnlockSession::class, StudySession::class, InventoryItem::class,
                Achievement::class, CustomShopItem::class, CustomPurchase::class],
    version = 7, exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subTaskDao(): SubTaskDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun playerDao(): PlayerDao
    abstract fun unlockSessionDao(): UnlockSessionDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun achievementDao(): AchievementDao
    abstract fun customShopDao(): CustomShopDao
    abstract fun customPurchaseDao(): CustomPurchaseDao
}
