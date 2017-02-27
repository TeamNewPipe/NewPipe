package org.schabi.newpipe.extractor.channel;

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemCollector;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 25.07.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelExtractor.java is part of NewPipe.
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

public abstract class ChannelExtractor {
    private int serviceId;
    private String url;
    private UrlIdHandler urlIdHandler;
    private StreamInfoItemCollector previewInfoCollector;
    private int page = -1;

    public ChannelExtractor(UrlIdHandler urlIdHandler, String url, int page, int serviceId)
            throws ExtractionException, IOException {
        this.url = url;
        this.page = page;
        this.serviceId = serviceId;
        this.urlIdHandler = urlIdHandler;
        previewInfoCollector = new StreamInfoItemCollector(urlIdHandler, serviceId);
    }

    public String getUrl() { return url; }
    public UrlIdHandler getUrlIdHandler() { return urlIdHandler; }
    public StreamInfoItemCollector getStreamPreviewInfoCollector() {
        return previewInfoCollector;
    }

    public abstract String getChannelName() throws ParsingException;
    public abstract String getAvatarUrl() throws ParsingException;
    public abstract String getBannerUrl() throws ParsingException;
    public abstract String getFeedUrl() throws ParsingException;
    public abstract StreamInfoItemCollector getStreams() throws ParsingException;
    public abstract long getSubscriberCount() throws ParsingException;
    public abstract boolean hasNextPage() throws ParsingException;
    public int getServiceId() {
        return serviceId;
    }
}
