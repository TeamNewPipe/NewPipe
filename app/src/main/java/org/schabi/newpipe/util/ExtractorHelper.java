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
import static org.schabi.newpipe.util.text.TextLinkifier.SET_LINK_MOVEMENT_METHOD;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentSortOrder;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.App;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.util.text.TextLinkifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class ExtractorHelper {
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final String RETURN_YOUTUBE_DISLIKE_VOTES_URL =
            "https://returnyoutubedislikeapi.com/votes?videoId=";
    private static final String YOUTUBE_RELOAD_REQUIRED_MESSAGE =
            "The page needs to be reloaded.";
    private static final InfoCache CACHE = InfoCache.getInstance();

    private ExtractorHelper() {
        //no instance
    }

    private static void checkServiceId(final int serviceId) {
        if (serviceId == Constants.NO_SERVICE_ID) {
            throw new IllegalArgumentException("serviceId is NO_SERVICE_ID");
        }
    }

    public static Single<SearchInfo> searchForFilters(final int serviceId,
                                                      final String searchString,
                                                      final List<String> contentFilter,
                                                      final List<Integer> sortFilterIds) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            if (!KidsMode.isSearchAllowed(App.getInstance(), serviceId)) {
                throw new IllegalStateException(App.getInstance()
                        .getString(R.string.kids_mode_search_disabled));
            }
            final SearchQueryHandlerFactory factory = NewPipe.getService(serviceId)
                    .getSearchQHFactory();
            return SearchInfo.getInfo(NewPipe.getService(serviceId), factory.fromQuery(
                    searchString,
                    resolveContentFilterItems(factory.getAvailableContentFilter(), contentFilter),
                    resolveFilterItemsByIds(factory, sortFilterIds)));
        });
    }

    public static Single<SearchInfo> searchFor(final int serviceId, final String searchString,
                                               final List<String> contentFilter,
                                               final String sortFilter) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            if (!KidsMode.isSearchAllowed(App.getInstance(), serviceId)) {
                throw new IllegalStateException(App.getInstance()
                        .getString(R.string.kids_mode_search_disabled));
            }
            final SearchQueryHandlerFactory factory = NewPipe.getService(serviceId)
                    .getSearchQHFactory();
            return SearchInfo.getInfo(NewPipe.getService(serviceId), factory.fromQuery(
                    searchString,
                    resolveContentFilterItems(factory.getAvailableContentFilter(), contentFilter),
                    resolveFilterItems(factory.getAvailableSortFilter(), sortFilter)));
        });
    }

    public static Single<InfoItemsPage<InfoItem>> getMoreSearchItems(
            final int serviceId,
            final String searchString,
            final List<String> contentFilter,
            final List<Integer> sortFilterIds,
            final Page page) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            if (!KidsMode.isSearchAllowed(App.getInstance(), serviceId)) {
                throw new IllegalStateException(App.getInstance()
                        .getString(R.string.kids_mode_search_disabled));
            }
            final SearchQueryHandlerFactory factory = NewPipe.getService(serviceId)
                    .getSearchQHFactory();
            return SearchInfo.getMoreItems(NewPipe.getService(serviceId), factory.fromQuery(
                    searchString,
                    resolveContentFilterItems(factory.getAvailableContentFilter(), contentFilter),
                    resolveFilterItemsByIds(factory, sortFilterIds)), page);
        });

    }

    public static List<FilterItem> resolveFilterItems(@Nullable final Filter availableFilters,
                                                      @Nullable final List<String> names) {
        if (availableFilters == null || names == null || names.isEmpty()) {
            return Collections.emptyList();
        }

        final List<FilterItem> selected = new ArrayList<>();
        for (final String name : names) {
            final FilterItem filterItem = findFilterItemByName(availableFilters, name);
            if (filterItem != null) {
                selected.add(filterItem);
            }
        }
        return selected;
    }

    public static List<FilterItem> resolveContentFilterItems(
            @Nullable final Filter availableFilters,
            @Nullable final List<String> names) {
        final List<FilterItem> selected = resolveFilterItems(availableFilters, names);
        if (!selected.isEmpty() || availableFilters == null
                || availableFilters.getFilterGroups() == null) {
            return selected;
        }

        for (final FilterGroup group : availableFilters.getFilterGroups()) {
            if (group != null && group.filterItems != null && group.filterItems.length > 0) {
                return Collections.singletonList(group.filterItems[0]);
            }
        }

        return Collections.emptyList();
    }

    public static List<FilterItem> resolveFilterItems(@Nullable final Filter availableFilters,
                                                      @Nullable final String name) {
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }
        return resolveFilterItems(availableFilters, Collections.singletonList(name));
    }

    public static List<FilterItem> resolveFilterItemsByIds(
            @NonNull final SearchQueryHandlerFactory factory,
            @Nullable final List<Integer> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return Collections.emptyList();
        }
        final List<FilterItem> selected = new ArrayList<>();
        for (final Integer identifier : identifiers) {
            final FilterItem filterItem = identifier == null
                    ? null : factory.getFilterItem(identifier);
            if (filterItem != null) {
                selected.add(filterItem);
            }
        }
        return selected;
    }

    @Nullable
    private static FilterItem findFilterItemByName(@NonNull final Filter filter,
                                                   @Nullable final String name) {
        if (name == null || filter.getFilterGroups() == null) {
            return null;
        }
        for (final FilterGroup group : filter.getFilterGroups()) {
            if (group == null || group.filterItems == null) {
                continue;
            }
            for (final FilterItem item : group.filterItems) {
                if (name.equals(item.getName())) {
                    return item;
                }
            }
        }
        return null;
    }

    public static Single<List<String>> suggestionsFor(final int serviceId, final String query) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            if (!KidsMode.isSearchAllowed(App.getInstance(), serviceId)) {
                throw new IllegalStateException(App.getInstance()
                        .getString(R.string.kids_mode_search_disabled));
            }
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
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.STREAM,
                Single.fromCallable(() -> getStreamInfoFromNetwork(serviceId, url)))
                .doOnSuccess(org.schabi.newpipe.local.feed.FeedDurationCache::remember);
    }

    @NonNull
    private static StreamInfo getStreamInfoFromNetwork(final int serviceId,
                                                       final String url) throws Exception {
        StreamInfo streamInfo;
        try {
            streamInfo = StreamInfo.getInfo(NewPipe.getService(serviceId), url);
        } catch (final ContentNotAvailableException error) {
            if (!isTransientYouTubeReloadError(serviceId, error)) {
                throw error;
            }
            Log.i(TAG, "Retrying YouTube extraction after a transient reload response");
            streamInfo = StreamInfo.getInfo(NewPipe.getService(serviceId), url);
        }
        backfillYouTubeRatingsAndViewCount(serviceId, streamInfo);
        backfillYouTubeUploaderAvatarFromChannel(serviceId, streamInfo);
        return streamInfo;
    }

    static boolean isTransientYouTubeReloadError(
            final int serviceId,
            @Nullable final ContentNotAvailableException error) {
        return serviceId == ServiceList.YouTube.getServiceId()
                && error != null
                && error.getMessage() != null
                && YOUTUBE_RELOAD_REQUIRED_MESSAGE.equalsIgnoreCase(
                        error.getMessage().trim());
    }

    private static void backfillYouTubeRatingsAndViewCount(final int serviceId,
                                                           final StreamInfo info) {
        if (serviceId != ServiceList.YouTube.getServiceId()
                || !ServiceList.YouTube.isFetchDislike()
                || (info.getLikeCount() >= 0
                && info.getDislikeCount() >= 0
                && info.getViewCount() >= 0)) {
            return;
        }

        final String videoId = info.getId();
        if (isNullOrEmpty(videoId)) {
            return;
        }

        try {
            final String responseBody = NewPipe.getDownloader()
                    .get(RETURN_YOUTUBE_DISLIKE_VOTES_URL + videoId)
                    .responseBody();
            final JSONObject votes = new JSONObject(responseBody);
            if (info.getLikeCount() < 0) {
                final long likes = votes.optLong("likes", -1);
                if (likes >= 0) {
                    info.setLikeCount(likes);
                }
            }
            if (info.getDislikeCount() < 0) {
                final long dislikes = votes.optLong("dislikes", -1);
                if (dislikes >= 0) {
                    info.setDislikeCount(dislikes);
                }
            }
            if (info.getViewCount() < 0) {
                final long views = votes.optLong("viewCount", -1);
                if (views >= 0) {
                    info.setViewCount(views);
                }
            }
        } catch (final Exception ignored) {
            // Keep extractor-provided values when the optional metadata backfill is unavailable.
        }
    }

    private static void backfillYouTubeUploaderAvatarFromChannel(final int serviceId,
                                                                 final StreamInfo info) {
        if (serviceId != ServiceList.YouTube.getServiceId()) {
            return;
        }

        final boolean avatarMissing = isNullOrEmpty(info.getUploaderAvatarUrl())
                && (info.getUploaderAvatars() == null || info.getUploaderAvatars().isEmpty());
        final boolean subscriberCountMissing = info.getUploaderSubscriberCount() < 0;
        if ((!avatarMissing && !subscriberCountMissing) || isNullOrEmpty(info.getUploaderUrl())) {
            return;
        }

        try {
            final ChannelInfo channelInfo = getChannelInfo(
                    serviceId, info.getUploaderUrl(), false).blockingGet();

            if (subscriberCountMissing && channelInfo.getSubscriberCount() >= 0) {
                info.setUploaderSubscriberCount(channelInfo.getSubscriberCount());
            }

            if (!avatarMissing) {
                return;
            }

            final List<Image> avatars = channelInfo.getAvatars();
            if (avatars == null || avatars.isEmpty()) {
                return;
            }

            info.setUploaderAvatars(avatars);
            final String avatarUrl = findLastNonEmptyImageUrl(avatars);
            if (avatarUrl != null) {
                info.setUploaderAvatarUrl(avatarUrl);
            }
        } catch (final Exception ignored) {
            // Keep the stream page usable if the optional channel metadata fallback fails.
        }
    }

    @Nullable
    static String findLastNonEmptyImageUrl(@Nullable final List<Image> images) {
        if (images == null) {
            return null;
        }

        for (int i = images.size() - 1; i >= 0; i--) {
            final Image image = images.get(i);
            if (image != null && !isNullOrEmpty(image.getUrl())) {
                return image.getUrl();
            }
        }
        return null;
    }

    public static Single<ChannelInfo> getChannelInfo(final int serviceId, final String url,
                                                     final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.CHANNEL,
                Single.fromCallable(() ->
                        ChannelInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<ChannelTabInfo> getChannelTab(final int serviceId,
                                                       final ListLinkHandler listLinkHandler,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        if (listLinkHandler.getContentFilters().stream()
                .anyMatch(filter -> ChannelTabs.SEARCH.equals(filter.getName()))
                && !KidsMode.isSearchAllowed(App.getInstance(), serviceId)) {
            return Single.error(new IllegalStateException(App.getInstance()
                    .getString(R.string.kids_mode_search_disabled)));
        }
        return checkCache(forceLoad, serviceId,
                listLinkHandler.getUrl(), InfoCache.Type.CHANNEL_TAB,
                Single.fromCallable(() ->
                        ChannelTabInfo.getInfo(NewPipe.getService(serviceId), listLinkHandler)));
    }

    public static Single<InfoItemsPage<InfoItem>> getMoreChannelTabItems(
            final int serviceId,
            final ListLinkHandler listLinkHandler,
            final Page nextPage) {
        checkServiceId(serviceId);
        if (listLinkHandler.getContentFilters().stream()
                .anyMatch(filter -> ChannelTabs.SEARCH.equals(filter.getName()))
                && !KidsMode.isSearchAllowed(App.getInstance(), serviceId)) {
            return Single.error(new IllegalStateException(App.getInstance()
                    .getString(R.string.kids_mode_search_disabled)));
        }
        return Single.fromCallable(() ->
                ChannelTabInfo.getMoreItems(NewPipe.getService(serviceId),
                        listLinkHandler, nextPage));
    }

    public static Single<CommentsInfo> getCommentsInfo(final int serviceId,
                                                       final String url,
                                                       final boolean forceLoad) {
        return getCommentsInfo(serviceId, url, forceLoad, CommentSortOrder.TOP);
    }

    public static Single<CommentsInfo> getCommentsInfo(final int serviceId,
                                                       final String url,
                                                       final boolean forceLoad,
                                                       final CommentSortOrder sortOrder) {
        checkServiceId(serviceId);
        final String cacheKey = sortOrder == CommentSortOrder.TOP ? url
                : url + "#comments-sort=" + sortOrder.name();
        return checkCache(forceLoad, serviceId, cacheKey, InfoCache.Type.COMMENTS,
                Single.fromCallable(() ->
                        CommentsInfo.getInfo(NewPipe.getService(serviceId), url, sortOrder)));
    }

    public static Single<InfoItemsPage<CommentsInfoItem>> getMoreCommentItems(
            final int serviceId,
            final CommentsInfo info,
            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage));
    }

    public static Single<InfoItemsPage<CommentsInfoItem>> getMoreCommentItems(
            final int serviceId,
            final String url,
            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId,
                                                       final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.PLAYLIST,
                Single.fromCallable(() -> getPlaylistInfoFromNetwork(serviceId, url)));
    }

    @NonNull
    private static PlaylistInfo getPlaylistInfoFromNetwork(final int serviceId,
                                                           final String url) throws Exception {
        final PlaylistInfo playlistInfo =
                PlaylistInfo.getInfo(NewPipe.getService(serviceId), url);
        backfillYouTubePlaylistUploaderAvatarFromChannel(serviceId, playlistInfo);
        backfillYouTubePlaylistItemUploaderAvatars(
                serviceId,
                playlistInfo.getRelatedItems(),
                playlistInfo.getUploaderUrl(),
                playlistInfo.getUploaderAvatarUrl());
        return playlistInfo;
    }

    private static void backfillYouTubePlaylistUploaderAvatarFromChannel(
            final int serviceId,
            @NonNull final PlaylistInfo info) {
        if (!shouldBackfillYouTubePlaylistUploaderAvatar(
                serviceId, info.getUploaderUrl(), info.getUploaderAvatarUrl())) {
            return;
        }

        try {
            final ChannelInfo channelInfo = getChannelInfo(
                    serviceId, info.getUploaderUrl(), false).blockingGet();
            final String avatarUrl = findLastNonEmptyImageUrl(channelInfo.getAvatars());
            if (avatarUrl != null) {
                info.setUploaderAvatarUrl(avatarUrl);
            }
        } catch (final Exception ignored) {
            // Keep the playlist page usable if the optional channel metadata fallback fails.
        }
    }

    static boolean shouldBackfillYouTubePlaylistUploaderAvatar(
            final int serviceId,
            @Nullable final String uploaderUrl,
            @Nullable final String uploaderAvatarUrl) {
        return serviceId == ServiceList.YouTube.getServiceId()
                && !isNullOrEmpty(uploaderUrl)
                && isNullOrEmpty(uploaderAvatarUrl);
    }

    private static void backfillYouTubePlaylistItemUploaderAvatars(
            final int serviceId,
            @Nullable final List<StreamInfoItem> items,
            @Nullable final String playlistUploaderUrl,
            @Nullable final String playlistUploaderAvatarUrl) {
        if (serviceId != ServiceList.YouTube.getServiceId()
                || items == null || items.isEmpty()) {
            return;
        }

        final String unresolvedUploaderUrl = enrichKnownPlaylistItemUploaderAvatars(
                items, playlistUploaderUrl, playlistUploaderAvatarUrl);
        if (isNullOrEmpty(unresolvedUploaderUrl)) {
            return;
        }

        try {
            final ChannelInfo channelInfo = getChannelInfo(
                    serviceId, unresolvedUploaderUrl, false).blockingGet();
            final String avatarUrl = findLastNonEmptyImageUrl(channelInfo.getAvatars());
            applyPlaylistItemUploaderAvatar(items, unresolvedUploaderUrl, avatarUrl);
        } catch (final Exception ignored) {
            // Keep the playlist usable if the optional shared-uploader lookup fails.
        }
    }

    @Nullable
    static String enrichKnownPlaylistItemUploaderAvatars(
            final List<StreamInfoItem> items,
            @Nullable final String playlistUploaderUrl,
            @Nullable final String playlistUploaderAvatarUrl) {
        final Map<String, String> knownAvatars = new HashMap<>();
        if (!isNullOrEmpty(playlistUploaderUrl)
                && !isNullOrEmpty(playlistUploaderAvatarUrl)) {
            knownAvatars.put(playlistUploaderUrl, playlistUploaderAvatarUrl);
        }

        for (final StreamInfoItem item : items) {
            if (!isNullOrEmpty(item.getUploaderUrl())
                    && !isNullOrEmpty(item.getUploaderAvatarUrl())) {
                knownAvatars.putIfAbsent(item.getUploaderUrl(), item.getUploaderAvatarUrl());
            }
        }

        for (final Map.Entry<String, String> entry : knownAvatars.entrySet()) {
            applyPlaylistItemUploaderAvatar(items, entry.getKey(), entry.getValue());
        }

        final Set<String> unresolvedUploaderUrls = new LinkedHashSet<>();
        for (final StreamInfoItem item : items) {
            if (isNullOrEmpty(item.getUploaderAvatarUrl())
                    && !isNullOrEmpty(item.getUploaderUrl())) {
                unresolvedUploaderUrls.add(item.getUploaderUrl());
                if (unresolvedUploaderUrls.size() > 1) {
                    return null;
                }
            }
        }
        return unresolvedUploaderUrls.stream().findFirst().orElse(null);
    }

    static void applyPlaylistItemUploaderAvatar(
            final List<StreamInfoItem> items,
            @Nullable final String uploaderUrl,
            @Nullable final String uploaderAvatarUrl) {
        if (isNullOrEmpty(uploaderUrl) || isNullOrEmpty(uploaderAvatarUrl)) {
            return;
        }

        for (final StreamInfoItem item : items) {
            if (uploaderUrl.equals(item.getUploaderUrl())
                    && isNullOrEmpty(item.getUploaderAvatarUrl())) {
                item.setUploaderAvatarUrl(uploaderAvatarUrl);
            }
        }
    }

    public static Single<InfoItemsPage<StreamInfoItem>> getMorePlaylistItems(final int serviceId,
                                                                             final String url,
                                                                             final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            final InfoItemsPage<StreamInfoItem> page = PlaylistInfo.getMoreItems(
                    NewPipe.getService(serviceId), url, nextPage);
            backfillYouTubePlaylistItemUploaderAvatars(
                    serviceId, page.getItems(), null, null);
            return page;
        });
    }

    public static Single<KioskInfo> getKioskInfo(final int serviceId,
                                                 final String url,
                                                 final boolean forceLoad) {
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.KIOSK,
                Single.fromCallable(() -> KioskInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage<StreamInfoItem>> getMoreKioskItems(final int serviceId,
                                                                          final String url,
                                                                          final Page nextPage) {
        return Single.fromCallable(() ->
                KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Cache
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
     * @param cacheType       the {@link InfoCache.Type} of the item
     * @param loadFromNetwork the {@link Single} to load the item from the network
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Single<I> checkCache(final boolean forceLoad,
                                                         final int serviceId,
                                                         @NonNull final String url,
                                                         @NonNull final InfoCache.Type cacheType,
                                                         @NonNull final Single<I> loadFromNetwork) {
        checkServiceId(serviceId);
        final Single<I> actualLoadFromNetwork = loadFromNetwork
                .doOnSuccess(info -> CACHE.putInfo(serviceId, url, info, cacheType));

        final Single<I> load;
        if (forceLoad) {
            CACHE.removeInfo(serviceId, url, cacheType);
            load = actualLoadFromNetwork;
        } else {
            load = Maybe.concat(ExtractorHelper.loadFromCache(serviceId, url, cacheType),
                            actualLoadFromNetwork.toMaybe())
                    .firstElement() // Take the first valid
                    .toSingle();
        }

        return load;
    }

    /**
     * Default implementation uses the {@link InfoCache} to get cached results.
     *
     * @param <I>       the item type's class that extends {@link Info}
     * @param serviceId the service to load from
     * @param url       the URL to load
     * @param cacheType the {@link InfoCache.Type} of the item
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Maybe<I> loadFromCache(
            final int serviceId,
            @NonNull final String url,
            @NonNull final InfoCache.Type cacheType) {
        checkServiceId(serviceId);
        return Maybe.defer(() -> {
            //noinspection unchecked
            final I info = (I) CACHE.getFromKey(serviceId, url, cacheType);
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

    public static boolean isCached(final int serviceId,
                                   @NonNull final String url,
                                   @NonNull final InfoCache.Type cacheType) {
        return null != loadFromCache(serviceId, url, cacheType).blockingGet();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

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
            TextLinkifier.fromHtml(metaInfoTextView, stringBuilder.toString(),
                    HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING, null, null, disposables,
                    SET_LINK_MOVEMENT_METHOD);
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
