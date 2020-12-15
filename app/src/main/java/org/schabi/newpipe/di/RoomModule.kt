package org.schabi.newpipe.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RoomModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context) = NewPipeDatabase.getInstance(appContext)

    @Provides
    @Singleton
    fun provideSearchHistoryDAO(appDatabase: AppDatabase) = appDatabase.searchHistoryDAO()

    @Provides
    @Singleton
    fun provideStreamDAO(appDatabase: AppDatabase) = appDatabase.streamDAO()

    @Provides
    @Singleton
    fun provideStreamHistoryDAO(appDatabase: AppDatabase) = appDatabase.streamHistoryDAO()

    @Provides
    @Singleton
    fun provideStreamStateDAO(appDatabase: AppDatabase) = appDatabase.streamStateDAO()

    @Provides
    @Singleton
    fun providePlaylistDAO(appDatabase: AppDatabase) = appDatabase.playlistDAO()

    @Provides
    @Singleton
    fun providePlaylistStreamDAO(appDatabase: AppDatabase) = appDatabase.playlistStreamDAO()

    @Provides
    @Singleton
    fun providePlaylistRemoteDAO(appDatabase: AppDatabase) = appDatabase.playlistRemoteDAO()

    @Provides
    @Singleton
    fun provideFeedDAO(appDatabase: AppDatabase) = appDatabase.feedDAO()

    @Provides
    @Singleton
    fun provideFeedGroupDAO(appDatabase: AppDatabase) = appDatabase.feedGroupDAO()

    @Provides
    @Singleton
    fun provideSubscriptionDAO(appDatabase: AppDatabase) = appDatabase.subscriptionDAO()
}
