/*
 * SPDX-FileCopyrightText: PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Source: PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Shared filter contract retained for binary plugin compatibility.
 */

package org.schabi.newpipe.extractor.comments;

import org.jsoup.Jsoup;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.utils.KeywordMatcher;
import org.schabi.newpipe.extractor.utils.ServiceScopedId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * User-defined rules for hiding comments: by keywords (or {@code /regex/}) in the comment text,
 * by the commenter's name, or by a minimum like count.
 */
public class CommentsFilterConfig {
    private final KeywordMatcher keywords;
    private final List<String> usernames = new ArrayList<>();
    private final List<String> userIds = new ArrayList<>();
    private final int minLikeCount;

    public CommentsFilterConfig(final List<String> keywords,
                                final List<String> usernames,
                                final int minLikeCount) {
        this(keywords, usernames, null, minLikeCount);
    }

    public CommentsFilterConfig(final List<String> keywords,
                                final List<String> usernames,
                                final List<String> userIds,
                                final int minLikeCount) {
        if (userIds != null) {
            this.userIds.addAll(userIds);
        }
        this.keywords = new KeywordMatcher(keywords);
        if (usernames != null) {
            for (final String username : usernames) {
                final String normalized = normalizeUsername(username);
                if (!normalized.isEmpty()) {
                    this.usernames.add(normalized);
                }
            }
        }
        this.minLikeCount = Math.max(0, minLikeCount);
    }

    public boolean isEmpty() {
        return keywords.isEmpty() && usernames.isEmpty() && userIds.isEmpty()
                && minLikeCount <= 0;
    }

    public boolean shouldFilter(final CommentsInfoItem item) {
        if (item == null) {
            return false;
        }

        // Like count filter. Unknown like counts are kept, and pinned comments are exempt
        // so that the uploader's pinned comment is still visible on new videos.
        if (minLikeCount > 0 && !item.isPinned()
                && item.getLikeCount() != CommentsInfoItem.NO_LIKE_COUNT
                && item.getLikeCount() >= 0
                && item.getLikeCount() < minLikeCount) {
            return true;
        }

        if (!userIds.isEmpty()) {
            final String key = ServiceScopedId.of(item.getServiceId(), item.getUploaderId());
            if (key != null && userIds.contains(key)) {
                return true;
            }
        }

        if (!usernames.isEmpty()) {
            final String uploader = normalizeUsername(item.getUploaderName());
            if (!uploader.isEmpty() && usernames.contains(uploader)) {
                return true;
            }
        }

        if (!keywords.isEmpty() && keywords.matches(getPlainText(item.getCommentText()))) {
            return true;
        }

        return false;
    }

    public List<CommentsInfoItem> filter(final List<CommentsInfoItem> items) {
        if (items == null || isEmpty()) {
            return items;
        }
        final List<CommentsInfoItem> result = new ArrayList<>(items.size());
        for (final CommentsInfoItem item : items) {
            if (!shouldFilter(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static String normalizeUsername(final String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim();
        // YouTube shows handles as "@name"; match with or without the leading "@"
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String getPlainText(final Description description) {
        if (description == null || description.getContent() == null) {
            return "";
        }
        String content = description.getContent();
        if (description.getType() == Description.HTML) {
            content = Jsoup.parse(content).text();
        }
        return content;
    }
}
