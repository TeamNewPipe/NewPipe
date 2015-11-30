package org.schabi.newpipe.services.youtube;

import org.schabi.newpipe.services.StreamingService;
import org.schabi.newpipe.services.VideoExtractor;
import org.schabi.newpipe.services.SearchEngine;


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

public class YoutubeService implements StreamingService {
    @Override
    public ServiceInfo getServiceInfo() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.name = "Youtube";
        return serviceInfo;
    }
    @Override
    public VideoExtractor getExtractorInstance(String url) {
        if(acceptUrl(url)) {
            return new YoutubeVideoExtractor(url);
        }
        else {
            throw new IllegalArgumentException("supplied String is not a valid Youtube URL");
        }
    }
    @Override
    public SearchEngine getSearchEngineInstance() {
        return new YoutubeSearchEngine();
    }
    @Override
    public boolean acceptUrl(String videoUrl) {
        return videoUrl.contains("youtube") ||
                videoUrl.contains("youtu.be");
    }
}
