package org.schabi.newpipe.di

import dagger.Component
import javax.inject.Singleton
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistCreationDialog
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.settings.ContentSettingsFragment
import org.schabi.newpipe.settings.SelectPlaylistFragment

@Singleton
@Component(modules = [AppModule::class, RoomModule::class])
interface AppComponent {
    fun inject(bookmarkFragment: BookmarkFragment)
    fun inject(contentSettingsFragment: ContentSettingsFragment)
    fun inject(localPlaylistFragment: LocalPlaylistFragment)
    fun inject(playlistFragment: PlaylistFragment)
    fun inject(selectPlaylistFragment: SelectPlaylistFragment)

    fun inject(playlistAppendDialog: PlaylistAppendDialog)
    fun inject(playlistCreationDialog: PlaylistCreationDialog)
}
