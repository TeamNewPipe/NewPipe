/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.library.LibraryActions
import io.github.yewplay.feature.library.LibraryIcons
import io.github.yewplay.feature.library.LibraryScreen
import io.github.yewplay.feature.library.LibraryStrings
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpLibraryRepository
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the "Library" destination of the bottom bar, drawn by the Compose screen in
 * :feature:library on top of the app's data.
 */
class LibraryFragment : BaseFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val themeKey = prefs.getString(
            getString(R.string.theme_key),
            getString(R.string.default_theme_value)
        ) ?: getString(R.string.default_theme_value)
        val nightThemeKey = prefs.getString(
            getString(R.string.night_theme_key),
            getString(R.string.default_night_theme_value)
        ) ?: getString(R.string.default_night_theme_value)
        val historyEnabled = prefs.getBoolean(getString(R.string.enable_watch_history_key), true)
        val repository = YpLibraryRepository(context)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) {
                    LibraryScreen(
                        repository = repository,
                        strings = LibraryStrings(
                            continueWatching = stringResource(R.string.yp_library_continue),
                            history = stringResource(R.string.yp_library_history),
                            downloads = stringResource(R.string.yp_library_downloads),
                            savedPlaylists = stringResource(R.string.yp_library_saved_playlists),
                            about = stringResource(R.string.yp_library_about),
                            yourPlaylists = stringResource(R.string.yp_library_your_playlists),
                            videoCount = { count ->
                                resources.getQuantityString(
                                    R.plurals.yp_video_count,
                                    count.toInt(),
                                    count.toInt()
                                )
                            }
                        ),
                        icons = LibraryIcons(
                            history = painterResource(R.drawable.ic_history),
                            downloads = painterResource(R.drawable.ic_file_download),
                            savedPlaylists = painterResource(R.drawable.ic_bookmark),
                            about = painterResource(R.drawable.ic_info_outline),
                            chevron = painterResource(R.drawable.yp_ic_caret_right)
                        ),
                        actions = LibraryActions(
                            openVideo = { video ->
                                NavigationHelper.openVideoDetailFragment(
                                    requireContext(),
                                    fm,
                                    video.serviceId,
                                    video.url,
                                    video.title,
                                    null,
                                    false
                                )
                            },
                            openHistory = { NavigationHelper.openStatisticFragment(fm) },
                            openDownloads = { NavigationHelper.openDownloads(requireActivity()) },
                            openSavedPlaylists = { NavigationHelper.openBookmarksFragment(fm) },
                            openAbout = { NavigationHelper.openAbout(requireContext()) },
                            openPlaylist = { playlist ->
                                if (playlist.isLocal) {
                                    NavigationHelper.openLocalPlaylistFragment(
                                        fm,
                                        playlist.id,
                                        playlist.name
                                    )
                                } else {
                                    playlist.url?.let { url ->
                                        NavigationHelper.openPlaylistFragment(
                                            fm,
                                            playlist.serviceId,
                                            url,
                                            playlist.name
                                        )
                                    }
                                }
                            }
                        ),
                        historyEnabled = historyEnabled
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_yp_library, menu)
        setTitle(getString(R.string.yp_nav_library))
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        activity.supportActionBar?.subtitle = null
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_yp_settings) {
            NavigationHelper.openSettings(requireContext())
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
