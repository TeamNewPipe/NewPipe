package org.schabi.newpipe.extractor.services.youtube;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.StreamPreviewInfoExtractor;

public class YoutubeStreamPreviewInfoExtractor implements StreamPreviewInfoExtractor {

    private final Element item;

    public YoutubeStreamPreviewInfoExtractor(Element item) {
        this.item = item;
    }

    @Override
    public String getWebPageUrl() throws ParsingException {
        try {
            Element el = item.select("div[class*=\"yt-lockup-video\"").first();
            Element dl = el.select("h3").first().select("a").first();
            return dl.attr("abs:href");
        } catch (Exception e) {
            throw new ParsingException("Could not get web page url for the video", e);
        }
    }

    @Override
    public String getTitle() throws ParsingException {
        try {
            Element el = item.select("div[class*=\"yt-lockup-video\"").first();
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
                    item.select("span[class=\"video-time\"]").first().text());
        } catch(Exception e) {
            if(isLiveStream(item)) {
                // -1 for no duration
                return -1;
            } else {
                throw new ParsingException("Could not get Duration", e);
            }


        }
    }

    @Override
    public String getUploader() throws ParsingException {
        try {
            return item.select("div[class=\"yt-lockup-byline\"]").first()
                    .select("a").first()
                    .text();
        } catch (Exception e) {
            throw new ParsingException("Could not get uploader", e);
        }
    }

    @Override
    public String getUploadDate() throws ParsingException {
        try {
            return item.select("div[class=\"yt-lockup-meta\"]").first()
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
            input = item.select("div[class=\"yt-lockup-meta\"]").first()
                    .select("li").get(1)
                    .text();
        } catch (IndexOutOfBoundsException e) {
            if(isLiveStream(item)) {
                // -1 for no view count
                return -1;
            } else {
                throw new ParsingException(
                        "Could not parse yt-lockup-meta although available", e);
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
            Element te = item.select("div[class=\"yt-thumb video-thumb\"]").first()
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
        return bla != null;
    }
}
