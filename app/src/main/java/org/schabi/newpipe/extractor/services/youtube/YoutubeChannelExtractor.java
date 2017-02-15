package org.schabi.newpipe.extractor.services.youtube;



import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.AbstractStreamInfo;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemCollector;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemExtractor;


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

    private Document doc = null;

    private boolean isAjaxPage = false;
    private static String userUrl = "";
    private static String channelName = "";
    private static String avatarUrl = "";
    private static String bannerUrl = "";
    private static String feedUrl = "";
    // the fist page is html all other pages are ajax. Every new page can be requested by sending
    // this request url.
    private static String nextPageUrl = "";

    public YoutubeChannelExtractor(UrlIdHandler urlIdHandler, String url, int page, int serviceId)
            throws ExtractionException, IOException {
        super(urlIdHandler, url, page, serviceId);

        Downloader downloader = NewPipe.getDownloader();

        url = urlIdHandler.cleanUrl(url) ; //+ "/video?veiw=0&flow=list&sort=dd";

        if(page == 0) {
            if (isUserUrl(url)) {
                userUrl = url;
            } else {
                // we first need to get the user url. Otherwise we can't find videos
                String channelPageContent = downloader.download(url);
                Document channelDoc = Jsoup.parse(channelPageContent, url);
                userUrl = getUserUrl(channelDoc);
            }

            userUrl = userUrl + "/videos?veiw=0&flow=list&sort=dd&live_view=10000";
            String pageContent = downloader.download(userUrl);
            doc = Jsoup.parse(pageContent, userUrl);
            nextPageUrl = getNextPageUrl(doc);
            isAjaxPage = false;
        } else {
            String ajaxDataRaw = downloader.download(nextPageUrl);
            JSONObject ajaxData;
            try {
                ajaxData = new JSONObject(ajaxDataRaw);
                String htmlDataRaw = ajaxData.getString("content_html");
                doc = Jsoup.parse(htmlDataRaw, nextPageUrl);

                String nextPageHtmlDataRaw = ajaxData.getString("load_more_widget_html");
                if(!nextPageHtmlDataRaw.isEmpty()) {
                    Document nextPageData = Jsoup.parse(nextPageHtmlDataRaw, nextPageUrl);
                    nextPageUrl = getNextPageUrl(nextPageData);
                } else {
                    nextPageUrl = "";
                }
            } catch (JSONException e) {
                throw new ParsingException("Could not parse json data for next page", e);
            }
            isAjaxPage = true;
        }
    }

    @Override
    public String getChannelName() throws ParsingException {
        try {
            if(!isAjaxPage) {
                channelName = doc.select("span[class=\"qualified-channel-title-text\"]").first()
                        .select("a").first().text();
            }
            return channelName;
        } catch(Exception e) {
            throw new ParsingException("Could not get channel name");
        }
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        try {
            if(!isAjaxPage) {
                avatarUrl = doc.select("img[class=\"channel-header-profile-image\"]")
                        .first().attr("abs:src");
            }
            return avatarUrl;
        } catch(Exception e) {
            throw new ParsingException("Could not get avatar", e);
        }
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        try {
            if(!isAjaxPage) {
                Element el = doc.select("div[id=\"gh-banner\"]").first().select("style").first();
                String cssContent = el.html();
                String url = "https:" + Parser.matchGroup1("url\\(([^)]+)\\)", cssContent);

                if (url.contains("s.ytimg.com") || url.contains("default_banner")) {
                    bannerUrl = null;
                } else {
                    bannerUrl = url;
                }
            }
            return bannerUrl;
        } catch(Exception e) {
            throw new ParsingException("Could not get Banner", e);
        }
    }

    @Override
    public StreamInfoItemCollector getStreams() throws ParsingException {
        StreamInfoItemCollector collector = getStreamPreviewInfoCollector();
        Element ul = null;
        if(isAjaxPage) {
            ul = doc.select("body").first();
        } else {
            ul = doc.select("ul[id=\"browse-items-primary\"]").first();
        }

        for(final Element li : ul.children()) {
            if (li.select("div[class=\"feed-item-dismissable\"]").first() != null) {
                collector.commit(new StreamInfoItemExtractor() {
                    @Override
                    public AbstractStreamInfo.StreamType getStreamType() throws ParsingException {
                        return AbstractStreamInfo.StreamType.VIDEO_STREAM;
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
                });
            }
        }

        return collector;
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        try {
            if(userUrl.contains("channel")) {
                //channels don't have feeds in youtube, only user can provide such
                return "";
            }
            if(!isAjaxPage) {
                feedUrl = doc.select("link[title=\"RSS\"]").first().attr("abs:href");
            }
            return feedUrl;
        } catch(Exception e) {
            throw new ParsingException("Could not get feed url", e);
        }
    }

    @Override
    public boolean hasNextPage() throws ParsingException {
        return !nextPageUrl.isEmpty();
    }

    private String getUserUrl(Document d) throws ParsingException {
        return d.select("span[class=\"qualified-channel-title-text\"]").first()
                .select("a").first().attr("abs:href");
    }

    private boolean isUserUrl(String url) throws ParsingException {
        return url.contains("/user/");
    }

    private String getNextPageUrl(Document d) throws ParsingException {
        try {
            Element button = d.select("button[class*=\"yt-uix-load-more\"]").first();
            if(button != null) {
                return button.attr("abs:data-uix-load-more-href");
            } else {
                // sometimes channels are simply so small, they don't have a second/next4q page
                return "";
            }
        } catch(Exception e) {
            throw new ParsingException("could not load next page url", e);
        }
    }
}
