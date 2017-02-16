package org.schabi.newpipe.extractor.services.youtube;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.InfoItemSearchCollector;
import org.schabi.newpipe.extractor.search.SearchEngine;

import java.net.URLEncoder;
import java.io.IOException;
import java.util.EnumSet;


/**
 * Created by Christian Schabesberger on 09.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeSearchEngine.java is part of NewPipe.
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

public class YoutubeSearchEngine extends SearchEngine {

    private static final String TAG = YoutubeSearchEngine.class.toString();
    public static final String CHARSET_UTF_8 = "UTF-8";

    public YoutubeSearchEngine(UrlIdHandler urlIdHandler, int serviceId) {
        super(urlIdHandler, serviceId);
    }

    @Override
    public InfoItemSearchCollector search(String query,
                                          int page,
                                          String languageCode,
                                          EnumSet<Filter> filter)
            throws IOException, ExtractionException {
        InfoItemSearchCollector collector = getInfoItemSearchCollector();


        Downloader downloader = NewPipe.getDownloader();

        String url = "https://www.youtube.com/results"
                + "?q=" + URLEncoder.encode(query, CHARSET_UTF_8)
                + "&page=" + Integer.toString(page + 1);
        if(filter.contains(Filter.STREAM) && !filter.contains(Filter.CHANNEL)) {
            url += "&sp=EgIQAQ%253D%253D";
        } else if(!filter.contains(Filter.STREAM) && filter.contains(Filter.CHANNEL)) {
            url += "&sp=EgIQAg%253D%253D";
        }

        String site;
        //String url = builder.build().toString();
        //if we've been passed a valid language code, append it to the URL
        if(!languageCode.isEmpty()) {
            //assert Pattern.matches("[a-z]{2}(-([A-Z]{2}|[0-9]{1,3}))?", languageCode);
            site  = downloader.download(url, languageCode);
        }
        else {
            site = downloader.download(url);
        }

        Document doc = Jsoup.parse(site, url);
        Element list = doc.select("ol[class=\"item-section\"]").first();

        for (Element item : list.children()) {
            /* First we need to determine which kind of item we are working with.
               Youtube depicts five different kinds of items on its search result page. These are
               regular videos, playlists, channels, two types of video suggestions, and a "no video
               found" item. Since we only want videos, we need to filter out all the others.
               An example for this can be seen here:
               https://www.youtube.com/results?search_query=asdf&page=1

               We already applied a filter to the url, so we don't need to care about channels and
               playlists now.
            */

            Element el;

            // both types of spell correction item
            if ((el = item.select("div[class*=\"spell-correction\"]").first()) != null) {
                collector.setSuggestion(el.select("a").first().text());
                if(list.children().size() == 1) {
                    throw new NothingFoundException("Did you mean: " + el.select("a").first().text());
                }
                // search message item
            } else if ((el = item.select("div[class*=\"search-message\"]").first()) != null) {
                throw new NothingFoundException(el.text());

                // video item type
            } else if ((el = item.select("div[class*=\"yt-lockup-video\"]").first()) != null) {
                collector.commit(new YoutubeStreamInfoItemExtractor(el));
            } else if((el = item.select("div[class*=\"yt-lockup-channel\"]").first()) != null) {
                collector.commit(new YoutubeChannelInfoItemExtractor(el));
            } else {
                // noinspection ConstantConditions
                // simply ignore not known items
                // throw new ExtractionException("unexpected element found: \"" + item + "\"");
            }
        }

        return collector;
    }
}
