/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.DetailsSupportFragmentBackgroundController;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.tv.presenter.DetailsDescriptionPresenter;
import org.schabi.newpipe.tv.presenter.VideoCardPresenter;
import org.schabi.newpipe.tv.util.TvNavigationHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TvVideoDetailsFragment extends DetailsSupportFragment
        implements OnActionClickedListener, OnItemViewClickedListener {

    private static final String TAG = "TvVideoDetailsFragment";

    private static final int ACTION_PLAY = 1;
    private static final int ACTION_ENQUEUE = 2;
    private static final int ACTION_CHANNEL = 3;
    private static final int ACTION_COMMENTS = 4;

    private int serviceId;
    private String url;
    private String title;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private DetailsSupportFragmentBackgroundController backgroundController;
    private ArrayObjectAdapter rowsAdapter;
    private DetailsOverviewRow detailsOverviewRow;
    private ArrayObjectAdapter relatedAdapter;
    private StreamInfo currentStreamInfo;

    public static TvVideoDetailsFragment newInstance(final int serviceId,
                                                    @NonNull final String url,
                                                    @Nullable final String title) {
        final TvVideoDetailsFragment fragment = new TvVideoDetailsFragment();
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
            title = args.getString(TvNavigationHelper.KEY_TITLE, "");
        }

        backgroundController = new DetailsSupportFragmentBackgroundController(this);
        backgroundController.enableParallax();

        setupPresenterAndRows();
        loadStreamDetails();
    }

    private void setupPresenterAndRows() {
        final FullWidthDetailsOverviewRowPresenter detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(Color.parseColor("#1F1F1F"));
        detailsPresenter.setInitialState(FullWidthDetailsOverviewRowPresenter.STATE_HALF);
        detailsPresenter.setOnActionClickedListener(this);

        final ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
        presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());

        rowsAdapter = new ArrayObjectAdapter(presenterSelector);
        setAdapter(rowsAdapter);

        setOnItemViewClickedListener(this);
    }

    private void loadStreamDetails() {
        if (url == null || url.isEmpty()) {
            return;
        }

        disposables.add(ExtractorHelper.getStreamInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onStreamInfoLoaded, throwable -> {
                    Log.e(TAG, "Error loading stream info", throwable);
                    Toast.makeText(getContext(), R.string.general_error, Toast.LENGTH_SHORT).show();
                }));
    }

    private void onStreamInfoLoaded(@NonNull final StreamInfo streamInfo) {
        currentStreamInfo = streamInfo;

        detailsOverviewRow = new DetailsOverviewRow(streamInfo);

        // Actions
        final SparseArrayObjectAdapter actionAdapter = new SparseArrayObjectAdapter();
        actionAdapter.set(ACTION_PLAY, new Action(ACTION_PLAY, getString(R.string.play)));
        actionAdapter.set(ACTION_ENQUEUE,
                new Action(ACTION_ENQUEUE, getString(R.string.enqueue_stream)));
        actionAdapter.set(ACTION_CHANNEL, new Action(ACTION_CHANNEL, getString(R.string.channel)));
        actionAdapter.set(ACTION_COMMENTS,
                new Action(ACTION_COMMENTS, getString(R.string.controls_comments_title)));
        detailsOverviewRow.setActionsAdapter(actionAdapter);

        rowsAdapter.clear();
        rowsAdapter.add(detailsOverviewRow);

        // Related streams row
        relatedAdapter = new ArrayObjectAdapter(new VideoCardPresenter());
        final HeaderItem relatedHeader = new HeaderItem(1, getString(R.string.related_videos));
        for (final InfoItem item : streamInfo.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                relatedAdapter.add(item);
            }
        }
        rowsAdapter.add(new ListRow(relatedHeader, relatedAdapter));

        // Load cover background asynchronously
        final Context context = getContext();
        if (context != null) {
            Schedulers.io().scheduleDirect(() -> {
                final String thumbUrl = ImageStrategy.choosePreferredImage(
                        streamInfo.getThumbnails());
                final Bitmap bitmap = CoilHelper.INSTANCE.loadBitmapBlocking(context, thumbUrl, 0);
                if (bitmap != null) {
                    AndroidSchedulers.mainThread().scheduleDirect(() -> {
                        detailsOverviewRow.setImageBitmap(context, bitmap);
                        backgroundController.setCoverBitmap(bitmap);
                    });
                }
            });
        }
    }

    @Override
    public void onActionClicked(final Action action) {
        if (currentStreamInfo == null) {
            return;
        }

        final int actionId = (int) action.getId();
        switch (actionId) {
            case ACTION_PLAY:
                TvNavigationHelper.playOnTv(requireContext(),
                        new SinglePlayQueue(currentStreamInfo));
                break;
            case ACTION_ENQUEUE:
                Toast.makeText(getContext(), R.string.enqueued, Toast.LENGTH_SHORT).show();
                break;
            case ACTION_CHANNEL:
                if (currentStreamInfo.getUploaderUrl() != null) {
                    TvNavigationHelper.openChannel(requireContext(),
                            currentStreamInfo.getServiceId(),
                            currentStreamInfo.getUploaderUrl(),
                            currentStreamInfo.getUploaderName());
                }
                break;
            case ACTION_COMMENTS:
                final TvCommentsDialog dialog = TvCommentsDialog.newInstance(
                        currentStreamInfo.getServiceId(), currentStreamInfo.getUrl());
                dialog.show(getChildFragmentManager(), "tv_comments");
                break;
            default:
                break;
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
