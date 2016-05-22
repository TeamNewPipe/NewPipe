package org.schabi.newpipe.extractor.services.youtube;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.AbstractVideoInfo;
import org.schabi.newpipe.extractor.ParsingException;

/**
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeStreamPreviewInfoExtractor.java is part of NewPipe.
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

public class YoutubeChannelPreviewInfoExtractor extends YoutubeStreamPreviewInfoExtractor {

    private final Element item;

    public YoutubeChannelPreviewInfoExtractor(Element item) {
        super(item);
        this.item = item;
    }

    @Override
    public String getWebPageUrl() throws ParsingException {
        try {
            Element el = item.select("div[class*=\"yt-lockup-channel\"").first();
            Element dl = el.select("h3").first().select("a").first();
            return dl.attr("abs:href");
        } catch (Exception e) {
            throw new ParsingException("Could not get web page url for the video", e);
        }
    }

    @Override
    public String getTitle() throws ParsingException {
        try {
            Element el = item.select("div[class*=\"yt-lockup-channel\"").first();
            Element dl = el.select("h3").first().select("a").first();
            return dl.text();
        } catch (Exception e) {
            throw new ParsingException("Could not get title", e);
        }
    }

    @Override
    public int getDuration() throws ParsingException {
        return -1;
    }

    @Override
    public long getVideoCount() throws ParsingException {
        return super.getViewCount();
    }

    @Override
    public long getViewCount() throws ParsingException {
        return -1L;
    }

    @Override
    public String getUploader() throws ParsingException {
            return "";
    }

    @Override
    public AbstractVideoInfo.StreamType getStreamType() {
            return AbstractVideoInfo.StreamType.CHANNEL;
    }
}
