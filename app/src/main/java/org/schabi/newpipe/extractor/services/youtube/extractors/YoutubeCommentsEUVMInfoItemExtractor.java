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
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeDescriptionHelper.attributedDescriptionToHtml;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

/**
 * A {@link CommentsInfoItemExtractor} for YouTube comment data returned in a view model and entity
 * updates.
 */
class YoutubeCommentsEUVMInfoItemExtractor implements CommentsInfoItemExtractor {

    private static final String AUTHOR = "author";
    private static final String PROPERTIES = "properties";

    @Nonnull
    private final JsonObject commentViewModel;
    @Nullable
    private final JsonObject commentRepliesRenderer;
    @Nonnull
    private final JsonObject commentEntityPayload;
    @Nonnull
    private final JsonObject engagementToolbarStateEntityPayload;
    @Nonnull
    private final JsonObject commentSurface;
    @Nonnull
    private final String videoUrl;
    @Nonnull
    private final TimeAgoParser timeAgoParser;

    YoutubeCommentsEUVMInfoItemExtractor(
            @Nonnull final JsonObject commentViewModel,
            @Nullable final JsonObject commentRepliesRenderer,
            @Nonnull final JsonObject commentEntityPayload,
            @Nonnull final JsonObject engagementToolbarStateEntityPayload,
            @Nonnull final String videoUrl,
            @Nonnull final TimeAgoParser timeAgoParser) {
        this(commentViewModel, commentRepliesRenderer, commentEntityPayload,
                engagementToolbarStateEntityPayload, new JsonObject(), videoUrl, timeAgoParser);
    }

    YoutubeCommentsEUVMInfoItemExtractor(
            @Nonnull final JsonObject commentViewModel,
            @Nullable final JsonObject commentRepliesRenderer,
            @Nonnull final JsonObject commentEntityPayload,
            @Nonnull final JsonObject engagementToolbarStateEntityPayload,
            @Nonnull final JsonObject commentSurface,
            @Nonnull final String videoUrl,
            @Nonnull final TimeAgoParser timeAgoParser) {
        this.commentViewModel = commentViewModel;
        this.commentRepliesRenderer = commentRepliesRenderer;
        this.commentEntityPayload = commentEntityPayload;
        this.engagementToolbarStateEntityPayload = engagementToolbarStateEntityPayload;
        this.commentSurface = commentSurface;
        this.videoUrl = videoUrl;
        this.timeAgoParser = timeAgoParser;
    }

    @Override
    public String getName() throws ParsingException {
        return getUploaderName();
    }

    @Override
    public String getUrl() throws ParsingException {
        return videoUrl;
    }


    @Override
    public int getLikeCount() throws ParsingException {
        final String textualLikeCount = getTextualLikeCount();
        try {
            if (Utils.isBlank(textualLikeCount)) {
                return 0;
            }

            return (int) Utils.mixedNumberWordToLong(textualLikeCount);
        } catch (final Exception e) {
            throw new ParsingException(
                    "Unexpected error while converting textual like count to like count", e);
        }
    }

    @Override
    public String getTextualLikeCount() {
        return commentEntityPayload.getObject("toolbar")
                .getString("likeCountNotliked");
    }

    @Override
    public String getCommentText() throws ParsingException {
        // Comments' text work in the same way as an attributed video description
        return HtmlParser.htmlToString(new Description(
                attributedDescriptionToHtml(commentEntityPayload.getObject(PROPERTIES)
                        .getObject("content")), Description.HTML).getContent());
    }

    @Nonnull
    @Override
    public Collection<Image> getPictures() {
        final JsonObject attachment = commentSurface.getObject("imageAttachmentViewModel");
        final JsonObject imageViewModel = attachment.has("commentImageAttachmentViewModel")
                ? attachment.getObject("commentImageAttachmentViewModel") : attachment;
        final JsonObject image = imageViewModel.getObject("image");
        final List<Image> candidates = getImagesFromThumbnailsArray(
                image.getArray("sources").isEmpty()
                        ? image.getArray("thumbnails") : image.getArray("sources"));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        // Sources are alternative sizes of the same attached picture, not separate pictures.
        final Image largest = candidates.stream().max(java.util.Comparator
                .comparingLong(candidate -> (long) candidate.getHeight()
                        * candidate.getWidth())).orElse(candidates.get(0));
        return Collections.singletonList(largest);
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return commentEntityPayload.getObject(PROPERTIES)
                .getString("publishedTime");
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualPublishedTime = getTextualUploadDate();
        if (isNullOrEmpty(textualPublishedTime)) {
            return null;
        }

        return timeAgoParser.parse(textualPublishedTime);
    }

    @Override
    public String getCommentId() throws ParsingException {
        String commentId = commentEntityPayload.getObject(PROPERTIES)
                .getString("commentId");
        if (isNullOrEmpty(commentId)) {
            commentId = commentViewModel.getString("commentId");
            if (isNullOrEmpty(commentId)) {
                throw new ParsingException("Could not get comment ID");
            }
        }
        return commentId;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        final JsonObject author = commentEntityPayload.getObject(AUTHOR);
        String channelId = author.getString("channelId");
        if (isNullOrEmpty(channelId)) {
            channelId = author.getObject("channelCommand")
                    .getObject("innertubeCommand")
                    .getObject("browseEndpoint")
                    .getString("browseId");
            if (isNullOrEmpty(channelId)) {
                channelId = author.getObject("avatar")
                        .getObject("endpoint")
                        .getObject("innertubeCommand")
                        .getObject("browseEndpoint")
                        .getString("browseId");
                if (isNullOrEmpty(channelId)) {
                    throw new ParsingException("Could not get channel ID");
                }
            }
        }
        return "https://www.youtube.com/channel/" + channelId;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return commentEntityPayload.getObject(AUTHOR)
                .getString("displayName");
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        // Older entity payloads contain a full image object at the root.
        final JsonObject avatar = commentEntityPayload.getObject("avatar", null);
        if (avatar != null) {
            final List<Image> uploaderAvatars = getImagesFromThumbnailsArray(
                    avatar.getObject("image").getArray("sources"));
            if (!uploaderAvatars.isEmpty()) {
                return uploaderAvatars.get(0).getUrl();
            }
        }

        // Current YouTube entity payloads expose the avatar as a direct URL on the author.
        final JsonObject author = commentEntityPayload.getObject(AUTHOR);
        final String avatarThumbnailUrl = author.getString("avatarThumbnailUrl");
        if (!isNullOrEmpty(avatarThumbnailUrl)) {
            return avatarThumbnailUrl;
        }

        // Keep accepting the short-lived nested author shape used by some responses.
        final List<Image> authorAvatars = getImagesFromThumbnailsArray(
                author.getObject("avatar").getObject("image").getArray("sources"));
        return authorAvatars.isEmpty() ? "" : authorAvatars.get(0).getUrl();
    }

    @Override
    public boolean isHeartedByUploader() {
        return "TOOLBAR_HEART_STATE_HEARTED".equals(
                engagementToolbarStateEntityPayload.getString("heartState"));
    }

    @Override
    public boolean isPinned() {
        return commentViewModel.has("pinnedText");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        final JsonObject author = commentEntityPayload.getObject(AUTHOR);
        return author.getBoolean("isVerified") || author.getBoolean("isArtist");
    }

    @Override
    public int getReplyCount() throws ParsingException {
        // As YouTube allows replies up to 750 comments, we cannot check if the count returned is a
        // mixed number or a real number
        // Assume it is a mixed one, as it matches how numbers of most properties are returned
        final String replyCountString = commentEntityPayload.getObject("toolbar")
                .getString("replyCount");
        if (isNullOrEmpty(replyCountString)) {
            return 0;
        }
        return (int) Utils.mixedNumberWordToLong(replyCountString);
    }

    @Nullable
    @Override
    public Page getReplies() throws ParsingException {
        if (isNullOrEmpty(commentRepliesRenderer)) {
            return null;
        }

        final String continuation = commentRepliesRenderer.getArray("contents")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(content -> content.getObject("continuationItemRenderer", null))
                .filter(Objects::nonNull)
                .findFirst()
                .map(continuationItemRenderer ->
                        continuationItemRenderer.getObject("continuationEndpoint")
                                .getObject("continuationCommand")
                                .getString("token"))
                .orElse(null);
        if (continuation == null) {
            return null;
        }
        return new Page(videoUrl, continuation);
    }
}
