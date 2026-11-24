package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.utils.HtmlParser;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static org.schabi.newpipe.extractor.comments.CommentsInfoItem.UNKNOWN_REPLY_COUNT;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;

public class YoutubeCommentsInfoItemExtractor implements CommentsInfoItemExtractor {

    @Nonnull
    private final JsonObject commentRenderer;
    @Nullable
    private final JsonObject commentRepliesRenderer;
    @Nonnull
    private final String url;
    @Nonnull
    private final TimeAgoParser timeAgoParser;

    public YoutubeCommentsInfoItemExtractor(@Nonnull final JsonObject commentRenderer,
                                            @Nullable final JsonObject commentRepliesRenderer,
                                            @Nonnull final String url,
                                            @Nonnull final TimeAgoParser timeAgoParser) {
        this.commentRenderer = commentRenderer;
        this.commentRepliesRenderer = commentRepliesRenderer;
        this.url = url;
        this.timeAgoParser = timeAgoParser;
    }

    @Nonnull
    private List<Image> getAuthorThumbnails() throws ParsingException {
        try {
            return getImagesFromThumbnailsArray(JsonUtils.getArray(commentRenderer,
                    "authorThumbnail.thumbnails"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get author thumbnails", e);
        }
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return url;
    }


    @Override
    public String getName() throws ParsingException {
        try {
            return getTextFromObject(JsonUtils.getObject(commentRenderer, "authorText"));
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        try {
            return getTextFromObject(JsonUtils.getObject(commentRenderer,
                    "publishedTimeText"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get publishedTimeText", e);
        }
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualPublishedTime = getTextualUploadDate();
        if (textualPublishedTime != null && !textualPublishedTime.isEmpty()) {
            return timeAgoParser.parse(textualPublishedTime);
        } else {
            return null;
        }
    }

    /**
     * @implNote The method tries first to get the exact like count by using the accessibility data
     * returned. But if the parsing of this accessibility data fails, the method parses internally
     * a localized string.
     * <br>
     * <ul>
     *     <li>More than 1k likes will result in an inaccurate number</li>
     *     <li>This will fail for other languages than English. However as long as the Extractor
     *     only uses "en-GB" (as seen in {@link
     *     org.schabi.newpipe.extractor.services.youtube.YoutubeService#getSupportedLocalizations})
     *     , everything will work fine.</li>
     * </ul>
     * <br>
     * Consider using {@link #getTextualLikeCount()}
     */
    @Override
    public int getLikeCount() throws ParsingException {
        // Try first to get the exact like count by using the accessibility data
        final String likeCount;
        try {
            likeCount = Utils.removeNonDigitCharacters(JsonUtils.getString(commentRenderer,
                    "actionButtons.commentActionButtonsRenderer.likeButton.toggleButtonRenderer"
                            + ".accessibilityData.accessibilityData.label"));
        } catch (final Exception e) {
            // Use the approximate like count returned into the voteCount object
            // This may return a language dependent version, e.g. in German: 3,3 Mio
            final String textualLikeCount = getTextualLikeCount();
            try {
                if (Utils.isBlank(textualLikeCount)) {
                    return 0;
                }

                return (int) Utils.mixedNumberWordToLong(textualLikeCount);
            } catch (final Exception i) {
                throw new ParsingException(
                        "Unexpected error while converting textual like count to like count", i);
            }
        }

        try {
            if (Utils.isBlank(likeCount)) {
                return 0;
            }

            return Integer.parseInt(likeCount);
        } catch (final Exception e) {
            throw new ParsingException("Unexpected error while parsing like count as Integer", e);
        }
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        /*
         * Example results as of 2021-05-20:
         * Language = English
         * 3.3M
         * 48K
         * 1.4K
         * 270K
         * 19
         * 6
         *
         * Language = German
         * 3,3 Mio
         * 48.189
         * 1419
         * 270.984
         * 19
         * 6
         */
        try {
            // If a comment has no likes voteCount is not set
            if (!commentRenderer.has("voteCount")) {
                return "";
            }

            final JsonObject voteCountObj = JsonUtils.getObject(commentRenderer, "voteCount");
            if (voteCountObj.isEmpty()) {
                return "";
            }
            return getTextFromObject(voteCountObj);
        } catch (final Exception e) {
            throw new ParsingException("Could not get the vote count", e);
        }
    }

    @Override
    public String getCommentTextContent() throws ParsingException {
        try {
            final JsonObject contentText = JsonUtils.getObject(commentRenderer, "contentText");
            if (contentText.isEmpty()) {
                // completely empty comments as described in
                // https://github.com/TeamNewPipe/NewPipeExtractor/issues/380#issuecomment-668808584
                return "";
            }
            final String commentText = getTextFromObject(contentText, true);
            // YouTube adds U+FEFF in some comments.
            // eg. https://www.youtube.com/watch?v=Nj4F63E59io<feff>
            final String commentTextBomRemoved = Utils.removeUTF8BOM(commentText);

            return HtmlParser.htmlToString(commentTextBomRemoved);
        } catch (final Exception e) {
            throw new ParsingException("Could not get comment text", e);
        }
    }

    @Override
    public String getCommentId() throws ParsingException {
        try {
            return JsonUtils.getString(commentRenderer, "commentId");
        } catch (final Exception e) {
            throw new ParsingException("Could not get comment id", e);
        }
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        final List<Image> authorThumbnails = getAuthorThumbnails();
        return authorThumbnails.isEmpty() ? "" : authorThumbnails.get(0).getUrl();
    }

    @Override
    public boolean isHeartedByUploader() {
        final JsonObject commentActionButtonsRenderer = commentRenderer.getObject("actionButtons")
                .getObject("commentActionButtonsRenderer");
        return commentActionButtonsRenderer.has("creatorHeart");
    }

    @Override
    public boolean isPinned() {
        return commentRenderer.has("pinnedCommentBadge");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return commentRenderer.has("authorCommentBadge");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        try {
            return getTextFromObject(JsonUtils.getObject(commentRenderer, "authorText"));
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        try {
            return "https://www.youtube.com/channel/" + JsonUtils.getString(commentRenderer,
                    "authorEndpoint.browseEndpoint.browseId");
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public int getReplyCount() {
        if (commentRenderer.has("replyCount")) {
            return commentRenderer.getInt("replyCount");
        }
        return UNKNOWN_REPLY_COUNT;
    }

    @Override
    public Page getReplies() {
        if (commentRepliesRenderer == null) {
            return null;
        }

        try {
            final String id = JsonUtils.getString(
                    JsonUtils.getArray(commentRepliesRenderer, "contents")
                            .getObject(0),
                    "continuationItemRenderer.continuationEndpoint.continuationCommand.token");
            return new Page(url, id);
        } catch (final Exception e) {
            return null;
        }
    }
}
