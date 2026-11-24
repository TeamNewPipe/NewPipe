package org.schabi.newpipe.extractor.channel;

import org.schabi.newpipe.extractor.plugin.PluginExtractorAdapters;

import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

/*
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemsCollector.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public final class ChannelInfoItemsCollector
        extends InfoItemsCollector<ChannelInfoItem, ChannelInfoItemExtractor> {
    public ChannelInfoItemsCollector(final int serviceId) {
        super(serviceId);
    }

    @Override
    public ChannelInfoItem extract(final ChannelInfoItemExtractor input)
            throws ParsingException {
        final ChannelInfoItemExtractor extractor = PluginExtractorAdapters.channel(input);
        final ChannelInfoItem resultItem = new ChannelInfoItem(
                getServiceId(), extractor.getUrl(), extractor.getName());

        // optional information
        try {
            resultItem.setUploaderId(extractor.getUploaderId());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setSubscriberCount(extractor.getSubscriberCount());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setStreamCount(extractor.getStreamCount());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setThumbnailUrl(extractor.getThumbnailUrl());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setDescription(extractor.getDescription());
        } catch (final Exception e) {
            addError(e);
        }
        try {
            resultItem.setVerified(extractor.isVerified());
        } catch (final Exception e) {
            addError(e);
        }

        return resultItem;
    }
}
