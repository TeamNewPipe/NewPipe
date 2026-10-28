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

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.util.text.TextLinkifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class ExtractorHelper {
    private static final String TOURNESOL_KIOSK_ID = "Tournesol";
    private static final String TOURNESOL_KIOSK_ID_LOWER = "tournesol";
    private static final String TOURNESOL_EXTRACTOR_CLASS =
            "org.schabi.newpipe.extractor.services.youtube.extractors.kiosk."
                    + "TournesolKioskExtractor";
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
        final List<String> effectiveContentFilter =
                enrichTournesolSearchFilters(contentFilter);
        return Single.fromCallable(() ->
                SearchInfo.getInfo(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, effectiveContentFilter, sortFilter)));
    }

    public static Single<InfoItemsPage<InfoItem>> getMoreSearchItems(
            final int serviceId,
            final String searchString,
            final List<String> contentFilter,
            final String sortFilter,
            final Page page) {
        checkServiceId(serviceId);
        final List<String> effectiveContentFilter =
                enrichTournesolSearchFilters(contentFilter);
        return Single.fromCallable(() ->
                SearchInfo.getMoreItems(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, effectiveContentFilter, sortFilter),
                                page));

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
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.STREAM,
                Single.fromCallable(() -> StreamInfo.getInfo(NewPipe.getService(serviceId), url)));
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
        return Single.fromCallable(() ->
                ChannelTabInfo.getMoreItems(NewPipe.getService(serviceId),
                        listLinkHandler, nextPage));
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId,
                                                       final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.PLAYLIST,
                Single.fromCallable(() ->
                        PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage<StreamInfoItem>> getMorePlaylistItems(final int serviceId,
                                                                             final String url,
                                                                             final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    public static Single<KioskInfo> getKioskInfo(final int serviceId,
                                                 final String url,
                                                 final boolean forceLoad) {
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.KIOSK,
                Single.fromCallable(() -> {
                    // Tournesol logic is now handled by the tab selection or service selection
                    // If we are here, we are loading a standard kiosk or Tournesol if selected.

                    // Check if the service is Tournesol or if the URL/Kiosk ID implies Tournesol.
                    // However, Tournesol isn't a separate service ID in NewPipe usually,
                    // it might be an extractor override.

                    // Since we removed the setting check that forced Tournesol, we should ensure
                    // that if the user explicitly selected Tournesol tab (if it exists), it works.
                    // But if Tournesol was just a "recommendation algorithm" that replaced
                    // "Trending", and we want it to be a tab, we need to ensure there is a way
                    // to get Tournesol content.

                    // If Tournesol is implemented as a Kiosk, it should be in the Kiosk list.
                    // If it was just a hack in ExtractorHelper, we might need to expose it as
                    // a Kiosk or keep a check here if the "url" requested matches a Tournesol
                    // identifier.

                    // Special handling for Tournesol request
                    if (isTournesolUrl(url)) {
                        final KioskExtractor extractor = getTournesolExtractor(serviceId);
                        configureTournesolExtractor(extractor, url);
                        extractor.fetchPage();
                        return KioskInfo.getInfo(extractor);
                    }

                    return KioskInfo.getInfo(NewPipe.getService(serviceId), url);
                }));
    }

    private static void configureTournesolExtractor(final KioskExtractor extractor,
                                                    final String url) {
        if (!isTournesolExtractor(extractor)) {
            return;
        }

        invokeSetDateGte(extractor, null);
        invokeSetIncludeUnsafe(extractor, PreferenceManager
                .getDefaultSharedPreferences(App.getInstance())
                .getBoolean(TournesolHelper.PREF_TOURNESOL_FILTER_INCLUDE_LOW_SCORE,
                        TournesolHelper.DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE));
        if (url.contains("?")) {
            final String query = url.substring(url.indexOf("?") + 1);
            final String[] pairs = query.split("&");
            for (final String pair : pairs) {
                final String[] parts = pair.split("=", -1);
                if (parts.length == 2) {
                    final String value = decodeQueryValue(parts[1]);
                    if ("languages".equals(parts[0])) {
                        if (value != null && !value.isEmpty()) {
                            invokeSetLanguages(extractor, Arrays.asList(value.split(",")));
                        } else {
                            invokeSetLanguages(extractor, Collections.emptyList());
                        }
                    } else if ("date_gte".equals(parts[0])) {
                        invokeSetDateGte(extractor, value);
                    } else if ("uploader".equals(parts[0])) {
                        invokeSetUploader(extractor, value);
                    } else if ("unsafe".equals(parts[0])) {
                        invokeSetIncludeUnsafe(extractor, "true".equalsIgnoreCase(value));
                    } else if ("duration_gte".equals(parts[0])) {
                        invokeSetInt(extractor, "setDurationGte", parseIntOrDefault(value, -1));
                    } else if ("duration_lte".equals(parts[0])) {
                        invokeSetInt(extractor, "setDurationLte", parseIntOrDefault(value, -1));
                    } else if ("weight_largely_recommended".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightLargelyRecommended",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_reliability".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightReliability",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_importance".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightImportance",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_pedagogy".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightPedagogy",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_layman_friendly".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightLaymanFriendly",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_entertaining_relaxing".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightEntertainingRelaxing",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_engaging".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightEngaging",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_diversity_inclusion".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightDiversityInclusion",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_better_habits".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightBetterHabits",
                                parseIntOrDefault(value, -1));
                    } else if ("weight_backfire_risk".equals(parts[0])) {
                        invokeSetInt(extractor,
                                "setWeightBackfireRisk",
                                parseIntOrDefault(value, -1));
                    }
                }
            }
        }
    }

    private static boolean isTournesolExtractor(final KioskExtractor extractor) {
        if (extractor == null) {
            return false;
        }
        return TOURNESOL_EXTRACTOR_CLASS.equals(extractor.getClass().getName());
    }

    private static boolean isTournesolUrl(@Nullable final String url) {
        if (url == null) {
            return false;
        }
        return url.regionMatches(true, 0, TOURNESOL_KIOSK_ID_LOWER, 0,
                TOURNESOL_KIOSK_ID_LOWER.length());
    }

    private static KioskExtractor getTournesolExtractor(final int serviceId)
            throws ExtractionException, IOException {
        final KioskList kioskList = NewPipe.getService(serviceId).getKioskList();
        try {
            return kioskList.getExtractorById(TOURNESOL_KIOSK_ID, null);
        } catch (final ExtractionException e) {
            return kioskList.getExtractorById(TOURNESOL_KIOSK_ID_LOWER, null);
        }
    }

    private static void invokeSetDateGte(final KioskExtractor extractor, final String value) {
        try {
            extractor.getClass()
                    .getMethod("setDateGte", String.class)
                    .invoke(extractor, value);
        } catch (final ReflectiveOperationException ignored) {
            // Method not available in this extractor version.
        }
    }

    private static void invokeSetLanguages(final KioskExtractor extractor,
                                           final List<String> languages) {
        try {
            extractor.getClass()
                    .getMethod("setLanguages", List.class)
                    .invoke(extractor, languages);
        } catch (final ReflectiveOperationException ignored) {
            // Method not available in this extractor version.
        }
    }

    private static void invokeSetUploader(final KioskExtractor extractor,
                                          @Nullable final String uploader) {
        try {
            extractor.getClass()
                    .getMethod("setUploader", String.class)
                    .invoke(extractor, uploader);
        } catch (final ReflectiveOperationException ignored) {
            // Method not available in this extractor version.
        }
    }

    private static void invokeSetIncludeUnsafe(final KioskExtractor extractor,
                                               final boolean includeUnsafe) {
        try {
            extractor.getClass()
                    .getMethod("setIncludeUnsafe", boolean.class)
                    .invoke(extractor, includeUnsafe);
        } catch (final ReflectiveOperationException ignored) {
            // Method not available in this extractor version.
        }
    }

    private static void invokeSetInt(final KioskExtractor extractor,
                                     final String methodName, final int value) {
        try {
            extractor.getClass()
                    .getMethod(methodName, int.class)
                    .invoke(extractor, value);
        } catch (final ReflectiveOperationException ignored) {
            // Method not available in this extractor version.
        }
    }

    private static int parseIntOrDefault(final String value, final int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    @NonNull
    private static List<String> enrichTournesolSearchFilters(
            @NonNull final List<String> contentFilters) {
        final boolean isTournesolSearch = !contentFilters.isEmpty()
                && YoutubeSearchQueryHandlerFactory.TOURNESOL.equals(contentFilters.get(0));
        if (!isTournesolSearch) {
            return contentFilters;
        }

        final boolean includeLowScoreVideos = PreferenceManager
                .getDefaultSharedPreferences(App.getInstance())
                .getBoolean(TournesolHelper.PREF_TOURNESOL_FILTER_INCLUDE_LOW_SCORE,
                        TournesolHelper.DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE);
        if (!includeLowScoreVideos
                || contentFilters.contains(YoutubeSearchQueryHandlerFactory.TOURNESOL_UNSAFE)) {
            return contentFilters;
        }

        final List<String> enrichedFilters = new ArrayList<>(contentFilters);
        enrichedFilters.add(YoutubeSearchQueryHandlerFactory.TOURNESOL_UNSAFE);
        return enrichedFilters;
    }

    private static String decodeQueryValue(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(
                    value, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (final Exception ignored) {
            return value;
        }
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

                String content = metaInfo.getContent().content().trim();
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
