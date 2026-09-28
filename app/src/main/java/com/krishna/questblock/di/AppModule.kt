package com.krishna.questblock.di

import android.content.Context
import androidx.room.Room
import com.krishna.questblock.data.AppDatabase
import com.krishna.questblock.data.BlockedAppDao
import com.krishna.questblock.data.PlayerDao
import com.krishna.questblock.data.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "questblock.db").build()

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideBlockedAppDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()
    @Provides fun providePlayerDao(db: AppDatabase): PlayerDao = db.playerDao()
}
