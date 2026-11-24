package org.schabi.newpipe.extractor.post;

import org.schabi.newpipe.extractor.plugin.PluginExtractorAdapters;

import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

public final class PostInfoItemsCollector
        extends InfoItemsCollector<PostInfoItem, PostInfoItemExtractor> {
    public PostInfoItemsCollector(final int serviceId) {
        super(serviceId);
    }

    @Override
    public PostInfoItem extract(final PostInfoItemExtractor input) throws ParsingException {
        final PostInfoItemExtractor extractor = PluginExtractorAdapters.post(input);
        final PostInfoItem item = new PostInfoItem(
                getServiceId(), extractor.getUrl(), extractor.getName());

        trySet(() -> item.setPostId(extractor.getPostId()));
        trySet(() -> item.setContent(extractor.getContent()));
        trySet(() -> item.setUploaderName(extractor.getUploaderName()));
        trySet(() -> item.setUploaderUrl(extractor.getUploaderUrl()));
        trySet(() -> item.setUploaderAvatars(extractor.getUploaderAvatars()));
        trySet(() -> item.setTextualUploadDate(extractor.getTextualUploadDate()));
        trySet(() -> item.setUploadDate(extractor.getUploadDate()));
        trySet(() -> item.setLikeCount(extractor.getLikeCount()));
        trySet(() -> item.setCommentCount(extractor.getCommentCount()));
        trySet(() -> item.setTextualLikeCount(extractor.getTextualLikeCount()));
        trySet(() -> item.setTextualCommentCount(extractor.getTextualCommentCount()));
        trySet(() -> item.setImages(extractor.getImages()));
        trySet(() -> item.setAttachment(extractor.getAttachment()));
        trySet(() -> item.setPoll(extractor.getPoll()));
        trySet(() -> item.setPinned(extractor.isPinned()));
        trySet(() -> item.setEdited(extractor.isEdited()));
        trySet(() -> item.setThumbnailUrl(extractor.getThumbnailUrl()));

        return item;
    }

    private void trySet(final ThrowingRunnable setter) {
        try {
            setter.run();
        } catch (final Exception e) {
            addError(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
