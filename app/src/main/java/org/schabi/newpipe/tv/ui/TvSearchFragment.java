/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.tv.presenter.ChannelCardPresenter;
import org.schabi.newpipe.tv.presenter.PlaylistCardPresenter;
import org.schabi.newpipe.tv.presenter.VideoCardPresenter;
import org.schabi.newpipe.tv.util.TvNavigationHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class TvSearchFragment extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider, OnItemViewClickedListener {

    private static final String TAG = "TvSearchFragment";
    private static final int REQUEST_SPEECH_PERMISSION = 101;
    private static final int REQUEST_SPEECH_RECOGNIZE = 102;
    private static final long DEBOUNCE_DELAY_MS = 400L;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final PublishSubject<String> searchSubject = PublishSubject.create();

    private ArrayObjectAdapter rowsAdapter;
    private ArrayObjectAdapter videoResultsAdapter;
    private ArrayObjectAdapter channelResultsAdapter;
    private ArrayObjectAdapter playlistResultsAdapter;

    public static TvSearchFragment newInstance(@Nullable final String query) {
        final TvSearchFragment fragment = new TvSearchFragment();
        final Bundle args = new Bundle();
        if (query != null) {
            args.putString(TvNavigationHelper.KEY_SEARCH_QUERY, query);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSearchResultProvider(this);
        setOnItemViewClickedListener(this);

        setupRows();
        setupSpeechRecognition();
        setupSearchDebounce();

        final Bundle args = getArguments();
        if (args != null && args.containsKey(TvNavigationHelper.KEY_SEARCH_QUERY)) {
            final String query = args.getString(TvNavigationHelper.KEY_SEARCH_QUERY);
            if (query != null && !query.isEmpty()) {
                setSearchQuery(query, true);
            }
        }
    }

    private void setupRows() {
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        videoResultsAdapter = new ArrayObjectAdapter(new VideoCardPresenter());
        channelResultsAdapter = new ArrayObjectAdapter(new ChannelCardPresenter());
        playlistResultsAdapter = new ArrayObjectAdapter(new PlaylistCardPresenter());
    }

    private void setupSearchDebounce() {
        disposables.add(searchSubject
                .debounce(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::performSearch,
                        throwable -> Log.e(TAG, "Search debounce error", throwable)));
    }

    private void setupSpeechRecognition() {
        setSpeechRecognitionCallback(() -> {
            final Context context = getContext();
            if (context == null) {
                return;
            }

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_SPEECH_PERMISSION);
            } else {
                startSpeechRecognizer();
            }
        });
    }

    private void startSpeechRecognizer() {
        try {
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            startActivityForResult(intent, REQUEST_SPEECH_RECOGNIZE);
        } catch (final Exception e) {
            Log.e(TAG, "Speech recognition service unavailable on device", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SPEECH_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognizer();
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH_RECOGNIZE && resultCode == Activity.RESULT_OK
                && data != null) {
            final ArrayList<String> matches =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                setSearchQuery(matches.get(0), true);
            }
        }
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return rowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(final String newQuery) {
        if (newQuery == null || newQuery.trim().isEmpty()) {
            clearResults();
            return true;
        }

        searchSubject.onNext(newQuery.trim());
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(final String query) {
        if (query == null || query.trim().isEmpty()) {
            clearResults();
            return true;
        }

        performSearch(query.trim());
        return true;
    }

    private void clearResults() {
        videoResultsAdapter.clear();
        channelResultsAdapter.clear();
        playlistResultsAdapter.clear();
        rowsAdapter.clear();
    }

    private void performSearch(final String query) {
        final Context context = getContext();
        if (context == null || query.trim().isEmpty()) {
            clearResults();
            return;
        }

        final int serviceId = ServiceHelper.getSelectedServiceId(context);

        disposables.add(ExtractorHelper.searchFor(serviceId, query, Collections.emptyList(), "")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onSearchResultsLoaded,
                        throwable -> Log.e(TAG, "Search query failed", throwable)));
    }

    private void onSearchResultsLoaded(@NonNull final SearchInfo searchInfo) {
        clearResults();

        for (final InfoItem item : searchInfo.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                videoResultsAdapter.add(item);
            } else if (item instanceof ChannelInfoItem) {
                channelResultsAdapter.add(item);
            } else if (item instanceof PlaylistInfoItem) {
                playlistResultsAdapter.add(item);
            }
        }

        if (channelResultsAdapter.size() > 0) {
            rowsAdapter.add(new ListRow(
                    new HeaderItem(0, getString(R.string.channel)), channelResultsAdapter));
        }
        if (videoResultsAdapter.size() > 0) {
            rowsAdapter.add(new ListRow(
                    new HeaderItem(1, getString(R.string.videos_string)), videoResultsAdapter));
        }
        if (playlistResultsAdapter.size() > 0) {
            rowsAdapter.add(new ListRow(
                    new HeaderItem(2, getString(R.string.playlists)), playlistResultsAdapter));
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
        } else if (item instanceof ChannelInfoItem) {
            final ChannelInfoItem channel = (ChannelInfoItem) item;
            TvNavigationHelper.openChannel(
                    requireContext(), channel.getServiceId(), channel.getUrl(), channel.getName());
        } else if (item instanceof PlaylistInfoItem) {
            final PlaylistInfoItem playlist = (PlaylistInfoItem) item;
            TvNavigationHelper.openVideoDetails(
                    requireContext(), playlist.getServiceId(), playlist.getUrl(),
                    playlist.getName());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}
