package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.search.SuggestionExtractor;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by Christian Schabesberger on 28.09.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeSuggestionExtractor.java is part of NewPipe.
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

public class YoutubeSuggestionExtractor extends SuggestionExtractor {

    public static final String CHARSET_UTF_8 = "UTF-8";

    public YoutubeSuggestionExtractor(int serviceId) {
        super(serviceId);
    }

    @Override
    public List<String> suggestionList(
            String query, String contentCountry)
            throws ExtractionException, IOException {
        List<String> suggestions = new ArrayList<>();

        Downloader dl = NewPipe.getDownloader();

        String url = "https://suggestqueries.google.com/complete/search"
                + "?client=" + ""
                + "&output=" + "toolbar"
                + "&ds=" + "yt"
                + "&hl=" + URLEncoder.encode(contentCountry, CHARSET_UTF_8)
                + "&q=" + URLEncoder.encode(query, CHARSET_UTF_8);


        String response = dl.download(url);

        //TODO: Parse xml data using Jsoup not done
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        org.w3c.dom.Document doc = null;

        try {
            dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(new InputSource(
                    new ByteArrayInputStream(response.getBytes(CHARSET_UTF_8))));
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
}
