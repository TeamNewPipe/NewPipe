package org.schabi.newpipe.di

import android.app.Application
import dagger.Module
import dagger.Provides
import org.schabi.newpipe.database.AppDatabase
import javax.inject.Singleton

@Module
class RoomModule(application: Application) {
    private val appDatabase = AppDatabase.getInstance(application)

    @Provides
    @Singleton
    fun provideAppDatabase() = appDatabase

    @Provides
    @Singleton
    fun provideSearchHistoryDAO() = appDatabase.searchHistoryDAO()

    @Provides
    @Singleton
    fun provideStreamDAO() = appDatabase.streamDAO()

    @Provides
    @Singleton
    fun provideStreamHistoryDAO() = appDatabase.streamHistoryDAO()

    @Provides
    @Singleton
    fun provideStreamStateDAO() = appDatabase.streamStateDAO()

    @Provides
    @Singleton
    fun providePlaylistDAO() = appDatabase.playlistDAO()

    @Provides
    @Singleton
    fun providePlaylistStreamDAO() = appDatabase.playlistStreamDAO()

    @Provides
    @Singleton
    fun providePlaylistRemoteDAO() = appDatabase.playlistRemoteDAO()

    @Provides
    @Singleton
    fun provideFeedDAO() = appDatabase.feedDAO()

    @Provides
    @Singleton
    fun provideFeedGroupDAO() = appDatabase.feedGroupDAO()

    @Provides
    @Singleton
    fun provideSubscriptionDAO() = appDatabase.subscriptionDAO()
}
