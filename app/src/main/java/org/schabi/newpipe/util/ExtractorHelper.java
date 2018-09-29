/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Extractors.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.Single;

public final class ExtractorHelper {
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final InfoCache cache = InfoCache.getInstance();

    private ExtractorHelper() {
        //no instance
    }

    private static void checkServiceId(int serviceId) {
        if (serviceId == Constants.NO_SERVICE_ID) {
            throw new IllegalArgumentException("serviceId is NO_SERVICE_ID");
        }
    }

    public static Single<SearchInfo> searchFor(final int serviceId,
                                               final String searchString,
                                               final List<String> contentFilter,
                                               final String sortFilter,
                                               final String contentCountry) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
            SearchInfo.getInfo(NewPipe.getService(serviceId),
                    NewPipe.getService(serviceId)
                        .getSearchQHFactory()
                        .fromQuery(searchString, contentFilter, sortFilter),
                    contentCountry));
    }

    public static Single<InfoItemsPage> getMoreSearchItems(final int serviceId,
                                                           final String searchString,
                                                           final List<String> contentFilter,
                                                           final String sortFilter,
                                                           final String pageUrl,
                                                           final String contentCountry) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                SearchInfo.getMoreItems(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                            .getSearchQHFactory()
                            .fromQuery(searchString, contentFilter, sortFilter),
                        contentCountry,
                        pageUrl));

    }

    public static Single<List<String>> suggestionsFor(final int serviceId,
                                                      final String query,
                                                      final String contentCountry) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                NewPipe.getService(serviceId)
                        .getSuggestionExtractor()
                        .suggestionList(query, contentCountry));
    }

    public static Single<StreamInfo> getStreamInfo(final int serviceId,
                                                   final String url,
                                                   boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.STREAM, Single.fromCallable(() ->
                StreamInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<ChannelInfo> getChannelInfo(final int serviceId,
                                                     final String url,
                                                     boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.CHANNEL, Single.fromCallable(() ->
                ChannelInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreChannelItems(final int serviceId,
                                                            final String url,
                                                            final String nextStreamsUrl) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                ChannelInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl));
    }

    public static Single<CommentsInfo> getCommentsInfo(final int serviceId,
                                                       final String url,
                                                       boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.COMMENT, Single.fromCallable(() ->
                CommentsInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreCommentItems(final int serviceId,
                                                            final CommentsInfo info,
                                                            final String nextPageUrl) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPageUrl));
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId,
                                                       final String url,
                                                       boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST, Single.fromCallable(() ->
                PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMorePlaylistItems(final int serviceId,
                                                             final String url,
                                                             final String nextStreamsUrl) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl));
    }

    public static Single<KioskInfo> getKioskInfo(final int serviceId,
                                                 final String url,
                                                 final String contentCountry,
                                                 boolean forceLoad) {
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST, Single.fromCallable(() ->
                KioskInfo.getInfo(NewPipe.getService(serviceId), url, contentCountry)));
    }

    public static Single<InfoItemsPage> getMoreKioskItems(final int serviceId,
                                                          final String url,
                                                          final String nextStreamsUrl,
                                                          final String contentCountry) {
        return Single.fromCallable(() ->
                KioskInfo.getMoreItems(NewPipe.getService(serviceId),
                        url, nextStreamsUrl, contentCountry));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't,
     * load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     */
    private static <I extends Info> Single<I> checkCache(boolean forceLoad,
                                                         int serviceId,
                                                         String url,
                                                         InfoItem.InfoType infoType,
                                                         Single<I> loadFromNetwork) {
        checkServiceId(serviceId);
        loadFromNetwork = loadFromNetwork.doOnSuccess(info -> cache.putInfo(serviceId, url, info, infoType));

        Single<I> load;
        if (forceLoad) {
            cache.removeInfo(serviceId, url, infoType);
            load = loadFromNetwork;
        } else {
            load = Maybe.concat(ExtractorHelper.loadFromCache(serviceId, url, infoType),
                    loadFromNetwork.toMaybe())
                    .firstElement() //Take the first valid
                    .toSingle();
        }

        return load;
    }

    /**
     * Default implementation uses the {@link InfoCache} to get cached results
     */
    public static <I extends Info> Maybe<I> loadFromCache(final int serviceId, final String url, InfoItem.InfoType infoType) {
        checkServiceId(serviceId);
        return Maybe.defer(() -> {
            //noinspection unchecked
            I info = (I) cache.getFromKey(serviceId, url, infoType);
            if (MainActivity.DEBUG) Log.d(TAG, "loadFromCache() called, info > " + info);

            // Only return info if it's not null (it is cached)
            if (info != null) {
                return Maybe.just(info);
            }

            return Maybe.empty();
        });
    }

    /**
     * A simple and general error handler that show a Toast for known exceptions, and for others, opens the report error activity with the (optional) error message.
     */
    public static void handleGeneralException(Context context, int serviceId, String url, Throwable exception, UserAction userAction, String optionalErrorMessage) {
        final Handler handler = new Handler(context.getMainLooper());

        handler.post(() -> {
            if (exception instanceof ReCaptchaException) {
                Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
                // Starting ReCaptcha Challenge Activity
                Intent intent = new Intent(context, ReCaptchaActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else if (exception instanceof IOException) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
            } else if (exception instanceof YoutubeStreamExtractor.GemaException) {
                Toast.makeText(context, R.string.blocked_by_gema, Toast.LENGTH_LONG).show();
            } else if (exception instanceof ContentNotAvailableException) {
                Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
            } else {
                int errorId = exception instanceof YoutubeStreamExtractor.DecryptException ? R.string.youtube_signature_decryption_error :
                        exception instanceof ParsingException ? R.string.parsing_error : R.string.general_error;
                ErrorActivity.reportError(handler, context, exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(userAction,
                        serviceId == -1 ? "none" : NewPipe.getNameOfService(serviceId), url + (optionalErrorMessage == null ? "" : optionalErrorMessage), errorId));
            }
        });
    }

    /**
     * Check if throwable have the cause that can be assignable from the causes to check.
     *
     * @see Class#isAssignableFrom(Class)
     */
    public static boolean hasAssignableCauseThrowable(Throwable throwable,
                                                      Class<?>... causesToCheck) {
        // Check if getCause is not the same as cause (the getCause is already the root),
        // as it will cause a infinite loop if it is
        Throwable cause, getCause = throwable;

        // Check if throwable is a subclass of any of the filtered classes
        final Class throwableClass = throwable.getClass();
        for (Class<?> causesEl : causesToCheck) {
            if (causesEl.isAssignableFrom(throwableClass)) {
                return true;
            }
        }

        // Iteratively checks if the root cause of the throwable is a subclass of the filtered class
        while ((cause = throwable.getCause()) != null && getCause != cause) {
            getCause = cause;
            final Class causeClass = cause.getClass();
            for (Class<?> causesEl : causesToCheck) {
                if (causesEl.isAssignableFrom(causeClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if throwable have the exact cause from one of the causes to check.
     */
    public static boolean hasExactCauseThrowable(Throwable throwable, Class<?>... causesToCheck) {
        // Check if getCause is not the same as cause (the getCause is already the root),
        // as it will cause a infinite loop if it is
        Throwable cause, getCause = throwable;

        for (Class<?> causesEl : causesToCheck) {
            if (throwable.getClass().equals(causesEl)) {
                return true;
            }
        }

        while ((cause = throwable.getCause()) != null && getCause != cause) {
            getCause = cause;
            for (Class<?> causesEl : causesToCheck) {
                if (cause.getClass().equals(causesEl)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if throwable have Interrupted* exception as one of its causes.
     */
    public static boolean isInterruptedCaused(Throwable throwable) {
        return ExtractorHelper.hasExactCauseThrowable(throwable,
                InterruptedIOException.class,
                InterruptedException.class);
    }
}
