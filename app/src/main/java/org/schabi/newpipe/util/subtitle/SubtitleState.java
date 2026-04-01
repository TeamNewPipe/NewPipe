package org.schabi.newpipe.extractor.utils;

import javax.annotation.Nonnull;

/**
 * Describes the processing state of a subtitle.
 *
 * - This enum represents whether the subtitle content
 *   is original or has been post-processed (e.g. deduplicated).
 * - Unlike `enum SubtitleOrigin`, this does not describe
 *   how the subtitle was created, but how it has been
 *   processed locally.
 */
public enum SubtitleState {

    // Original subtitle content, no modifications
    ORIGINAL("original"),
    // Subtitle content after deduplication processing
    DEDUPLICATED("deduplicated");

    private final String id;

    SubtitleState(@Nonnull final String id) {
        this.id = id;
    }

    @Nonnull
    public String getId() {
        return id;
    }
}
