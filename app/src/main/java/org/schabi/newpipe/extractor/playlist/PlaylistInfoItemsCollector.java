package org.schabi.newpipe.extractor.playlist;

import org.schabi.newpipe.extractor.plugin.PluginExtractorAdapters;

import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

public class PlaylistInfoItemsCollector
        extends InfoItemsCollector<PlaylistInfoItem, PlaylistInfoItemExtractor> {

    public PlaylistInfoItemsCollector(final int serviceId) {
        super(serviceId);
    }

    @Override
    public PlaylistInfoItem extract(final PlaylistInfoItemExtractor input)
            throws ParsingException {
        final PlaylistInfoItemExtractor extractor = PluginExtractorAdapters.playlist(input);
        final PlaylistInfoItem resultItem = new PlaylistInfoItem(
                getServiceId(), extractor.getUrl(), extractor.getName());

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
            resultItem.setUploaderUrl(extractor.getUploaderUrl());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setThumbnailUrl(extractor.getThumbnailUrl());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setStreamCount(extractor.getStreamCount());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setPlaylistType(extractor.getPlaylistType());
        } catch (final Exception e) {
            addError(e);
        }
        return resultItem;
    }
}
