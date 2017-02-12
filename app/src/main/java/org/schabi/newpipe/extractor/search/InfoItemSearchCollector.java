package org.schabi.newpipe.extractor.search;

import org.schabi.newpipe.extractor.InfoItemCollector;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemCollector;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemCollector;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemExtractor;

/**
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * InfoItemSearchCollector.java is part of NewPipe.
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

public class InfoItemSearchCollector extends InfoItemCollector {
    private String suggestion = "";
    private StreamInfoItemCollector streamCollector;
    private ChannelInfoItemCollector channelCollector;

    InfoItemSearchCollector(UrlIdHandler handler, int serviceId) {
        super(serviceId);
        streamCollector = new StreamInfoItemCollector(handler, serviceId);
        channelCollector = new ChannelInfoItemCollector(serviceId);
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public SearchResult getSearchResult() throws ExtractionException {
        SearchResult result = new SearchResult();

        addFromCollector(channelCollector);
        addFromCollector(streamCollector);

        result.suggestion = suggestion;
        result.errors = getErrors();
        result.resultList = getItemList();
        return result;
    }

    public void commit(StreamInfoItemExtractor extractor) throws ParsingException {
        streamCollector.commit(extractor);
    }

    public void commit(ChannelInfoItemExtractor extractor) throws ParsingException {
        channelCollector.commit(extractor);
    }
}
