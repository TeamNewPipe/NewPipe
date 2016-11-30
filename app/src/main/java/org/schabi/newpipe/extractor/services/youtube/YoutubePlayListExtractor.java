package org.schabi.newpipe.extractor.services.youtube;

import android.text.TextUtils;
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
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfoCollector;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfoExtractor;

import java.io.IOException;

public class YoutubePlayListExtractor extends ChannelExtractor {

    private Document doc = null;

    private boolean isAjaxPage = false;
    private static String channelName = "";
    private static String avatarUrl = "";
    private static String bannerUrl = "";
    private static String nextPageUrl = "";


    public YoutubePlayListExtractor(UrlIdHandler urlIdHandler, String url, int page, int serviceId) throws ExtractionException, IOException {
        super(urlIdHandler, url, page, serviceId);
        Downloader downloader = NewPipe.getDownloader();
        url = urlIdHandler.cleanUrl(url);
        if(page == 0) {
            String channelPageContent = downloader.download(url);
            doc = Jsoup.parse(channelPageContent, url);
            nextPageUrl = getNextPageUrl(doc);
            isAjaxPage = false;
        } else {
            String ajaxDataRaw = downloader.download(nextPageUrl);
            JSONObject ajaxData;
            try {
                ajaxData = new JSONObject(ajaxDataRaw);
                final String htmlDataRaw = "<table><tbody id=\"pl-load-more-destination\">" + ajaxData.getString("content_html") + "</tbody></table>";
                doc = Jsoup.parse(htmlDataRaw, nextPageUrl);
                final String nextPageHtmlDataRaw = ajaxData.getString("load_more_widget_html");
                if(!nextPageHtmlDataRaw.isEmpty()) {
                    final Document nextPageData = Jsoup.parse(nextPageHtmlDataRaw, nextPageUrl);
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
            if (!isAjaxPage) {
                final Element channelName = doc.select("span[class=\"qualified-channel-title-text\"] a").first();
                final Element playlistName = doc.select("meta[name=title]").first();
                if(channelName != null) {
                    YoutubePlayListExtractor.channelName = channelName.text();
                }
                if(playlistName != null) {
                    if (TextUtils.isEmpty(YoutubePlayListExtractor.channelName)) {
                        YoutubePlayListExtractor.channelName = playlistName.attr("content");
                    } else {
                        YoutubePlayListExtractor.channelName += " - " + playlistName.attr("content");
                    }
                }
            }
            return channelName;
        } catch (Exception e) {
            throw new ParsingException("Could not get channel name");
        }
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        try {
            if(!isAjaxPage) {
                avatarUrl = doc.select("div[id=gh-banner] img").first().attr("src");
                if(avatarUrl.startsWith("//")) {
                    avatarUrl = "https:" + avatarUrl;
                }
            }
            return avatarUrl;
        } catch(Exception e) {
        }
        return "";
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        try {
            if(!isAjaxPage) {
                Element el = doc.select("div[id=\"gh-banner\"] style").first();
                String cssContent = el.html();
                String url = "https:" + Parser.matchGroup1("url\\((.*)\\)", cssContent);
                if (url.contains("s.ytimg.com")) {
                    bannerUrl = null;
                } else {
                    bannerUrl = url.substring(0, url.indexOf(");"));
                }
            }
            return bannerUrl;
        } catch(Exception e) {
        }
        return "";
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        return "";
    }

    @Override
    public StreamPreviewInfoCollector getStreams() throws ParsingException {
        StreamPreviewInfoCollector collector = getStreamPreviewInfoCollector();
        Element tbody = doc.select("tbody[id=\"pl-load-more-destination\"]").first();
        final YoutubeStreamUrlIdHandler youtubeStreamUrlIdHandler = new YoutubeStreamUrlIdHandler();
        for(final Element li : tbody.children()) {
                collector.commit(new StreamPreviewInfoExtractor() {
                    @Override
                    public AbstractStreamInfo.StreamType getStreamType() throws ParsingException {
                        return AbstractStreamInfo.StreamType.VIDEO_STREAM;
                    }

                    @Override
                    public String getWebPageUrl() throws ParsingException {
                        try {
                            return youtubeStreamUrlIdHandler.getUrl(li.attr("data-video-id"));
                        } catch (Exception e) {
                            throw new ParsingException("Could not get web page url for the video", e);
                        }
                    }

                    @Override
                    public String getTitle() throws ParsingException {
                        try {
                            return li.attr("data-title");
                        } catch (Exception e) {
                            throw new ParsingException("Could not get title", e);
                        }
                    }

                    @Override
                    public int getDuration() throws ParsingException {
                        try {
                            return YoutubeParsingHelper.parseDurationString(
                                    li.select("div[class=\"timestamp\"] span").first().text().trim());
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
                        return li.select("div[class=pl-video-owner] a").text();
                    }

                    @Override
                    public String getUploadDate() throws ParsingException {
                        return "";
                    }

                    @Override
                    public long getViewCount() throws ParsingException {
                        return -1;
                    }

                    @Override
                    public String getThumbnailUrl() throws ParsingException {
                        try {
                            return "https://i.ytimg.com/vi/" + youtubeStreamUrlIdHandler.getId(getWebPageUrl()) + "/hqdefault.jpg";
                        } catch (Exception e) {
                            throw new ParsingException("Could not get thumbnail url", e);
                        }
                    }

                    @Override
                    public int getServiceId() {
                        return YoutubePlayListExtractor.this.getServiceId();
                    }

                    @Override
                    public int getPosition() {
                        return 0;
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

        return collector;
    }

    @Override
    public boolean hasNextPage() throws ParsingException {
        return nextPageUrl != null && !nextPageUrl.isEmpty();
    }

    private String getNextPageUrl(Document d) throws ParsingException {
        try {
            Element button = d.select("button[class*=\"yt-uix-load-more\"]").first();
            if(button != null) {
                return "https://www.youtube.com" + button.attr("data-uix-load-more-href");
            } else {
                // sometimes channels are simply so small, they don't have a second/next4q page
                return "";
            }
        } catch(Exception e) {
            throw new ParsingException("could not load next page url", e);
        }
    }
}
