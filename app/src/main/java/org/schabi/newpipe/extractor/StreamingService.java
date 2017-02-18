package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamingService.java is part of NewPipe.
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

public abstract class StreamingService {
    public class ServiceInfo {
        public String name = "";
    }

    public enum LinkType {
        NONE,
        STREAM,
        CHANNEL,
        PLAYLIST
    }

    private int serviceId;

    public StreamingService(int id) {
        serviceId = id;
    }

    public abstract ServiceInfo getServiceInfo();

    public abstract StreamExtractor getExtractorInstance(String url)
            throws IOException, ExtractionException;
    public abstract SearchEngine getSearchEngineInstance();
    public abstract UrlIdHandler getStreamUrlIdHandlerInstance();
    public abstract UrlIdHandler getChannelUrlIdHandlerInstance();
    public abstract ChannelExtractor getChannelExtractorInstance(String url, int page)
            throws ExtractionException, IOException;
    public abstract SuggestionExtractor getSuggestionExtractorInstance();

    public final int getServiceId() {
        return serviceId;
    }

    /**
     * figure out where the link is pointing to (a channel, video, playlist, etc.)
     */
    public final LinkType getLinkTypeByUrl(String url) {
        UrlIdHandler sH = getStreamUrlIdHandlerInstance();
        UrlIdHandler cH = getChannelUrlIdHandlerInstance();

        if(sH.acceptUrl(url)) {
            return LinkType.STREAM;
        } else if(cH.acceptUrl(url)) {
            return LinkType.CHANNEL;
        } else {
            return LinkType.NONE;
        }
    }
}
