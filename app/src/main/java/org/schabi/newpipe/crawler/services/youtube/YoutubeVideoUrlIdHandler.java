package org.schabi.newpipe.crawler.services.youtube;

import org.schabi.newpipe.crawler.Parser;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.VideoUrlIdHandler;

/**
 * Created by Christian Schabesberger on 02.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeVideoUrlIdHandler.java is part of NewPipe.
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

public class YoutubeVideoUrlIdHandler implements VideoUrlIdHandler {
    @SuppressWarnings("WeakerAccess")
    @Override
    public String getVideoUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    @SuppressWarnings("WeakerAccess")
    @Override
    public String getVideoId(String url) throws ParsingException {
        String id;
        String pat;

        if(url.contains("youtube")) {
            pat = "youtube\\.com/watch\\?v=([\\-a-zA-Z0-9_]{11})";
        }
        else if(url.contains("youtu.be")) {
            pat = "youtu\\.be/([a-zA-Z0-9_-]{11})";
        }
        else {
            throw new ParsingException("Error no suitable url: " + url);
        }

        id = Parser.matchGroup1(pat, url);
        if(!id.isEmpty()){
            //Log.i(TAG, "string \""+url+"\" matches!");
            return id;
        } else {
            throw new ParsingException("Error could not parse url: " + url);
        }
    }

    public String cleanUrl(String complexUrl) throws ParsingException {
        return getVideoUrl(getVideoId(complexUrl));
    }

    @Override
    public boolean acceptUrl(String videoUrl) {
        return videoUrl.contains("youtube") ||
                videoUrl.contains("youtu.be");
    }
}
