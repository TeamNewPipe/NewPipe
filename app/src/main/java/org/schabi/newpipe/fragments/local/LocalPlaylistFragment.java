package org.schabi.newpipe.fragments.local;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class LocalPlaylistFragment extends BaseLocalListFragment<List<PlaylistStreamEntry>, Void> {

    private static final long SAVE_DEBOUNCE_MILLIS = 1000;

    private View headerRootLayout;
    private TextView headerTitleView;
    private TextView headerStreamCount;

    private View playlistControl;
    private View headerPlayAllButton;
    private View headerPopupButton;
    private View headerBackgroundButton;

    @State
    protected Long playlistId;
    @State
    protected String name;
    @State
    protected Parcelable itemsListState;

    private ItemTouchHelper itemTouchHelper;

    private LocalPlaylistManager playlistManager;
    private Subscription databaseSubscription;

    private PublishSubject<Long> debouncedSaveSignal;
    private Disposable debouncedSaver;

    public static LocalPlaylistFragment getInstance(long playlistId, String name) {
        LocalPlaylistFragment instance = new LocalPlaylistFragment();
        instance.setInitialData(playlistId, name);
        return instance;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistManager = new LocalPlaylistManager(NewPipeDatabase.getInstance(getContext()));
        debouncedSaveSignal = PublishSubject.create();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);

        if (headerTitleView != null) {
            headerTitleView.setText(title);
        }
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setTitle(name);
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

        headerTitleView.setOnClickListener(view -> createRenameDialog());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setSelectedListener(new OnLocalItemGesture<LocalItem>() {
            @Override
            public void selected(LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistStreamEntry) {
                    final PlaylistStreamEntry item = (PlaylistStreamEntry) selectedItem;
                    NavigationHelper.openVideoDetailFragment(getFragmentManager(),
                            item.serviceId, item.url, item.title);
                }
            }

            @Override
            public void held(LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistStreamEntry) {
                    showStreamDialog((PlaylistStreamEntry) selectedItem);
                }
            }

            @Override
            public void drag(LocalItem selectedItem, RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void showLoading() {
        super.showLoading();
        animateView(headerRootLayout, false, 200);
        animateView(playlistControl, false, 200);
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        animateView(headerRootLayout, true, 200);
        animateView(playlistControl, true, 200);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);

        if (debouncedSaver != null) debouncedSaver.dispose();
        debouncedSaver = getDebouncedSaver();

        playlistManager.getPlaylistStreams(playlistId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistObserver());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (databaseSubscription != null) databaseSubscription.cancel();
        if (debouncedSaver != null) debouncedSaver.dispose();

        databaseSubscription = null;
        debouncedSaver = null;
        itemTouchHelper = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (debouncedSaveSignal != null) debouncedSaveSignal.onComplete();

        debouncedSaveSignal = null;
        playlistManager = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playlist Stream Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistStreamEntry>> getPlaylistObserver() {
        return new Subscriber<List<PlaylistStreamEntry>>() {
            @Override
            public void onSubscribe(Subscription s) {
                showLoading();

                if (databaseSubscription != null) databaseSubscription.cancel();
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(List<PlaylistStreamEntry> streams) {
                // Do not allow saving while the result is being updated
                if (debouncedSaver != null) debouncedSaver.dispose();
                handleResult(streams);
                debouncedSaver = getDebouncedSaver();

                if (databaseSubscription != null) databaseSubscription.request(1);
            }

            @Override
            public void onError(Throwable exception) {
                LocalPlaylistFragment.this.onError(exception);
            }

            @Override
            public void onComplete() {}
        };
    }

    @Override
    public void handleResult(@NonNull List<PlaylistStreamEntry> result) {
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
        setVideoCount(itemListAdapter.getItemsList().size());

        headerPlayAllButton.setOnClickListener(view ->
                NavigationHelper.playOnMainPlayer(activity, getPlayQueue()));
        headerPopupButton.setOnClickListener(view ->
                NavigationHelper.playOnPopupPlayer(activity, getPlayQueue()));
        headerBackgroundButton.setOnClickListener(view ->
                NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue()));

        hideLoading();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (databaseSubscription != null) databaseSubscription.cancel();
    }

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Local Playlist", R.string.general_error);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata/Streams Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void createRenameDialog() {
        if (playlistId == null || name == null || getContext() == null) return;

        final View dialogView = View.inflate(getContext(), R.layout.dialog_playlist_name, null);
        EditText nameEdit = dialogView.findViewById(R.id.playlist_name);
        nameEdit.setText(name);
        nameEdit.setSelection(nameEdit.getText().length());

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.rename_playlist)
                .setView(dialogView)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) ->
                        changePlaylistName(nameEdit.getText().toString())
                );

        dialogBuilder.show();
    }

    private void changePlaylistName(final String name) {
        this.name = name;
        setTitle(name);

        Log.e(TAG, "Updating playlist id=[" + playlistId +
                "] with new name=[" + name + "] items");

        playlistManager.renamePlaylist(playlistId, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> {/*Do nothing on success*/}, this::onError);
    }

    private void changeThumbnailUrl(final String thumbnailUrl) {
        final Toast successToast = Toast.makeText(getActivity(),
                R.string.playlist_thumbnail_change_success,
                Toast.LENGTH_SHORT);

        Log.e(TAG, "Updating playlist id=[" + playlistId +
                "] with new thumbnail url=[" + thumbnailUrl + "]");

        playlistManager.changePlaylistThumbnail(playlistId, thumbnailUrl)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignore -> successToast.show(), this::onError);
    }

    private void deleteItem(final PlaylistStreamEntry item) {
        itemListAdapter.removeItem(item);
        setVideoCount(itemListAdapter.getItemsList().size());
        saveDebounced();
    }

    private void saveDebounced() {
        debouncedSaveSignal.onNext(System.currentTimeMillis());
    }

    private Disposable getDebouncedSaver() {
        return debouncedSaveSignal
                .debounce(SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> saveJoin());
    }

    private void saveJoin() {
        final List<LocalItem> items = itemListAdapter.getItemsList();
        List<Long> streamIds = new ArrayList<>(items.size());
        for (final LocalItem item : items) {
            if (item instanceof PlaylistStreamEntry) {
                streamIds.add(((PlaylistStreamEntry) item).streamId);
            }
        }

        Log.e(TAG, "Updating playlist id=[" + playlistId +
                "] with [" + streamIds.size() + "] items");

        playlistManager.updateJoin(playlistId, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {/*Do nothing on success*/}, this::onError);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void showStreamDialog(final PlaylistStreamEntry item) {
        final Context context = getContext();
        final Activity activity = getActivity();
        if (context == null || context.getResources() == null || getActivity() == null) return;

        final StreamInfoItem infoItem = item.toStreamInfoItem();

        final String[] commands = new String[]{
                context.getResources().getString(R.string.enqueue_on_background),
                context.getResources().getString(R.string.enqueue_on_popup),
                context.getResources().getString(R.string.start_here_on_main),
                context.getResources().getString(R.string.start_here_on_background),
                context.getResources().getString(R.string.start_here_on_popup),
                context.getResources().getString(R.string.set_as_playlist_thumbnail),
                context.getResources().getString(R.string.delete)
        };

        final DialogInterface.OnClickListener actions = (dialogInterface, i) -> {
            final int index = Math.max(itemListAdapter.getItemsList().indexOf(item), 0);
            switch (i) {
                case 0:
                    NavigationHelper.enqueueOnBackgroundPlayer(context,
                            new SinglePlayQueue(infoItem));
                    break;
                case 1:
                    NavigationHelper.enqueueOnPopupPlayer(activity, new
                            SinglePlayQueue(infoItem));
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
                case 5:
                    changeThumbnailUrl(item.thumbnailUrl);
                    break;
                case 6:
                    deleteItem(item);
                    break;
                default:
                    break;
            }
        };

        new InfoItemDialog(getActivity(), infoItem, commands, actions).show();
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
                                  RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType() ||
                        itemListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                final boolean isSwapped = itemListAdapter.swapItems(sourceIndex, targetIndex);
                if (isSwapped) saveDebounced();
                return isSwapped;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
        };
    }

    private void setInitialData(long playlistId, String name) {
        this.playlistId = playlistId;
        this.name = !TextUtils.isEmpty(name) ? name : "";
    }

    private void setVideoCount(final long count) {
        if (activity != null && headerStreamCount != null) {
            headerStreamCount.setText(Localization.localizeStreamCount(activity, count));
        }
    }

    private PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        final List<LocalItem> infoItems = itemListAdapter.getItemsList();
        List<StreamInfoItem> streamInfoItems = new ArrayList<>(infoItems.size());
        for (final LocalItem item : infoItems) {
            if (item instanceof PlaylistStreamEntry) {
                streamInfoItems.add(((PlaylistStreamEntry) item).toStreamInfoItem());
            }
        }
        return new SinglePlayQueue(streamInfoItems, index);
    }
}

