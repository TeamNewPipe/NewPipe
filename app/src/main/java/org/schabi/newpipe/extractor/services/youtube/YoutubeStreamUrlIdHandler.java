package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.StreamUrlIdHandler;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Created by Christian Schabesberger on 02.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeStreamUrlIdHandler.java is part of NewPipe.
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

public class YoutubeStreamUrlIdHandler implements StreamUrlIdHandler {
    @SuppressWarnings("WeakerAccess")
    @Override
    public String getVideoUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    @SuppressWarnings("WeakerAccess")
    @Override
    public String getVideoId(String url) throws ParsingException {
        String id;

        if(url.contains("youtube")) {
            if(url.contains("attribution_link")) {
                try {
                    String escapedQuery = Parser.matchGroup1("u=(.[^&|$]*)", url);
                    String query = URLDecoder.decode(escapedQuery, "UTF-8");
                    id = Parser.matchGroup1("v=([\\-a-zA-Z0-9_]{11})", query);
                } catch(UnsupportedEncodingException uee) {
                    throw new ParsingException("Could not parse attribution_link", uee);
                }
            } else {
                id = Parser.matchGroup1("[?&]v=([\\-a-zA-Z0-9_]{11})", url);
            }
        }
        else if(url.contains("youtu.be")) {
            if(url.contains("v=")) {
                id = Parser.matchGroup1("v=([\\-a-zA-Z0-9_]{11})", url);
            } else {
                id = Parser.matchGroup1("youtu\\.be/([a-zA-Z0-9_-]{11})", url);
            }
        }
        else {
            throw new ParsingException("Error no suitable url: " + url);
        }


        if(!id.isEmpty()){
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
