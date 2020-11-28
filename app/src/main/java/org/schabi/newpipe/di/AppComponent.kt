package org.schabi.newpipe.di

import dagger.Component
import dagger.Module
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.subscription.di.SubscriptionComponent
import org.schabi.newpipe.local.subscription.dialog.di.FeedGroupDialogsComponent
import org.schabi.newpipe.player.MainPlayer
import us.shandian.giga.service.DownloadManagerService
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        RoomModule::class,
        ViewModelBuilderModule::class,
        SubcomponentsModule::class
    ]
)
interface AppComponent {
    fun feedGroupDialogsComponent(): FeedGroupDialogsComponent.Factory
    fun fragmentComponent(): FragmentComponent.Factory
    fun subscriptionComponent(): SubscriptionComponent.Factory

    fun inject(downloadManagerService: DownloadManagerService)
    fun inject(feedLoadService: FeedLoadService)
    fun inject(mainPlayer: MainPlayer)

    fun inject(mainActivity: MainActivity)
    fun inject(reCaptchaActivity: ReCaptchaActivity)
    fun inject(routerActivity: RouterActivity)
}

@Module(
    subcomponents = [
        FeedGroupDialogsComponent::class,
        FragmentComponent::class,
        SubscriptionComponent::class
    ]
)
object SubcomponentsModule
