package com.krishna.questblock.di

import android.content.Context
import androidx.room.Room
import com.krishna.questblock.data.AppDatabase
import com.krishna.questblock.data.BlockedAppDao
import com.krishna.questblock.data.InventoryDao
import com.krishna.questblock.data.PlayerDao
import com.krishna.questblock.data.StudySessionDao
import com.krishna.questblock.data.TaskDao
import com.krishna.questblock.data.UnlockEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "questblock.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun taskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun appDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()
    @Provides fun playerDao(db: AppDatabase): PlayerDao = db.playerDao()
    @Provides fun unlockEventDao(db: AppDatabase): UnlockEventDao = db.unlockEventDao()
    @Provides fun inventoryDao(db: AppDatabase): InventoryDao = db.inventoryDao()
    @Provides fun studySessionDao(db: AppDatabase): StudySessionDao = db.studySessionDao()
}
