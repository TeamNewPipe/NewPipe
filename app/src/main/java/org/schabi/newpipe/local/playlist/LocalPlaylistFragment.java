package org.schabi.newpipe.local.playlist;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static org.schabi.newpipe.error.ErrorUtil.showUiErrorSnackbar;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.local.playlist.ExportPlaylistKt.export;
import static org.schabi.newpipe.local.playlist.PlayListShareMode.JUST_URLS;
import static org.schabi.newpipe.local.playlist.PlayListShareMode.WITH_TITLES;
import static org.schabi.newpipe.local.playlist.PlayListShareMode.YOUTUBE_TEMP_PLAYLIST;
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
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.learning.LearningPlaylistContext;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.learning.LearningDifficulty;
import org.schabi.newpipe.learning.LearningReviewDialog;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.databinding.LocalPlaylistHeaderBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.download.BulkDownloadDialog;
import org.schabi.newpipe.download.BulkDownloadItem;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.LocalUploaderNavigation;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.local.search.NoteSearch;
import org.schabi.newpipe.learning.LearningContentManager;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.learning.LearningPlaylistNotesDialog;
import org.schabi.newpipe.learning.LearningPlaylistProgress;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExpandableSearchViewHelper;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LocalPlaylistFragment extends BaseLocalListFragment<List<PlaylistStreamEntry>, Void>
        implements PlaylistControlViewHolder, DebounceSavable, ContextualSearchable {

    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;
    @State
    protected Long playlistId;
    @State
    protected String name;
    @State
    Parcelable itemsListState;
    private org.schabi.newpipe.local.history.ListFastScroller fastScroller;
    @State
    protected String contextualSearchQuery = "";
    private java.util.Map<Long, String> noteSearchText = Collections.emptyMap();
    @State
    protected boolean standaloneSearchExpanded;
    @State
    protected String playlistSortName = "PLAYLIST_ORDER";

    private LocalPlaylistHeaderBinding headerBinding;
    private View editToggle;
    private View selectMove;
    private PlaylistControlBinding playlistControlBinding;

    private ItemTouchHelper itemTouchHelper;
    @State
    public boolean editMode;
    private LocalPlaylistSelectionController selectionController;
    private final PlaylistSelectionSaveGate selectionSaveGate = new PlaylistSelectionSaveGate();
    private String playlistProfileId;
    private Snackbar swipeRemovalSnackbar;

    private LocalPlaylistManager playlistManager;
    private LearningContentManager learningContentManager;
    private Subscription databaseSubscription;
    private org.schabi.newpipe.local.ListSeparators separators;

    private CompositeDisposable disposables;

    /** Whether the playlist has been fully loaded from db. */
    private AtomicBoolean isLoadingComplete;
    /** Used to debounce saving playlist edits to disk. */
    private DebounceSaver debounceSaver;
    /** Flag to prevent simultaneous rewrites of the playlist. */
    private boolean isRewritingPlaylist = false;
    private boolean isDeletingPlaylist = false;
    private final List<PlaylistStreamEntry> unfilteredItems = new ArrayList<>();

    /**
     * The pager adapter that the fragment is created from when it is used as frontpage, i.e.
     * {@link #useAsFrontPage} is {@link true}.
     */
    @Nullable
    private MainFragment.SelectedTabsPagerAdapter tabsPagerAdapter = null;

    public static LocalPlaylistFragment getInstance(final long playlistId, final String name) {
        final var instance = new LocalPlaylistFragment();
        instance.setInitialData(playlistId, name);
        return instance;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null && playlistId != null) {
            playlistSortName = LocalPlaylistSortPreferences.get(requireContext(),
                    ProfileManager.getActiveProfileId(requireContext()), playlistId).name();
        }
        if (editMode) {
            playlistSortName = LocalPlaylistSortMode.PLAYLIST_ORDER.name();
        }
        playlistProfileId = ProfileManager.getActiveProfileId(requireContext());
        playlistManager = new LocalPlaylistManager(
                NewPipeDatabase.getInstance(requireContext()), playlistProfileId);
        learningContentManager = LearningContentManager.getInstance(requireContext());

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
        fastScroller = org.schabi.newpipe.local.history.ListFastScroller.attach(itemsList);
        setTitle(name);
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        headerBinding = LocalPlaylistHeaderBinding.inflate(activity.getLayoutInflater(), itemsList,
            false);
        playlistControlBinding = headerBinding.playlistControl;

        headerBinding.playlistTitleView.setSelected(true);

        return headerBinding::getRoot;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        itemListAdapter.setUploaderSelectedListener(selectedItem -> {
            if (selectedItem instanceof PlaylistStreamEntry) {
                LocalUploaderNavigation.openChannel(this,
                        ((PlaylistStreamEntry) selectedItem).getStreamEntity());
            }
        });

        editToggle = requireView().findViewById(R.id.playlist_edit_toggle);
        selectMove = requireView().findViewById(R.id.collection_select_move);
        headerBinding.playlistTitleView.setOnClickListener(view -> createRenameDialog());
        selectionController = new LocalPlaylistSelectionController(this, itemsList,
                itemListAdapter, selectMove, playlistProfileId, () -> playlistId,
                this::getSelectionItems,
                () -> debounceSaver != null && debounceSaver.getIsModified(),
                selectionSaveGate, this::saveImmediate, this::handleResult);
        editToggle.setOnClickListener(view -> {
            if (!selectionController.finishBeforeChange()) {
                return;
            }
            editMode = !editMode;
            if (editMode) {
                // Editing always uses the saved order, not the temporary sorted view.
                playlistSortName = LocalPlaylistSortMode.PLAYLIST_ORDER.name();
                applyPlaylistSearch();
            } else {
                // Preserve any reordered entries before restoring the user's view-only sort.
                captureUnfilteredItemsFromAdapter();
                playlistSortName = LocalPlaylistSortPreferences.get(requireContext(),
                        ProfileManager.getActiveProfileId(requireContext()), playlistId).name();
                applyPlaylistSearch();
            }
            updateEditMode();
            requireActivity().invalidateOptionsMenu();
        });
        updateEditMode();

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistStreamEntry entry) {
                    final StreamEntity item = entry.getStreamEntity();
                    if (item.isLocalMedia()) {
                        NavigationHelper.playOnMainPlayer(requireContext(),
                                getPlayQueueStartingAt(entry), false);
                        return;
                    }
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            item.getServiceId(), item.getUrl(), item.getTitle(),
                            isLearningPlaylist() ? getPlayQueueStartingAt(entry) : null, false);
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
                if (editMode && itemTouchHelper != null) {
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

        disposables.add(NewPipeDatabase.getInstance(requireContext()).learningNoteDAO()
                .localPlaylistNotes(playlistId, ProfileManager.getActiveProfileId(requireContext()))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(notes -> {
                    noteSearchText = NoteSearch.textByStream(notes);
                    if (ContextualSearchHelper.isActive(contextualSearchQuery)) {
                        applyPlaylistSearch();
                    }
                }, error -> showError(new ErrorInfo(error,
                        UserAction.REQUESTED_BOOKMARK, "Loading playlist notes"))));

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

        menu.findItem(R.id.menu_item_delete_playlist).setVisible(!useAsFrontPage);
        menu.findItem(R.id.menu_item_sort_playlist).setVisible(!editMode);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search_content);
        searchItem.setVisible(!useAsFrontPage);
        if (!useAsFrontPage) {
            ExpandableSearchViewHelper.configure(
                    searchItem,
                    getString(R.string.contextual_search_hint, name),
                    contextualSearchQuery,
                    standaloneSearchExpanded,
                    this::setContextualSearchQuery,
                    expanded -> standaloneSearchExpanded = expanded);
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_item_sort_playlist).setVisible(!editMode);
        menu.findItem(R.id.menu_item_learning_review).setVisible(
                LearningDifficulty.isEnabled(requireContext())
                        && learningContentManager.isLocalPlaylistMarked(playlistId));
        menu.findItem(R.id.menu_item_learning_notes).setVisible(
                LearningMode.isEnabled(requireContext())
                        && learningContentManager.isLocalPlaylistMarked(playlistId));
        final boolean enabled = LearningMode.isPlaylistProgressEnabled(requireContext())
                && learningContentManager.isLocalPlaylistMarked(playlistId);
        menu.findItem(R.id.menu_item_mark_all_watched).setVisible(enabled);
        menu.findItem(R.id.menu_item_reset_learning_progress).setVisible(enabled);
        final MenuItem learningItem = menu.findItem(R.id.menu_item_learning_content);
        final boolean learningEnabled = LearningMode.isEnabled(requireContext());
        learningItem.setVisible(learningEnabled);
        learningItem.setTitle(learningContentManager.isLocalPlaylistMarked(playlistId)
                ? R.string.learning_remove_content : R.string.learning_mark_content);
    }

    @Override
    public void onDestroyView() {
        if (fastScroller != null) {
            fastScroller.close();
            fastScroller = null;
        }
        if (selectionController != null) {
            selectionController.close();
            selectionController = null;
        }
        if (swipeRemovalSnackbar != null) {
            swipeRemovalSnackbar.dismiss();
            swipeRemovalSnackbar = null;
        }
        if (itemListAdapter != null) {
            itemListAdapter.unsetSelectedListener();
            itemListAdapter.unsetUploaderSelectedListener();
        }

        super.onDestroyView();

        headerBinding = null;
        editToggle = null;
        selectMove = null;
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
        selectionSaveGate.close();
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
        if (selectionController != null && !selectionController.finishBeforeChange()) {
            return true;
        }
        if (item.getItemId() == R.id.menu_item_learning_review) {
            LearningReviewDialog.show(getParentFragmentManager(),
                    LearningContentManager.localPlaylistSourceId(playlistId),
                    ProfileManager.getActiveProfileId(requireContext()), false);
            return true;
        }
        if (item.getItemId() == R.id.menu_item_learning_notes) {
            LearningPlaylistNotesDialog.show(getParentFragmentManager(),
                    LearningContentManager.localPlaylistSourceId(playlistId),
                    ProfileManager.getActiveProfileId(requireContext()));
        } else if (item.getItemId() == R.id.menu_item_share_playlist) {
            createShareConfirmationDialog();
        } else if (item.getItemId() == R.id.menu_item_download_playlist) {
            showBulkDownloadDialog();
        } else if (item.getItemId() == R.id.menu_item_sort_playlist) {
            showPlaylistSortDialog();
        } else if (item.getItemId() == R.id.menu_item_shuffle_playlist) {
            final PlayQueue playQueue = getCompletePlaylistPlayQueue();
            playQueue.shuffleFromStart();
            NavigationHelper.playOnMainPlayer(requireContext(), playQueue, false);
        } else if (item.getItemId() == R.id.menu_item_delete_playlist) {
            createDeleteDialog();
        } else if (item.getItemId() == R.id.menu_item_rename_playlist) {
            createRenameDialog();
        } else if (item.getItemId() == R.id.menu_item_remove_watched) {
            if (!isRewritingPlaylist) {
                openRemoveWatchedConfirmationDialog();
            }
        } else if (item.getItemId() == R.id.menu_item_remove_duplicates) {
            if (!isRewritingPlaylist) {
                openRemoveDuplicatesDialog();
            }
        } else if (item.getItemId() == R.id.menu_item_mark_all_watched) {
            confirmBulkProgressChange(true);
        } else if (item.getItemId() == R.id.menu_item_reset_learning_progress) {
            confirmBulkProgressChange(false);
        } else if (item.getItemId() == R.id.menu_item_learning_content) {
            toggleLearningPlaylist();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showPlaylistSortDialog() {
        if (selectionController != null && !selectionController.finishBeforeChange()) {
            return;
        }
        final LocalPlaylistSortMode[] modes = LocalPlaylistSortMode.values();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.local_playlist_sort_title)
                .setSingleChoiceItems(R.array.local_playlist_sort_entries,
                        LocalPlaylistSortMode.fromName(playlistSortName).ordinal(),
                        (dialog, which) -> {
                            playlistSortName = modes[which].name();
                            LocalPlaylistSortPreferences.set(requireContext(),
                                    ProfileManager.getActiveProfileId(requireContext()),
                                    playlistId, modes[which]);
                            applyPlaylistSearch();
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean isPlaylistSortActive() {
        return LocalPlaylistSortMode.fromName(playlistSortName)
                != LocalPlaylistSortMode.PLAYLIST_ORDER;
    }

    private void showBulkDownloadDialog() {
        final List<BulkDownloadItem> downloadItems =
                LocalPlaylistBulkDownloadMapper.from(unfilteredItems);
        if (downloadItems.isEmpty()) {
            Toast.makeText(requireContext(), R.string.bulk_download_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        BulkDownloadDialog.newInstance(downloadItems)
                .show(getChildFragmentManager(), "bulkDownloadDialog");
    }

    private void toggleLearningPlaylist() {
        final boolean marked = learningContentManager.isLocalPlaylistMarked(playlistId);
        disposables.add(learningContentManager
                .setLocalPlaylistMarked(playlistId, name, !marked)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            requireActivity().invalidateOptionsMenu();
                            startLoading(false);
                            Toast.makeText(requireContext(), marked
                                            ? R.string.learning_content_removed
                                            : R.string.learning_content_added,
                                    Toast.LENGTH_SHORT).show();
                        },
                        error -> Toast.makeText(requireContext(),
                                R.string.learning_content_update_error,
                                Toast.LENGTH_SHORT).show()
                ));
    }

    /**
     * Shares the playlist in one of 3 ways, depending on the value of {@code shareMode}:
     * <ul>
     *     <li>{@code JUST_URLS}: shares the URLs only.</li>
     *     <li>{@code WITH_TITLES}: each entry in the list is accompanied by its title.</li>
     *     <li>{@code YOUTUBE_TEMP_PLAYLIST}: shares as a YouTube temporary playlist.</li>
     * </ul>
     *
     * @param shareMode The way the playlist should be shared.
     */
    private void sharePlaylist(final PlayListShareMode shareMode) {
        final Context context = requireContext();

        disposables.add(playlistManager.getPlaylistStreams(playlistId)
            .flatMapSingle(playlist -> Single.just(export(

                shareMode,
                playlist,
                context
            )))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                urlsText -> {

                    final String content = shareMode == WITH_TITLES
                        ? context.getString(R.string.share_playlist_content_details,
                                            name,
                                            urlsText
                                           )
                        : urlsText;

                    ShareUtils.shareText(context, name, content);
                },
                throwable -> showUiErrorSnackbar(this, "Sharing playlist", throwable)
            )
        );
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

                    final var streamStates = recordManager
                            .loadLocalStreamStateBatch(playlist).blockingGet();

                    for (int i = 0; i < playlist.size(); i++) {
                        final var playlistItem = playlist.get(i);
                        final var streamStateEntity = streamStates.get(i);
                        final int indexInHistory = Collections.binarySearch(historyStreamIds,
                                playlistItem.getStreamId());
                        final long duration = playlistItem.toStreamInfoItem().getDuration();

                        if (indexInHistory < 0 // stream is not in history
                                // stream is in history but the streamStateEntity is null
                                // if the stream was played for less than 5 seconds, see
                                // StreamStateEntity#PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS
                                || streamStateEntity == null
                                || (!removePartiallyWatched
                                        && !streamStateEntity.isFinished(duration))) {
                            itemsToKeep.add(playlistItem);
                        } else if (!isThumbnailPermanent && !thumbnailVideoRemoved
                                && playlistManager.getPlaylistThumbnailStreamId(playlistId)
                                == playlistItem.getStreamEntity().getUid()) {
                            thumbnailVideoRemoved = true;
                        }
                    }

                    return new Pair<>(itemsToKeep, thumbnailVideoRemoved);
                });

        disposables.add(streamsMaybe.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(flow -> {
                    final List<PlaylistStreamEntry> itemsToKeep = flow.first;
                    final boolean thumbnailVideoRemoved = flow.second;

                    replaceUnfilteredItems(itemsToKeep);
                    applyPlaylistSearch();
                    debounceSaver.setHasChangesToSave();
                    saveImmediate();

                    if (thumbnailVideoRemoved) {
                        updateThumbnailUrl();
                    }

                    final long videoCount = unfilteredItems.size();
                    setStreamCountAndOverallDuration(unfilteredItems);
                    if (videoCount == 0) {
                        showEmptyState();
                    }

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
        updateLearningProgress(result);
        unfilteredItems.clear();
        unfilteredItems.addAll(result);
        setStreamCountAndOverallDuration(result);

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        applyPlaylistSearch();
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        PlayButtonHelper.initPlaylistControlClickListener(activity, playlistControlBinding, this);
    }

    @Override
    public void setContextualSearchQuery(@NonNull final String query) {
        final String normalizedQuery = ContextualSearchHelper.normalizeQuery(query);
        if (!ContextualSearchHelper.isActive(contextualSearchQuery)
                && ContextualSearchHelper.isActive(normalizedQuery)) {
            captureUnfilteredItemsFromAdapter();
        }
        contextualSearchQuery = normalizedQuery;
        applyPlaylistSearch();
    }

    private void captureUnfilteredItemsFromAdapter() {
        // A temporary sorted view must never replace the saved playlist order.
        if (itemListAdapter == null || isPlaylistSortActive()
                || ContextualSearchHelper.isActive(contextualSearchQuery)) {
            return;
        }
        unfilteredItems.clear();
        itemListAdapter.getItemsList().stream()
                .filter(PlaylistStreamEntry.class::isInstance)
                .map(PlaylistStreamEntry.class::cast)
                .forEach(unfilteredItems::add);
    }

    private void replaceUnfilteredItems(
            @NonNull final List<PlaylistStreamEntry> items) {
        unfilteredItems.clear();
        unfilteredItems.addAll(items);
    }

    private void applyPlaylistSearch() {
        if (itemListAdapter == null) {
            return;
        }
        if (separators == null) {
            separators = new org.schabi.newpipe.local.ListSeparators(requireContext(),
                    ProfileManager.getActiveProfileId(requireContext()), "playlist:" + playlistId);
        }
        itemListAdapter.setSeparatorGestures(
                (from, to) -> {
                    separators.move(itemListAdapter.getItemsList(), from, to);
                    if (debounceSaver != null) {
                        debounceSaver.setHasChangesToSave();
                    }
                },
                row -> separators.removeRow(itemListAdapter.getItemsList(), row),
                row -> separators.insertRow(itemListAdapter.getItemsList(), row),
                holder -> {
                    if (editMode && itemTouchHelper != null) {
                        itemTouchHelper.startDrag(holder);
                    }
                });
        itemListAdapter.setSeparators(ContextualSearchHelper.isActive(contextualSearchQuery)
                || isPlaylistSortActive() ? null : separators::labels, this::editSeparatorRow);
        final List<PlaylistStreamEntry> displayedItems = ContextualSearchHelper.filter(
                unfilteredItems, contextualSearchQuery,
                item -> new String[]{item.getStreamEntity().getTitle(),
                        item.getStreamEntity().getUploader(),
                        item.getStreamEntity().getLocalAlbum(),
                        item.getStreamEntity().getLocalFolder(),
                        noteSearchText.get(item.getStreamId())});
        itemListAdapter.clearStreamItemList();
        itemListAdapter.setItemHandleEnabled(editMode
                && !ContextualSearchHelper.isActive(contextualSearchQuery));
        itemListAdapter.addItems(LocalPlaylistSorting.sort(
                displayedItems, LocalPlaylistSortMode.fromName(playlistSortName)));

        final boolean searchActive = ContextualSearchHelper.isActive(contextualSearchQuery);
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(searchActive ? null : itemsList);
        }
        if (displayedItems.isEmpty()) {
            showEmptyState();
            if (searchActive) {
                setEmptyStateMessage(R.string.search_no_results);
            }
        } else {
            hideLoading();
        }
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

    private void createDeleteDialog() {
        if (playlistId == null || playlistManager == null || getContext() == null
                || isDeletingPlaylist) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> deletePlaylist())
                .show();
    }

    private void deletePlaylist() {
        if (playlistId == null || playlistManager == null || disposables == null
                || isDeletingPlaylist) {
            return;
        }

        isDeletingPlaylist = true;
        final Context appContext = requireContext().getApplicationContext();
        final String profileId = ProfileManager.getActiveProfileId(appContext);
        disposables.add(playlistManager.updatePlaylists(Collections.emptyList(),
                        Collections.singletonList(playlistId))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    LocalPlaylistSortPreferences.clear(appContext, profileId, playlistId);
                    if (debounceSaver != null) {
                        debounceSaver.setNoChangesToSave();
                    }
                    if (isAdded()) {
                        final var fragmentManager = getFM();
                        if (fragmentManager != null && !fragmentManager.isStateSaved()) {
                            if (!fragmentManager.popBackStackImmediate()) {
                                NavigationHelper.gotoMainFragment(fragmentManager);
                            }
                        }
                    }
                }, throwable -> {
                    isDeletingPlaylist = false;
                    showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                            "Deleting local playlist"));
                }));
    }

    private void createRenameDialog() {
        if (playlistId == null || name == null || getContext() == null) {
            return;
        }

        final var dialogBinding = DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setSelection(dialogBinding.dialogEditText.getText().length());
        dialogBinding.dialogEditText.setText(name);

        new MaterialAlertDialogBuilder(requireContext())
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

        final List<? extends LocalItem> items = ContextualSearchHelper.isActive(
                contextualSearchQuery) ? unfilteredItems : itemListAdapter.getItemsList();
        if (!items.isEmpty()) {
            thumbnailStreamId = ((PlaylistStreamEntry) items.get(0))
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
                    replaceUnfilteredItems(itemsToKeep);
                    applyPlaylistSearch();
                    setStreamCountAndOverallDuration(unfilteredItems);
                    debounceSaver.setHasChangesToSave();
                    saveImmediate();

                    isRewritingPlaylist = false;
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Removing duplicated streams"))));
    }

    private void deleteItem(final PlaylistStreamEntry item) {
        if (itemListAdapter == null) {
            return;
        }

        final boolean searchActive = ContextualSearchHelper.isActive(contextualSearchQuery);
        final boolean sorted = isPlaylistSortActive();
        if (searchActive || sorted) {
            unfilteredItems.remove(item);
        }
        itemListAdapter.removeItem(item);
        if (playlistManager.getPlaylistThumbnailStreamId(playlistId) == item.getStreamId()) {
            updateThumbnailUrl();
        }
        if (searchActive || sorted) {
            applyPlaylistSearch();
        }

        setStreamCountAndOverallDuration(
                searchActive || sorted ? unfilteredItems : itemListAdapter.getItemsList());
        debounceSaver.setHasChangesToSave();
        saveImmediate();
    }

    /**
     * <p>Commit changes immediately if the playlist has been modified.</p>
     *  Delete operations and other modifications will be committed to ensure that the database
     *  is up to date, e.g. when the user adds the just deleted stream from another fragment.
     */
    @Override
    public void saveImmediate() {
        if (playlistManager == null || itemListAdapter == null || isDeletingPlaylist) {
            return;
        }
        if (selectionController != null && selectionController.isWriting()) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || debounceSaver == null
                || !isLoadingComplete.get() || !debounceSaver.getIsModified()) {
            return;
        }

        final List<? extends LocalItem> items = ContextualSearchHelper.isActive(
                contextualSearchQuery) || isPlaylistSortActive()
                ? unfilteredItems : itemListAdapter.getItemsList();
        final List<Long> streamIds = new ArrayList<>(items.size());
        for (final LocalItem item : items) {
            if (item instanceof PlaylistStreamEntry entry) {
                streamIds.add(entry.getStreamId());
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with [" + streamIds.size() + "] items");
        }

        final Disposable disposable = selectionSaveGate.write(
                NewPipeDatabase.getInstance(requireContext()), playlistProfileId,
                playlistId, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        saved -> {
                            if (debounceSaver != null && selectionSaveGate.canClearDirty(saved,
                                    getSelectionItems().stream()
                                            .map(PlaylistStreamEntry::getStreamId)
                                            .collect(Collectors.toList()))) {
                                debounceSaver.setNoChangesToSave();
                            }
                            if (selectionController != null) {
                                selectionController.onSaveSettled();
                            }
                        },
                        throwable -> {
                            if (selectionController != null) {
                                selectionController.onSaveFailed();
                            }
                            showError(new ErrorInfo(throwable,
                                    UserAction.REQUESTED_BOOKMARK, "Saving playlist"));
                        }
                );
        disposables.add(disposable);
    }


    private void updateEditMode() {
        if (selectMove != null) {
            PlaylistCompactControls.update(requireView(), editMode);
        }
        if (itemListAdapter != null) {
            itemListAdapter.setItemHandleEnabled(editMode
                    && !ContextualSearchHelper.isActive(contextualSearchQuery));
            itemListAdapter.notifyDataSetChanged();
        }
    }

    private boolean isSwipeRemovalEnabled() {
        if (isPlaylistSortActive()) {
            return false;
        }
        final boolean enabled = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.playlist_swipe_remove_key), false);
        return LocalPlaylistSwipePolicy.isItemRemovalSwipeEnabled(
                useAsFrontPage, editMode, enabled);
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        if (shouldUseGridLayout(requireContext())) {
            directions |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        }
        return new ItemTouchHelper.SimpleCallback(directions,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getDragDirs(@NonNull final RecyclerView recyclerView,
                                   @NonNull final RecyclerView.ViewHolder viewHolder) {
                return editMode && !itemListAdapter.isSelecting()
                        && !ContextualSearchHelper.isActive(contextualSearchQuery)
                        ? super.getDragDirs(recyclerView, viewHolder) : 0;
            }

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
                if (!editMode || itemListAdapter == null) {
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
            public void clearView(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                saveImmediate();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return isSwipeRemovalEnabled();
            }

            @Override
            public int getSwipeDirs(@NonNull final RecyclerView recyclerView,
                                    @NonNull final RecyclerView.ViewHolder viewHolder) {
                if (!isSwipeRemovalEnabled()
                        || itemListAdapter == null || itemListAdapter.isSelecting()) {
                    return ItemTouchHelper.ACTION_STATE_IDLE;
                }

                final int itemIndex = itemListAdapter.getItemIndex(
                        viewHolder.getBindingAdapterPosition());
                if (itemIndex < 0 || !(itemListAdapter.getItemsList().get(itemIndex)
                        instanceof PlaylistStreamEntry)) {
                    return ItemTouchHelper.ACTION_STATE_IDLE;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                removeItemBySwipe(viewHolder.getBindingAdapterPosition());
            }
        };
    }

    private void removeItemBySwipe(final int adapterPosition) {
        if (itemListAdapter == null) {
            return;
        }

        final int itemIndex = itemListAdapter.getItemIndex(adapterPosition);
        if (itemIndex < 0 || !(itemListAdapter.getItemsList().get(itemIndex)
                instanceof PlaylistStreamEntry)) {
            if (adapterPosition != RecyclerView.NO_POSITION) {
                itemListAdapter.notifyItemChanged(adapterPosition);
            }
            return;
        }

        if (swipeRemovalSnackbar != null) {
            swipeRemovalSnackbar.dismiss();
        }

        final LocalItem removedItem = itemListAdapter.removeItemAt(itemIndex);
        if (!(removedItem instanceof PlaylistStreamEntry removedEntry)) {
            if (adapterPosition != RecyclerView.NO_POSITION) {
                itemListAdapter.notifyItemChanged(adapterPosition);
            }
            return;
        }

        persistSwipeChange();

        final Snackbar snackbar = Snackbar.make(requireView(),
                R.string.playlist_item_removed, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, view -> {
            if (itemListAdapter == null) {
                return;
            }
            itemListAdapter.insertItemAt(itemIndex, removedEntry, adapterPosition);
            persistSwipeChange();
        });
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(final Snackbar transientBottomBar, final int event) {
                if (swipeRemovalSnackbar == transientBottomBar) {
                    swipeRemovalSnackbar = null;
                }
            }
        });
        swipeRemovalSnackbar = snackbar;
        snackbar.show();
    }

    private void persistSwipeChange() {
        updateThumbnailUrl();
        setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
        debounceSaver.setHasChangesToSave();
        saveImmediate();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private PlayQueue getPlayQueueStartingAt(final PlaylistStreamEntry infoItem) {
        return getPlayQueue(Math.max(itemListAdapter.getItemsList().indexOf(infoItem), 0));
    }

    protected void showInfoItemDialog(final PlaylistStreamEntry item) {
        if (item.getStreamEntity().isLocalMedia()) {
            final boolean learningEnabled = LearningMode.isEnabled(requireContext());
            final boolean marked = learningContentManager.isStreamSourceMarked(
                    item.getStreamEntity().getServiceId(), item.getStreamEntity().getUrl());
            final String[] actions = learningEnabled
                    ? new String[] {
                        getString(R.string.play),
                        getString(R.string.local_media_play_background),
                        getString(marked
                                ? R.string.learning_remove_content
                                : R.string.learning_mark_content),
                        getString(R.string.delete),
                        getString(R.string.playlist_video_notes)
                    }
                    : new String[] {
                        getString(R.string.play),
                        getString(R.string.local_media_play_background),
                        getString(R.string.delete),
                        getString(R.string.playlist_video_notes)
                    };
            final List<String> menuActions = new ArrayList<>(java.util.Arrays.asList(actions));
            if (!ContextualSearchHelper.isActive(contextualSearchQuery)) {
                menuActions.add(getString(R.string.list_separator_add));
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(item.getStreamEntity().getTitle())
                    .setItems(menuActions.toArray(new String[0]), (dialog, which) -> {
                        if (which == actions.length) {
                            editSeparatorAt(itemListAdapter.getItemsList().indexOf(item));
                        } else if (which == 0) {
                            NavigationHelper.playOnMainPlayer(requireContext(),
                                    getPlayQueueStartingAt(item), false);
                        } else if (which == 1) {
                            NavigationHelper.playOnBackgroundPlayer(requireContext(),
                                    getPlayQueueStartingAt(item), true);
                        } else if (learningEnabled && which == 2) {
                            disposables.add(learningContentManager
                                    .setStreamMarked(item.getStreamEntity(), !marked)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            () -> Toast.makeText(requireContext(), marked
                                                            ? R.string.learning_content_removed
                                                            : R.string.learning_content_added,
                                                    Toast.LENGTH_SHORT).show(),
                                            error -> Toast.makeText(requireContext(),
                                                    R.string.learning_content_update_error,
                                                    Toast.LENGTH_SHORT).show()
                                    ));
                        } else if (which == (learningEnabled ? 3 : 2)) {
                            deleteItem(item);
                        } else {
                            showVideoNotes(item);
                        }
                    })
                    .show();
            return;
        }
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

            dialogBuilder.addEntry(new org.schabi.newpipe.info_list.dialog.StreamDialogEntry(
                    R.string.playlist_video_notes, (f, i) -> showVideoNotes(item)));

            if (!ContextualSearchHelper.isActive(contextualSearchQuery)) {
                dialogBuilder.addEntry(new org.schabi.newpipe.info_list.dialog.StreamDialogEntry(
                        R.string.list_separator_add, (f, i) -> editSeparatorAt(
                                itemListAdapter.getItemsList().indexOf(item))));
            }

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

    private void editSeparatorRow(final int row) {
        if (selectionController != null && !selectionController.finishBeforeChange()) {
            return;
        }
        if (separators != null && itemListAdapter != null) {
            separators.editRow(requireContext(), itemListAdapter.getItemsList(), row,
                    () -> itemListAdapter.setSeparators(separators::labels,
                            this::editSeparatorRow),
                    false);
        }
    }

    private void editSeparatorAt(final int index) {
        if (selectionController != null && !selectionController.finishBeforeChange()) {
            return;
        }
        if (separators != null && itemListAdapter != null) {
            separators.edit(requireContext(), itemListAdapter.getItemsList(), index,
                    () -> itemListAdapter.setSeparators(separators::labels,
                            this::editSeparatorRow));
        }
    }

    private void showVideoNotes(final PlaylistStreamEntry item) {
        org.schabi.newpipe.learning.VideoNotesDialog.show(getParentFragmentManager(),
                item.getStreamEntity().getUid(), item.getStreamEntity().getTitle());
    }

    private List<PlaylistStreamEntry> getSelectionItems() {
        return LocalPlaylistSelectionController.savedOrder(itemListAdapter,
                unfilteredItems, contextualSearchQuery, playlistSortName);
    }

    private void setInitialData(final long pid, final String title) {
        this.playlistId = pid;
        this.name = !TextUtils.isEmpty(title) ? title : "";
    }

    private void setStreamCountAndOverallDuration(
            final List<? extends LocalItem> itemsList) {
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

    private void updateLearningProgress(final List<PlaylistStreamEntry> streams) {
        if (headerBinding == null) {
            return;
        }
        final boolean enabled = LearningMode.isPlaylistProgressEnabled(requireContext())
                && learningContentManager.isLocalPlaylistMarked(playlistId);
        headerBinding.playlistLearningProgressContainer.getRoot().setVisibility(
                enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            return;
        }

        final var progress = LearningPlaylistProgress.calculate(streams);
        final int eligible = progress.getEligible();
        final int completed = progress.getCompleted();
        final int percentage = progress.getPercentage();
        headerBinding.playlistLearningProgressContainer.playlistLearningProgressText.setText(
                eligible == 0
                        ? getString(R.string.learning_progress_unavailable)
                        : completed == eligible
                                ? getString(R.string.learning_progress_completed)
                                : getString(R.string.learning_progress_format, completed, eligible,
                                        percentage));
        headerBinding.playlistLearningProgressContainer.playlistLearningProgressBar
                .setProgressCompat(percentage, true);
    }

    private void confirmBulkProgressChange(final boolean markWatched) {
        new AlertDialog.Builder(requireContext())
                .setTitle(markWatched
                        ? R.string.learning_mark_all_watched
                        : R.string.learning_reset_progress)
                .setMessage(markWatched
                        ? R.string.learning_mark_all_watched_confirm
                        : R.string.learning_reset_progress_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) ->
                        applyBulkProgressChange(markWatched))
                .show();
    }

    private void applyBulkProgressChange(final boolean markWatched) {
        if (itemListAdapter == null) {
            return;
        }
        final HistoryRecordManager manager = new HistoryRecordManager(requireContext());
        final List<Completable> operations = itemListAdapter.getItemsList().stream()
                .filter(PlaylistStreamEntry.class::isInstance)
                .map(PlaylistStreamEntry.class::cast)
                .map(entry -> markWatched
                        ? (entry.getStreamEntity().isLocalMedia()
                                ? manager.markAsWatched(entry.toPlayQueueItem()).ignoreElement()
                                : manager.markAsWatched(entry.toStreamInfoItem()).ignoreElement())
                        : manager.deleteStreamHistoryAndState(entry.getStreamId()))
                .collect(Collectors.toList());
        disposables.add(Completable.concat(operations)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Toast.makeText(requireContext(),
                                markWatched
                                        ? R.string.learning_mark_all_watched_done
                                        : R.string.learning_reset_progress_done,
                                Toast.LENGTH_SHORT).show(),
                        throwable -> showUiErrorSnackbar(
                                this, "Updating learning progress", throwable)
                ));
    }

    @Override
    public PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        if (itemListAdapter == null) {
            return new SinglePlayQueue(Collections.emptyList(), 0);
        }

        return getPlayQueue(itemListAdapter.getItemsList(), index);
    }

    private PlayQueue getCompletePlaylistPlayQueue() {
        if (itemListAdapter == null) {
            return new SinglePlayQueue(Collections.emptyList(), 0);
        }

        final List<? extends LocalItem> infoItems = ContextualSearchHelper.isActive(
                contextualSearchQuery) ? unfilteredItems : itemListAdapter.getItemsList();
        return getPlayQueue(infoItems, 0);
    }

    private PlayQueue getPlayQueue(final List<? extends LocalItem> infoItems, final int index) {
        final boolean learning = isLearningPlaylist();
        final List<? extends LocalItem> orderedItems = learning
                && ContextualSearchHelper.isActive(contextualSearchQuery)
                ? unfilteredItems : infoItems;
        final int selectedIndex = learning && index >= 0 && index < infoItems.size()
                ? Math.max(0, orderedItems.indexOf(infoItems.get(index))) : index;
        final List<PlayQueueItem> queueItems = new ArrayList<>(orderedItems.size());
        for (final LocalItem item : orderedItems) {
            if (item instanceof PlaylistStreamEntry) {
                queueItems.add(((PlaylistStreamEntry) item).toPlayQueueItem());
            }
        }
        final PlayQueue queue = new LocalMediaPlayQueue(queueItems, selectedIndex);
        if (learning) {
            queue.setLearningPlaylistContext(new LearningPlaylistContext(
                    LearningContentManager.localPlaylistSourceId(playlistId), name,
                    ProfileManager.getActiveProfileId(requireContext())));
        }
        return queue;
    }

    private boolean isLearningPlaylist() {
        return LearningMode.isEnabled(requireContext())
                && learningContentManager.isLocalPlaylistMarked(playlistId);
    }

    /**
     * Creates a dialog to confirm whether the user wants to share the playlist
     * with the playlist details or just the list of stream URLs.
     * After the user has made a choice, the playlist is shared.
     */
    private void createShareConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_playlist)
                .setCancelable(true)
                .setPositiveButton(R.string.share_playlist_with_titles, (dialog, which) ->
                    sharePlaylist(WITH_TITLES)
                )
                .setNeutralButton(R.string.share_playlist_as_youtube_temporary_playlist,
                    (dialog, which) -> sharePlaylist(YOUTUBE_TEMP_PLAYLIST)
                )
                .setNegativeButton(R.string.share_playlist_with_list, (dialog, which) ->
                    sharePlaylist(JUST_URLS)
                )
                .show();
    }

    /**
     * Opens a confirmation dialog to remove watched streams from the playlist.
     * The user can also choose to remove partially watched streams.
     */
    private void openRemoveWatchedConfirmationDialog() {
        final android.widget.CheckBox removePartiallyWatchedCheckbox =
                new android.widget.CheckBox(requireContext());
        removePartiallyWatchedCheckbox.setText(
                R.string.remove_watched_popup_partially_watched_streams);

        // Wrap the checkbox in a container with dialog-like horizontal padding
        // so it aligns with the dialog title and message on the start side.
        final LinearLayout checkboxContainer = new LinearLayout(requireContext());
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);
        final int padding = DeviceUtils.dpToPx(20, requireContext());
        checkboxContainer.setPadding(padding, padding, padding, 0);
        checkboxContainer.addView(removePartiallyWatchedCheckbox,
                new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.remove_watched_popup_warning)
                .setTitle(R.string.remove_watched_popup_title)
                .setView(checkboxContainer)
                .setPositiveButton(R.string.yes, (d, id) ->
                        removeWatchedStreams(removePartiallyWatchedCheckbox.isChecked()))
                .setNegativeButton(R.string.cancel, (d, id) -> d.cancel())
                .show();
    }

    public void setTabsPagerAdapter(
            @Nullable final MainFragment.SelectedTabsPagerAdapter tabsPagerAdapter) {
        this.tabsPagerAdapter = tabsPagerAdapter;
    }
}
