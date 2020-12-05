package org.schabi.newpipe

import dagger.Component
import org.schabi.newpipe.di.AppComponent
import org.schabi.newpipe.di.AppModule
import org.schabi.newpipe.di.SubcomponentsModule
import org.schabi.newpipe.di.ViewModelBuilderModule
import org.schabi.newpipe.local.playlist.LocalPlaylistManagerTest
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        TestRoomModule::class,
        ViewModelBuilderModule::class,
        SubcomponentsModule::class
    ]
)
interface TestAppComponent : AppComponent {
    fun inject(localPlaylistManagerTest: LocalPlaylistManagerTest)
}
