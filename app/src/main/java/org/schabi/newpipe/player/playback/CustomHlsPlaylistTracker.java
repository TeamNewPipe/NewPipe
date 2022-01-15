/*
 * Original source code (DefaultHlsPlaylistTracker): Copyright (C) 2016 The Android Open Source
 * Project
 *
 * Original source code licensed under the Apache License, Version 2.0 (the "License");
 * you may not use the original source code of this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.schabi.newpipe.player.playback;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.max;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.Variant;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Part;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.RenditionReport;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * NewPipe's implementation for {@link HlsPlaylistTracker}, based on
 * {@link DefaultHlsPlaylistTracker}.
 *
 * <p>
 * It redefines the way of how
 * {@link PlaylistStuckException PlaylistStuckExceptions} are thrown: instead of
 * using a multiplication between the target duration of segments and
 * {@link DefaultHlsPlaylistTracker#DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT}, it uses a
 * constant value (see {@link #MAXIMUM_PLAYLIST_STUCK_DURATION_MS}), in order to reduce the number
 * of this exception thrown, especially on (very) low-latency livestreams.
 * </p>
 */
public final class CustomHlsPlaylistTracker implements HlsPlaylistTracker,
        Loader.Callback<ParsingLoadable<HlsPlaylist>> {

    /**
     * Factory for {@link CustomHlsPlaylistTracker} instances.
     */
    public static final Factory FACTORY = CustomHlsPlaylistTracker::new;

    /**
     * The maximum duration before a {@link PlaylistStuckException} is thrown, in milliseconds.
     */
    private static final double MAXIMUM_PLAYLIST_STUCK_DURATION_MS = 15000;

    private final HlsDataSourceFactory dataSourceFactory;
    private final HlsPlaylistParserFactory playlistParserFactory;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final HashMap<Uri, MediaPlaylistBundle> playlistBundles;
    private final List<PlaylistEventListener> listeners;

    @Nullable
    private EventDispatcher eventDispatcher;
    @Nullable
    private Loader initialPlaylistLoader;
    @Nullable
    private Handler playlistRefreshHandler;
    @Nullable
    private PrimaryPlaylistListener primaryPlaylistListener;
    @Nullable
    private HlsMasterPlaylist masterPlaylist;
    @Nullable
    private Uri primaryMediaPlaylistUrl;
    @Nullable
    private HlsMediaPlaylist primaryMediaPlaylistSnapshot;
    private boolean isLive;
    private long initialStartTimeUs;

    /**
     * Creates an instance.
     *
     * @param dataSourceFactory       A factory for {@link DataSource} instances.
     * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
     * @param playlistParserFactory   An {@link HlsPlaylistParserFactory}.
     */
    public CustomHlsPlaylistTracker(final HlsDataSourceFactory dataSourceFactory,
                                    final LoadErrorHandlingPolicy loadErrorHandlingPolicy,
                                    final HlsPlaylistParserFactory playlistParserFactory) {
        this.dataSourceFactory = dataSourceFactory;
        this.playlistParserFactory = playlistParserFactory;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        listeners = new ArrayList<>();
        playlistBundles = new HashMap<>();
        initialStartTimeUs = C.TIME_UNSET;
    }

    // HlsPlaylistTracker implementation.

    @Override
    public void start(@NonNull final Uri initialPlaylistUri,
                      @NonNull final EventDispatcher eventDispatcherObject,
                      @NonNull final PrimaryPlaylistListener primaryPlaylistListenerObject) {
        this.playlistRefreshHandler = Util.createHandlerForCurrentLooper();
        this.eventDispatcher = eventDispatcherObject;
        this.primaryPlaylistListener = primaryPlaylistListenerObject;
        final ParsingLoadable<HlsPlaylist> masterPlaylistLoadable = new ParsingLoadable<>(
                dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST),
                initialPlaylistUri,
                C.DATA_TYPE_MANIFEST,
                playlistParserFactory.createPlaylistParser());
        Assertions.checkState(initialPlaylistLoader == null);
        initialPlaylistLoader = new Loader("CustomHlsPlaylistTracker:MasterPlaylist");
        final long elapsedRealtime = initialPlaylistLoader.startLoading(masterPlaylistLoadable,
                this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(
                        masterPlaylistLoadable.type));
        eventDispatcherObject.loadStarted(new LoadEventInfo(masterPlaylistLoadable.loadTaskId,
                        masterPlaylistLoadable.dataSpec, elapsedRealtime),
                masterPlaylistLoadable.type);
    }

    @Override
    public void stop() {
        primaryMediaPlaylistUrl = null;
        primaryMediaPlaylistSnapshot = null;
        masterPlaylist = null;
        initialStartTimeUs = C.TIME_UNSET;
        initialPlaylistLoader.release();
        initialPlaylistLoader = null;
        for (final MediaPlaylistBundle bundle : playlistBundles.values()) {
            bundle.release();
        }
        playlistRefreshHandler.removeCallbacksAndMessages(null);
        playlistRefreshHandler = null;
        playlistBundles.clear();
    }

    @Override
    public void addListener(@NonNull final PlaylistEventListener listener) {
        checkNotNull(listener);
        listeners.add(listener);
    }

    @Override
    public void removeListener(@NonNull final PlaylistEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    @Nullable
    public HlsMasterPlaylist getMasterPlaylist() {
        return masterPlaylist;
    }

    @Override
    @Nullable
    public HlsMediaPlaylist getPlaylistSnapshot(@NonNull final Uri url,
                                                final boolean isForPlayback) {
        final HlsMediaPlaylist snapshot = playlistBundles.get(url).getPlaylistSnapshot();
        if (snapshot != null && isForPlayback) {
            maybeSetPrimaryUrl(url);
        }
        return snapshot;
    }

    @Override
    public long getInitialStartTimeUs() {
        return initialStartTimeUs;
    }

    @Override
    public boolean isSnapshotValid(@NonNull final Uri url) {
        return playlistBundles.get(url).isSnapshotValid();
    }

    @Override
    public void maybeThrowPrimaryPlaylistRefreshError() throws IOException {
        if (initialPlaylistLoader != null) {
            initialPlaylistLoader.maybeThrowError();
        }
        if (primaryMediaPlaylistUrl != null) {
            maybeThrowPlaylistRefreshError(primaryMediaPlaylistUrl);
        }
    }

    @Override
    public void maybeThrowPlaylistRefreshError(@NonNull final Uri url) throws IOException {
        playlistBundles.get(url).maybeThrowPlaylistRefreshError();
    }

    @Override
    public void refreshPlaylist(@NonNull final Uri url) {
        playlistBundles.get(url).loadPlaylist();
    }

    @Override
    public boolean isLive() {
        return isLive;
    }

    // Loader.Callback implementation.

    @Override
    public void onLoadCompleted(@NonNull final ParsingLoadable<HlsPlaylist> loadable,
                                final long elapsedRealtimeMs,
                                final long loadDurationMs) {
        final HlsPlaylist result = loadable.getResult();
        final HlsMasterPlaylist newMasterPlaylist;
        final boolean isMediaPlaylist = result instanceof HlsMediaPlaylist;
        if (isMediaPlaylist) {
            newMasterPlaylist = HlsMasterPlaylist.createSingleVariantMasterPlaylist(
                    result.baseUri);
        } else { // result instanceof HlsMasterPlaylist
            newMasterPlaylist = (HlsMasterPlaylist) result;
        }
        this.masterPlaylist = newMasterPlaylist;
        primaryMediaPlaylistUrl = newMasterPlaylist.variants.get(0).url;
        createBundles(newMasterPlaylist.mediaPlaylistUrls);
        final LoadEventInfo loadEventInfo = new LoadEventInfo(loadable.loadTaskId,
                loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(),
                elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
        final MediaPlaylistBundle primaryBundle = playlistBundles.get(primaryMediaPlaylistUrl);
        if (isMediaPlaylist) {
            // We don't need to load the playlist again. We can use the same result.
            primaryBundle.processLoadedPlaylist((HlsMediaPlaylist) result, loadEventInfo);
        } else {
            primaryBundle.loadPlaylist();
        }
        loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
        eventDispatcher.loadCompleted(loadEventInfo, C.DATA_TYPE_MANIFEST);
    }

    @Override
    public void onLoadCanceled(@NonNull final ParsingLoadable<HlsPlaylist> loadable,
                               final long elapsedRealtimeMs,
                               final long loadDurationMs,
                               final boolean released) {
        final LoadEventInfo loadEventInfo = new LoadEventInfo(loadable.loadTaskId,
                loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(),
                elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
        loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
        eventDispatcher.loadCanceled(loadEventInfo, C.DATA_TYPE_MANIFEST);
    }

    @Override
    public LoadErrorAction onLoadError(@NonNull final ParsingLoadable<HlsPlaylist> loadable,
                                       final long elapsedRealtimeMs,
                                       final long loadDurationMs,
                                       final IOException error,
                                       final int errorCount) {
        final LoadEventInfo loadEventInfo = new LoadEventInfo(loadable.loadTaskId,
                loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(),
                elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
        final MediaLoadData mediaLoadData = new MediaLoadData(loadable.type);
        final long retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(new LoadErrorInfo(
                loadEventInfo, mediaLoadData, error, errorCount));
        final boolean isFatal = retryDelayMs == C.TIME_UNSET;
        eventDispatcher.loadError(loadEventInfo, loadable.type, error, isFatal);
        if (isFatal) {
            loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
        }
        return isFatal ? Loader.DONT_RETRY_FATAL : Loader.createRetryAction(false, retryDelayMs);
    }

    // Internal methods.

    private boolean maybeSelectNewPrimaryUrl() {
        final List<Variant> variants = masterPlaylist.variants;
        final int variantsSize = variants.size();
        final long currentTimeMs = SystemClock.elapsedRealtime();
        for (int i = 0; i < variantsSize; i++) {
            final MediaPlaylistBundle bundle = checkNotNull(playlistBundles.get(
                    variants.get(i).url));
            if (currentTimeMs > bundle.excludeUntilMs) {
                primaryMediaPlaylistUrl = bundle.playlistUrl;
                bundle.loadPlaylistInternal(getRequestUriForPrimaryChange(
                        primaryMediaPlaylistUrl));
                return true;
            }
        }
        return false;
    }

    private void maybeSetPrimaryUrl(@NonNull final Uri url) {
        if (url.equals(primaryMediaPlaylistUrl) || !isVariantUrl(url)
                || (primaryMediaPlaylistSnapshot != null
                && primaryMediaPlaylistSnapshot.hasEndTag)) {
            // Ignore if the primary media playlist URL is unchanged, if the media playlist is not
            // referenced directly by a variant, or it the last primary snapshot contains an end
            // tag.
            return;
        }
        primaryMediaPlaylistUrl = url;
        final MediaPlaylistBundle newPrimaryBundle = playlistBundles.get(primaryMediaPlaylistUrl);
        final HlsMediaPlaylist newPrimarySnapshot = newPrimaryBundle.playlistSnapshot;
        if (newPrimarySnapshot != null && newPrimarySnapshot.hasEndTag) {
            primaryMediaPlaylistSnapshot = newPrimarySnapshot;
            primaryPlaylistListener.onPrimaryPlaylistRefreshed(newPrimarySnapshot);
        } else {
            // The snapshot for the new primary media playlist URL may be stale. Defer updating the
            // primary snapshot until after we've refreshed it.
            newPrimaryBundle.loadPlaylistInternal(getRequestUriForPrimaryChange(url));
        }
    }

    private Uri getRequestUriForPrimaryChange(@NonNull final Uri newPrimaryPlaylistUri) {
        if (primaryMediaPlaylistSnapshot != null
                && primaryMediaPlaylistSnapshot.serverControl.canBlockReload) {
            final RenditionReport renditionReport = primaryMediaPlaylistSnapshot.renditionReports
                    .get(newPrimaryPlaylistUri);
            if (renditionReport != null) {
                final Uri.Builder uriBuilder = newPrimaryPlaylistUri.buildUpon();
                uriBuilder.appendQueryParameter(MediaPlaylistBundle.BLOCK_MSN_PARAM,
                        String.valueOf(renditionReport.lastMediaSequence));
                if (renditionReport.lastPartIndex != C.INDEX_UNSET) {
                    uriBuilder.appendQueryParameter(MediaPlaylistBundle.BLOCK_PART_PARAM,
                            String.valueOf(renditionReport.lastPartIndex));
                }
                return uriBuilder.build();
            }
        }
        return newPrimaryPlaylistUri;
    }

    /**
     * @return whether any of the variants in the master playlist have the specified playlist URL.
     * @param playlistUrl the playlist URL to test
     */
    private boolean isVariantUrl(final Uri playlistUrl) {
        final List<Variant> variants = masterPlaylist.variants;
        final int variantsSize = variants.size();
        for (int i = 0; i < variantsSize; i++) {
            if (playlistUrl.equals(variants.get(i).url)) {
                return true;
            }
        }
        return false;
    }

    private void createBundles(@NonNull final List<Uri> urls) {
        final int listSize = urls.size();
        for (int i = 0; i < listSize; i++) {
            final Uri url = urls.get(i);
            final MediaPlaylistBundle bundle = new MediaPlaylistBundle(url);
            playlistBundles.put(url, bundle);
        }
    }

    /**
     * Called by the bundles when a snapshot changes.
     *
     * @param url         The url of the playlist.
     * @param newSnapshot The new snapshot.
     */
    private void onPlaylistUpdated(@NonNull final Uri url, final HlsMediaPlaylist newSnapshot) {
        if (url.equals(primaryMediaPlaylistUrl)) {
            if (primaryMediaPlaylistSnapshot == null) {
                // This is the first primary URL snapshot.
                isLive = !newSnapshot.hasEndTag;
                initialStartTimeUs = newSnapshot.startTimeUs;
            }
            primaryMediaPlaylistSnapshot = newSnapshot;
            primaryPlaylistListener.onPrimaryPlaylistRefreshed(newSnapshot);
        }
        final int listenersSize = listeners.size();
        for (int i = 0; i < listenersSize; i++) {
            listeners.get(i).onPlaylistChanged();
        }
    }

    private boolean notifyPlaylistError(final Uri playlistUrl, final long exclusionDurationMs) {
        final int listenersSize = listeners.size();
        boolean anyExclusionFailed = false;
        for (int i = 0; i < listenersSize; i++) {
            anyExclusionFailed |= !listeners.get(i).onPlaylistError(playlistUrl,
                    exclusionDurationMs);
        }
        return anyExclusionFailed;
    }

    @SuppressWarnings("squid:S2259")
    private HlsMediaPlaylist getLatestPlaylistSnapshot(
            @Nullable final HlsMediaPlaylist oldPlaylist,
            @NonNull final HlsMediaPlaylist loadedPlaylist) {
        if (!loadedPlaylist.isNewerThan(oldPlaylist)) {
            if (loadedPlaylist.hasEndTag) {
                // If the loaded playlist has an end tag but is not newer than the old playlist
                // then we have an inconsistent state. This is typically caused by the server
                // incorrectly resetting the media sequence when appending the end tag. We resolve
                // this case as best we can by returning the old playlist with the end tag
                // appended.
                return oldPlaylist.copyWithEndTag();
            } else {
                return oldPlaylist;
            }
        }
        final long startTimeUs = getLoadedPlaylistStartTimeUs(oldPlaylist, loadedPlaylist);
        final int discontinuitySequence = getLoadedPlaylistDiscontinuitySequence(oldPlaylist,
                loadedPlaylist);
        return loadedPlaylist.copyWith(startTimeUs, discontinuitySequence);
    }

    private long getLoadedPlaylistStartTimeUs(@Nullable final HlsMediaPlaylist oldPlaylist,
                                              @NonNull final HlsMediaPlaylist loadedPlaylist) {
        if (loadedPlaylist.hasProgramDateTime) {
            return loadedPlaylist.startTimeUs;
        }
        final long primarySnapshotStartTimeUs = primaryMediaPlaylistSnapshot != null
                ? primaryMediaPlaylistSnapshot.startTimeUs : 0;
        if (oldPlaylist == null) {
            return primarySnapshotStartTimeUs;
        }
        final Segment firstOldOverlappingSegment = getFirstOldOverlappingSegment(oldPlaylist,
                loadedPlaylist);
        if (firstOldOverlappingSegment != null) {
            return oldPlaylist.startTimeUs + firstOldOverlappingSegment.relativeStartTimeUs;
        } else if (oldPlaylist.segments.size() == loadedPlaylist.mediaSequence
                - oldPlaylist.mediaSequence) {
            return oldPlaylist.getEndTimeUs();
        } else {
            // No segments overlap, we assume the new playlist start coincides with the primary
            // playlist.
            return primarySnapshotStartTimeUs;
        }
    }

    private int getLoadedPlaylistDiscontinuitySequence(
            @Nullable final HlsMediaPlaylist oldPlaylist,
            @NonNull final HlsMediaPlaylist loadedPlaylist) {
        if (loadedPlaylist.hasDiscontinuitySequence) {
            return loadedPlaylist.discontinuitySequence;
        }
        // TODO: Improve cross-playlist discontinuity adjustment.
        final int primaryUrlDiscontinuitySequence = primaryMediaPlaylistSnapshot != null
                ? primaryMediaPlaylistSnapshot.discontinuitySequence : 0;
        if (oldPlaylist == null) {
            return primaryUrlDiscontinuitySequence;
        }
        final Segment firstOldOverlappingSegment = getFirstOldOverlappingSegment(oldPlaylist,
                loadedPlaylist);
        if (firstOldOverlappingSegment != null) {
            return oldPlaylist.discontinuitySequence
                    + firstOldOverlappingSegment.relativeDiscontinuitySequence
                    - loadedPlaylist.segments.get(0).relativeDiscontinuitySequence;
        }
        return primaryUrlDiscontinuitySequence;
    }

    @Nullable
    private static Segment getFirstOldOverlappingSegment(
            @NonNull final HlsMediaPlaylist oldPlaylist,
            @NonNull final HlsMediaPlaylist loadedPlaylist) {
        final int mediaSequenceOffset = (int) (loadedPlaylist.mediaSequence
                - oldPlaylist.mediaSequence);
        final List<Segment> oldSegments = oldPlaylist.segments;
        return mediaSequenceOffset < oldSegments.size() ? oldSegments.get(mediaSequenceOffset)
                : null;
    }

    /**
     * Hold all information related to a specific Media Playlist.
     */
    private final class MediaPlaylistBundle
            implements Loader.Callback<ParsingLoadable<HlsPlaylist>> {

        private static final String BLOCK_MSN_PARAM = "_HLS_msn";
        private static final String BLOCK_PART_PARAM = "_HLS_part";
        private static final String SKIP_PARAM = "_HLS_skip";

        private final Uri playlistUrl;
        private final Loader mediaPlaylistLoader;
        private final DataSource mediaPlaylistDataSource;

        @Nullable
        private HlsMediaPlaylist playlistSnapshot;
        private long lastSnapshotLoadMs;
        private long lastSnapshotChangeMs;
        private long earliestNextLoadTimeMs;
        private long excludeUntilMs;
        private boolean loadPending;
        @Nullable
        private IOException playlistError;

        MediaPlaylistBundle(final Uri playlistUrl) {
            this.playlistUrl = playlistUrl;
            mediaPlaylistLoader = new Loader("CustomHlsPlaylistTracker:MediaPlaylist");
            mediaPlaylistDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST);
        }

        @Nullable
        public HlsMediaPlaylist getPlaylistSnapshot() {
            return playlistSnapshot;
        }

        public boolean isSnapshotValid() {
            if (playlistSnapshot == null) {
                return false;
            }
            final long currentTimeMs = SystemClock.elapsedRealtime();
            final long snapshotValidityDurationMs = max(30000, C.usToMs(
                    playlistSnapshot.durationUs));
            return playlistSnapshot.hasEndTag
                    || playlistSnapshot.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
                    || playlistSnapshot.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
                    || lastSnapshotLoadMs + snapshotValidityDurationMs > currentTimeMs;
        }

        public void loadPlaylist() {
            loadPlaylistInternal(playlistUrl);
        }

        public void maybeThrowPlaylistRefreshError() throws IOException {
            mediaPlaylistLoader.maybeThrowError();
            if (playlistError != null) {
                throw playlistError;
            }
        }

        public void release() {
            mediaPlaylistLoader.release();
        }

        // Loader.Callback implementation.

        @Override
        public void onLoadCompleted(@NonNull final ParsingLoadable<HlsPlaylist> loadable,
                                    final long elapsedRealtimeMs,
                                    final long loadDurationMs) {
            final HlsPlaylist result = loadable.getResult();
            final LoadEventInfo loadEventInfo = new LoadEventInfo(loadable.loadTaskId,
                    loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(),
                    elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
            if (result instanceof HlsMediaPlaylist) {
                processLoadedPlaylist((HlsMediaPlaylist) result, loadEventInfo);
                eventDispatcher.loadCompleted(loadEventInfo, C.DATA_TYPE_MANIFEST);
            } else {
                playlistError = new ParserException("Loaded playlist has unexpected type.");
                eventDispatcher.loadError(
                        loadEventInfo, C.DATA_TYPE_MANIFEST, playlistError, true);
            }
            loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
        }

        @Override
        public void onLoadCanceled(@NonNull final ParsingLoadable<HlsPlaylist> loadable,
                                   final long elapsedRealtimeMs,
                                   final long loadDurationMs,
                                   final boolean released) {
            final LoadEventInfo loadEventInfo = new LoadEventInfo(loadable.loadTaskId,
                    loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(),
                    elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
            loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
            eventDispatcher.loadCanceled(loadEventInfo, C.DATA_TYPE_MANIFEST);
        }

        @Override
        public LoadErrorAction onLoadError(@NonNull final ParsingLoadable<HlsPlaylist> loadable,
                                           final long elapsedRealtimeMs,
                                           final long loadDurationMs,
                                           final IOException error,
                                           final int errorCount) {
            final LoadEventInfo loadEventInfo = new LoadEventInfo(loadable.loadTaskId,
                    loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(),
                    elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
            final boolean isBlockingRequest = loadable.getUri().getQueryParameter(BLOCK_MSN_PARAM)
                    != null;
            final boolean deltaUpdateFailed = error instanceof HlsPlaylistParser
                    .DeltaUpdateException;
            if (isBlockingRequest || deltaUpdateFailed) {
                int responseCode = Integer.MAX_VALUE;
                if (error instanceof HttpDataSource.InvalidResponseCodeException) {
                    responseCode = ((HttpDataSource.InvalidResponseCodeException) error)
                            .responseCode;
                }
                if (deltaUpdateFailed || responseCode == 400 || responseCode == 503) {
                    // Intercept failed delta updates and blocking requests producing a Bad Request
                    // (400) and Service Unavailable (503). In such cases, force a full,
                    // non-blocking request (see RFC 8216, section 6.2.5.2 and 6.3.7).
                    earliestNextLoadTimeMs = SystemClock.elapsedRealtime();
                    loadPlaylist();
                    castNonNull(eventDispatcher).loadError(loadEventInfo, loadable.type, error,
                            true);
                    return Loader.DONT_RETRY;
                }
            }
            final MediaLoadData mediaLoadData = new MediaLoadData(loadable.type);
            final LoadErrorInfo loadErrorInfo = new LoadErrorInfo(loadEventInfo, mediaLoadData,
                    error, errorCount);
            final LoadErrorAction loadErrorAction;
            final long exclusionDurationMs = loadErrorHandlingPolicy.getBlacklistDurationMsFor(
                    loadErrorInfo);
            final boolean shouldExclude = exclusionDurationMs != C.TIME_UNSET;

            boolean exclusionFailed = notifyPlaylistError(playlistUrl, exclusionDurationMs)
                    || !shouldExclude;
            if (shouldExclude) {
                exclusionFailed |= excludePlaylist(exclusionDurationMs);
            }

            if (exclusionFailed) {
                final long retryDelay = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
                loadErrorAction = retryDelay != C.TIME_UNSET
                        ? Loader.createRetryAction(false, retryDelay)
                        : Loader.DONT_RETRY_FATAL;
            } else {
                loadErrorAction = Loader.DONT_RETRY;
            }

            final boolean wasCanceled = !loadErrorAction.isRetry();
            eventDispatcher.loadError(loadEventInfo, loadable.type, error, wasCanceled);
            if (wasCanceled) {
                loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
            }
            return loadErrorAction;
        }

        // Internal methods.

        private void loadPlaylistInternal(@NonNull final Uri playlistRequestUri) {
            excludeUntilMs = 0;
            if (loadPending || mediaPlaylistLoader.isLoading()
                    || mediaPlaylistLoader.hasFatalError()) {
                // Load already pending, in progress, or a fatal error has been encountered. Do
                // nothing.
                return;
            }
            final long currentTimeMs = SystemClock.elapsedRealtime();
            if (currentTimeMs < earliestNextLoadTimeMs) {
                loadPending = true;
                playlistRefreshHandler.postDelayed(
                        () -> {
                            loadPending = false;
                            loadPlaylistImmediately(playlistRequestUri);
                        },
                        earliestNextLoadTimeMs - currentTimeMs);
            } else {
                loadPlaylistImmediately(playlistRequestUri);
            }
        }

        private void loadPlaylistImmediately(@NonNull final Uri playlistRequestUri) {
            final ParsingLoadable.Parser<HlsPlaylist> mediaPlaylistParser = playlistParserFactory
                    .createPlaylistParser(masterPlaylist, playlistSnapshot);
            final ParsingLoadable<HlsPlaylist> mediaPlaylistLoadable = new ParsingLoadable<>(
                    mediaPlaylistDataSource, playlistRequestUri, C.DATA_TYPE_MANIFEST,
                    mediaPlaylistParser);
            final long elapsedRealtime = mediaPlaylistLoader.startLoading(mediaPlaylistLoadable,
                    this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(
                            mediaPlaylistLoadable.type));
            eventDispatcher.loadStarted(new LoadEventInfo(mediaPlaylistLoadable.loadTaskId,
                            mediaPlaylistLoadable.dataSpec, elapsedRealtime),
                    mediaPlaylistLoadable.type);
        }

        @SuppressWarnings("squid:S2259")
        private void processLoadedPlaylist(final HlsMediaPlaylist loadedPlaylist,
                                           final LoadEventInfo loadEventInfo) {
            final HlsMediaPlaylist oldPlaylist = playlistSnapshot;
            final long currentTimeMs = SystemClock.elapsedRealtime();
            lastSnapshotLoadMs = currentTimeMs;
            playlistSnapshot = getLatestPlaylistSnapshot(oldPlaylist, loadedPlaylist);
            if (playlistSnapshot != oldPlaylist) {
                playlistError = null;
                lastSnapshotChangeMs = currentTimeMs;
                onPlaylistUpdated(playlistUrl, playlistSnapshot);
            } else if (!playlistSnapshot.hasEndTag) {
                if (loadedPlaylist.mediaSequence + loadedPlaylist.segments.size()
                        < playlistSnapshot.mediaSequence) {
                    // TODO: Allow customization of playlist resets handling.
                    // The media sequence jumped backwards. The server has probably reset. We do
                    // not try excluding in this case.
                    playlistError = new PlaylistResetException(playlistUrl);
                    notifyPlaylistError(playlistUrl, C.TIME_UNSET);
                } else if (currentTimeMs - lastSnapshotChangeMs
                        > MAXIMUM_PLAYLIST_STUCK_DURATION_MS) {
                    // TODO: Allow customization of stuck playlists handling.
                    playlistError = new PlaylistStuckException(playlistUrl);
                    final LoadErrorInfo loadErrorInfo = new LoadErrorInfo(loadEventInfo,
                            new MediaLoadData(C.DATA_TYPE_MANIFEST),
                            playlistError, 1);
                    final long exclusionDurationMs = loadErrorHandlingPolicy
                            .getBlacklistDurationMsFor(loadErrorInfo);
                    notifyPlaylistError(playlistUrl, exclusionDurationMs);
                    if (exclusionDurationMs != C.TIME_UNSET) {
                        excludePlaylist(exclusionDurationMs);
                    }
                }
            }
            long durationUntilNextLoadUs = 0L;
            if (!playlistSnapshot.serverControl.canBlockReload) {
                // If blocking requests are not supported, do not allow the playlist to load again
                // within the target duration if we obtained a new snapshot, or half the target
                // duration otherwise.
                durationUntilNextLoadUs = playlistSnapshot != oldPlaylist
                        ? playlistSnapshot.targetDurationUs
                        : (playlistSnapshot.targetDurationUs / 2);
            }
            earliestNextLoadTimeMs = currentTimeMs + C.usToMs(durationUntilNextLoadUs);
            // Schedule a load if this is the primary playlist or a playlist of a low-latency
            // stream and it doesn't have an end tag. Else the next load will be scheduled when
            // refreshPlaylist is called, or when this playlist becomes the primary.
            final boolean scheduleLoad = playlistSnapshot.partTargetDurationUs != C.TIME_UNSET
                    || playlistUrl.equals(primaryMediaPlaylistUrl);
            if (scheduleLoad && !playlistSnapshot.hasEndTag) {
                loadPlaylistInternal(getMediaPlaylistUriForReload());
            }
        }

        private Uri getMediaPlaylistUriForReload() {
            if (playlistSnapshot == null
                    || (playlistSnapshot.serverControl.skipUntilUs == C.TIME_UNSET
                    && !playlistSnapshot.serverControl.canBlockReload)) {
                return playlistUrl;
            }
            final Uri.Builder uriBuilder = playlistUrl.buildUpon();
            if (playlistSnapshot.serverControl.canBlockReload) {
                final long targetMediaSequence = playlistSnapshot.mediaSequence
                        + playlistSnapshot.segments.size();
                uriBuilder.appendQueryParameter(BLOCK_MSN_PARAM, String.valueOf(
                        targetMediaSequence));
                if (playlistSnapshot.partTargetDurationUs != C.TIME_UNSET) {
                    final List<Part> trailingParts = playlistSnapshot.trailingParts;
                    int targetPartIndex = trailingParts.size();
                    if (!trailingParts.isEmpty() && Iterables.getLast(trailingParts).isPreload) {
                        // Ignore the preload part.
                        targetPartIndex--;
                    }
                    uriBuilder.appendQueryParameter(BLOCK_PART_PARAM, String.valueOf(
                            targetPartIndex));
                }
            }
            if (playlistSnapshot.serverControl.skipUntilUs != C.TIME_UNSET) {
                uriBuilder.appendQueryParameter(SKIP_PARAM,
                        playlistSnapshot.serverControl.canSkipDateRanges ? "v2" : "YES");
            }
            return uriBuilder.build();
        }

        /**
         * Exclude the playlist.
         *
         * @param exclusionDurationMs The number of milliseconds for which the playlist should be
         *                            excluded.
         * @return Whether the playlist is the primary, despite being excluded.
         */
        private boolean excludePlaylist(final long exclusionDurationMs) {
            excludeUntilMs = SystemClock.elapsedRealtime() + exclusionDurationMs;
            return playlistUrl.equals(primaryMediaPlaylistUrl) && !maybeSelectNewPrimaryUrl();
        }
    }
}
