package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class FilterUtils {
    private FilterUtils() {
    }

    public static boolean filter(final StreamInfoItem item,
                                 final FilterOptions filterOptions) {
        final String[] videoTitleKeywords =
                filterOptions
                        .getVideoTitleKeywords()
                        .toLowerCase(Locale.ROOT)
                        .split(",");
        final String[] uploaderNameKeywords =
                filterOptions
                        .getUploaderNameKeywords()
                        .toLowerCase(Locale.ROOT)
                        .split(",");
        final boolean hideShorts = filterOptions.isHideShorts();
        final String videoTitlePattern =
                filterOptions.getVideoTitlePattern().toLowerCase(Locale.ROOT);
        final String uploaderNamePattern =
                filterOptions.getUploaderNamePattern().toLowerCase(Locale.ROOT);

        final String videoTitle = item.getName().toLowerCase(Locale.ROOT);
        final String uploaderName = item.getUploaderName().toLowerCase(Locale.ROOT);

        try {
            // filter - is short
            if (hideShorts) {
                if (item.isShort()) {
                    return false;
                }
            }
            // filter (simple) - video title
            if (videoTitleKeywords.length > 0) {
                for (String keyword : videoTitleKeywords) {
                    keyword = keyword.trim();
                    if (!keyword.equals("") && videoTitle.contains(keyword)) {
                        return false;
                    }
                }
            }
            // filter (simple) - video uploader name
            if (uploaderNameKeywords.length > 0) {
                for (String keyword : uploaderNameKeywords) {
                    keyword = keyword.trim();
                    if (!keyword.equals("") && uploaderName.contains(keyword)) {
                        return false;
                    }
                }
            }
            // filter (advanced) - video title
            if (!Objects.equals(videoTitlePattern, "")
                    && !Pattern.matches(videoTitlePattern, videoTitle)) {
                return false;
            }
            // filter (advanced) - video uploader name
            if (!Objects.equals(uploaderNamePattern, "")
                    && !Pattern.matches(uploaderNamePattern, uploaderName)) {
                return false;
            }
        } catch (final Exception ex) {
            // ignored
        }

        return true;
    }
}
