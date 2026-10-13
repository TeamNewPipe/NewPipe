/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
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

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.tv.presenter.VideoCardPresenter;
import org.schabi.newpipe.tv.util.TvNavigationHelper;
import org.schabi.newpipe.util.ExtractorHelper;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TvChannelFragment extends BrowseSupportFragment implements OnItemViewClickedListener {

    private static final String TAG = "TvChannelFragment";

    private int serviceId;
    private String url;
    private String channelTitle;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ArrayObjectAdapter rowsAdapter;
    private ArrayObjectAdapter uploadsAdapter;

    public static TvChannelFragment newInstance(final int serviceId,
                                                @NonNull final String url,
                                                @Nullable final String title) {
        final TvChannelFragment fragment = new TvChannelFragment();
        final Bundle args = new Bundle();
        args.putInt(TvNavigationHelper.KEY_SERVICE_ID, serviceId);
        args.putString(TvNavigationHelper.KEY_URL, url);
        args.putString(TvNavigationHelper.KEY_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args != null) {
            serviceId = args.getInt(TvNavigationHelper.KEY_SERVICE_ID, 0);
            url = args.getString(TvNavigationHelper.KEY_URL, "");
            channelTitle = args.getString(TvNavigationHelper.KEY_TITLE, "");
        }

        setupUi();
        setupRowAdapters();
        loadChannelInfo();
    }

    private void setupUi() {
        setTitle(channelTitle != null && !channelTitle.isEmpty()
                ? channelTitle : getString(R.string.channel));
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
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        uploadsAdapter = new ArrayObjectAdapter(new VideoCardPresenter());
        final HeaderItem uploadsHeader = new HeaderItem(0, getString(R.string.recent_videos));
        rowsAdapter.add(new ListRow(uploadsHeader, uploadsAdapter));

        setAdapter(rowsAdapter);
    }

    private void loadChannelInfo() {
        if (url == null || url.isEmpty()) {
            return;
        }

        disposables.add(ExtractorHelper.getChannelInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onChannelInfoLoaded,
                        throwable -> Log.e(TAG, "Error loading channel", throwable)));
    }

    private void onChannelInfoLoaded(@NonNull final ChannelInfo channelInfo) {
        if (channelInfo.getName() != null) {
            setTitle(channelInfo.getName());
        }

        if (channelInfo.getTabs() != null && !channelInfo.getTabs().isEmpty()) {
            final ListLinkHandler tabHandler = channelInfo.getTabs().get(0);
            disposables.add(ExtractorHelper.getChannelTab(
                            channelInfo.getServiceId(), tabHandler, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(tabInfo -> {
                        uploadsAdapter.clear();
                        for (final InfoItem item : tabInfo.getRelatedItems()) {
                            if (item instanceof StreamInfoItem) {
                                uploadsAdapter.add(item);
                            }
                        }
                    }, throwable -> Log.e(TAG, "Error loading channel tab", throwable)));
        }
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
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}
