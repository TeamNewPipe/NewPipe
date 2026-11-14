/*
 * SPDX-FileCopyrightText: 2026 ninjaeu (Merak)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Matches item titles against a search query typed by the user.
 * <p>
 * Matching folds case and strips accents, so "beyonce" finds "Beyoncé". Every word of the
 * query must appear somewhere in the title, in any order, so "live artist" finds
 * "Artist - Song Title (Live 1985)".
 * </p>
 */
public final class TitleFilter {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @NonNull
    private final String[] terms;

    private TitleFilter(@NonNull final String[] terms) {
        this.terms = terms;
    }

    /**
     * @param query the text the user typed
     * @return a filter for the query, or {@code null} when the query holds no searchable text,
     * which callers treat as "show everything"
     */
    @Nullable
    public static TitleFilter of(@Nullable final String query) {
        if (query == null) {
            return null;
        }
        final String normalized = normalize(query).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return new TitleFilter(WHITESPACE.split(normalized));
    }

    /**
     * @param title the title of a playlist, stream or channel
     * @return whether the title contains every word of the query
     */
    public boolean matches(@Nullable final String title) {
        if (title == null) {
            return false;
        }
        final String normalized = normalize(title);
        for (final String term : terms) {
            if (!normalized.contains(term)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    static String normalize(@NonNull final String text) {
        // NFD splits "é" into "e" plus a combining accent, which the pattern then removes.
        final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
