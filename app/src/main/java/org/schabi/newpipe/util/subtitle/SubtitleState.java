package org.schabi.newpipe.util.subtitle;

import androidx.annotation.NonNull;

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

    SubtitleState(@NonNull final String id) {
        this.id = id;
    }

    @NonNull
    public String getId() {
        return id;
    }
}
