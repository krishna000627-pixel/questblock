package com.krishna.questblock.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, SubTask::class, BlockedApp::class, Player::class,
                UnlockEvent::class, InventoryItem::class, StudySession::class, Achievement::class,
                CustomReward::class, FocusLog::class, CoinLog::class, UnlockSession::class],
    version = 9, exportSchema = false,
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
    abstract fun focusLogDao(): FocusLogDao
    abstract fun coinLogDao(): CoinLogDao
    abstract fun unlockSessionDao(): UnlockSessionDao

    companion object {
        /** v8 → v9: add unlock_sessions table for real-time usage tracking. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS unlock_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        label TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER,
                        msUsed INTEGER NOT NULL DEFAULT 0,
                        msBought INTEGER NOT NULL DEFAULT 0,
                        coinsSpent INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
    }
}
