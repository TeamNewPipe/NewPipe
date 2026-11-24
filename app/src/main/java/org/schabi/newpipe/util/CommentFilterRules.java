package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.comments.CommentsInfoItem;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Optional comment-only rules; unknown like counts are never treated as zero. */
public final class CommentFilterRules {
    private final List<String> keywords;
    private final List<String> authors;
    private final int minimumLikes;

    public CommentFilterRules(final String keywords, final String authors, final int minimumLikes) {
        this.keywords = lines(keywords);
        this.authors = lines(authors);
        this.minimumLikes = Math.max(0, minimumLikes);
    }

    public boolean isBlocked(final CommentsInfoItem comment) {
        final String text = normalize(comment.getCommentTextContent());
        return keywords.stream().anyMatch(text::contains)
                || authors.contains(normalize(comment.getUploaderName()))
                || comment.getLikeCount() >= 0 && comment.getLikeCount() < minimumLikes;
    }

    private static List<String> lines(final String value) {
        return Arrays.stream((value == null ? "" : value).split("\\n"))
                .map(CommentFilterRules::normalize).filter(line -> !line.isEmpty())
                .distinct().collect(Collectors.toList());
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
