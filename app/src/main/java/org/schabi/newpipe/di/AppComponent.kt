package org.schabi.newpipe.di

import dagger.Component
import dagger.Module
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.fragments.list.videos.RelatedVideosFragment
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistCreationDialog
import org.schabi.newpipe.local.feed.FeedFragment
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.local.subscription.di.SubscriptionComponent
import org.schabi.newpipe.local.subscription.dialog.di.FeedGroupDialogsComponent
import org.schabi.newpipe.player.MainPlayer
import org.schabi.newpipe.settings.ContentSettingsFragment
import org.schabi.newpipe.settings.HistorySettingsFragment
import org.schabi.newpipe.settings.SelectChannelFragment
import org.schabi.newpipe.settings.SelectPlaylistFragment
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
    fun subscriptionComponent(): SubscriptionComponent.Factory

    fun inject(feedLoadService: FeedLoadService)
    fun inject(mainPlayer: MainPlayer)

    // Fragments that extend BaseListFragment
    fun inject(channelFragment: ChannelFragment)
    fun inject(commentsFragment: CommentsFragment)
    fun inject(feedFragment: FeedFragment)
    fun inject(kioskFragment: KioskFragment)
    fun inject(relatedVideosFragment: RelatedVideosFragment)
    fun inject(searchFragment: SearchFragment)

    // Other fragments
    fun inject(bookmarkFragment: BookmarkFragment)
    fun inject(contentSettingsFragment: ContentSettingsFragment)
    fun inject(historySettingsFragment: HistorySettingsFragment)
    fun inject(localPlaylistFragment: LocalPlaylistFragment)
    fun inject(playlistFragment: PlaylistFragment)
    fun inject(selectChannelFragment: SelectChannelFragment)
    fun inject(selectPlaylistFragment: SelectPlaylistFragment)
    fun inject(statisticsPlaylistFragment: StatisticsPlaylistFragment)
    fun inject(videoDetailFragment: VideoDetailFragment)

    fun inject(playlistAppendDialog: PlaylistAppendDialog)
    fun inject(playlistCreationDialog: PlaylistCreationDialog)
}

@Module(
    subcomponents = [
        FeedGroupDialogsComponent::class,
        SubscriptionComponent::class
    ]
)
object SubcomponentsModule
