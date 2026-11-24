package org.schabi.newpipe.extractor.comments;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeCommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface CommentsInfoItemExtractor extends InfoItemExtractor {

    /**
     * Return the like count of the comment,
     * or {@link CommentsInfoItem#NO_LIKE_COUNT} if it is unavailable.
     *
     * <br>
     * <p>
     * NOTE: Currently only implemented for YT {@link
     * YoutubeCommentsInfoItemExtractor#getLikeCount()}
     * with limitations (only approximate like count is returned)
     *
     * @return the comment's like count
     * or {@link CommentsInfoItem#NO_LIKE_COUNT} if it is unavailable
     * @see StreamExtractor#getLikeCount()
     */
    default int getLikeCount() throws ParsingException {
        return CommentsInfoItem.NO_LIKE_COUNT;
    }

    /**
     * The unmodified like count given by the service
     * <br>
     * It may be language dependent
     */
    default String getTextualLikeCount() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    /**
     * The text of the comment
     */
    default Description getCommentText() throws ParsingException {
        return new Description(getCommentTextContent(), Description.PLAIN_TEXT);
    }

    /** Keeps existing plain-text provider parsing separate from the shared Description API. */
    default String getCommentTextContent() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    /**
     * The upload date given by the service, unmodified
     *
     * @see StreamExtractor#getTextualUploadDate()
     */
    default String getTextualUploadDate() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    /**
     * The upload date wrapped with DateWrapper class
     *
     * @see StreamExtractor#getUploadDate()
     */
    @Nullable
    default DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

    default String getCommentId() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    default String getUploaderUrl() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    default String getUploaderName() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    @Nullable
    default String getUploaderId() throws ParsingException {
        return null;
    }

    default String getUploaderAvatarUrl() throws ParsingException {
        final List<Image> avatars = getUploaderAvatars();
        return avatars.isEmpty() ? Utils.EMPTY_STRING : avatars.get(0).getUrl();
    }

    default List<Image> getUploaderAvatars() throws ParsingException {
        return Collections.emptyList();
    }

    /**
     * Whether the comment has been hearted by the uploader
     */
    default boolean isHeartedByUploader() throws ParsingException {
        return false;
    }

    /**
     * Whether the comment is pinned
     */
    default boolean isPinned() throws ParsingException {
        return false;
    }

    /**
     * Whether the uploader is verified by the service
     */
    default boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    /**
     * The playback position of the stream to which this comment belongs.
     *
     * @see CommentsInfoItem#getStreamPosition()
     */
    default int getStreamPosition() throws ParsingException {
        return CommentsInfoItem.NO_STREAM_POSITION;
    }

    /**
     * The count of comment replies.
     *
     * @return the count of the replies
     * or {@link CommentsInfoItem#UNKNOWN_REPLY_COUNT} if replies are not supported
     */
    default int getReplyCount() throws ParsingException {
        return CommentsInfoItem.UNKNOWN_REPLY_COUNT;
    }

    /**
     * The continuation page which is used to get comment replies from.
     *
     * @return the continuation Page for the replies, or null if replies are not supported
     */
    @Nullable
    default Page getReplies() throws ParsingException {
        return null;
    }

    @Nonnull
    default Collection<Image> getPictures() throws ParsingException {
        return Collections.emptyList();
    }

    @Override
    default String getThumbnailUrl() throws ParsingException {
        return null;
    }
}
