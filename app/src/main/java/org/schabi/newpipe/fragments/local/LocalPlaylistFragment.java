package org.schabi.newpipe.fragments.local;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.info_list.OnInfoItemGesture;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.List;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class LocalPlaylistFragment extends BaseListFragment<List<StreamEntity>, Void> {

    private View headerRootLayout;
    private TextView headerTitleView;
    private TextView headerStreamCount;
    private View playlistControl;

    private View headerPlayAllButton;
    private View headerPopupButton;
    private View headerBackgroundButton;

    @State
    protected long playlistId;
    @State
    protected String name;
    @State
    protected Parcelable itemsListState;

    /* Used for independent events */
    private CompositeDisposable disposables = new CompositeDisposable();
    private Subscription databaseSubscription;
    private LocalPlaylistManager playlistManager;

    public static LocalPlaylistFragment getInstance(long playlistId, String name) {
        LocalPlaylistFragment instance = new LocalPlaylistFragment();
        instance.setInitialData(playlistId, name);
        return instance;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        playlistManager = new LocalPlaylistManager(NewPipeDatabase.getInstance(context));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        if (disposables != null) disposables.clear();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (disposables != null) disposables.dispose();
        if (databaseSubscription != null) databaseSubscription.cancel();

        disposables = null;
        databaseSubscription = null;
        playlistManager = null;

        super.onDestroy();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        infoListAdapter.useMiniItemVariants(true);

        setFragmentTitle(name);
    }

    @Override
    protected View getListHeader() {
        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.local_playlist_header,
                itemsList, false);

        headerTitleView = headerRootLayout.findViewById(R.id.playlist_title_view);
        headerTitleView.setSelected(true);

        headerStreamCount = headerRootLayout.findViewById(R.id.playlist_stream_count);
        playlistControl = headerRootLayout.findViewById(R.id.playlist_control);
        headerPlayAllButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_all_button);
        headerPopupButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_popup_button);
        headerBackgroundButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_bg_button);

        return headerRootLayout;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        infoListAdapter.setOnStreamSelectedListener(new OnInfoItemGesture<StreamInfoItem>() {
            @Override
            public void selected(StreamInfoItem selectedItem) {
                // Requires the parent fragment to find holder for fragment replacement
                NavigationHelper.openVideoDetailFragment(getFragmentManager(),
                        selectedItem.getServiceId(), selectedItem.url, selectedItem.getName());
            }

            @Override
            public void held(StreamInfoItem selectedItem) {
                showStreamDialog(selectedItem);
            }
        });

    }

    @Override
    protected void showStreamDialog(final StreamInfoItem item) {
        final Context context = getContext();
        final Activity activity = getActivity();
        if (context == null || context.getResources() == null || getActivity() == null) return;

        final String[] commands = new String[]{
                context.getResources().getString(R.string.enqueue_on_background),
                context.getResources().getString(R.string.enqueue_on_popup),
                context.getResources().getString(R.string.start_here_on_main),
                context.getResources().getString(R.string.start_here_on_background),
                context.getResources().getString(R.string.start_here_on_popup),
        };

        final DialogInterface.OnClickListener actions = (dialogInterface, i) -> {
            final int index = Math.max(infoListAdapter.getItemsList().indexOf(item), 0);
            switch (i) {
                case 0:
                    NavigationHelper.enqueueOnBackgroundPlayer(context, new SinglePlayQueue(item));
                    break;
                case 1:
                    NavigationHelper.enqueueOnPopupPlayer(activity, new SinglePlayQueue(item));
                    break;
                case 2:
                    NavigationHelper.playOnMainPlayer(context, getPlayQueue(index));
                    break;
                case 3:
                    NavigationHelper.playOnBackgroundPlayer(context, getPlayQueue(index));
                    break;
                case 4:
                    NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(index));
                    break;
                default:
                    break;
            }
        };

        new InfoItemDialog(getActivity(), item, commands, actions).show();
    }

    private void resetFragment() {
        if (disposables != null) disposables.clear();
        if (databaseSubscription != null) databaseSubscription.cancel();
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Loader
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void showLoading() {
        super.showLoading();
        animateView(headerRootLayout, false, 200);
        animateView(itemsList, false, 100);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);
        resetFragment();

        playlistManager.getPlaylistStreams(playlistId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistObserver());
    }

    private Subscriber<List<StreamEntity>> getPlaylistObserver() {
        return new Subscriber<List<StreamEntity>>() {
            @Override
            public void onSubscribe(Subscription s) {
                showLoading();

                if (databaseSubscription != null) databaseSubscription.cancel();
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(List<StreamEntity> streams) {
                handleResult(streams);
                if (databaseSubscription != null) databaseSubscription.request(1);
            }

            @Override
            public void onError(Throwable exception) {
                LocalPlaylistFragment.this.onError(exception);
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull List<StreamEntity> result) {
        super.handleResult(result);
        infoListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        animateView(headerRootLayout, true, 100);
        animateView(itemsList, true, 300);

        infoListAdapter.addInfoItemList(getStreamItems(result));
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }

        playlistControl.setVisibility(View.VISIBLE);
        headerStreamCount.setText(
                getResources().getQuantityString(R.plurals.videos, result.size(), result.size()));

        headerPlayAllButton.setOnClickListener(view ->
                NavigationHelper.playOnMainPlayer(activity, getPlayQueue()));
        headerPopupButton.setOnClickListener(view ->
                NavigationHelper.playOnPopupPlayer(activity, getPlayQueue()));
        headerBackgroundButton.setOnClickListener(view ->
                NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue()));
        hideLoading();
    }


    private List<InfoItem> getStreamItems(final List<StreamEntity> streams) {
        List<InfoItem> items = new ArrayList<>(streams.size());
        for (final StreamEntity stream : streams) {
            items.add(stream.toStreamEntityInfoItem());
        }
        return items;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void loadMoreItems() {
        // Do nothing
    }

    @Override
    protected boolean hasMoreItems() {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean onError(Throwable exception) {
        resetFragment();
        if (super.onError(exception)) return true;

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Local Playlist", R.string.general_error);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void setInitialData(long playlistId, String name) {
        this.playlistId = playlistId;
        this.name = !TextUtils.isEmpty(name) ? name : "";
    }

    protected void setFragmentTitle(final String title) {
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(title);
        }
        if (headerTitleView != null) {
            headerTitleView.setText(title);
        }
    }

    private PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        final List<InfoItem> infoItems = infoListAdapter.getItemsList();
        List<StreamInfoItem> streamInfoItems = new ArrayList<>(infoItems.size());
        for (final InfoItem item : infoItems) {
            if (item instanceof StreamInfoItem) streamInfoItems.add((StreamInfoItem) item);
        }
        return new SinglePlayQueue(streamInfoItems, index);
    }
}

