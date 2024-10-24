package org.schabi.newpipe.local.playlist;

import static org.schabi.newpipe.error.ErrorUtil.showUiErrorSnackbar;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.util.ThemeHelper.shouldUseGridLayout;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.evernote.android.state.State;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.databinding.LocalPlaylistHeaderBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.debounce.DebounceSavable;
import org.schabi.newpipe.util.debounce.DebounceSaver;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LocalPlaylistFragment extends BaseLocalListFragment<List<PlaylistStreamEntry>, Void>
        implements PlaylistControlViewHolder, DebounceSavable {

    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;
    @State
    protected Long playlistId;
    @State
    protected String name;
    @State
    Parcelable itemsListState;

    private LocalPlaylistHeaderBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;

    private ItemTouchHelper itemTouchHelper;

    private LocalPlaylistManager playlistManager;
    private Subscription databaseSubscription;

    private CompositeDisposable disposables;

    /** Whether the playlist has been fully loaded from db. */
    private AtomicBoolean isLoadingComplete;
    /** Used to debounce saving playlist edits to disk. */
    private DebounceSaver debounceSaver;
    /** Flag to prevent simultaneous rewrites of the playlist. */
    private boolean isRewritingPlaylist = false;

    /**
     * The pager adapter that the fragment is created from when it is used as frontpage, i.e.
     * {@link #useAsFrontPage} is {@link true}.
     */
    @Nullable
    private MainFragment.SelectedTabsPagerAdapter tabsPagerAdapter = null;

    public static LocalPlaylistFragment getInstance(final long playlistId, final String name) {
        final LocalPlaylistFragment instance = new LocalPlaylistFragment();
        instance.setInitialData(playlistId, name);
        return instance;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistManager = new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));

        disposables = new CompositeDisposable();

        isLoadingComplete = new AtomicBoolean();
        debounceSaver = new DebounceSaver(this);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);

        if (headerBinding != null) {
            headerBinding.playlistTitleView.setText(title);
        }
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setTitle(name);
    }

    @Override
    protected ViewBinding getListHeader() {
        headerBinding = LocalPlaylistHeaderBinding.inflate(activity.getLayoutInflater(), itemsList,
                false);
        playlistControlBinding = headerBinding.playlistControl;

        headerBinding.playlistTitleView.setSelected(true);

        return headerBinding;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        headerBinding.playlistTitleView.setOnClickListener(view -> createRenameDialog());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistStreamEntry) {
                    final StreamEntity item =
                            ((PlaylistStreamEntry) selectedItem).getStreamEntity();
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            item.getServiceId(), item.getUrl(), item.getTitle(), null, false);
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistStreamEntry) {
                    showInfoItemDialog((PlaylistStreamEntry) selectedItem);
                }
            }

            @Override
            public void drag(final LocalItem selectedItem,
                             final RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void showLoading() {
        super.showLoading();
        if (headerBinding != null) {
            animate(headerBinding.getRoot(), false, 200);
            animate(playlistControlBinding.getRoot(), false, 200);
        }
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        if (headerBinding != null) {
            animate(headerBinding.getRoot(), true, 200);
            animate(playlistControlBinding.getRoot(), true, 200);
        }
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        if (disposables != null) {
            disposables.clear();
        }

        if (debounceSaver != null) {
            disposables.add(debounceSaver.getDebouncedSaver());
            debounceSaver.setNoChangesToSave();
        }

        isLoadingComplete.set(false);

        playlistManager.getPlaylistStreams(playlistId)
                .onBackpressureLatest()
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

        // Save on exit
        saveImmediate();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_local_playlist, menu);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (itemListAdapter != null) {
            itemListAdapter.unsetSelectedListener();
        }

        headerBinding = null;
        playlistControlBinding = null;


        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }
        if (disposables != null) {
            disposables.clear();
        }

        databaseSubscription = null;
        itemTouchHelper = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (debounceSaver != null) {
            debounceSaver.getDebouncedSaveSignal().onComplete();
        }
        if (disposables != null) {
            disposables.dispose();
        }
        if (tabsPagerAdapter != null) {
            tabsPagerAdapter.getLocalPlaylistFragments().remove(this);
        }

        debounceSaver = null;
        playlistManager = null;
        disposables = null;

        isLoadingComplete = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playlist Stream Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistStreamEntry>> getPlaylistObserver() {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();
                isLoadingComplete.set(false);

                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<PlaylistStreamEntry> streams) {
                // Skip handling the result after it has been modified
                if (debounceSaver == null || !debounceSaver.getIsModified()) {
                    handleResult(streams);
                    isLoadingComplete.set(true);
                }

                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(new ErrorInfo(exception, UserAction.REQUESTED_BOOKMARK,
                        "Loading local playlist"));
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_share_playlist) {
            createShareConfirmationDialog();
        } else if (item.getItemId() == R.id.menu_item_rename_playlist) {
            createRenameDialog();
        } else if (item.getItemId() == R.id.menu_item_remove_watched) {
            if (!isRewritingPlaylist) {
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.remove_watched_popup_warning)
                        .setTitle(R.string.remove_watched_popup_title)
                        .setPositiveButton(R.string.ok, (d, id) ->
                                removeWatchedStreams(false))
                        .setNeutralButton(
                                R.string.remove_watched_popup_yes_and_partially_watched_videos,
                                (d, id) -> removeWatchedStreams(true))
                        .setNegativeButton(R.string.cancel,
                                (d, id) -> d.cancel())
                        .show();
            }
        } else if (item.getItemId() == R.id.menu_item_remove_duplicates) {
            if (!isRewritingPlaylist) {
                openRemoveDuplicatesDialog();
            }
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Shares the playlist as a list of stream URLs if {@code shouldSharePlaylistDetails} is
     * set to {@code false}. Shares the playlist name along with a list of video titles and URLs
     * if {@code shouldSharePlaylistDetails} is set to {@code true}.
     *
     * @param shouldSharePlaylistDetails Whether the playlist details should be included in the
     *                                   shared content.
     */
    private void sharePlaylist(final boolean shouldSharePlaylistDetails) {
        final Context context = requireContext();

        disposables.add(playlistManager.getPlaylistStreams(playlistId)
                .flatMapSingle(playlist -> Single.just(playlist.stream()
                        .map(PlaylistStreamEntry::getStreamEntity)
                        .map(streamEntity -> {
                            if (shouldSharePlaylistDetails) {
                                return context.getString(R.string.video_details_list_item,
                                        streamEntity.getTitle(), streamEntity.getUrl());
                            } else {
                                return streamEntity.getUrl();
                            }
                        })
                        .collect(Collectors.joining("\n"))))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(urlsText -> ShareUtils.shareText(
                                context, name, shouldSharePlaylistDetails
                                        ? context.getString(R.string.share_playlist_content_details,
                                        name, urlsText) : urlsText),
                        throwable -> showUiErrorSnackbar(this, "Sharing playlist", throwable)));
    }

    public void removeWatchedStreams(final boolean removePartiallyWatched) {
        if (isRewritingPlaylist) {
            return;
        }
        isRewritingPlaylist = true;
        showLoading();

        final var recordManager = new HistoryRecordManager(getContext());
        final var historyIdsMaybe = recordManager.getStreamHistorySortedById()
                .firstElement()
                // already sorted by ^ getStreamHistorySortedById(), binary search can be used
                .map(historyList -> historyList.stream().map(StreamHistoryEntry::getStreamId)
                        .collect(Collectors.toList()));
        final var streamsMaybe = playlistManager.getPlaylistStreams(playlistId)
                .firstElement()
                .zipWith(historyIdsMaybe, (playlist, historyStreamIds) -> {
                    // Remove Watched, Functionality data
                    final List<PlaylistStreamEntry> itemsToKeep = new ArrayList<>();
                    final boolean isThumbnailPermanent = playlistManager
                            .getIsPlaylistThumbnailPermanent(playlistId);
                    boolean thumbnailVideoRemoved = false;

                    if (removePartiallyWatched) {
                        for (final var playlistItem : playlist) {
                            final int indexInHistory = Collections.binarySearch(historyStreamIds,
                                    playlistItem.getStreamId());

                            if (indexInHistory < 0) {
                                itemsToKeep.add(playlistItem);
                            } else if (!isThumbnailPermanent && !thumbnailVideoRemoved
                                    && playlistManager.getPlaylistThumbnailStreamId(playlistId)
                                    == playlistItem.getStreamEntity().getUid()) {
                                thumbnailVideoRemoved = true;
                            }
                        }
                    } else {
                        final var streamStates = recordManager
                                .loadLocalStreamStateBatch(playlist).blockingGet();

                        for (int i = 0; i < playlist.size(); i++) {
                            final var playlistItem = playlist.get(i);
                            final var streamStateEntity = streamStates.get(i);

                            final int indexInHistory = Collections.binarySearch(historyStreamIds,
                                    playlistItem.getStreamId());
                            final long duration = playlistItem.toStreamInfoItem().getDuration();

                            if (indexInHistory < 0 || (streamStateEntity != null
                                    && !streamStateEntity.isFinished(duration))) {
                                itemsToKeep.add(playlistItem);
                            } else if (!isThumbnailPermanent && !thumbnailVideoRemoved
                                    && playlistManager.getPlaylistThumbnailStreamId(playlistId)
                                    == playlistItem.getStreamEntity().getUid()) {
                                thumbnailVideoRemoved = true;
                            }
                        }
                    }

                    return new Pair<>(itemsToKeep, thumbnailVideoRemoved);
                });

        disposables.add(streamsMaybe.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(flow -> {
                    final List<PlaylistStreamEntry> itemsToKeep = flow.first;
                    final boolean thumbnailVideoRemoved = flow.second;

                    itemListAdapter.clearStreamItemList();
                    itemListAdapter.addItems(itemsToKeep);
                    debounceSaver.setHasChangesToSave();

                    if (thumbnailVideoRemoved) {
                        updateThumbnailUrl();
                    }

                    final long videoCount = itemListAdapter.getItemsList().size();
                    setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
                    if (videoCount == 0) {
                        showEmptyState();
                    }

                    hideLoading();
                    isRewritingPlaylist = false;
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Removing watched videos, partially watched=" + removePartiallyWatched))));
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistStreamEntry> result) {
        super.handleResult(result);
        if (itemListAdapter == null) {
            return;
        }

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
        setStreamCountAndOverallDuration(itemListAdapter.getItemsList());

        PlayButtonHelper.initPlaylistControlClickListener(activity, playlistControlBinding, this);

        hideLoading();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata/Streams Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void createRenameDialog() {
        if (playlistId == null || name == null || getContext() == null) {
            return;
        }

        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setSelection(dialogBinding.dialogEditText.getText().length());
        dialogBinding.dialogEditText.setText(name);

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.rename_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.rename, (dialogInterface, i) ->
                        changePlaylistName(dialogBinding.dialogEditText.getText().toString()))
                .show();
    }

    private void changePlaylistName(final String title) {
        if (playlistManager == null) {
            return;
        }

        this.name = title;
        setTitle(title);

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with new title=[" + title + "] items");
        }

        final Disposable disposable = playlistManager.renamePlaylist(playlistId, title)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> { /*Do nothing on success*/ }, throwable ->
                        showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                "Renaming playlist")));
        disposables.add(disposable);
    }

    private void changeThumbnailStreamId(final long thumbnailStreamId, final boolean isPermanent) {
        if (playlistManager == null || (!isPermanent && playlistManager
                .getIsPlaylistThumbnailPermanent(playlistId))) {
            return;
        }

        final Toast successToast = Toast.makeText(getActivity(),
                R.string.playlist_thumbnail_change_success,
                Toast.LENGTH_SHORT);

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with new thumbnail stream id=[" + thumbnailStreamId + "]");
        }

        final Disposable disposable = playlistManager
                .changePlaylistThumbnail(playlistId, thumbnailStreamId, isPermanent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignore -> successToast.show(), throwable ->
                        showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                "Changing playlist thumbnail")));
        disposables.add(disposable);
    }

    private void updateThumbnailUrl() {
        if (playlistManager.getIsPlaylistThumbnailPermanent(playlistId)) {
            return;
        }

        final long thumbnailStreamId;

        if (!itemListAdapter.getItemsList().isEmpty()) {
            thumbnailStreamId = ((PlaylistStreamEntry) itemListAdapter.getItemsList().get(0))
                    .getStreamEntity().getUid();
        } else {
            thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID;
        }

        changeThumbnailStreamId(thumbnailStreamId, false);
    }

    private void openRemoveDuplicatesDialog() {
        new AlertDialog.Builder(this.getActivity())
                .setTitle(R.string.remove_duplicates_title)
                .setMessage(R.string.remove_duplicates_message)
                .setPositiveButton(R.string.ok, (dialog, i) ->
                        removeDuplicatesInPlaylist())
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private void removeDuplicatesInPlaylist() {
        if (isRewritingPlaylist) {
            return;
        }
        isRewritingPlaylist = true;
        showLoading();

        final var streamsMaybe = playlistManager
                .getDistinctPlaylistStreams(playlistId).firstElement();


        disposables.add(streamsMaybe.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(itemsToKeep -> {
                    itemListAdapter.clearStreamItemList();
                    itemListAdapter.addItems(itemsToKeep);
                    setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
                    debounceSaver.setHasChangesToSave();

                    hideLoading();
                    isRewritingPlaylist = false;
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Removing duplicated streams"))));
    }

    private void deleteItem(final PlaylistStreamEntry item) {
        if (itemListAdapter == null) {
            return;
        }

        itemListAdapter.removeItem(item);
        if (playlistManager.getPlaylistThumbnailStreamId(playlistId) == item.getStreamId()) {
            updateThumbnailUrl();
        }

        setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
        debounceSaver.setHasChangesToSave();
    }

    /**
     * <p>Commit changes immediately if the playlist has been modified.</p>
     *  Delete operations and other modifications will be committed to ensure that the database
     *  is up to date, e.g. when the user adds the just deleted stream from another fragment.
     */
    @Override
    public void saveImmediate() {
        if (playlistManager == null || itemListAdapter == null) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || debounceSaver == null
                || !isLoadingComplete.get() || !debounceSaver.getIsModified()) {
            return;
        }

        final List<LocalItem> items = itemListAdapter.getItemsList();
        final List<Long> streamIds = new ArrayList<>(items.size());
        for (final LocalItem item : items) {
            if (item instanceof PlaylistStreamEntry) {
                streamIds.add(((PlaylistStreamEntry) item).getStreamId());
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with [" + streamIds.size() + "] items");
        }

        final Disposable disposable = playlistManager.updateJoin(playlistId, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (debounceSaver != null) {
                                debounceSaver.setNoChangesToSave();
                            }
                        },
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                );
        disposables.add(disposable);
    }


    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        if (shouldUseGridLayout(requireContext())) {
            directions |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        }
        return new ItemTouchHelper.SimpleCallback(directions,
                ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView,
                        viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder source,
                                  @NonNull final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || itemListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getBindingAdapterPosition();
                final int targetIndex = target.getBindingAdapterPosition();
                final boolean isSwapped = itemListAdapter.swapItems(sourceIndex, targetIndex);
                if (isSwapped) {
                    debounceSaver.setHasChangesToSave();
                }
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
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
            }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private PlayQueue getPlayQueueStartingAt(final PlaylistStreamEntry infoItem) {
        return getPlayQueue(Math.max(itemListAdapter.getItemsList().indexOf(infoItem), 0));
    }

    protected void showInfoItemDialog(final PlaylistStreamEntry item) {
        final StreamInfoItem infoItem = item.toStreamInfoItem();

        try {
            final Context context = getContext();
            final InfoItemDialog.Builder dialogBuilder =
                    new InfoItemDialog.Builder(getActivity(), context, this, infoItem);

            // add entries in the middle
            dialogBuilder.addAllEntries(
                    StreamDialogDefaultEntry.SET_AS_PLAYLIST_THUMBNAIL,
                    StreamDialogDefaultEntry.DELETE
            );

            // set custom actions
            // all entries modified below have already been added within the builder
            dialogBuilder
                    .setAction(
                            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND,
                            (f, i) -> NavigationHelper.playOnBackgroundPlayer(
                                    context, getPlayQueueStartingAt(item), true))
                    .setAction(
                            StreamDialogDefaultEntry.SET_AS_PLAYLIST_THUMBNAIL,
                            (f, i) ->
                                    changeThumbnailStreamId(item.getStreamEntity().getUid(),
                                            true))
                    .setAction(
                            StreamDialogDefaultEntry.DELETE,
                            (f, i) -> deleteItem(item))
                    .create()
                    .show();
        } catch (final IllegalArgumentException e) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, infoItem);
        }
    }

    private void setInitialData(final long pid, final String title) {
        this.playlistId = pid;
        this.name = !TextUtils.isEmpty(title) ? title : "";
    }

    private void setStreamCountAndOverallDuration(final ArrayList<LocalItem> itemsList) {
        if (activity != null && headerBinding != null) {
            final long streamCount = itemsList.size();
            final long playlistOverallDurationSeconds = itemsList.stream()
                    .filter(PlaylistStreamEntry.class::isInstance)
                    .map(PlaylistStreamEntry.class::cast)
                    .map(PlaylistStreamEntry::getStreamEntity)
                    .mapToLong(StreamEntity::getDuration)
                    .sum();
            headerBinding.playlistStreamCount.setText(
                    Localization.concatenateStrings(
                            Localization.localizeStreamCount(activity, streamCount),
                            Localization.getDurationString(playlistOverallDurationSeconds,
                                                            true, true))
            );
        }
    }

    @Override
    public PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        if (itemListAdapter == null) {
            return new SinglePlayQueue(Collections.emptyList(), 0);
        }

        final List<LocalItem> infoItems = itemListAdapter.getItemsList();
        final List<StreamInfoItem> streamInfoItems = new ArrayList<>(infoItems.size());
        for (final LocalItem item : infoItems) {
            if (item instanceof PlaylistStreamEntry) {
                streamInfoItems.add(((PlaylistStreamEntry) item).toStreamInfoItem());
            }
        }
        return new SinglePlayQueue(streamInfoItems, index);
    }

    /**
     * Creates a dialog to confirm whether the user wants to share the playlist
     * with the playlist details or just the list of stream URLs.
     * After the user has made a choice, the playlist is shared.
     */
    private void createShareConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_playlist)
                .setMessage(R.string.share_playlist_with_titles_message)
                .setCancelable(true)
                .setPositiveButton(R.string.share_playlist_with_titles, (dialog, which) ->
                    sharePlaylist(/* shouldSharePlaylistDetails= */ true)
                )
                .setNegativeButton(R.string.share_playlist_with_list, (dialog, which) ->
                    sharePlaylist(/* shouldSharePlaylistDetails= */ false)
                )
                .show();
    }

    public void setTabsPagerAdapter(
            @Nullable final MainFragment.SelectedTabsPagerAdapter tabsPagerAdapter) {
        this.tabsPagerAdapter = tabsPagerAdapter;
    }
}

