/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * ExtractorHelper.java is part of NewPipe
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

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.feed.FeedInfo;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.util.external_communication.TextLinkifier;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class ExtractorHelper {
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final String RETURN_YOUTUBE_DISLIKE_VOTES_URL =
            "https://returnyoutubedislikeapi.com/votes?videoId=";
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
                                               final List<FilterItem> contentFilter,
                                               final List<FilterItem> sortFilter) {
        checkServiceId(serviceId);
        try {
            StreamingService service = NewPipe.getService(serviceId);
            SearchQueryHandler handler = NewPipe.getService(serviceId)
                    .getSearchQHFactory()
                    .fromQuery(searchString, contentFilter, sortFilter);
            return Single.fromCallable(() ->
                    SearchInfo.getInfo(service,
                            handler));
        } catch (ExtractionException e) {
            throw new RuntimeException(e);
        }
    }

    public static Single<InfoItemsPage<InfoItem>> getMoreSearchItems(
            final int serviceId,
            final String searchString,
            final List<FilterItem> contentFilter,
            final List<FilterItem> sortFilter,
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
            final SuggestionExtractor extractor = NewPipe.getService(serviceId)
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
                Single.fromCallable(() -> getNewStreamInfo(serviceId, url)));
    }

    public static StreamInfo getNewStreamInfo(final int serviceId, final String url) throws ExtractionException, IOException {
//        if (true) {
            final StreamInfo streamInfo = StreamInfo.getInfo(NewPipe.getService(serviceId), url);
            backfillYouTubeDislikeCount(serviceId, streamInfo);
            return streamInfo;
//        }
//        StreamInfo result = null;
//        if (!ServiceList.YouTube.isYtdlpEnabled()) {
//            result = StreamInfo.getInfo(NewPipe.getService(serviceId), url);
//            if (!result.getAudioStreams().isEmpty() || !result.getVideoStreams().isEmpty()) {
//                return result;
//            }
//        }
//
//        StreamInfo fallbackInfo = YtdlpHelper.getFallbackStreams(url);
//        if(fallbackInfo.getAudioStreams().isEmpty() && fallbackInfo.getVideoStreams().isEmpty()) {
//            throw new ExtractionException("Couldn't get fallback streams for " + url);
//        }
//        return fallbackInfo;
    }

    private static void backfillYouTubeDislikeCount(final int serviceId,
                                                    final StreamInfo streamInfo) {
        if (serviceId != ServiceList.YouTube.getServiceId()
                || !ServiceList.YouTube.isFetchDislike()
                || streamInfo.getDislikeCount() >= 0
                || isNullOrEmpty(streamInfo.getId())) {
            return;
        }

        try {
            final String responseBody = NewPipe.getDownloader()
                    .get(RETURN_YOUTUBE_DISLIKE_VOTES_URL + streamInfo.getId())
                    .responseBody();
            final long dislikeCount = new JSONObject(responseBody).optLong("dislikes", -1);
            if (dislikeCount >= 0) {
                streamInfo.setDislikeCount(dislikeCount);
            }
        } catch (final Exception e) {
            if (MainActivity.DEBUG) {
                Log.d(TAG, "Return YouTube Dislike backfill failed", e);
            }
        }
    }

    public static Single<StreamInfo> getStreamInfoWithoutException(final int serviceId, final String url,
                                                   final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.STREAM,
                Single.fromCallable(() -> {
                    try{
                        return getNewStreamInfo(serviceId, url);
                    } catch (Exception e){
                        System.err.println("Error processing stream item: " + url + " - " + e.getMessage());
                        return new StreamInfo();
                    }
                }));
    }

    public static Single<ChannelInfo> getChannelInfo(final int serviceId, final String url,
                                                     final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.CHANNEL,
                Single.fromCallable(() ->
                        ChannelInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage<StreamInfoItem>> getMoreChannelItems(final int serviceId,
                                                                            final String url,
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

    public static Single<CommentsInfo> getCommentsReplyInfo(final int serviceId, final String url,
                                                            final boolean forceLoad,
                                                            final Page replyPage) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId,
                url + "?reply_placeholder_id=" + replyPage.getId(),
                InfoItem.InfoType.COMMENT,
                Single.fromCallable(() -> {
                            final var info = CommentsInfo.getInfoTemplate(NewPipe.getService(serviceId).getCommentsExtractor(url));
                            // use CommentsInfo make a info template
                            final var replies = CommentsInfo.getMoreItems(
                                    NewPipe.getService(serviceId), info, replyPage);
                            // push replies to info, replace original comments
                            info.setRelatedItems(replies.getItems());
                            // set next page
                            info.setNextPage(replies.getNextPage());
                            return info;
                        }
                ));
    }

    public static Single<ChannelTabInfo> getChannelTab(final int serviceId,
                                                       final ListLinkHandler listLinkHandler,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId,
                listLinkHandler.getUrl(), InfoItem.InfoType.CHANNEL,
                Single.fromCallable(() ->
                        ChannelTabInfo.getInfo(NewPipe.getService(serviceId), listLinkHandler)));
    }

    public static Single<InfoItemsPage<InfoItem>> getMoreChannelTabItems(final int serviceId,
                                                                         final ListLinkHandler
                                                                                 listLinkHandler,
                                                                         final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                ChannelTabInfo.getMoreItems(NewPipe.getService(serviceId),
                        listLinkHandler, nextPage));
    }

    public static Single<CommentsInfo> getCommentsInfo(final int serviceId, final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.COMMENT,
                Single.fromCallable(() ->
                        CommentsInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<BulletCommentsInfo> getBulletCommentsInfo(final int serviceId,
                                                                   final String url,
                                                                   final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.BULLET_COMMENT,
                Single.fromCallable(() ->
                        BulletCommentsInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage<CommentsInfoItem>> getMoreCommentItems(
            final int serviceId,
            final CommentsInfo info,
            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage));
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId,
                                                       final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST,
                Single.fromCallable(() ->
                        PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<PlaylistInfo> getPlaylistInfoWithFullItems(final int serviceId,
                                                       final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST,
                Single.fromCallable(() ->
                        PlaylistInfo.getInfoWithFullItems(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage<StreamInfoItem>> getMorePlaylistItems(final int serviceId,
                                                                             final String url,
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

    public static Single<InfoItemsPage<StreamInfoItem>> getMoreKioskItems(final int serviceId,
                                                                          final String url,
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
        final Single<I> actualLoadFromNetwork = loadFromNetwork
                .doOnSuccess(info -> cacheInfo(serviceId, url, info, infoType));

        final Single<I> load;
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

    static void cacheInfo(final int serviceId, @NonNull final String requestedUrl,
                          @NonNull final Info info, @NonNull final InfoItem.InfoType infoType) {
        CACHE.putInfo(serviceId, requestedUrl, info, infoType);
        // StreamInfo can canonicalize its URL. PlayQueueItem keeps that canonical URL, while the
        // detail request was cached under the originally entered URL. Cache both keys so clicking
        // play cannot trigger a second full extraction.
        if (infoType == InfoItem.InfoType.STREAM
                && info.getUrl() != null && !requestedUrl.equals(info.getUrl())) {
            CACHE.putInfo(serviceId, info.getUrl(), info, infoType);
        }
    }

    /**
     * Default implementation uses the {@link InfoCache} to get cached results.
     *
     * @param <I>       the item type's class that extends {@link Info}
     * @param serviceId the service to load from
     * @param url       the URL to load
     * @param infoType  the {@link InfoItem.InfoType} of the item
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Maybe<I> loadFromCache(final int serviceId, final String url,
                                                           final InfoItem.InfoType infoType) {
        checkServiceId(serviceId);
        return Maybe.defer(() -> {
            //noinspection unchecked
            final I info = (I) CACHE.getFromKey(serviceId, url, infoType);
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
     * Formats the text contained in the meta info list as HTML and puts it into the text view,
     * while also making the separator visible. If the list is null or empty, or the user chose not
     * to see meta information, both the text view and the separator are hidden
     *
     * @param metaInfos         a list of meta information, can be null or empty
     * @param metaInfoTextView  the text view in which to show the formatted HTML
     * @param metaInfoSeparator another view to be shown or hidden accordingly to the text view
     * @param disposables       disposables created by the method are added here and their lifecycle
     *                          should be handled by the calling class
     */
    public static void showMetaInfoInTextView(@Nullable final List<MetaInfo> metaInfos,
                                              final TextView metaInfoTextView,
                                              final View metaInfoSeparator,
                                              final CompositeDisposable disposables) {
        final Context context = metaInfoTextView.getContext();
        if (metaInfos == null || metaInfos.isEmpty()
                || !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.show_meta_info_key), true)) {
            metaInfoTextView.setVisibility(View.GONE);
            metaInfoSeparator.setVisibility(View.GONE);

        } else {
            final StringBuilder stringBuilder = new StringBuilder();
            for (final MetaInfo metaInfo : metaInfos) {
                if (!isNullOrEmpty(metaInfo.getTitle())) {
                    stringBuilder.append("<b>").append(metaInfo.getTitle()).append("</b>")
                            .append(Localization.DOT_SEPARATOR);
                }

                String content = metaInfo.getContent().getContent().trim();
                if (content.endsWith(".")) {
                    content = content.substring(0, content.length() - 1); // remove . at end
                }
                stringBuilder.append(content);

                for (int i = 0; i < metaInfo.getUrls().size(); i++) {
                    if (i == 0) {
                        stringBuilder.append(Localization.DOT_SEPARATOR);
                    } else {
                        stringBuilder.append("<br/><br/>");
                    }

                    stringBuilder
                            .append("<a href=\"").append(metaInfo.getUrls().get(i)).append("\">")
                            .append(capitalizeIfAllUppercase(metaInfo.getUrlTexts().get(i).trim()))
                            .append("</a>");
                }
            }

            metaInfoSeparator.setVisibility(View.VISIBLE);
            TextLinkifier.createLinksFromHtmlBlock(metaInfoTextView, stringBuilder.toString(),
                    HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING, null, disposables);
        }
    }

    private static String capitalizeIfAllUppercase(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLowerCase(text.charAt(i))) {
                return text; // there is at least a lowercase letter -> not all uppercase
            }
        }

        if (text.isEmpty()) {
            return text;
        } else {
            return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
        }
    }
}
