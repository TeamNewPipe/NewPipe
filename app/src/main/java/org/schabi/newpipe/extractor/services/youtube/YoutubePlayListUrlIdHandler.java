package org.schabi.newpipe.extractor.services.youtube;

import android.net.UrlQuerySanitizer;

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ParsingException;


public class YoutubePlayListUrlIdHandler implements UrlIdHandler {

    @Override
    public String getUrl(String listId) {
        return "https://www.youtube.com/playlist?list=" + listId;
    }

    @Override
    public String getId(String url) throws ParsingException {
        try {
            // http://developer.android.com/reference/android/net/UrlQuerySanitizer.html
            final UrlQuerySanitizer sanitizer = new UrlQuerySanitizer(url);
            return sanitizer.getValue("list");
        } catch (final Exception exception) {
            throw new ParsingException("Error could not parse url :" + exception.getMessage(), exception);
        }
    }

    @Override
    public String cleanUrl(String complexUrl) throws ParsingException {
        return getUrl(getId(complexUrl));
    }

    @Override
    public boolean acceptUrl(String videoUrl) {
        return videoUrl != null && !videoUrl.isEmpty() &&
               (videoUrl.contains("youtube") || videoUrl.contains("youtu.be")) &&
                videoUrl.contains("list");
    }

}
