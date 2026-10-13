/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.tv.presenter.PlaylistCardPresenter;
import org.schabi.newpipe.tv.presenter.VideoCardPresenter;
import org.schabi.newpipe.tv.util.TvNavigationHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TvBrowseFragment extends BrowseSupportFragment implements OnItemViewClickedListener {

    private static final String TAG = "TvBrowseFragment";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ArrayObjectAdapter rowsAdapter;

    private ArrayObjectAdapter trendingAdapter;
    private ArrayObjectAdapter subscriptionsAdapter;
    private ArrayObjectAdapter playlistsAdapter;
    private ArrayObjectAdapter historyAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupUi();
        setupRowAdapters();
        loadContent();
    }

    private void setupUi() {
        setTitle(getString(R.string.app_name));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        final Context context = requireContext();
        final int brandColor = ContextCompat.getColor(context, R.color.light_settings_accent_color);
        setBrandColor(brandColor);
        setSearchAffordanceColor(brandColor);

        setOnSearchClickedListener(v -> TvNavigationHelper.openSearch(requireContext(), null));
        setOnItemViewClickedListener(this);
    }

    private void setupRowAdapters() {
        final ListRowPresenter listRowPresenter = new ListRowPresenter();
        rowsAdapter = new ArrayObjectAdapter(listRowPresenter);

        final VideoCardPresenter videoCardPresenter = new VideoCardPresenter();
        final PlaylistCardPresenter playlistCardPresenter = new PlaylistCardPresenter();

        // 1. Trending / What's Hot
        trendingAdapter = new ArrayObjectAdapter(videoCardPresenter);
        final HeaderItem trendingHeader = new HeaderItem(0, getString(R.string.trending));
        rowsAdapter.add(new ListRow(trendingHeader, trendingAdapter));

        // 2. Subscriptions
        subscriptionsAdapter = new ArrayObjectAdapter(videoCardPresenter);
        final HeaderItem subscriptionsHeader =
                new HeaderItem(1, getString(R.string.tab_subscriptions));
        rowsAdapter.add(new ListRow(subscriptionsHeader, subscriptionsAdapter));

        // 3. Playlists & Bookmarks
        playlistsAdapter = new ArrayObjectAdapter(playlistCardPresenter);
        final HeaderItem playlistsHeader = new HeaderItem(2, getString(R.string.tab_bookmarks));
        rowsAdapter.add(new ListRow(playlistsHeader, playlistsAdapter));

        // 4. Watch History
        historyAdapter = new ArrayObjectAdapter(videoCardPresenter);
        final HeaderItem historyHeader =
                new HeaderItem(3, getString(R.string.title_activity_history));
        rowsAdapter.add(new ListRow(historyHeader, historyAdapter));

        setAdapter(rowsAdapter);
    }

    private void loadContent() {
        loadTrending();
        loadSubscriptions();
        loadPlaylists();
        loadHistory();
    }

    private void loadTrending() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final int serviceId = ServiceHelper.getSelectedServiceId(context);
        try {
            final StreamingService service = NewPipe.getService(serviceId);
            final String defaultKioskId = service.getKioskList().getDefaultKioskId();

            disposables.add(ExtractorHelper.getKioskInfo(serviceId,
                    service.getKioskList().getListLinkHandlerFactoryByType(defaultKioskId)
                            .fromId(defaultKioskId).getUrl(), false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(kioskInfo -> {
                        trendingAdapter.clear();
                        for (final InfoItem item : kioskInfo.getRelatedItems()) {
                            if (item instanceof StreamInfoItem) {
                                trendingAdapter.add(item);
                            }
                        }
                    }, throwable -> Log.e(TAG, "Error loading trending", throwable)));
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing kiosk", e);
        }
    }

    private void loadSubscriptions() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final FeedDatabaseManager feedDatabaseManager = new FeedDatabaseManager(context);
        disposables.add(feedDatabaseManager.getStreams(
                        FeedGroupEntity.GROUP_ALL_ID, true, true, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(streamList -> {
                    subscriptionsAdapter.clear();
                    for (final var streamWithState : streamList) {
                        subscriptionsAdapter.add(streamWithState.getStream().toStreamInfoItem());
                    }
                }, throwable -> Log.e(TAG, "Error loading subscriptions", throwable)));
    }

    private void loadPlaylists() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final LocalPlaylistManager playlistManager = new LocalPlaylistManager(
                NewPipeDatabase.getInstance(context));
        disposables.add(playlistManager.getPlaylists()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlists -> {
                    playlistsAdapter.clear();
                    for (final var pl : playlists) {
                        final PlaylistInfoItem item = new PlaylistInfoItem(
                                Constants.NO_SERVICE_ID,
                                String.valueOf(pl.getUid()),
                                pl.getOrderingName() != null ? pl.getOrderingName() : "");
                        item.setStreamCount(pl.getStreamCount());
                        item.setThumbnails(ImageStrategy.dbUrlToImageList(pl.getThumbnailUrl()));
                        playlistsAdapter.add(item);
                    }
                }, throwable -> Log.e(TAG, "Error loading playlists", throwable)));
    }

    private void loadHistory() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final HistoryRecordManager historyRecordManager = new HistoryRecordManager(context);
        disposables.add(historyRecordManager.getStreamHistorySortedById()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(historyList -> {
                    historyAdapter.clear();
                    for (final var entry : historyList) {
                        historyAdapter.add(entry.toStreamInfoItem());
                    }
                }, throwable -> Log.e(TAG, "Error loading history", throwable)));
    }

    @Override
    public void onItemClicked(final Presenter.ViewHolder itemViewHolder,
                              final Object item,
                              final RowPresenter.ViewHolder rowViewHolder,
                              final Row row) {
        if (item instanceof StreamInfoItem) {
            final StreamInfoItem stream = (StreamInfoItem) item;
            TvNavigationHelper.openVideoDetails(
                    requireContext(), stream.getServiceId(), stream.getUrl(), stream.getName());
        } else if (item instanceof ChannelInfoItem) {
            final ChannelInfoItem channel = (ChannelInfoItem) item;
            TvNavigationHelper.openChannel(
                    requireContext(), channel.getServiceId(), channel.getUrl(), channel.getName());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}
