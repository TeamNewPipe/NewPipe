package org.schabi.newpipe.extractor.comments;

import org.schabi.newpipe.extractor.plugin.PluginExtractorAdapters;

import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.ArrayList;
import java.util.List;

public final class CommentsInfoItemsCollector
        extends InfoItemsCollector<CommentsInfoItem, CommentsInfoItemExtractor> {

    public CommentsInfoItemsCollector(final int serviceId) {
        super(serviceId);
    }

    @Override
    public CommentsInfoItem extract(final CommentsInfoItemExtractor input)
            throws ParsingException {
        final CommentsInfoItemExtractor extractor = PluginExtractorAdapters.comments(input);
        final CommentsInfoItem resultItem = new CommentsInfoItem(
                getServiceId(), extractor.getUrl(), extractor.getName());

        // optional information
        try {
            resultItem.setCommentId(extractor.getCommentId());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setCommentText(extractor.getCommentText());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setUploaderName(extractor.getUploaderName());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setUploaderId(extractor.getUploaderId());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setUploaderAvatarUrl(extractor.getUploaderAvatarUrl());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setUploaderUrl(extractor.getUploaderUrl());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setTextualUploadDate(extractor.getTextualUploadDate());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setUploadDate(extractor.getUploadDate());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setLikeCount(extractor.getLikeCount());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setTextualLikeCount(extractor.getTextualLikeCount());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setThumbnailUrl(extractor.getThumbnailUrl());
        } catch (final Exception e) {
            addError(e);
        }

        try {
            resultItem.setHeartedByUploader(extractor.isHeartedByUploader());
        } catch (final Exception e) {
            addError(e);
        }

        try {
            resultItem.setPinned(extractor.isPinned());
        } catch (final Exception e) {
            addError(e);
        }

        try {
            resultItem.setStreamPosition(extractor.getStreamPosition());
        } catch (final Exception e) {
            addError(e);
        }

        try {
            resultItem.setReplyCount(extractor.getReplyCount());
        } catch (final Exception e) {
            addError(e);
        }

        try {
            resultItem.setReplies(extractor.getReplies());
        } catch (final Exception e) {
            addError(e);
        }

        try {
            resultItem.setPictures(extractor.getPictures());
        } catch (final Exception e) {
            addError(e);
        }

        return resultItem;
    }

    @Override
    public void commit(final CommentsInfoItemExtractor extractor) {
        try {
            addItem(extract(extractor));
        } catch (final Exception e) {
            addError(e);
        }
    }

    public List<CommentsInfoItem> getCommentsInfoItemList() {
        return new ArrayList<>(super.getItems());
    }
}
