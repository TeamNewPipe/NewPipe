/*
 * SPDX-FileCopyrightText: PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Source: PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Shared filter contract retained for binary plugin compatibility.
 */

package org.schabi.newpipe.extractor.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Case-insensitive matcher for user-defined keyword filters.
 *
 * <p>A keyword wrapped in slashes ({@code /pattern/}) is treated as a regular expression
 * and matched with {@link java.util.regex.Matcher#find()}; any other keyword is matched
 * as a plain substring. Invalid regular expressions are ignored.</p>
 */
public final class KeywordMatcher {
    private final List<String> plainKeywords = new ArrayList<>();
    private final List<Pattern> patterns = new ArrayList<>();

    public KeywordMatcher(final List<String> keywords) {
        if (keywords == null) {
            return;
        }
        for (final String raw : keywords) {
            if (raw == null) {
                continue;
            }
            final String keyword = raw.trim();
            if (keyword.isEmpty()) {
                continue;
            }
            if (isRegex(keyword)) {
                try {
                    patterns.add(compile(keyword));
                } catch (final PatternSyntaxException ignored) {
                    // Skip invalid patterns, e.g. from imported preferences
                }
            } else {
                plainKeywords.add(keyword.toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * @return whether the keyword uses the {@code /pattern/} regex syntax
     */
    public static boolean isRegex(final String keyword) {
        return keyword != null && keyword.length() > 2
                && keyword.startsWith("/") && keyword.endsWith("/");
    }

    /**
     * Compile a {@code /pattern/} keyword.
     *
     * @throws PatternSyntaxException if the pattern is invalid
     */
    public static Pattern compile(final String regexKeyword) {
        return Pattern.compile(regexKeyword.substring(1, regexKeyword.length() - 1),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public boolean isEmpty() {
        return plainKeywords.isEmpty() && patterns.isEmpty();
    }

    public boolean matches(final String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (!plainKeywords.isEmpty()) {
            final String lower = text.toLowerCase(Locale.ROOT);
            for (final String keyword : plainKeywords) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }
        for (final Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
