package org.schabi.newpipe.youtube;

import android.net.Uri;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.SearchEngine;
import org.schabi.newpipe.VideoInfoItem;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class YoutubeSearchEngine implements SearchEngine {

    private static final String TAG = YoutubeSearchEngine.class.toString();

    @Override
    public Result search(String query, int page) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("www.youtube.com")
                .appendPath("results")
                .appendQueryParameter("search_query", query)
                .appendQueryParameter("page", Integer.toString(page))
                .appendQueryParameter("filters", "video");
        String url = builder.build().toString();

        String site = Downloader.download(url);
        Document doc = Jsoup.parse(site, url);
        Result result = new Result();
        Element list = doc.select("ol[class=\"item-section\"]").first();


        int i = 0;
        for(Element item : list.children()) {
            i++;
            /* First we need to determine witch kind of item we are working with.
               Youtube depicts fife different kinds if items at its search result page. These are
               regular videos, playlists, channels, two types of video suggestions, and a no video
               found item. Since we only want videos, we net to filter out all the others.
               An example for this can be seen here:
               https://www.youtube.com/results?search_query=asdf&page=1

               We already applied a filter to the url, so we don't need to care about channels, and
               playlists now.
            */

            Element el;

            // both types of spell correction item
            if(!((el = item.select("div[class*=\"spell-correction\"]").first()) == null)) {
                result.suggestion = el.select("a").first().text();
                // search message item
            } else if(!((el = item.select("div[class*=\"search-message\"]").first()) == null)) {
                result.errorMessage = el.text();

                // video item type
            } else if(!((el = item.select("div[class*=\"yt-lockup-video\"").first()) == null)) {
                VideoInfoItem resultItem = new VideoInfoItem();
                Element dl = el.select("h3").first().select("a").first();
                resultItem.webpage_url = dl.attr("abs:href");
                try {
                    Pattern p = Pattern.compile("v=([0-9a-zA-Z-]*)");
                    Matcher m = p.matcher(resultItem.webpage_url);
                    m.find();
                    resultItem.id=m.group(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                resultItem.title = dl.text();
                resultItem.duration = item.select("span[class=\"video-time\"]").first()
                        .text();
                resultItem.uploader = item.select("div[class=\"yt-lockup-byline\"]").first()
                        .select("a").first()
                        .text();
                resultItem.upload_date = item.select("div[class=\"yt-lockup-meta\"]").first()
                        .select("li").first()
                        .text();
                Element te = item.select("div[class=\"yt-thumb video-thumb\"]").first()
                        .select("img").first();
                resultItem.thumbnail_url = te.attr("abs:src");
                // Sometimes youtube sends links to gif files witch somehow seam to not exist
                // anymore. Items with such gif also offer a secondary image source. So we are going
                // to use that if we caught such an item.
                if(resultItem.thumbnail_url.contains(".gif")) {
                    resultItem.thumbnail_url = te.attr("abs:data-thumb");
                }
                result.resultList.add(resultItem);
            } else {
                Log.e(TAG, "GREAT FUCKING ERROR");
            }
        }

        return result;
    }
}
