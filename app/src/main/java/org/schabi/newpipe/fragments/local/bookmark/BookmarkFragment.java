package org.schabi.newpipe.fragments.local.bookmark;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.fragments.local.BaseLocalListFragment;
import org.schabi.newpipe.fragments.local.LocalPlaylistManager;
import org.schabi.newpipe.fragments.local.OnLocalItemGesture;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.List;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public final class BookmarkFragment
        extends BaseLocalListFragment<List<PlaylistMetadataEntry>, Void> {

    private View watchHistoryButton;
    private View mostWatchedButton;

    @State
    protected Parcelable itemsListState;

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localPlaylistManager = new LocalPlaylistManager(NewPipeDatabase.getInstance(getContext()));
        disposables = new CompositeDisposable();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(true);
            activity.setTitle(R.string.tab_subscriptions);
        }

        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }


    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) setTitle(getString(R.string.tab_bookmarks));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
    }

    @Override
    protected View getListHeader() {
        final View headerRootLayout = activity.getLayoutInflater()
                .inflate(R.layout.bookmark_header, itemsList, false);
        watchHistoryButton = headerRootLayout.findViewById(R.id.watchHistory);
        mostWatchedButton = headerRootLayout.findViewById(R.id.mostWatched);
        return headerRootLayout;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        itemListAdapter.setSelectedListener(new OnLocalItemGesture<LocalItem>() {
            @Override
            public void selected(LocalItem selectedItem) {
                // Requires the parent fragment to find holder for fragment replacement
                if (selectedItem instanceof PlaylistMetadataEntry && getParentFragment() != null) {
                    final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                    NavigationHelper.openLocalPlaylistFragment(
                            getParentFragment().getFragmentManager(), entry.uid, entry.name);
                }
            }

            @Override
            public void held(LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistMetadataEntry) {
                    showDeleteDialog((PlaylistMetadataEntry) selectedItem);
                }
            }
        });

        watchHistoryButton.setOnClickListener(view -> {
            if (getParentFragment() != null) {
                NavigationHelper.openWatchHistoryFragment(getParentFragment().getFragmentManager());
            }
        });

        mostWatchedButton.setOnClickListener(view -> {
            if (getParentFragment() != null) {
                NavigationHelper.openMostPlayedFragment(getParentFragment().getFragmentManager());
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);
        localPlaylistManager.getPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionSubscriber());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposables != null) disposables.clear();
        if (databaseSubscription != null) databaseSubscription.cancel();

        databaseSubscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.dispose();

        disposables = null;
        localPlaylistManager = null;
        itemsListState = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistMetadataEntry>> getSubscriptionSubscriber() {
        return new Subscriber<List<PlaylistMetadataEntry>>() {
            @Override
            public void onSubscribe(Subscription s) {
                showLoading();
                if (databaseSubscription != null) databaseSubscription.cancel();
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(List<PlaylistMetadataEntry> subscriptions) {
                handleResult(subscriptions);
                if (databaseSubscription != null) databaseSubscription.request(1);
            }

            @Override
            public void onError(Throwable exception) {
                BookmarkFragment.this.onError(exception);
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull List<PlaylistMetadataEntry> result) {
        super.handleResult(result);

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(result);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        hideLoading();
    }
    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Bookmark", R.string.general_error);
        return true;
    }

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (disposables != null) disposables.clear();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private void showDeleteDialog(final PlaylistMetadataEntry item) {
        new AlertDialog.Builder(activity)
                .setTitle(item.name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) ->
                        disposables.add(deletePlaylist(item.uid))
                )
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private Disposable deletePlaylist(final long playlistId) {
        return localPlaylistManager.deletePlaylist(playlistId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {/*Do nothing on success*/},
                        throwable -> Log.e(TAG, "Playlist deletion failed, id=["
                                + playlistId + "]")
                );
    }
}

