package org.schabi.newpipe.settings.dependency_injection

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import org.schabi.newpipe.error.usecases.OpenErrorActivity
import org.schabi.newpipe.settings.domain.repositories.HistoryRecordRepository
import org.schabi.newpipe.settings.domain.repositories.HistoryRecordRepositoryImpl
import org.schabi.newpipe.settings.domain.usecases.DeleteCompleteSearchHistory
import org.schabi.newpipe.settings.domain.usecases.DeleteCompleteStreamStateHistory
import org.schabi.newpipe.settings.domain.usecases.DeletePlaybackStates
import org.schabi.newpipe.settings.domain.usecases.DeleteWatchHistory
import org.schabi.newpipe.settings.domain.usecases.RemoveOrphanedRecords
import org.schabi.newpipe.settings.domain.usecases.get_preference.GetPreference
import org.schabi.newpipe.settings.domain.usecases.get_preference.GetPreferenceImpl
import org.schabi.newpipe.settings.domain.usecases.update_preference.UpdatePreference
import org.schabi.newpipe.settings.domain.usecases.update_preference.UpdatePreferenceImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideGetBooleanPreference(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
    ): GetPreference<Boolean> = GetPreferenceImpl(sharedPreferences, context)

    @Provides
    @Singleton
    fun provideGetStringPreference(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
    ): GetPreference<String> = GetPreferenceImpl(sharedPreferences, context)

    @Provides
    @Singleton
    fun provideUpdateBooleanPreference(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
    ): UpdatePreference<Boolean> = UpdatePreferenceImpl(context, sharedPreferences) { key, value ->
        putBoolean(
            key,
            value
        )
    }

    @Provides
    @Singleton
    fun provideUpdateStringPreference(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
    ): UpdatePreference<String> = UpdatePreferenceImpl(context, sharedPreferences) { key, value ->
        putString(
            key,
            value
        )
    }

    @Provides
    @Singleton
    fun provideUpdateIntPreference(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
    ): UpdatePreference<Int> = UpdatePreferenceImpl(context, sharedPreferences) { key, value ->
        putInt(key, value)
    }

    @Provides
    @Singleton
    fun provideHistoryRecordRepository(
        streamStateDao: StreamStateDAO,
        streamHistoryDAO: StreamHistoryDAO,
        streamDAO: StreamDAO,
        searchHistoryDAO: SearchHistoryDAO,
    ): HistoryRecordRepository = HistoryRecordRepositoryImpl(
        streamStateDao = streamStateDao,
        streamHistoryDAO = streamHistoryDAO,
        streamDAO = streamDAO,
        searchHistoryDAO = searchHistoryDAO,
    )

    @Provides
    @Singleton
    fun provideDeletePlaybackStatesUseCase(
        historyRecordRepository: HistoryRecordRepository,
    ): DeletePlaybackStates = DeletePlaybackStates(
        historyRecordRepository = historyRecordRepository,
    )

    @Provides
    @Singleton
    fun provideDeleteWholeStreamHistoryUseCase(
        historyRecordRepository: HistoryRecordRepository,
    ): DeleteCompleteStreamStateHistory = DeleteCompleteStreamStateHistory(
        historyRecordRepository = historyRecordRepository,
    )

    @Provides
    @Singleton
    fun provideRemoveOrphanedRecordsUseCase(
        historyRecordRepository: HistoryRecordRepository,
    ): RemoveOrphanedRecords = RemoveOrphanedRecords(
        historyRecordRepository = historyRecordRepository,
    )

    @Provides
    @Singleton
    fun provideDeleteCompleteSearchHistoryUseCase(
        historyRecordRepository: HistoryRecordRepository,
    ): DeleteCompleteSearchHistory = DeleteCompleteSearchHistory(
        historyRecordRepository = historyRecordRepository,
    )

    @Provides
    @Singleton
    fun provideDeleteWatchHistoryUseCase(
        deletePlaybackStates: DeletePlaybackStates,
        deleteCompleteStreamStateHistory: DeleteCompleteStreamStateHistory,
        removeOrphanedRecords: RemoveOrphanedRecords,
        openErrorActivity: OpenErrorActivity,
    ): DeleteWatchHistory = DeleteWatchHistory(
        deletePlaybackStates = deletePlaybackStates,
        deleteCompleteStreamStateHistory = deleteCompleteStreamStateHistory,
        removeOrphanedRecords = removeOrphanedRecords,
        openErrorActivity = openErrorActivity
    )
}
