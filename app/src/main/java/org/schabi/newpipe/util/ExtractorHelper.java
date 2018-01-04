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

import android.util.Log;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.ListExtractor.NextItemsResult;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public final class ExtractorHelper {
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final InfoCache cache = InfoCache.getInstance();

    private ExtractorHelper() {
        //no instance
    }

    private static void checkServiceId(int serviceId) {
        if(serviceId == Constants.NO_SERVICE_ID) {
            throw new IllegalArgumentException("serviceId is NO_SERVICE_ID");
        }
    }

    public static Single<SearchResult> searchFor(final int serviceId, final String query, final int pageNumber, final String searchLanguage, final SearchEngine.Filter filter) {
        checkServiceId(serviceId);
        return Single.fromCallable(new Callable<SearchResult>() {
            @Override
            public SearchResult call() throws Exception {
                return SearchResult.getSearchResult(NewPipe.getService(serviceId).getSearchEngine(),
                        query, pageNumber, searchLanguage, filter);
            }
        });
    }

    public static Single<NextItemsResult> getMoreSearchItems(final int serviceId, final String query, final int nextPageNumber, final String searchLanguage, final SearchEngine.Filter filter) {
        checkServiceId(serviceId);
        return searchFor(serviceId, query, nextPageNumber, searchLanguage, filter)
                .map(new Function<SearchResult, NextItemsResult>() {
                    @Override
                    public NextItemsResult apply(@NonNull SearchResult searchResult) throws Exception {
                        return new NextItemsResult(searchResult.resultList, nextPageNumber + "", searchResult.errors);
                    }
                });
    }

    public static Single<List<String>> suggestionsFor(final int serviceId, final String query, final String searchLanguage) {
        checkServiceId(serviceId);
        return Single.fromCallable(new Callable<List<String>>() {
            @Override
            public List<String> call() throws Exception {
                return NewPipe.getService(serviceId).getSuggestionExtractor().suggestionList(query, searchLanguage);
            }
        });
    }

    public static Single<StreamInfo> getStreamInfo(final int serviceId, final String url, boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, Single.fromCallable(new Callable<StreamInfo>() {
            @Override
            public StreamInfo call() throws Exception {
                return StreamInfo.getInfo(NewPipe.getService(serviceId), url);
            }
        }));
    }

    public static Single<ChannelInfo> getChannelInfo(final int serviceId, final String url, boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, Single.fromCallable(new Callable<ChannelInfo>() {
            @Override
            public ChannelInfo call() throws Exception {
                return ChannelInfo.getInfo(NewPipe.getService(serviceId), url);
            }
        }));
    }

    public static Single<NextItemsResult> getMoreChannelItems(final int serviceId, final String url, final String nextStreamsUrl) {
        checkServiceId(serviceId);
        return Single.fromCallable(new Callable<NextItemsResult>() {
            @Override
            public NextItemsResult call() throws Exception {
                return ChannelInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl);
            }
        });
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId, final String url, boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, Single.fromCallable(new Callable<PlaylistInfo>() {
            @Override
            public PlaylistInfo call() throws Exception {
                return PlaylistInfo.getInfo(NewPipe.getService(serviceId), url);
            }
        }));
    }

    public static Single<NextItemsResult> getMorePlaylistItems(final int serviceId, final String url, final String nextStreamsUrl) {
        checkServiceId(serviceId);
        return Single.fromCallable(new Callable<NextItemsResult>() {
            @Override
            public NextItemsResult call() throws Exception {
                return PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl);
            }
        });
    }

    public static Single<KioskInfo> getKioskInfo(final int serviceId, final String url, final String contentCountry, boolean forceLoad) {
        return checkCache(forceLoad, serviceId, url, Single.fromCallable(new Callable<KioskInfo>() {
            @Override
            public KioskInfo call() throws Exception {
                return KioskInfo.getInfo(NewPipe.getService(serviceId), url, toUpperCase(contentCountry));
            }
        }));
    }

    public static Single<NextItemsResult> getMoreKioskItems(final int serviceId, final String url, final String nextStreamsUrl, final String contentCountry) {
        return Single.fromCallable(new Callable<NextItemsResult>() {
            @Override
            public NextItemsResult call() throws Exception {
                return KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl, toUpperCase(contentCountry));
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't, load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     */
    private static <I extends Info> Single<I> checkCache(boolean forceLoad, int serviceId, String url, Single<I> loadFromNetwork) {
        checkServiceId(serviceId);
        loadFromNetwork = loadFromNetwork.doOnSuccess(new Consumer<I>() {
            @Override
            public void accept(@NonNull I i) throws Exception {
                cache.putInfo(i);
            }
        });

        Single<I> load;
        if (forceLoad) {
            cache.removeInfo(serviceId, url);
            load = loadFromNetwork;
        } else {
            load = Maybe.concat(ExtractorHelper.<I>loadFromCache(serviceId, url), loadFromNetwork.toMaybe())
                    .firstElement() //Take the first valid
                    .toSingle();
        }

        return load;
    }

    /**
     * Default implementation uses the {@link InfoCache} to get cached results
     */
    public static <I extends Info> Maybe<I> loadFromCache(final int serviceId, final String url) {
        checkServiceId(serviceId);
        return Maybe.defer(new Callable<MaybeSource<? extends I>>() {
            @Override
            public MaybeSource<? extends I> call() throws Exception {
                //noinspection unchecked
                I info = (I) cache.getFromKey(serviceId, url);
                if (MainActivity.DEBUG) Log.d(TAG, "loadFromCache() called, info > " + info);

                // Only return info if it's not null (it is cached)
                if (info != null) {
                    return Maybe.just(info);
                }

                return Maybe.empty();
            }
        });
    }

    /**
     * Check if throwable have the cause that can be assignable from the causes to check.
     *
     * @see Class#isAssignableFrom(Class)
     */
    public static boolean hasAssignableCauseThrowable(Throwable throwable, Class<?>... causesToCheck) {
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
        return ExtractorHelper.hasExactCauseThrowable(throwable, InterruptedIOException.class, InterruptedException.class);
    }

    public static String toUpperCase(String value) {
        StringBuilder sb = new StringBuilder(value);
        for (int index = 0; index < sb.length(); index++) {
            char c = sb.charAt(index);
            if (Character.isLowerCase(c)) {
                sb.setCharAt(index, Character.toUpperCase(c));
            } else {
                sb.setCharAt(index, Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
