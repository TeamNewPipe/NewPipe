package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.FoundAdException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
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

public class YoutubeStreamUrlIdHandler implements UrlIdHandler {

    private static final YoutubeStreamUrlIdHandler instance = new YoutubeStreamUrlIdHandler();
    private static final String ID_PATTERN = "([\\-a-zA-Z0-9_]{11})";

    private YoutubeStreamUrlIdHandler() {}

    public static YoutubeStreamUrlIdHandler getInstance() {
        return instance;
    }

    @Override
    public String getUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    @Override
    public String getId(String url) throws ParsingException, IllegalArgumentException {
        if(url.isEmpty()) {
            throw new IllegalArgumentException("The url parameter should not be empty");
        }

        String id;
        String lowercaseUrl = url.toLowerCase();
        if(lowercaseUrl.contains("youtube")) {
            if (url.contains("attribution_link")) {
                try {
                    String escapedQuery = Parser.matchGroup1("u=(.[^&|$]*)", url);
                    String query = URLDecoder.decode(escapedQuery, "UTF-8");
                    id = Parser.matchGroup1("v=" + ID_PATTERN, query);
                } catch (UnsupportedEncodingException uee) {
                    throw new ParsingException("Could not parse attribution_link", uee);
                }
            } else if(lowercaseUrl.contains("youtube.com/shared?ci=")) {
                return getRealIdFromSharedLink(url);
            } else if (url.contains("vnd.youtube")) {
                id = Parser.matchGroup1(ID_PATTERN, url);
            } else if (url.contains("embed")) {
                id = Parser.matchGroup1("embed/" + ID_PATTERN, url);
            } else if(url.contains("googleads")) {
                throw new FoundAdException("Error found add: " + url);
            } else {
                id = Parser.matchGroup1("[?&]v=" + ID_PATTERN, url);
            }
        }
        else if(lowercaseUrl.contains("youtu.be")) {
            if(url.contains("v=")) {
                id = Parser.matchGroup1("v=" + ID_PATTERN, url);
            } else {
                id = Parser.matchGroup1("[Yy][Oo][Uu][Tt][Uu]\\.[Bb][Ee]/" + ID_PATTERN, url);
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

    /**
     * Get the real url from a shared uri.
     *
     * Shared URI's look like this:
     * <pre>
     *     * https://www.youtube.com/shared?ci=PJICrTByb3E
     *     * vnd.youtube://www.youtube.com/shared?ci=PJICrTByb3E&feature=twitter-deep-link
     * </pre>
     * @param url The shared url
     * @return the id of the stream
     * @throws ParsingException
     */
    private String getRealIdFromSharedLink(String url) throws ParsingException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ParsingException("Invalid shared link", e);
        }
        String sharedId = getSharedId(uri);
        Downloader downloader = NewPipe.getDownloader();
        String content;
        try {
            content = downloader.download("https://www.youtube.com/shared?ci=" + sharedId);
        } catch (IOException | ReCaptchaException e) {
            throw new ParsingException("Unable to resolve shared link", e);
        }
        // is this bad? is this fragile?:
        String realId = Parser.matchGroup1("rel=\"shortlink\" href=\"https://youtu.be/" + ID_PATTERN, content);
        if(sharedId.equals(realId)) {
            throw new ParsingException("Got same id for as shared id: " + sharedId);
        }
        return realId;
    }

    private String getSharedId(URI uri) throws ParsingException {
        if (!"/shared".equals(uri.getPath())) {
            throw new ParsingException("Not a shared link: " + uri.toString() + " (path != " + uri.getPath() + ")");
        }
        return Parser.matchGroup1("ci=" + ID_PATTERN, uri.getQuery());
    }

    public String cleanUrl(String complexUrl) throws ParsingException {
        return getUrl(getId(complexUrl));
    }

    @Override
    public boolean acceptUrl(String videoUrl) {
        String lowercaseUrl = videoUrl.toLowerCase();
        if(lowercaseUrl.contains("youtube") ||
                lowercaseUrl.contains("youtu.be")) {
            // bad programming I know
            try {
                getId(videoUrl);
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }
}
