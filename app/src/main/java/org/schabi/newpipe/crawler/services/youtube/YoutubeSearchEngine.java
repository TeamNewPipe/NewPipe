package org.schabi.newpipe.crawler.services.youtube;

import android.net.Uri;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.crawler.Downloader;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.SearchEngine;
import org.schabi.newpipe.crawler.VideoPreviewInfo;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
    public Result search(String query, int page, String languageCode, Downloader downloader)
            throws IOException, ParsingException {
        Result result = new Result();
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("www.youtube.com")
                .appendPath("results")
                .appendQueryParameter("search_query", query)
                .appendQueryParameter("page", Integer.toString(page))
                .appendQueryParameter("filters", "video");

        String site;
        String url = builder.build().toString();
        //if we've been passed a valid language code, append it to the URL
        if(!languageCode.isEmpty()) {
            //assert Pattern.matches("[a-z]{2}(-([A-Z]{2}|[0-9]{1,3}))?", languageCode);
            site  = downloader.download(url, languageCode);
        }
        else {
            site = downloader.download(url);
        }

        try {

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
                if (!((el = item.select("div[class*=\"spell-correction\"]").first()) == null)) {
                    result.suggestion = el.select("a").first().text();
                    // search message item
                } else if (!((el = item.select("div[class*=\"search-message\"]").first()) == null)) {
                    result.errorMessage = el.text();

                    // video item type
                } else if (!((el = item.select("div[class*=\"yt-lockup-video\"").first()) == null)) {
                    VideoPreviewInfo resultItem = new VideoPreviewInfo();
                    Element dl = el.select("h3").first().select("a").first();
                    resultItem.webpage_url = dl.attr("abs:href");
                    try {
                        Pattern p = Pattern.compile("v=([0-9a-zA-Z-]*)");
                        Matcher m = p.matcher(resultItem.webpage_url);
                        resultItem.id = m.group(1);
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                    resultItem.title = dl.text();

                    resultItem.duration = item.select("span[class=\"video-time\"]").first().text();

                    resultItem.uploader = item.select("div[class=\"yt-lockup-byline\"]").first()
                            .select("a").first()
                            .text();
                    resultItem.upload_date = item.select("div[class=\"yt-lockup-meta\"]").first()
                            .select("li").first()
                            .text();

                    //todo: test against view_count
                    String viewCountInfo = item.select("div[class=\"yt-lockup-meta\"]").first()
                            .select("li").get(1)
                            .text();
                    viewCountInfo = viewCountInfo.substring(0, viewCountInfo.indexOf(' '));
                    viewCountInfo = viewCountInfo.replaceAll("[,.]", "");
                    resultItem.view_count = Long.parseLong(viewCountInfo);

                    Element te = item.select("div[class=\"yt-thumb video-thumb\"]").first()
                            .select("img").first();
                    resultItem.thumbnail_url = te.attr("abs:src");
                    // Sometimes youtube sends links to gif files which somehow seem to not exist
                    // anymore. Items with such gif also offer a secondary image source. So we are going
                    // to use that if we've caught such an item.
                    if (resultItem.thumbnail_url.contains(".gif")) {
                        resultItem.thumbnail_url = te.attr("abs:data-thumb");
                    }
                    result.resultList.add(resultItem);
                } else {
                    //noinspection ConstantConditions
                    Log.e(TAG, "unexpected element found:\"" + el + "\"");
                }
            }
        } catch(Exception e) {
            throw new ParsingException(e);
        }
        return result;
    }

    @Override
    public ArrayList<String> suggestionList(String query, Downloader dl)
            throws IOException, ParsingException {

        ArrayList<String> suggestions = new ArrayList<>();

        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("suggestqueries.google.com")
                .appendPath("complete")
                .appendPath("search")
                .appendQueryParameter("client", "")
                .appendQueryParameter("output", "toolbar")
                .appendQueryParameter("ds", "yt")
                .appendQueryParameter("q", query);
        String url = builder.build().toString();


        String response = dl.download(url);

        try {

            //TODO: Parse xml data using Jsoup not done
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;
            org.w3c.dom.Document doc = null;

            try {
                dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.parse(new InputSource(
                        new ByteArrayInputStream(response.getBytes("utf-8"))));
                doc.getDocumentElement().normalize();
            } catch (ParserConfigurationException | SAXException | IOException e) {
                e.printStackTrace();
            }

            if (doc != null) {
                NodeList nList = doc.getElementsByTagName("CompleteSuggestion");
                for (int temp = 0; temp < nList.getLength(); temp++) {

                    NodeList nList1 = doc.getElementsByTagName("suggestion");
                    Node nNode1 = nList1.item(temp);
                    if (nNode1.getNodeType() == Node.ELEMENT_NODE) {
                        org.w3c.dom.Element eElement = (org.w3c.dom.Element) nNode1;
                        suggestions.add(eElement.getAttribute("data"));
                    }
                }
            } else {
                Log.e(TAG, "GREAT FUCKING ERROR");
            }
            return suggestions;
        } catch(Exception e) {
            throw new ParsingException(e);
        }
    }

}
