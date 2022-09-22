package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

public final class FilterOptions {
    private final boolean hideShorts;
    private final String videoTitleKeywords;
    private final String uploaderNameKeywords;
    private final String videoTitlePattern;
    private final String uploaderNamePattern;

    public FilterOptions(final String videoTitleKeywords,
                         final String uploaderNameKeywords,
                         final boolean hideShorts,
                         final String videoTitlePattern,
                         final String uploaderNamePattern) {
        this.videoTitleKeywords = videoTitleKeywords;
        this.uploaderNameKeywords = uploaderNameKeywords;
        this.hideShorts = hideShorts;
        this.videoTitlePattern = videoTitlePattern;
        this.uploaderNamePattern = uploaderNamePattern;
    }

    public String getVideoTitleKeywords() {
        return videoTitleKeywords;
    }

    public String getUploaderNameKeywords() {
        return uploaderNameKeywords;
    }

    public boolean isHideShorts() {
        return hideShorts;
    }

    public String getVideoTitlePattern() {
        return videoTitlePattern;
    }

    public String getUploaderNamePattern() {
        return uploaderNamePattern;
    }

    public static FilterOptions fromPreferences(final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final String videoTitleKeywords =
                preferences.getString(
                        context.getString(R.string.simple_video_title_filter_key), "");
        final String uploaderNameKeywords =
                preferences.getString(
                        context.getString(R.string.simple_uploader_name_filter_key), "");
        final boolean hideShorts = preferences.getBoolean(
                context.getString(R.string.hide_shorts_key), false);
        final String videoTitleFilter =
                preferences.getString(
                        context.getString(R.string.regex_video_title_filter_key), "");
        final String uploaderNamePattern =
                preferences.getString(
                        context.getString(R.string.regex_uploader_name_filter_key), "");

        return new FilterOptions(
                videoTitleKeywords,
                uploaderNameKeywords,
                hideShorts,
                videoTitleFilter,
                uploaderNamePattern);
    }
}
