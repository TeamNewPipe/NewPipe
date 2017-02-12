package org.schabi.newpipe.extractor.channel;

import org.schabi.newpipe.extractor.InfoItemCollector;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.FoundAdException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemExtractor;

/**
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemCollector.java is part of NewPipe.
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

public class ChannelInfoItemCollector extends InfoItemCollector {
    public ChannelInfoItemCollector(int serviceId) {
        super(serviceId);
    }

    public void commit(ChannelInfoItemExtractor extractor) throws ParsingException {
        try {
            ChannelInfoItem resultItem = new ChannelInfoItem();
            // importand information
            resultItem.channelName = extractor.getChannelName();

            resultItem.serviceId = getServiceId();
            resultItem.webPageUrl = extractor.getWebPageUrl();

            // optional information
            try {
                resultItem.subscriberCount = extractor.getSubscriberCount();
            } catch (Exception e) {
                addError(e);
            }
            try {
                resultItem.videoAmount = extractor.getVideoAmount();
            } catch (Exception e) {
                addError(e);
            }

            addItem(resultItem);
        } catch (Exception e) {
            addError(e);
        }
    }
}
