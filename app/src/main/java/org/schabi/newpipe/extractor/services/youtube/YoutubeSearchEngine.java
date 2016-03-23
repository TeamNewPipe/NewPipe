package org.schabi.newpipe.extractor.services.youtube;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.AbstractVideoInfo;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.SearchEngine;
import org.schabi.newpipe.extractor.StreamPreviewInfoCollector;
import org.schabi.newpipe.extractor.StreamPreviewInfoExtractor;
import org.schabi.newpipe.extractor.StreamUrlIdHandler;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.net.URLEncoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;

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

public class YoutubeSearchEngine extends SearchEngine {

    private static final String TAG = YoutubeSearchEngine.class.toString();

    public YoutubeSearchEngine(StreamUrlIdHandler urlIdHandler, int serviceId) {
        super(urlIdHandler, serviceId);
    }

    @Override
    public StreamPreviewInfoCollector search(String query, int page, String languageCode, Downloader downloader)
            throws IOException, ExtractionException {
        StreamPreviewInfoCollector collector = getStreamPreviewInfoCollector();

        /* Cant use Uri.Bilder since it's android code.
        // Android code is baned from the extractor side.
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("www.youtube.com")
                .appendPath("results")
                .appendQueryParameter("search_query", query)
                .appendQueryParameter("page", Integer.toString(page))
                .appendQueryParameter("filters", "video");
                */

        String url = "https://www.youtube.com/results"
                + "?search_query=" + URLEncoder.encode(query, "UTF-8")
                + "&page=" + Integer.toString(page)
                + "&filters=" + "video";

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
            if (!((el = item.select("div[class*=\"spell-correction\"]").first()) == null)) {
                collector.setSuggestion(el.select("a").first().text());
                if(list.children().size() == 1) {
                    throw new NothingFoundException("Did you mean: " + el.select("a").first().text());
                }
                // search message item
            } else if (!((el = item.select("div[class*=\"search-message\"]").first()) == null)) {
                //result.errorMessage = el.text();
                throw new NothingFoundException(el.text());

                // video item type
            } else if (!((el = item.select("div[class*=\"yt-lockup-video\"").first()) == null)) {
                collector.commit(extractPreviewInfo(el));
            } else {
                //noinspection ConstantConditions
                collector.addError(new Exception("unexpected element found:\"" + el + "\""));
            }
        }

        return collector;
    }

    @Override
    public ArrayList<String> suggestionList(String query, String contentCountry, Downloader dl)
            throws IOException, ParsingException {

        ArrayList<String> suggestions = new ArrayList<>();

        /* Cant use Uri.Bilder since it's android code.
        // Android code is baned from the extractor side.
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("suggestqueries.google.com")
                .appendPath("complete")
                .appendPath("search")
                .appendQueryParameter("client", "")
                .appendQueryParameter("output", "toolbar")
                .appendQueryParameter("ds", "yt")
                .appendQueryParameter("hl",contentCountry)
                .appendQueryParameter("q", query);
                */
        String url = "https://suggestqueries.google.com/complete/search"
                + "?client=" + ""
                + "&output=" + "toolbar"
                + "&ds=" + "yt"
                + "&hl=" + URLEncoder.encode(contentCountry, "UTF-8")
                + "&q=" + URLEncoder.encode(query, "UTF-8");


        String response = dl.download(url);

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
            throw new ParsingException("Could not parse document.");
        }

        try {
            NodeList nList = doc.getElementsByTagName("CompleteSuggestion");
            for (int temp = 0; temp < nList.getLength(); temp++) {

                NodeList nList1 = doc.getElementsByTagName("suggestion");
                Node nNode1 = nList1.item(temp);
                if (nNode1.getNodeType() == Node.ELEMENT_NODE) {
                    org.w3c.dom.Element eElement = (org.w3c.dom.Element) nNode1;
                    suggestions.add(eElement.getAttribute("data"));
                }
            }
            return suggestions;
        } catch(Exception e) {
            throw new ParsingException("Could not get suggestions form document.", e);
        }
    }

    private StreamPreviewInfoExtractor extractPreviewInfo(final Element item) {
        return new YoutubeStreamPreviewInfoExtractor(item);
    }
}
