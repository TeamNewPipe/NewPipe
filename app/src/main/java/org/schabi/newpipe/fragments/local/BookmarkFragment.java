package org.schabi.newpipe.fragments.local;

import android.content.Context;
import android.content.DialogInterface;
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
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.info_list.stored.LocalPlaylistInfoItem;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icepick.State;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class BookmarkFragment extends BaseStateFragment<List<PlaylistMetadataEntry>> {
    private View watchHistoryButton;
    private View mostWatchedButton;

    private InfoListAdapter infoListAdapter;
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
        infoListAdapter = new InfoListAdapter(activity);
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

        infoListAdapter = new InfoListAdapter(getActivity());
        itemsList = rootView.findViewById(R.id.items_list);
        itemsList.setLayoutManager(new LinearLayoutManager(activity));

        final View headerRootLayout = activity.getLayoutInflater()
                .inflate(R.layout.bookmark_header, itemsList, false);
        watchHistoryButton = headerRootLayout.findViewById(R.id.watchHistory);
        mostWatchedButton = headerRootLayout.findViewById(R.id.mostWatched);

        infoListAdapter.setHeader(headerRootLayout);
        infoListAdapter.useMiniItemVariants(true);

        itemsList.setAdapter(infoListAdapter);
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        infoListAdapter.setOnPlaylistSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener<PlaylistInfoItem>() {
            @Override
            public void selected(PlaylistInfoItem selectedItem) {
                // Requires the parent fragment to find holder for fragment replacement
                if (selectedItem instanceof LocalPlaylistInfoItem && getParentFragment() != null) {
                    final long playlistId = ((LocalPlaylistInfoItem) selectedItem).getPlaylistId();

                    NavigationHelper.openLocalPlaylistFragment(
                            getParentFragment().getFragmentManager(),
                            playlistId,
                            selectedItem.getName()
                    );
                }
            }

            @Override
            public void held(PlaylistInfoItem selectedItem) {
                if (selectedItem instanceof LocalPlaylistInfoItem) {
                    showPlaylistDialog((LocalPlaylistInfoItem) selectedItem);
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

    private void showPlaylistDialog(final LocalPlaylistInfoItem item) {
        final Context context = getContext();
        if (context == null || context.getResources() == null || getActivity() == null) return;

        final String[] commands = new String[]{
                context.getResources().getString(R.string.delete_playlist)
        };

        final DialogInterface.OnClickListener actions = (dialogInterface, i) -> {
            switch (i) {
                case 0:
                    final Toast deleteSuccessful =
                            Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT);
                    disposables.add(localPlaylistManager.deletePlaylist(item.getPlaylistId())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(ignored -> deleteSuccessful.show()));
                    break;
                default:
                    break;
            }
        };

        final String videoCount = getResources().getQuantityString(R.plurals.videos,
                (int) item.getStreamCount(), (int) item.getStreamCount());
        new InfoItemDialog(getActivity(), commands, actions, item.getName(), videoCount).show();
    }

    private void resetFragment() {
        if (disposables != null) disposables.clear();
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();
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

        infoListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
        } else {
            infoListAdapter.addInfoItemList(infoItemsOf(result));
            if (itemsListState != null) {
                itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
                itemsListState = null;
            }
            hideLoading();
        }
    }


    private List<InfoItem> infoItemsOf(List<PlaylistMetadataEntry> playlists) {
        List<InfoItem> playlistInfoItems = new ArrayList<>(playlists.size());
        for (final PlaylistMetadataEntry playlist : playlists) {
            playlistInfoItems.add(playlist.toStoredPlaylistInfoItem());
        }
        Collections.sort(playlistInfoItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return playlistInfoItems;
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

