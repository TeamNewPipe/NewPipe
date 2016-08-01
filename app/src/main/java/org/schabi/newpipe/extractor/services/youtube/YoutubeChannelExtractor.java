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
import org.schabi.newpipe.extractor.AbstractVideoInfo;
import org.schabi.newpipe.extractor.ChannelExtractor;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.StreamPreviewInfoCollector;
import org.schabi.newpipe.extractor.StreamPreviewInfoExtractor;
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
    private final String channelUrl;
    private String vUrl ="";


    public YoutubeChannelExtractor(UrlIdHandler urlIdHandler, String url, Downloader dl, int serviceId)
            throws ExtractionException, IOException {
        super(urlIdHandler, url, dl, serviceId);

        channelUrl = urlIdHandler.cleanUrl(url) ; //+ "/video?veiw=0&flow=list&sort=dd";
        downloader = dl;
        // we first need to get the user url. Otherwise we can't find videos
        String channelPageContent = downloader.download(channelUrl);
        Document channelDoc = Jsoup.parse(channelPageContent, channelUrl);
        String userUrl = getUserUrl(channelDoc);

        vUrl = userUrl + "/videos?veiw=0&flow=list&sort=dd";
        String pageContent = downloader.download(vUrl);
        doc = Jsoup.parse(pageContent, vUrl);
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
    public StreamPreviewInfoCollector getStreams() throws ParsingException {
        StreamPreviewInfoCollector collector = getStreamPreviewInfoCollector();
        Element ul = doc.select("ul[id=\"browse-items-primary\"]").first();

        for(final Element li : ul.children()) {
            if (li.select("div[class=\"feed-item-dismissable\"]").first() != null) {
                collector.commit(new StreamPreviewInfoExtractor() {
                    @Override
                    public AbstractVideoInfo.StreamType getStreamType() throws ParsingException {
                        return AbstractVideoInfo.StreamType.VIDEO_STREAM;
                    }

                    @Override
                    public String getWebPageUrl() throws ParsingException {
                        try {
                            Element el = li.select("div[class=\"feed-item-dismissable\"]").first();
                            Element dl = el.select("h3").first().select("a").first();
                            return dl.attr("abs:href");
                        } catch (Exception e) {
                            throw new ParsingException("Could not get web page url for the video", e);
                        }
                    }

                    @Override
                    public String getTitle() throws ParsingException {
                        try {
                            Element el = li.select("div[class=\"feed-item-dismissable\"]").first();
                            Element dl = el.select("h3").first().select("a").first();
                            return dl.text();
                        } catch (Exception e) {
                            throw new ParsingException("Could not get title", e);
                        }
                    }

                    @Override
                    public int getDuration() throws ParsingException {
                        try {
                            return YoutubeParsingHelper.parseDurationString(
                                    li.select("span[class=\"video-time\"]").first().text());
                        } catch(Exception e) {
                            if(isLiveStream(li)) {
                                // -1 for no duration
                                return -1;
                            } else {
                                throw new ParsingException("Could not get Duration: " + getTitle(), e);
                            }
                        }
                    }

                    @Override
                    public String getUploader() throws ParsingException {
                        return getChannelName();
                    }

                    @Override
                    public String getUploadDate() throws ParsingException {
                        try {
                            return li.select("div[class=\"yt-lockup-meta\"]").first()
                                    .select("li").first()
                                    .text();
                        } catch(Exception e) {
                            throw new ParsingException("Could not get uplaod date", e);
                        }
                    }

                    @Override
                    public long getViewCount() throws ParsingException {
                        String output;
                        String input;
                        try {
                            input = li.select("div[class=\"yt-lockup-meta\"]").first()
                                    .select("li").get(1)
                                    .text();
                        } catch (IndexOutOfBoundsException e) {
                            if(isLiveStream(li)) {
                                // -1 for no view count
                                return -1;
                            } else {
                                throw new ParsingException(
                                        "Could not parse yt-lockup-meta although available: " + getTitle(), e);
                            }
                        }

                        output = Parser.matchGroup1("([0-9,\\. ]*)", input)
                                .replace(" ", "")
                                .replace(".", "")
                                .replace(",", "");

                        try {
                            return Long.parseLong(output);
                        } catch (NumberFormatException e) {
                            // if this happens the video probably has no views
                            if(!input.isEmpty()) {
                                return 0;
                            } else {
                                throw new ParsingException("Could not handle input: " + input, e);
                            }
                        }
                    }

                    @Override
                    public String getThumbnailUrl() throws ParsingException {
                        try {
                            String url;
                            Element te = li.select("span[class=\"yt-thumb-clip\"]").first()
                                    .select("img").first();
                            url = te.attr("abs:src");
                            // Sometimes youtube sends links to gif files which somehow seem to not exist
                            // anymore. Items with such gif also offer a secondary image source. So we are going
                            // to use that if we've caught such an item.
                            if (url.contains(".gif")) {
                                url = te.attr("abs:data-thumb");
                            }
                            return url;
                        } catch (Exception e) {
                            throw new ParsingException("Could not get thumbnail url", e);
                        }
                    }
                });
            }
        }

        return collector;
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        try {
            return doc.select("link[title=\"RSS\"]").first().attr("abs:href");
        } catch(Exception e) {
            throw new ParsingException("Could not get feed url", e);
        }
    }

    private String getUserUrl(Document d) throws ParsingException {
        return d.select("span[class=\"qualified-channel-title-text\"]").first()
                .select("a").first().attr("abs:href");
    }

    private boolean isLiveStream(Element item) {
        Element bla = item.select("span[class*=\"yt-badge-live\"]").first();

        if(bla == null) {
            // sometimes livestreams dont have badges but sill are live streams
            // if video time is not available we most likly have an offline livestream
            if(item.select("span[class*=\"video-time\"]").first() == null) {
                return true;
            }
        }
        return bla != null;
    }
}
