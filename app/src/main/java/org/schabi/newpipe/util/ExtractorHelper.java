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
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.feed.FeedInfo;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import java.util.Collections;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.Single;

public final class ExtractorHelper {
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final InfoCache CACHE = InfoCache.getInstance();

    private ExtractorHelper() {
        //no instance
    }

    private static void checkServiceId(final int serviceId) {
        if (serviceId == Constants.NO_SERVICE_ID) {
            throw new IllegalArgumentException("serviceId is NO_SERVICE_ID");
        }
    }

    public static Single<SearchInfo> searchFor(final int serviceId, final String searchString,
                                               final List<String> contentFilter,
                                               final String sortFilter) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                SearchInfo.getInfo(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, contentFilter, sortFilter)));
    }

    public static Single<InfoItemsPage> getMoreSearchItems(final int serviceId,
                                                           final String searchString,
                                                           final List<String> contentFilter,
                                                           final String sortFilter,
                                                           final Page page) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                SearchInfo.getMoreItems(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, contentFilter, sortFilter), page));

    }

    public static Single<List<String>> suggestionsFor(final int serviceId, final String query) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            SuggestionExtractor extractor = NewPipe.getService(serviceId)
                    .getSuggestionExtractor();
            return extractor != null
                    ? extractor.suggestionList(query)
                    : Collections.emptyList();
        });
    }

    public static Single<StreamInfo> getStreamInfo(final int serviceId, final String url,
                                                   final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.STREAM,
                Single.fromCallable(() -> StreamInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<ChannelInfo> getChannelInfo(final int serviceId, final String url,
                                                     final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.CHANNEL,
                Single.fromCallable(() ->
                        ChannelInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreChannelItems(final int serviceId, final String url,
                                                            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                ChannelInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    public static Single<ListInfo<StreamInfoItem>> getFeedInfoFallbackToChannelInfo(
            final int serviceId, final String url) {
        final Maybe<ListInfo<StreamInfoItem>> maybeFeedInfo = Maybe.fromCallable(() -> {
            final StreamingService service = NewPipe.getService(serviceId);
            final FeedExtractor feedExtractor = service.getFeedExtractor(url);

            if (feedExtractor == null) {
                return null;
            }

            return FeedInfo.getInfo(feedExtractor);
        });

        return maybeFeedInfo.switchIfEmpty(getChannelInfo(serviceId, url, true));
    }

    public static Single<CommentsInfo> getCommentsInfo(final int serviceId, final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.COMMENT,
                Single.fromCallable(() ->
                        CommentsInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreCommentItems(final int serviceId,
                                                            final CommentsInfo info,
                                                            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage));
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId, final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST,
                Single.fromCallable(() ->
                        PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMorePlaylistItems(final int serviceId, final String url,
                                                             final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    public static Single<KioskInfo> getKioskInfo(final int serviceId, final String url,
                                                 final boolean forceLoad) {
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST,
                Single.fromCallable(() -> KioskInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreKioskItems(final int serviceId, final String url,
                                                          final Page nextPage) {
        return Single.fromCallable(() ->
                KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't,
     * load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     *
     * @param <I>             the item type's class that extends {@link Info}
     * @param forceLoad       whether to force loading from the network instead of from the cache
     * @param serviceId       the service to load from
     * @param url             the URL to load
     * @param infoType        the {@link InfoItem.InfoType} of the item
     * @param loadFromNetwork the {@link Single} to load the item from the network
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Single<I> checkCache(final boolean forceLoad,
                                                         final int serviceId, final String url,
                                                         final InfoItem.InfoType infoType,
                                                         final Single<I> loadFromNetwork) {
        checkServiceId(serviceId);
        Single<I> actualLoadFromNetwork = loadFromNetwork
                .doOnSuccess(info -> CACHE.putInfo(serviceId, url, info, infoType));

        Single<I> load;
        if (forceLoad) {
            CACHE.removeInfo(serviceId, url, infoType);
            load = actualLoadFromNetwork;
        } else {
            load = Maybe.concat(ExtractorHelper.loadFromCache(serviceId, url, infoType),
                    actualLoadFromNetwork.toMaybe())
                    .firstElement() // Take the first valid
                    .toSingle();
        }

        return load;
    }

    /**
     * Default implementation uses the {@link InfoCache} to get cached results.
     *
     * @param <I>             the item type's class that extends {@link Info}
     * @param serviceId       the service to load from
     * @param url             the URL to load
     * @param infoType        the {@link InfoItem.InfoType} of the item
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Maybe<I> loadFromCache(final int serviceId, final String url,
                                                           final InfoItem.InfoType infoType) {
        checkServiceId(serviceId);
        return Maybe.defer(() -> {
            //noinspection unchecked
            I info = (I) CACHE.getFromKey(serviceId, url, infoType);
            if (MainActivity.DEBUG) {
                Log.d(TAG, "loadFromCache() called, info > " + info);
            }

            // Only return info if it's not null (it is cached)
            if (info != null) {
                return Maybe.just(info);
            }

            return Maybe.empty();
        });
    }

    public static boolean isCached(final int serviceId, final String url,
                                   final InfoItem.InfoType infoType) {
        return null != loadFromCache(serviceId, url, infoType).blockingGet();
    }

    /**
     * A simple and general error handler that show a Toast for known exceptions,
     * and for others, opens the report error activity with the (optional) error message.
     *
     * @param context              Android app context
     * @param serviceId            the service the exception happened in
     * @param url                  the URL where the exception happened
     * @param exception            the exception to be handled
     * @param userAction           the action of the user that caused the exception
     * @param optionalErrorMessage the optional error message
     */
    public static void handleGeneralException(final Context context, final int serviceId,
                                              final String url, final Throwable exception,
                                              final UserAction userAction,
                                              final String optionalErrorMessage) {
        final Handler handler = new Handler(context.getMainLooper());

        handler.post(() -> {
            if (exception instanceof ReCaptchaException) {
                Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
                // Starting ReCaptcha Challenge Activity
                Intent intent = new Intent(context, ReCaptchaActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else if (ExceptionUtils.isNetworkRelated(exception)) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
            } else if (exception instanceof ContentNotAvailableException) {
                Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
            } else if (exception instanceof ContentNotSupportedException) {
                Toast.makeText(context, R.string.content_not_supported, Toast.LENGTH_LONG).show();
            } else {
                int errorId = exception instanceof YoutubeStreamExtractor.DecryptException
                        ? R.string.youtube_signature_decryption_error
                        : exception instanceof ParsingException
                        ? R.string.parsing_error : R.string.general_error;
                ErrorActivity.reportError(handler, context, exception, MainActivity.class, null,
                        ErrorActivity.ErrorInfo.make(userAction, serviceId == -1 ? "none"
                                : NewPipe.getNameOfService(serviceId),
                                url + (optionalErrorMessage == null ? ""
                                        : optionalErrorMessage), errorId));
            }
        });
    }
}
