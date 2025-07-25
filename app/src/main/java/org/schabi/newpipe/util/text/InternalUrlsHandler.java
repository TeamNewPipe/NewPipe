package org.schabi.newpipe.util.text;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.player.TimestampChangeData;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InternalUrlsHandler {
    private static final Pattern AMPERSAND_TIMESTAMP_PATTERN = Pattern.compile("(.*)&t=(\\d+)");

    private InternalUrlsHandler() {
    }

    /**
     * Handle a YouTube timestamp description URL in NewPipe.
     * <p>
     * This method will check if the provided url is a YouTube timestamp description URL ({@code
     * https://www.youtube.com/watch?v=}video_id{@code &t=}time_in_seconds). If yes, the popup
     * player will be opened when the user will click on the timestamp in the video description,
     * at the time and for the video indicated in the timestamp.
     *
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    public static boolean handleUrlDescriptionTimestamp(final Context context,
                                                        @NonNull final String url) {
        final Matcher matcher = AMPERSAND_TIMESTAMP_PATTERN.matcher(url);
        if (!matcher.matches()) {
            return false;
        }
        final String matchedUrl = matcher.group(1);
        final int seconds;
        if (matcher.group(2) == null) {
            seconds = -1;
        } else {
            seconds = Integer.parseInt(matcher.group(2));
        }

        final StreamingService service;
        final StreamingService.LinkType linkType;
        try {
            service = NewPipe.getServiceByUrl(matchedUrl);
            linkType = service.getLinkTypeByUrl(matchedUrl);
            if (linkType == StreamingService.LinkType.NONE) {
                return false;
            }
        } catch (final ExtractionException e) {
            return false;
        }

        if (linkType == StreamingService.LinkType.STREAM && seconds != -1) {
            return playOnPopup(context, matchedUrl, service, seconds);
        } else {
            NavigationHelper.openRouterActivity(context, matchedUrl);
            return true;
        }
    }

    /**
     * Play a content in the floating player.
     *
     * @param context     the context to be used
     * @param url         the URL of the content
     * @param service     the service of the content
     * @param seconds     the position in seconds at which the floating player will start
     * @return true if the playback of the content has successfully started or false if not
     */
    public static boolean playOnPopup(final Context context,
                                      final String url,
                                      @NonNull final StreamingService service,
                                      final int seconds) {
        final LinkHandlerFactory factory = service.getStreamLHFactory();
        final String cleanUrl;

        try {
            cleanUrl = factory.getUrl(factory.getId(url));
        } catch (final ParsingException e) {
            return false;
        }

        final Intent intent = NavigationHelper.getPlayerTimestampIntent(context,
                new TimestampChangeData(
                        service.getServiceId(),
                        cleanUrl,
                        seconds
                ));
        ContextCompat.startForegroundService(context, intent);
        return true;
    }
}
