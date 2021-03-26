package org.schabi.newpipe.util.external_communication;

import android.content.Context;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class InternalUrlsHandler {
    private static final Pattern AMPERSAND_TIMESTAMP_PATTERN = Pattern.compile("(.*)&t=(\\d+)");
    private static final Pattern HASHTAG_TIMESTAMP_PATTERN =
            Pattern.compile("(.*)#timestamp=(\\d+)");

    private InternalUrlsHandler() {
    }

    /**
     * Handle an URL in NewPipe.
     * <p>
     * This method will check if the provided url can be handled in NewPipe or not. If this is a
     * service URL with a timestamp, the popup player will be opened.
     * <p>
     * The timestamp param accepts two integers, corresponding to two timestamps types:
     * 0 for {@code &t=} (used for timestamps in descriptions),
     * 1 for {@code #timestamp=} (used for timestamps in comments).
     * Any other value of this integer will return false.
     *
     * @param context       the context to be used
     * @param url           the URL to check if it can be handled
     * @param timestampType the type of timestamp
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    public static boolean handleUrl(final Context context,
                                    final String url,
                                    final int timestampType) {
        String matchedUrl = "";
        int seconds = -1;
        final Pattern timestampPattern;

        if (timestampType == 0) {
            timestampPattern = AMPERSAND_TIMESTAMP_PATTERN;
        } else if (timestampType == 1) {
            timestampPattern = HASHTAG_TIMESTAMP_PATTERN;
        } else {
            return false;
        }

        final Matcher matcher = timestampPattern.matcher(url);
        if (matcher.matches()) {
            matchedUrl = matcher.group(1);
            seconds = Integer.parseInt(matcher.group(2));
        }

        if (matchedUrl == null || matchedUrl.isEmpty()) {
            return false;
        }

        final StreamingService service;
        final StreamingService.LinkType linkType;

        try {
            service = NewPipe.getServiceByUrl(matchedUrl);
            linkType = service.getLinkTypeByUrl(matchedUrl);
        } catch (final ExtractionException e) {
            return false;
        }

        if (linkType == StreamingService.LinkType.NONE) {
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
     * @param context the context to be used
     * @param url     the URL of the content
     * @param service the service of the content
     * @param seconds the position in seconds at which the floating player will start
     * @return true if the playback of the content has successfully started or false if not
     */
    public static boolean playOnPopup(final Context context,
                                      final String url,
                                      final StreamingService service,
                                      final int seconds) {
        final LinkHandlerFactory factory = service.getStreamLHFactory();
        final String cleanUrl;

        try {
            cleanUrl = factory.getUrl(factory.getId(url));
        } catch (final ParsingException e) {
            return false;
        }

        final Single<StreamInfo> single
                = ExtractorHelper.getStreamInfo(service.getServiceId(), cleanUrl, false);
        single.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    final PlayQueue playQueue
                            = new SinglePlayQueue(info, seconds * 1000);
                    NavigationHelper.playOnPopupPlayer(context, playQueue, false);
                });
        return true;
    }
}
