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

/** v6 -> v7: quest days, per-app options, reward limits, focus history. Keeps all existing user data. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN daysMask INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN availableFrom INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN dailyLimitMin INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN usedTodayMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN usedDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN priceMult INTEGER NOT NULL DEFAULT 100")
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN schedStart INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN schedEnd INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE custom_rewards ADD COLUMN oncePerDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS focus_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "startedAt INTEGER NOT NULL, minutes INTEGER NOT NULL, " +
                "completed INTEGER NOT NULL, questTitle TEXT NOT NULL)"
        )
    }
}

/** v7 -> v8: quest types + per-quest streaks, per-app coin rate, coin log. Keeps all existing user data. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN questType TEXT NOT NULL DEFAULT 'onetime'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN streak INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN lastDoneDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN challengeDays INTEGER NOT NULL DEFAULT 0")
        // Give every existing quest a type that matches how it already behaves.
        db.execSQL(
            "UPDATE tasks SET questType = CASE " +
                "WHEN isTimerQuest = 1 THEN 'focus' " +
                "WHEN daysMask != 0 THEN 'weekly' " +
                "WHEN isRecurring = 1 AND recurType = 'weekly' THEN 'weekly' " +
                "WHEN isRecurring = 1 THEN 'daily' " +
                "ELSE 'onetime' END"
        )
        db.execSQL("ALTER TABLE blocked_apps ADD COLUMN ratePer5 INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS coin_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "timestamp INTEGER NOT NULL, amount INTEGER NOT NULL, " +
                "source TEXT NOT NULL, detail TEXT NOT NULL)"
        )
    }
}

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "questblock.db")
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
    @Provides fun focusLogDao(db: AppDatabase) = db.focusLogDao()
    @Provides fun coinLogDao(db: AppDatabase) = db.coinLogDao()
}
