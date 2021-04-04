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
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public final class InternalUrlsHandler {
    private static final Pattern AMPERSAND_TIMESTAMP_PATTERN = Pattern.compile("(.*)&t=(\\d+)");
    private static final Pattern HASHTAG_TIMESTAMP_PATTERN =
            Pattern.compile("(.*)#timestamp=(\\d+)");

    private InternalUrlsHandler() {
    }

    /**
     * Handle a YouTube timestamp comment URL in NewPipe.
     * <p>
     * This method will check if the provided url is a YouTube comment description URL ({@code
     * https://www.youtube.com/watch?v=}video_id{@code #timestamp=}time_in_seconds). If yes, the
     * popup player will be opened when the user will click on the timestamp in the comment,
     * at the time and for the video indicated in the timestamp.
     *
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    public static boolean handleUrlCommentsTimestamp(final CompositeDisposable disposables,
                                                     final Context context,
                                                     final String url) {
        return handleUrl(disposables, context, url, HASHTAG_TIMESTAMP_PATTERN);
    }

    /**
     * Handle a YouTube timestamp description URL in NewPipe.
     * <p>
     * This method will check if the provided url is a YouTube timestamp description URL ({@code
     * https://www.youtube.com/watch?v=}video_id{@code &t=}time_in_seconds). If yes, the popup
     * player will be opened when the user will click on the timestamp in the video description,
     * at the time and for the video indicated in the timestamp.
     *
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    public static boolean handleUrlDescriptionTimestamp(final CompositeDisposable disposables,
                                                        final Context context,
                                                        final String url) {
        return handleUrl(disposables, context, url, AMPERSAND_TIMESTAMP_PATTERN);
    }

    /**
     * Handle an URL in NewPipe.
     * <p>
     * This method will check if the provided url can be handled in NewPipe or not. If this is a
     * service URL with a timestamp, the popup player will be opened and true will be returned;
     * else, false will be returned.
     *
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @param pattern     the pattern to use
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    private static boolean handleUrl(final CompositeDisposable disposables,
                                     final Context context,
                                     final String url,
                                     final Pattern pattern) {
        final String matchedUrl;
        final StreamingService service;
        final StreamingService.LinkType linkType;
        final int seconds;
        final Matcher matcher = pattern.matcher(url);
        if (matcher.matches()) {
            matchedUrl = matcher.group(1);
            seconds = Integer.parseInt(matcher.group(2));
        } else {
            return false;
        }

        if (isNullOrEmpty(matchedUrl)) {
            return false;
        }
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
            return playOnPopup(disposables, context, matchedUrl, service, seconds);
        } else {
            NavigationHelper.openRouterActivity(context, matchedUrl);
            return true;
        }
    }

    /**
     * Play a content in the floating player.
     *
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @param context     the context to be used
     * @param url         the URL of the content
     * @param service     the service of the content
     * @param seconds     the position in seconds at which the floating player will start
     * @return true if the playback of the content has successfully started or false if not
     */
    public static boolean playOnPopup(final CompositeDisposable disposables,
                                      final Context context,
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
        disposables.add(single.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    final PlayQueue playQueue
                            = new SinglePlayQueue(info, seconds * 1000);
                    NavigationHelper.playOnPopupPlayer(context, playQueue, false);
                }));
        return true;
    }
}
