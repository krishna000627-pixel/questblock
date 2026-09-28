package com.krishna.questblock.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, BlockedApp::class, Player::class, UnlockEvent::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun playerDao(): PlayerDao
    abstract fun unlockEventDao(): UnlockEventDao
}
