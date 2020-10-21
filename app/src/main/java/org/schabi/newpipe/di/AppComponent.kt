package org.schabi.newpipe.di

import dagger.Component
import org.schabi.newpipe.settings.ContentSettingsFragment
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class, RoomModule::class])
interface AppComponent {
    fun inject(contentSettingsFragment: ContentSettingsFragment)
}
