package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.StreamExtractor;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.StreamUrlIdHandler;
import org.schabi.newpipe.extractor.SearchEngine;

import java.io.IOException;


/**
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeService.java is part of NewPipe.
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

public class YoutubeService extends StreamingService {

    public YoutubeService(int id) {
        super(id);
    }

    @Override
    public ServiceInfo getServiceInfo() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.name = "Youtube";
        return serviceInfo;
    }
    @Override
    public StreamExtractor getExtractorInstance(String url, Downloader downloader)
            throws ExtractionException, IOException {
        StreamUrlIdHandler urlIdHandler = new YoutubeStreamUrlIdHandler();
        if(urlIdHandler.acceptUrl(url)) {
            return new YoutubeStreamExtractor(url, downloader, getServiceId());
        }
        else {
            throw new IllegalArgumentException("supplied String is not a valid Youtube URL");
        }
    }
    @Override
    public SearchEngine getSearchEngineInstance(Downloader downloader) {
        return new YoutubeSearchEngine(getUrlIdHandlerInstance(), getServiceId());
    }

    @Override
    public StreamUrlIdHandler getUrlIdHandlerInstance() {
        return new YoutubeStreamUrlIdHandler();
    }
}
