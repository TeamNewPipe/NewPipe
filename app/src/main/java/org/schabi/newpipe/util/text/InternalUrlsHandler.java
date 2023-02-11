package org.schabi.newpipe.util.text;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorPanelHelper;
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

public final class InternalUrlsHandler {
    private static final String TAG = InternalUrlsHandler.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

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
    public static boolean handleUrlCommentsTimestamp(@NonNull final CompositeDisposable
                                                             disposables,
                                                     final Context context,
                                                     @NonNull final String url) {
        return handleUrl(context, url, HASHTAG_TIMESTAMP_PATTERN, disposables);
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
    public static boolean handleUrlDescriptionTimestamp(@NonNull final CompositeDisposable
                                                                disposables,
                                                        final Context context,
                                                        @NonNull final String url) {
        return handleUrl(context, url, AMPERSAND_TIMESTAMP_PATTERN, disposables);
    }

    /**
     * Handle an URL in NewPipe.
     * <p>
     * This method will check if the provided url can be handled in NewPipe or not. If this is a
     * service URL with a timestamp, the popup player will be opened and true will be returned;
     * else, false will be returned.
     *
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @param pattern     the pattern to use
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    private static boolean handleUrl(final Context context,
                                     @NonNull final String url,
                                     @NonNull final Pattern pattern,
                                     @NonNull final CompositeDisposable disposables) {
        final Matcher matcher = pattern.matcher(url);
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
            return playOnPopup(context, matchedUrl, service, seconds, disposables);
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
     * @param disposables disposables created by the method are added here and their lifecycle
     *                    should be handled by the calling class
     * @return true if the playback of the content has successfully started or false if not
     */
    public static boolean playOnPopup(final Context context,
                                      final String url,
                                      @NonNull final StreamingService service,
                                      final int seconds,
                                      @NonNull final CompositeDisposable disposables) {
        final LinkHandlerFactory factory = service.getStreamLHFactory();
        final String cleanUrl;

        try {
            cleanUrl = factory.getUrl(factory.getId(url));
        } catch (final ParsingException e) {
            return false;
        }

        final Single<StreamInfo> single =
                ExtractorHelper.getStreamInfo(service.getServiceId(), cleanUrl, false);
        disposables.add(single.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    final PlayQueue playQueue =
                            new SinglePlayQueue(info, seconds * 1000L);
                    NavigationHelper.playOnPopupPlayer(context, playQueue, false);
                }, throwable -> {
                    if (DEBUG) {
                        Log.e(TAG, "Could not play on popup: " + url, throwable);
                    }
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.player_stream_failure)
                            .setMessage(
                                    ErrorPanelHelper.Companion.getExceptionDescription(throwable))
                            .setPositiveButton(R.string.ok, (v, b) -> { })
                            .show();
                }));
        return true;
    }
}
