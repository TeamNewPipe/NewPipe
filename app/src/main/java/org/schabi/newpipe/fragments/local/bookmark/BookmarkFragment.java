package org.schabi.newpipe.fragments.local.bookmark;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.local.LocalItemListAdapter;
import org.schabi.newpipe.fragments.local.LocalPlaylistManager;
import org.schabi.newpipe.fragments.local.OnLocalItemGesture;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Collections;
import java.util.List;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class BookmarkFragment extends BaseStateFragment<List<PlaylistMetadataEntry>> {
    private View watchHistoryButton;
    private View mostWatchedButton;

    private LocalItemListAdapter itemListAdapter;
    private RecyclerView itemsList;

    @State
    protected Parcelable itemsListState;

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser && activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.tab_bookmarks);
        }
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        itemListAdapter = new LocalItemListAdapter(activity);
        localPlaylistManager = new LocalPlaylistManager(NewPipeDatabase.getInstance(context));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        activity.setTitle(R.string.tab_bookmarks);
        if(useAsFrontPage) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        if (disposables != null) disposables.clear();
        if (databaseSubscription != null) databaseSubscription.cancel();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (disposables != null) disposables.dispose();
        if (databaseSubscription != null) databaseSubscription.cancel();

        disposables = null;
        databaseSubscription = null;
        localPlaylistManager = null;

        super.onDestroy();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        itemsList = rootView.findViewById(R.id.items_list);
        itemsList.setLayoutManager(new LinearLayoutManager(activity));

        final View headerRootLayout = activity.getLayoutInflater()
                .inflate(R.layout.bookmark_header, itemsList, false);
        watchHistoryButton = headerRootLayout.findViewById(R.id.watchHistory);
        mostWatchedButton = headerRootLayout.findViewById(R.id.mostWatched);

        itemListAdapter.setHeader(headerRootLayout);

        itemsList.setAdapter(itemListAdapter);
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

    private void showDeleteDialog(final PlaylistMetadataEntry item) {
        new AlertDialog.Builder(activity)
                .setTitle(item.name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) -> {
                    final Toast deleteSuccessful = Toast.makeText(getContext(),
                            R.string.playlist_delete_success, Toast.LENGTH_SHORT);
                    disposables.add(localPlaylistManager.deletePlaylist(item.uid)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(ignored -> deleteSuccessful.show()));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void resetFragment() {
        if (disposables != null) disposables.clear();
        if (itemListAdapter != null) itemListAdapter.clearStreamItemList();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);
        resetFragment();

        localPlaylistManager.getPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionSubscriber());
    }

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
        } else {
            itemListAdapter.addItems(infoItemsOf(result));
            if (itemsListState != null) {
                itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
                itemsListState = null;
            }
            hideLoading();
        }
    }


    private List<PlaylistMetadataEntry> infoItemsOf(List<PlaylistMetadataEntry> playlists) {
        Collections.sort(playlists, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return playlists;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        animateView(itemsList, false, 100);
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        animateView(itemsList, true, 200);
    }

    @Override
    public void showEmptyState() {
        super.showEmptyState();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean onError(Throwable exception) {
        resetFragment();
        if (super.onError(exception)) return true;

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Bookmark", R.string.general_error);
        return true;
    }
}

