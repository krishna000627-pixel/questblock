package com.krishna.questblock.di

import android.content.Context
import androidx.room.Room
import com.krishna.questblock.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "questblock.db")
            .fallbackToDestructiveMigration().build()

    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun subTaskDao(db: AppDatabase) = db.subTaskDao()
    @Provides fun blockedAppDao(db: AppDatabase) = db.blockedAppDao()
    @Provides fun playerDao(db: AppDatabase) = db.playerDao()
    @Provides fun unlockSessionDao(db: AppDatabase) = db.unlockSessionDao()
    @Provides fun studySessionDao(db: AppDatabase) = db.studySessionDao()
    @Provides fun inventoryDao(db: AppDatabase) = db.inventoryDao()
    @Provides fun achievementDao(db: AppDatabase) = db.achievementDao()
    @Provides fun customShopDao(db: AppDatabase) = db.customShopDao()
    @Provides fun customPurchaseDao(db: AppDatabase) = db.customPurchaseDao()
}
