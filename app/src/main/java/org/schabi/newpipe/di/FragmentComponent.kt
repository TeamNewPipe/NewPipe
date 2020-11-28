package org.schabi.newpipe.di

import dagger.Subcomponent
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.fragments.MainFragment
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
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.settings.BasePreferenceFragment
import org.schabi.newpipe.settings.ContentSettingsFragment
import org.schabi.newpipe.settings.HistorySettingsFragment
import org.schabi.newpipe.settings.PeertubeInstanceListFragment
import org.schabi.newpipe.settings.SelectChannelFragment
import org.schabi.newpipe.settings.SelectPlaylistFragment
import us.shandian.giga.ui.fragment.MissionsFragment

@Subcomponent
interface FragmentComponent {
    @Subcomponent.Factory
    interface Factory {
        fun create(): FragmentComponent
    }

    // Fragments that extend BaseListFragment
    fun inject(channelFragment: ChannelFragment)
    fun inject(commentsFragment: CommentsFragment)
    fun inject(feedFragment: FeedFragment)
    fun inject(kioskFragment: KioskFragment)
    fun inject(relatedVideosFragment: RelatedVideosFragment)
    fun inject(searchFragment: SearchFragment)

    // Preference fragments
    fun inject(basePreferenceFragment: BasePreferenceFragment)
    fun inject(contentSettingsFragment: ContentSettingsFragment)
    fun inject(historySettingsFragment: HistorySettingsFragment)

    // Other fragments
    fun inject(bookmarkFragment: BookmarkFragment)
    fun inject(mainFragment: MainFragment)
    fun inject(missionsFragment: MissionsFragment)
    fun inject(localPlaylistFragment: LocalPlaylistFragment)
    fun inject(peertubeInstanceListFragment: PeertubeInstanceListFragment)
    fun inject(playlistFragment: PlaylistFragment)
    fun inject(selectChannelFragment: SelectChannelFragment)
    fun inject(selectPlaylistFragment: SelectPlaylistFragment)
    fun inject(statisticsPlaylistFragment: StatisticsPlaylistFragment)
    fun inject(videoDetailFragment: VideoDetailFragment)

    // Dialogs
    fun inject(downloadDialog: DownloadDialog)
    fun inject(playbackParameterDialog: PlaybackParameterDialog)
    fun inject(playlistAppendDialog: PlaylistAppendDialog)
    fun inject(playlistCreationDialog: PlaylistCreationDialog)
}
