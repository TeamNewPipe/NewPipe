package org.schabi.newpipe.dependency_injection

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.AppDatabase.DATABASE_NAME
import org.schabi.newpipe.database.Migrations.MIGRATION_1_2
import org.schabi.newpipe.database.Migrations.MIGRATION_2_3
import org.schabi.newpipe.database.Migrations.MIGRATION_3_4
import org.schabi.newpipe.database.Migrations.MIGRATION_4_5
import org.schabi.newpipe.database.Migrations.MIGRATION_5_6
import org.schabi.newpipe.database.Migrations.MIGRATION_6_7
import org.schabi.newpipe.database.Migrations.MIGRATION_7_8
import org.schabi.newpipe.database.Migrations.MIGRATION_8_9
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase =
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            DATABASE_NAME
        ).addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
        ).build()

    @Provides
    fun provideStreamStateDao(appDatabase: AppDatabase): StreamStateDAO =
        appDatabase.streamStateDAO()

    @Provides
    fun providesStreamDao(appDatabase: AppDatabase): StreamDAO = appDatabase.streamDAO()

    @Provides
    fun provideStreamHistoryDao(appDatabase: AppDatabase): StreamHistoryDAO =
        appDatabase.streamHistoryDAO()

    @Provides
    fun provideSearchHistoryDao(appDatabase: AppDatabase): SearchHistoryDAO =
        appDatabase.searchHistoryDAO()
}
