package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

object ChannelTabHelper {
    /**
     * @param tab the channel tab to check
     * @return whether the tab should contain (playable) streams or not
     */
    fun isStreamsTab(tab: String?): Boolean {
        when (tab) {
            ChannelTabs.VIDEOS, ChannelTabs.TRACKS, ChannelTabs.SHORTS, ChannelTabs.LIVESTREAMS -> return true
            else -> return false
        }
    }

    /**
     * @param tab the channel tab link handler to check
     * @return whether the tab should contain (playable) streams or not
     */
    fun isStreamsTab(tab: ListLinkHandler?): Boolean {
        val contentFilters: List<String> = tab!!.getContentFilters()
        if (contentFilters.isEmpty()) {
            return false // this should never happen, but check just to be sure
        } else {
            return isStreamsTab(contentFilters.get(0))
        }
    }

    @StringRes
    private fun getShowTabKey(tab: String): Int {
        when (tab) {
            ChannelTabs.VIDEOS -> return R.string.show_channel_tabs_videos
            ChannelTabs.TRACKS -> return R.string.show_channel_tabs_tracks
            ChannelTabs.SHORTS -> return R.string.show_channel_tabs_shorts
            ChannelTabs.LIVESTREAMS -> return R.string.show_channel_tabs_livestreams
            ChannelTabs.CHANNELS -> return R.string.show_channel_tabs_channels
            ChannelTabs.PLAYLISTS -> return R.string.show_channel_tabs_playlists
            ChannelTabs.ALBUMS -> return R.string.show_channel_tabs_albums
            else -> return -1
        }
    }

    @StringRes
    private fun getFetchFeedTabKey(tab: String): Int {
        when (tab) {
            ChannelTabs.VIDEOS -> return R.string.fetch_channel_tabs_videos
            ChannelTabs.TRACKS -> return R.string.fetch_channel_tabs_tracks
            ChannelTabs.SHORTS -> return R.string.fetch_channel_tabs_shorts
            ChannelTabs.LIVESTREAMS -> return R.string.fetch_channel_tabs_livestreams
            else -> return -1
        }
    }

    @StringRes
    fun getTranslationKey(tab: String?): Int {
        when (tab) {
            ChannelTabs.VIDEOS -> return R.string.channel_tab_videos
            ChannelTabs.TRACKS -> return R.string.channel_tab_tracks
            ChannelTabs.SHORTS -> return R.string.channel_tab_shorts
            ChannelTabs.LIVESTREAMS -> return R.string.channel_tab_livestreams
            ChannelTabs.CHANNELS -> return R.string.channel_tab_channels
            ChannelTabs.PLAYLISTS -> return R.string.channel_tab_playlists
            ChannelTabs.ALBUMS -> return R.string.channel_tab_albums
            else -> return R.string.unknown_content
        }
    }

    fun showChannelTab(context: Context,
                       sharedPreferences: SharedPreferences,
                       @StringRes key: Int): Boolean {
        val enabledTabs: Set<String>? = sharedPreferences.getStringSet(
                context.getString(R.string.show_channel_tabs_key), null)
        if (enabledTabs == null) {
            return true // default to true
        } else {
            return enabledTabs.contains(context.getString(key))
        }
    }

    fun showChannelTab(context: Context,
                       sharedPreferences: SharedPreferences,
                       tab: String): Boolean {
        val key: Int = getShowTabKey(tab)
        if (key == -1) {
            return false
        }
        return showChannelTab(context, sharedPreferences, key)
    }

    fun fetchFeedChannelTab(context: Context,
                            sharedPreferences: SharedPreferences,
                            tab: ListLinkHandler): Boolean {
        val contentFilters: List<String> = tab.getContentFilters()
        if (contentFilters.isEmpty()) {
            return false // this should never happen, but check just to be sure
        }
        val key: Int = getFetchFeedTabKey(contentFilters.get(0))
        if (key == -1) {
            return false
        }
        val enabledTabs: Set<String>? = sharedPreferences.getStringSet(
                context.getString(R.string.feed_fetch_channel_tabs_key), null)
        if (enabledTabs == null) {
            return true // default to true
        } else {
            return enabledTabs.contains(context.getString(key))
        }
    }
}
