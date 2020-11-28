package org.schabi.newpipe.di

import android.app.Application
import dagger.Module
import dagger.Provides
import org.schabi.newpipe.ktx.defaultSharedPreferences
import javax.inject.Singleton

@Module
class AppModule(val application: Application) {
    @Provides
    @Singleton
    fun provideApplication() = application

    @Provides
    @Singleton
    fun provideDefaultSharedPreferences() = application.defaultSharedPreferences
}
