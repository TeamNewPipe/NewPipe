package org.schabi.newpipe.extractor.services.youtube;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.ChannelExtractor;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.UrlIdHandler;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 25.07.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeChannelExtractor.java is part of NewPipe.
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

public class YoutubeChannelExtractor extends ChannelExtractor {

    private static final String TAG = YoutubeChannelExtractor.class.toString();

    private Downloader downloader;
    private final Document doc;
    private final String siteUrl;


    public YoutubeChannelExtractor(UrlIdHandler urlIdHandler, String url, Downloader dl, int serviceId)
            throws ExtractionException, IOException {
        super(urlIdHandler, url, dl, serviceId);

        siteUrl = url;
        downloader = dl;
        String pageContent = downloader.download(url);
        doc = Jsoup.parse(pageContent, url);

        Log.d(TAG, pageContent);
    }

    @Override
    public String getChannelName() throws ParsingException {
        return getUrlIdHandler().getId(siteUrl);
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        try {
            return doc.select("img[class=\"channel-header-profile-image\"]")
                    .first().attr("abs:src");
        } catch(Exception e) {
            throw new ParsingException("Could not get avatar", e);
        }
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        return "https://yt3.ggpht.com/-oF0YbeAGkaA/VBgrKvEGY1I/AAAAAAAACdw/nx02iZSseFw/w2120-fcrop64=1,00005a57ffffa5a8-nd-c0xffffffff-rj-k-no/Channel-Art-Template-%2528Photoshop%2529.png";
    }
}
