package org.schabi.newpipe.extractor.bulletComments;

import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.utils.Utils;

import java.time.Duration;

public interface BulletCommentsInfoItemExtractor extends InfoItemExtractor {
    @Override
    default String getName() throws ParsingException {
        return null;
    }

    @Override
    default String getThumbnailUrl() throws ParsingException{
        return null;
    }

    @Override
    default String getUrl() throws ParsingException {
        return null;
    }

    default String getCommentText() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    /** Author name, or null when it is not provided. */
    default String getAuthorName() throws ParsingException {
        return null;
    }

    /** Author avatar, when the provider includes one for this comment. */
    default String getAuthorAvatarUrl() throws ParsingException {
        return null;
    }

    /** Paid amount for a super chat, or null for ordinary comments. */
    default String getAmountText() throws ParsingException {
        return null;
    }

    /**
     * Returns ARGB32 int. White: 0xFFFFFFFF.
     * @return ARGB32 int. White: 0xFFFFFFFF.
     */
    default int getArgbColor() throws ParsingException {
        return 0xFFFFFFFF;
    }

    default BulletCommentsInfoItem.Position getPosition() throws ParsingException {
        return BulletCommentsInfoItem.Position.REGULAR;
    }

    default double getRelativeFontSize() throws ParsingException {
        return 0.7;
    }

    default int getLastingTime() {
        return -1;
    }

    /** How long a paid comment stays pinned; zero means no separate pin duration. */
    default long getPinnedTimeMillis() {
        return 0;
    }

    default boolean isLive() throws ParsingException {
        return false;
    }

    // Must be implemented. If that is a live stream you should at least calculate the time from the start of the stream.
    Duration getDuration() throws ParsingException;
}
