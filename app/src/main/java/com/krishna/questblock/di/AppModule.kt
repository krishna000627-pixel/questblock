package com.krishna.questblock.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.krishna.questblock.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** v5 -> v6: daily login, focus total, custom shop rewards. Keeps all existing user data. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE player ADD COLUMN focusTotalMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE player ADD COLUMN lastLoginDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS custom_rewards (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, emoji TEXT NOT NULL, price INTEGER NOT NULL, " +
                "description TEXT NOT NULL, type TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
    }
}

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "questblock.db")
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration().build()

    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun subTaskDao(db: AppDatabase) = db.subTaskDao()
    @Provides fun appDao(db: AppDatabase) = db.blockedAppDao()
    @Provides fun playerDao(db: AppDatabase) = db.playerDao()
    @Provides fun unlockEventDao(db: AppDatabase) = db.unlockEventDao()
    @Provides fun inventoryDao(db: AppDatabase) = db.inventoryDao()
    @Provides fun studySessionDao(db: AppDatabase) = db.studySessionDao()
    @Provides fun achievementDao(db: AppDatabase) = db.achievementDao()
    @Provides fun customRewardDao(db: AppDatabase) = db.customRewardDao()
}
