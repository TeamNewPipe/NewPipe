package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabHandler.Tab;

import java.util.Set;

public final class ChannelTabs {
    private ChannelTabs() {
    }

    @StringRes
    private static int getShowTabKey(final Tab tab) {
        switch (tab) {
            case Playlists:
                return R.string.show_channel_tabs_playlists;
            case Livestreams:
                return R.string.show_channel_tabs_livestreams;
            case Shorts:
                return R.string.show_channel_tabs_shorts;
            case Channels:
                return R.string.show_channel_tabs_channels;
            case Albums:
                break;
        }
        return -1;
    }

    @StringRes
    public static int getTranslationKey(final Tab tab) {
        switch (tab) {
            case Playlists:
                return R.string.channel_tab_playlists;
            case Livestreams:
                return R.string.channel_tab_livestreams;
            case Shorts:
                return R.string.channel_tab_shorts;
            case Channels:
                return R.string.channel_tab_channels;
            case Albums:
                return R.string.channel_tab_albums;
        }
        return R.string.unknown_content;
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
                                         final Tab tab) {
        final int key = ChannelTabs.getShowTabKey(tab);
        if (key == -1) {
            return false;
        }
        return showChannelTab(context, sharedPreferences, key);
    }
}
