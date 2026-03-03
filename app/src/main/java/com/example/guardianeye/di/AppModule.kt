package com.example.guardianeye.di

import android.content.Context
import androidx.work.WorkManager
import com.example.guardianeye.data.local.AppDatabase
import com.example.guardianeye.data.local.ChatDao
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.data.repository.ChatRepository
import com.example.guardianeye.data.repository.FirebaseManager
import com.example.guardianeye.data.repository.FootageManager
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
import com.example.guardianeye.utils.PreferenceManager
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
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao, preferenceManager: PreferenceManager): ChatRepository {
        return ChatRepository(chatDao, preferenceManager)
    }

    @Provides
    @Singleton
    fun provideAlertRepository(database: AppDatabase, preferenceManager: PreferenceManager): AlertRepository {
        return AlertRepository(database.alertDao(), preferenceManager)
    }

    @Provides
    @Singleton
    fun provideFirebaseManager(@ApplicationContext context: Context): FirebaseManager {
        return FirebaseManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMpinStorage(@ApplicationContext context: Context): MpinStorage {
        return MpinStorage(context)
    }

    @Provides
    @Singleton
    fun provideFootageManager(@ApplicationContext context: Context, preferenceManager: PreferenceManager): FootageManager {
        return FootageManager(context, preferenceManager)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
