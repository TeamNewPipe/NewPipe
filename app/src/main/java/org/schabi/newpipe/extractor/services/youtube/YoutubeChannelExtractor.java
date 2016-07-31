package org.schabi.newpipe.extractor.services.youtube;

import android.util.Log;

/*
import com.steadystate.css.dom.CSSStyleDeclarationImpl;
import com.steadystate.css.dom.CSSStyleSheetImpl;
import com.steadystate.css.parser.CSSOMParser;
import com.steadystate.css.parser.SACParserCSS3;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSRuleList;
import org.w3c.dom.css.CSSStyleSheet;
import java.io.StringReader;
*/

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.ChannelExtractor;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.Parser;
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

    // private CSSOMParser cssParser = new CSSOMParser(new SACParserCSS3());

    private Downloader downloader;
    private final Document doc;
    private final String siteUrl;


    public YoutubeChannelExtractor(UrlIdHandler urlIdHandler, String url, Downloader dl, int serviceId)
            throws ExtractionException, IOException {
        super(urlIdHandler, url, dl, serviceId);

        siteUrl = urlIdHandler.cleanUrl(url);
        Log.d(TAG, siteUrl);
        downloader = dl;
        String pageContent = downloader.download(url);
        doc = Jsoup.parse(pageContent, url);
    }

    @Override
    public String getChannelName() throws ParsingException {
         try {
             return doc.select("span[class=\"qualified-channel-title-text\"]").first()
                     .select("a").first().text();
         } catch(Exception e) {
             throw new ParsingException("Could not get channel name");
         }
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
        String cssContent = "";
        try {
            Element el = doc.select("div[id=\"gh-banner\"]").first().select("style").first();
            cssContent = el.html();
            // todo: parse this using a css parser
            /*
            CSSStyleSheet sheet = cssParser.parseStyleSheet(
                    new org.w3c.css.sac.InputSource(
                            new StringReader(cssContent)), null, null);
            CSSRuleList rules = sheet.getCssRules();
            for (int i = 0; i < rules.getLength(); i++) {
                final CSSRule rule = rules.item(i);
                System.out.println(rule.getCssText());
            }
            */
            String url = "https:" + Parser.matchGroup1("url\\((.*)\\)", cssContent);
            if(url.contains("s.ytimg.com")) {
                return null;
            } else {
                return url;
            }
        /* } catch(CSSParseException csse) {
            throw new ParsingException("Could not parse css: " + cssContent); */
        } catch(Exception e) {
            throw new ParsingException("Could not get Banner", e);
        }
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        return siteUrl + "/feed";
    }

    private String getUserUrl() throws ParsingException {
        return doc.select("span[class=\"qualified-channel-title-text\"]").first()
                .select("a").first().attr("abs:href");
    }
}
