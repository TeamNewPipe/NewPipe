package org.schabi.newpipe.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.schabi.newpipe.ktx.defaultSharedPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    @Provides
    @Singleton
    fun provideDefaultSharedPreferences(@ApplicationContext appContext: Context) = appContext.defaultSharedPreferences
}
