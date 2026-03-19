package com.wourd.app.di

import android.content.Context
import androidx.room.Room
import com.wourd.app.data.chat.ChatRepositoryImpl
import com.wourd.app.data.db.WourdDatabase
import com.wourd.app.data.network.AiRepositoryImpl
import com.wourd.app.data.settings.SettingsDataStore
import com.wourd.app.domain.repository.AiRepository
import com.wourd.app.domain.repository.ChatRepository
import com.wourd.app.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WourdDatabase =
        Room.databaseBuilder(context, WourdDatabase::class.java, "wourd.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideChatDao(db: WourdDatabase) = db.chatDao()

    @Provides
    fun provideMessageDao(db: WourdDatabase) = db.messageDao()

    @Provides
    @Singleton
    fun provideChatRepository(db: WourdDatabase): ChatRepository =
        ChatRepositoryImpl(db.chatDao(), db.messageDao())

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideAiRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): AiRepository = AiRepositoryImpl(context, okHttpClient)
}

