package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.List;
import java.util.Set;

public final class ChannelTabHelper {
    private ChannelTabHelper() {
    }

    /**
     * @param tab the channel tab to check
     * @return whether the tab should contain (playable) streams or not
     */
    public static boolean isStreamsTab(final String tab) {
        switch (tab) {
            case ChannelTabs.VIDEOS:
            case ChannelTabs.TRACKS:
            case ChannelTabs.SHORTS:
            case ChannelTabs.LIVESTREAMS:
                return true;
            default:
                return false;
        }
    }

    /**
     * @param tab the channel tab link handler to check
     * @return whether the tab should contain (playable) streams or not
     */
    public static boolean isStreamsTab(final ListLinkHandler tab) {
        final List<String> contentFilters = tab.getContentFilters();
        if (contentFilters.isEmpty()) {
            return false; // this should never happen, but check just to be sure
        } else {
            return isStreamsTab(contentFilters.get(0));
        }
    }

    @StringRes
    private static int getShowTabKey(final String tab) {
        switch (tab) {
            case ChannelTabs.VIDEOS:
                return R.string.show_channel_tabs_videos;
            case ChannelTabs.TRACKS:
                return R.string.show_channel_tabs_tracks;
            case ChannelTabs.SHORTS:
                return R.string.show_channel_tabs_shorts;
            case ChannelTabs.LIVESTREAMS:
                return R.string.show_channel_tabs_livestreams;
            case ChannelTabs.CHANNELS:
                return R.string.show_channel_tabs_channels;
            case ChannelTabs.PLAYLISTS:
                return R.string.show_channel_tabs_playlists;
            case ChannelTabs.ALBUMS:
                return R.string.show_channel_tabs_albums;
            default:
                return -1;
        }
    }

    @StringRes
    private static int getFetchFeedTabKey(final String tab) {
        switch (tab) {
            case ChannelTabs.VIDEOS:
                return R.string.fetch_channel_tabs_videos;
            case ChannelTabs.TRACKS:
                return R.string.fetch_channel_tabs_tracks;
            case ChannelTabs.SHORTS:
                return R.string.fetch_channel_tabs_shorts;
            case ChannelTabs.LIVESTREAMS:
                return R.string.fetch_channel_tabs_livestreams;
            default:
                return -1;
        }
    }

    @StringRes
    public static int getTranslationKey(final String tab) {
        switch (tab) {
            case ChannelTabs.VIDEOS:
                return R.string.channel_tab_videos;
            case ChannelTabs.TRACKS:
                return R.string.channel_tab_tracks;
            case ChannelTabs.SHORTS:
                return R.string.channel_tab_shorts;
            case ChannelTabs.LIVESTREAMS:
                return R.string.channel_tab_livestreams;
            case ChannelTabs.CHANNELS:
                return R.string.channel_tab_channels;
            case ChannelTabs.PLAYLISTS:
                return R.string.channel_tab_playlists;
            case ChannelTabs.ALBUMS:
                return R.string.channel_tab_albums;
            default:
                return R.string.unknown_content;
        }
    }

    public static boolean showChannelTab(final Context context,
                                         final SharedPreferences sharedPreferences,
                                         @StringRes final int key) {
        final Set<String> enabledTabs = sharedPreferences.getStringSet(
                context.getString(R.string.show_channel_tabs_key), null);
        if (enabledTabs == null) {
            return true; // default to true
        } else {
            return enabledTabs.contains(context.getString(key));
        }
    }

    public static boolean showChannelTab(final Context context,
                                         final SharedPreferences sharedPreferences,
                                         final String tab) {
        final int key = ChannelTabHelper.getShowTabKey(tab);
        if (key == -1) {
            return false;
        }
        return showChannelTab(context, sharedPreferences, key);
    }

    public static boolean fetchFeedChannelTab(final Context context,
                                              final SharedPreferences sharedPreferences,
                                              final ListLinkHandler tab) {
        final List<String> contentFilters = tab.getContentFilters();
        if (contentFilters.isEmpty()) {
            return false; // this should never happen, but check just to be sure
        }

        final int key = ChannelTabHelper.getFetchFeedTabKey(contentFilters.get(0));
        if (key == -1) {
            return false;
        }

        final Set<String> enabledTabs = sharedPreferences.getStringSet(
                context.getString(R.string.feed_fetch_channel_tabs_key), null);
        if (enabledTabs == null) {
            return true; // default to true
        } else {
            return enabledTabs.contains(context.getString(key));
        }
    }
}
