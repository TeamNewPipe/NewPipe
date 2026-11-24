package org.schabi.newpipe.local.bookmark;

import static org.schabi.newpipe.local.bookmark.MergedPlaylistManager.getMergedOrderedPlaylists;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.learning.model.BookmarkNote;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.learning.LearningContentManager;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.learning.LearningPlaylistMarker;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.PlaylistCompactControls;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.local.search.NoteSearch;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.debounce.DebounceSavable;
import org.schabi.newpipe.util.debounce.DebounceSaver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public final class BookmarkFragment extends BaseLocalListFragment<List<PlaylistLocalItem>, Void>
        implements DebounceSavable, ContextualSearchable {

    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;
    private static final int PLAYLIST_ACTION_PLAY = 0;
    private static final int PLAYLIST_ACTION_ENQUEUE_NEXT = 1;
    private static final int PLAYLIST_ACTION_ENQUEUE = 2;
    @State
    Parcelable itemsListState;

    private Subscription databaseSubscription;
    private org.schabi.newpipe.local.ListSeparators separators;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;
    private ItemTouchHelper itemTouchHelper;
    @State
    public boolean editMode;
    private org.schabi.newpipe.local.CollectionSelectionController selectionController;

    /* Have the bookmarked playlists been fully loaded from db */
    private AtomicBoolean isLoadingComplete;

    /* Gives enough time to avoid interrupting user sorting operations */
    @Nullable
    private DebounceSaver debounceSaver;

    private List<Pair<Long, LocalItem.LocalItemType>> deletedItems;
    private List<PlaylistLocalItem> completePlaylists = Collections.emptyList();
    private String contextualSearchQuery = "";
    private List<BookmarkNote> bookmarkNotes = Collections.emptyList();
    private java.util.Set<String> learningSources = Collections.emptySet();
    private static final String LEARNING_CATEGORY = PlaylistOpeningPreferences.LEARNING_CATEGORY;
    private PlaylistCategories categories = new PlaylistCategories();
    @State
    public String selectedCategory = PlaylistCategories.ALL;
    @State
    public String categoryProfileId;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (activity == null) {
            return;
        }
        final String restoredProfileId = categoryProfileId;
        categoryProfileId = ProfileManager.getActiveProfileId(requireContext());
        if (!categoryProfileId.equals(restoredProfileId)) {
            itemsListState = null;
        }
        final AppDatabase database = NewPipeDatabase.getInstance(activity);
        localPlaylistManager = new LocalPlaylistManager(database, categoryProfileId);
        remotePlaylistManager = new RemotePlaylistManager(database, categoryProfileId);
        disposables = new CompositeDisposable();

        isLoadingComplete = new AtomicBoolean();
        debounceSaver = new DebounceSaver(3000, this);

        deletedItems = new ArrayList<>();
        readCategories();
        selectedCategory = PlaylistOpeningPreferences.resolveInitialCategory(requireContext(),
                categoryProfileId, categories, restoredProfileId, selectedCategory);
    }

    private void readCategories() {
        categories = new PlaylistCategories();
        try {
            final var preferences =
                    PreferenceManager.getDefaultSharedPreferences(requireContext());
            final String categoryKey = PlaylistCategories.preferenceKey(categoryProfileId);
            String categoryJson = preferences.getString(categoryKey, null);
            if (categoryJson == null
                    && ProfileManager.DEFAULT_PROFILE_ID.equals(categoryProfileId)) {
                categoryJson = preferences.getString(PlaylistCategories.PREFERENCE_KEY, "");
                if (categoryJson != null && !categoryJson.isEmpty()) {
                    preferences.edit().putString(categoryKey, categoryJson).apply();
                }
            }
            categories = PlaylistCategories.fromJson(categoryJson == null ? "" : categoryJson);
        } catch (final com.grack.nanojson.JsonParserException error) {
            Log.e("BookmarkFragment", "Could not read playlist categories", error);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final android.view.Menu menu,
                                    @NonNull final android.view.MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(android.view.Menu.NONE, R.id.smart_playlists,
                android.view.Menu.NONE, R.string.smart_playlists_title);
        menu.add(android.view.Menu.NONE, R.id.text_playlist_import,
                android.view.Menu.NONE, R.string.text_playlist_title);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final android.view.MenuItem item) {
        if (item.getItemId() == R.id.smart_playlists) {
            org.schabi.newpipe.local.playlist.smart.SmartPlaylistsDialog.show(
                    getChildFragmentManager(), requireContext());
            return true;
        }
        if (item.getItemId() == R.id.text_playlist_import) {
            org.schabi.newpipe.local.playlist.importing.TextPlaylistImportDialog
                    .show(getChildFragmentManager());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {

        if (!useAsFrontPage) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null) {
            setTitle(activity.getString(R.string.tab_bookmarks));
            refreshCategories();
            PlaylistOpeningPreferences.rememberCategory(requireContext(),
                    categoryProfileId, selectedCategory);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (categoryProfileId != null && PlaylistOpeningPreferences.affectsCategories(
                requireContext(), categoryProfileId, key)) {
            refreshCategories();
        }
    }

    private void refreshCategories() {
        if (getView() != null && !isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        readCategories();
        selectedCategory = PlaylistOpeningPreferences.validateSelection(requireContext(),
                categoryProfileId, categories, selectedCategory);
        updateCategoryLabel();
        if (getView() != null) {
            showFilteredPlaylists();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        selectedCategory = PlaylistOpeningPreferences.validateSelection(requireContext(),
                categoryProfileId, categories, selectedCategory);
        rootView.findViewById(R.id.playlist_category_filter)
                .setOnClickListener(view -> chooseCategory(null));
        rootView.findViewById(R.id.playlist_category_manage)
                .setOnClickListener(view -> manageCategories());
        PlaylistCompactControls.update(rootView, editMode);
        rootView.findViewById(R.id.playlist_edit_toggle).setOnClickListener(view -> {
            editMode = !editMode;
            updateEditMode();
        });
        rootView.findViewById(R.id.collection_select_move).setOnClickListener(view -> {
            if (!editMode || isPlaylistListFiltered() || separators == null) {
                return;
            }
            if (selectionController != null) {
                selectionController.close();
            }
            selectionController = new org.schabi.newpipe.local.CollectionSelectionController(
                    (androidx.appcompat.app.AppCompatActivity) requireActivity(),
                    itemsList, itemListAdapter, separators, () -> {
                        completePlaylists = itemListAdapter.getItemsList().stream()
                                .filter(PlaylistLocalItem.class::isInstance)
                                .map(PlaylistLocalItem.class::cast)
                                .collect(java.util.stream.Collectors.toList());
                        if (debounceSaver != null) {
                            debounceSaver.setHasChangesToSave();
                        }
                    });
            selectionController.show();
        });
        updateCategoryLabel();

        itemListAdapter.setUseItemHandle(true);
        updateEditMode();
        itemListAdapter.setItemHandleEnabled(editMode && !isPlaylistListFiltered());
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                final FragmentManager fragmentManager = getFM();

                if (selectedItem instanceof PlaylistMetadataEntry) {
                    final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                    NavigationHelper.openLocalPlaylistFragment(fragmentManager, entry.getUid(),
                            entry.getOrderingName());

                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            entry.getServiceId(),
                            entry.getUrl(),
                            entry.getOrderingName());
                } else if (selectedItem instanceof PlaylistStreamEntry entry) {
                    final var stream = entry.getStreamEntity();
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            stream.getServiceId(), stream.getUrl(), stream.getTitle(), null, false);
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistMetadataEntry) {
                    showLocalDialog((PlaylistMetadataEntry) selectedItem);
                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    showRemoteDeleteDialog((PlaylistRemoteEntity) selectedItem);
                } else if (selectedItem instanceof PlaylistStreamEntry entry) {
                    org.schabi.newpipe.learning.VideoNotesDialog.show(getParentFragmentManager(),
                            entry.getStreamId(), entry.getStreamEntity().getTitle());
                }
            }

            @Override
            public void drag(final LocalItem selectedItem,
                             final RecyclerView.ViewHolder viewHolder) {
                if (!isPlaylistListFiltered() && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        if (debounceSaver != null) {
            disposables.add(debounceSaver.getDebouncedSaver());
            debounceSaver.setNoChangesToSave();
        }
        isLoadingComplete.set(false);
        disposables.add(NewPipeDatabase.getInstance(requireContext()).learningContentDAO()
                .observeSourceIds().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(ids -> {
                    learningSources = new java.util.HashSet<>(ids);
                    showFilteredPlaylists();
                }, error -> showError(new ErrorInfo(error,
                        UserAction.REQUESTED_BOOKMARK, "Loading learning playlists"))));

        disposables.add(NewPipeDatabase.getInstance(requireContext()).learningNoteDAO()
                .bookmarkNotes(categoryProfileId)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(notes -> {
                    bookmarkNotes = notes;
                    if (isContextualSearchActive()) {
                        showFilteredPlaylists();
                    }
                }, error -> showError(new ErrorInfo(error,
                        UserAction.REQUESTED_BOOKMARK, "Loading bookmark notes"))));

        getMergedOrderedPlaylists(localPlaylistManager, remotePlaylistManager)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistsSubscriber());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();

        // Save on exit
        saveImmediate();
    }

    @Override
    public void onDestroyView() {
        if (selectionController != null) {
            selectionController.close();
            selectionController = null;
        }
        super.onDestroyView();

        if (disposables != null) {
            disposables.clear();
        }
        if (databaseSubscription != null) {
            databaseSubscription.cancel();
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

        debounceSaver = null;
        disposables = null;
        localPlaylistManager = null;
        remotePlaylistManager = null;
        itemsListState = null;

        isLoadingComplete = null;
        deletedItems = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistLocalItem>> getPlaylistsSubscriber() {
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
            public void onNext(final List<PlaylistLocalItem> subscriptions) {
                if (debounceSaver == null || !debounceSaver.getIsModified()) {
                    handleResult(subscriptions);
                    isLoadingComplete.set(true);
                }
                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(new ErrorInfo(exception,
                        UserAction.REQUESTED_BOOKMARK, "Loading playlists"));
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistLocalItem> result) {
        super.handleResult(result);
        completePlaylists = new ArrayList<>(result);
        showFilteredPlaylists();
    }

    private void showFilteredPlaylists() {
        if (itemListAdapter == null) {
            return;
        }

        if (separators == null) {
            separators = new org.schabi.newpipe.local.ListSeparators(requireContext(),
                    categoryProfileId, "bookmarks");
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
        if (getView() != null) {
            getView().findViewById(R.id.collection_select_move)
                    .setEnabled(!isPlaylistListFiltered());
        }
        itemListAdapter.setSeparators(isPlaylistListFiltered() ? null : separators::labels,
                this::editSeparatorRow);
        itemListAdapter.clearStreamItemList();
        itemListAdapter.setItemHandleEnabled(editMode && !isPlaylistListFiltered());
        setEmptyStateMessage(isPlaylistListFiltered()
                ? R.string.search_no_results : R.string.empty_list_subtitle);

        final List<PlaylistLocalItem> categoryPlaylists = completePlaylists.stream()
                .filter(playlist -> LEARNING_CATEGORY.equals(selectedCategory)
                        ? isLearningPlaylist(playlist)
                        : categories.matches(categoryKey(playlist), selectedCategory))
                .collect(java.util.stream.Collectors.toList());
        final List<LocalItem> results = new ArrayList<>(ContextualSearchHelper.filter(
                categoryPlaylists, contextualSearchQuery,
                playlist -> new String[]{playlist.getOrderingName()}));
        results.addAll(NoteSearch.bookmarkStreams(bookmarkNotes, contextualSearchQuery,
                categoryPlaylists.stream().map(BookmarkFragment::categoryKey)
                        .collect(java.util.stream.Collectors.toSet())));

        if (results.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(results);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        hideLoading();
    }

    @Override
    public void setContextualSearchQuery(@NonNull final String query) {
        final String normalizedQuery = ContextualSearchHelper.normalizeQuery(query);
        if (!isPlaylistListFiltered() && ContextualSearchHelper.isActive(normalizedQuery)) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        contextualSearchQuery = normalizedQuery;
        showFilteredPlaylists();
    }

    private boolean isContextualSearchActive() {
        return ContextualSearchHelper.isActive(contextualSearchQuery);
    }

    private void captureCanonicalOrderFromAdapter() {
        if (itemListAdapter == null || isPlaylistListFiltered()) {
            return;
        }
        final List<PlaylistLocalItem> displayedOrder = new ArrayList<>();
        for (final LocalItem item : itemListAdapter.getItemsList()) {
            if (item instanceof PlaylistLocalItem) {
                displayedOrder.add((PlaylistLocalItem) item);
            }
        }
        completePlaylists = displayedOrder;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (disposables != null) {
            disposables.clear();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void changeLocalPlaylistName(final long id, final String name) {
        if (localPlaylistManager == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + id + "] "
                    + "with new name=[" + name + "] items");
        }

        final Disposable disposable = localPlaylistManager.renamePlaylist(id, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> { /*Do nothing on success*/ }, throwable -> showError(
                        new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK,
                                "Changing playlist name")));
        disposables.add(disposable);
    }

    private void deleteItem(final PlaylistLocalItem item) {
        if (itemListAdapter == null) {
            return;
        }

        if (item instanceof PlaylistRemoteEntity) {
            deleteRemoteBookmark((PlaylistRemoteEntity) item);
            return;
        }

        if (isPlaylistListFiltered()) {
            disposables.add(localPlaylistManager.updatePlaylists(Collections.emptyList(),
                            Collections.singletonList(item.getUid()))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> { }, throwable -> showError(new ErrorInfo(throwable,
                            UserAction.REQUESTED_BOOKMARK, "Deleting categorized playlist"))));
            return;
        }

        itemListAdapter.removeItem(item);

        if (item instanceof PlaylistMetadataEntry) {
            deletedItems.add(new Pair<>(item.getUid(),
                    LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM));
        }

        if (debounceSaver != null) {
            debounceSaver.setHasChangesToSave();
            saveImmediate();
        }
    }

    private void deleteRemoteBookmark(final PlaylistRemoteEntity item) {
        if (remotePlaylistManager == null || disposables == null) {
            return;
        }

        disposables.add(remotePlaylistManager.deletePlaylist(item.getUid())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                    // The database Flowable refreshes the list. Removing here also gives immediate
                    // feedback if that refresh is queued behind another emission.
                    if (itemListAdapter != null) {
                        itemListAdapter.removeItem(item);
                    }
                    completePlaylists = completePlaylists.stream()
                            .filter(playlist -> playlist.getUid() != item.getUid()
                                    || playlist.getLocalItemType()
                                    != LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM)
                            .collect(java.util.stream.Collectors.toList());
                }, throwable -> showError(new ErrorInfo(throwable,
                        UserAction.REQUESTED_BOOKMARK, "Deleting playlist bookmark"))));
    }

    @Override
    public void saveImmediate() {
        if (itemListAdapter == null || isPlaylistListFiltered()) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || debounceSaver == null
                || !isLoadingComplete.get() || !debounceSaver.getIsModified()) {
            return;
        }

        final List<LocalItem> items = itemListAdapter.getItemsList();
        final List<PlaylistMetadataEntry> localItemsUpdate = new ArrayList<>();
        final List<Long> localItemsDeleteUid = new ArrayList<>();
        final List<PlaylistRemoteEntity> remoteItemsUpdate = new ArrayList<>();
        final List<Long> remoteItemsDeleteUid = new ArrayList<>();

        // Calculate display index
        for (int i = 0; i < items.size(); i++) {
            final LocalItem item = items.get(i);

            if (item instanceof PlaylistMetadataEntry
                    && ((PlaylistMetadataEntry) item).getDisplayIndex() != i) {
                ((PlaylistMetadataEntry) item).setDisplayIndex((long) i);
                localItemsUpdate.add((PlaylistMetadataEntry) item);
            } else if (item instanceof PlaylistRemoteEntity
                    && ((PlaylistRemoteEntity) item).getDisplayIndex() != i) {
                ((PlaylistRemoteEntity) item).setDisplayIndex((long) i);
                remoteItemsUpdate.add((PlaylistRemoteEntity) item);
            }
        }

        // Find deleted items
        for (final Pair<Long, LocalItem.LocalItemType> item : deletedItems) {
            if (item.second.equals(LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM)) {
                localItemsDeleteUid.add(item.first);
            } else if (item.second.equals(LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM)) {
                remoteItemsDeleteUid.add(item.first);
            }
        }

        deletedItems.clear();

        // 1. Update local playlists
        // 2. Update remote playlists
        // 3. Set NoChangesToSave
        disposables.add(localPlaylistManager.updatePlaylists(localItemsUpdate, localItemsDeleteUid)
                .mergeWith(remotePlaylistManager.updatePlaylists(
                        remoteItemsUpdate, remoteItemsDeleteUid))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                            if (debounceSaver != null) {
                                debounceSaver.setNoChangesToSave();
                            }
                        },
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                ));

    }

    private void updateEditMode() {
        if (!editMode && selectionController != null) {
            selectionController.close();
        }
        final View root = getView();
        if (root != null) {
            root.findViewById(R.id.collection_select_move).setEnabled(!isPlaylistListFiltered());
            PlaylistCompactControls.update(root, editMode);
        }
        if (itemListAdapter != null) {
            itemListAdapter.setItemHandleEnabled(editMode && !isPlaylistListFiltered());
            itemListAdapter.notifyDataSetChanged();
        }
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public int getDragDirs(@NonNull final RecyclerView recyclerView,
                                   @NonNull final RecyclerView.ViewHolder viewHolder) {
                if (!editMode || isPlaylistListFiltered() || itemListAdapter.isSelecting()) {
                    return 0;
                }
                final int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return recyclerView.getLayoutManager() instanceof GridLayoutManager
                        ? directions | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT : directions;
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

                if (!editMode || isPlaylistListFiltered() || itemListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getBindingAdapterPosition();
                final int targetIndex = target.getBindingAdapterPosition();
                final boolean isSwapped = itemListAdapter.swapItems(sourceIndex, targetIndex);
                if (isSwapped && debounceSaver != null) {
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
                // Do nothing.
            }
        };
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private void editSeparatorRow(final int row) {
        if (separators != null && itemListAdapter != null) {
            separators.editRow(requireContext(), itemListAdapter.getItemsList(), row,
                    () -> itemListAdapter.setSeparators(separators::labels,
                            this::editSeparatorRow),
                    false);
        }
    }

    private void editSeparatorAt(final int index) {
        if (separators != null && itemListAdapter != null) {
            separators.edit(requireContext(), itemListAdapter.getItemsList(), index,
                    () -> itemListAdapter.setSeparators(separators::labels,
                            this::editSeparatorRow));
        }
    }

    private void showRemoteDeleteDialog(final PlaylistRemoteEntity item) {
        final String moveToCategory = getString(R.string.playlist_move_to_category);
        final String delete = getString(R.string.delete);
        final String learning = learningActionLabel(item);
        final String separator = getString(R.string.list_separator_add);
        final List<String> actions = playlistActions();
        actions.add(moveToCategory);
        actions.add(delete);
        if (LearningMode.isEnabled(requireContext())) {
            actions.add(learning);
        }
        if (!isPlaylistListFiltered()) {
            actions.add(separator);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    final String selected = actions.get(which);
                    if (handlePlaylistAction(selected, action ->
                            loadRemotePlaylistQueue(item, action))) {
                        return;
                    }
                    if (selected.equals(getString(R.string.playlist_edit_mode))) {
                        enterPlaylistEditMode();
                    } else if (selected.equals(separator)) {
                        editSeparatorAt(itemListAdapter.getItemsList().indexOf(item));
                    } else if (selected.equals(moveToCategory)) {
                        chooseCategory(item);
                    } else if (selected.equals(delete)) {
                        showDeleteDialog(item.getOrderingName(), item);
                    } else if (selected.equals(learning)) {
                        toggleLearningPlaylist(item);
                    }
                }).show();
    }

    private void showLocalDialog(final PlaylistMetadataEntry selectedItem) {
        final String rename = getString(R.string.rename);
        final String delete = getString(R.string.delete);
        final String unsetThumbnail = getString(R.string.unset_playlist_thumbnail);
        final String moveToCategory = getString(R.string.playlist_move_to_category);
        final String learning = learningActionLabel(selectedItem);
        final String separator = getString(R.string.list_separator_add);
        final boolean isThumbnailPermanent = localPlaylistManager
                .getIsPlaylistThumbnailPermanent(selectedItem.getUid());

        final ArrayList<String> items = new ArrayList<>(playlistActions());
        items.add(rename);
        items.add(delete);
        items.add(moveToCategory);
        if (isThumbnailPermanent) {
            items.add(unsetThumbnail);
        }
        if (LearningMode.isEnabled(requireContext())) {
            items.add(learning);
        }
        if (!isPlaylistListFiltered()) {
            items.add(separator);
        }

        final DialogInterface.OnClickListener action = (dialog, index) -> {
            final String selected = items.get(index);
            if (handlePlaylistAction(selected, playbackAction ->
                    loadLocalPlaylistQueue(selectedItem, playbackAction))) {
                return;
            }
            if (selected.equals(getString(R.string.playlist_edit_mode))) {
                enterPlaylistEditMode();
            } else if (selected.equals(separator)) {
                editSeparatorAt(itemListAdapter.getItemsList().indexOf(selectedItem));
            } else if (selected.equals(rename)) {
                showRenameDialog(selectedItem);
            } else if (selected.equals(delete)) {
                showDeleteDialog(selectedItem.getOrderingName(), selectedItem);
            } else if (selected.equals(moveToCategory)) {
                chooseCategory(selectedItem);
            } else if (selected.equals(learning)) {
                toggleLearningPlaylist(selectedItem);
            } else if (isThumbnailPermanent && selected.equals(unsetThumbnail)) {
                final long thumbnailStreamId = localPlaylistManager
                        .getAutomaticPlaylistThumbnailStreamId(selectedItem.getUid());
                localPlaylistManager
                        .changePlaylistThumbnail(selectedItem.getUid(), thumbnailStreamId, false)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();
            }
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setItems(items.toArray(new String[0]), action)
                .show();
    }

    private ArrayList<String> playlistActions() {
        final ArrayList<String> actions = new ArrayList<>();
        actions.add(getString(R.string.play_all));
        actions.add(getString(R.string.enqueue_next_stream));
        actions.add(getString(R.string.enqueue_stream));
        if (!isPlaylistListFiltered()) {
            actions.add(getString(R.string.playlist_edit_mode));
        }
        return actions;
    }

    private boolean handlePlaylistAction(
            final String selected,
            final Consumer<Integer> queueLoader
    ) {
        if (selected.equals(getString(R.string.play_all))) {
            queueLoader.accept(PLAYLIST_ACTION_PLAY);
        } else if (selected.equals(getString(R.string.enqueue_next_stream))) {
            queueLoader.accept(PLAYLIST_ACTION_ENQUEUE_NEXT);
        } else if (selected.equals(getString(R.string.enqueue_stream))) {
            queueLoader.accept(PLAYLIST_ACTION_ENQUEUE);
        } else {
            return false;
        }
        return true;
    }

    private void loadLocalPlaylistQueue(
            final PlaylistMetadataEntry playlist,
            final int action
    ) {
        disposables.add(localPlaylistManager.getPlaylistStreams(playlist.getUid())
                .firstOrError()
                .map(entries -> entries.stream()
                        .map(PlaylistStreamEntry::toStreamInfoItem)
                        .collect(Collectors.toList()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.no_streams, Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    runPlaylistAction(new SinglePlayQueue(items, 0), action);
                }, throwable -> showError(new ErrorInfo(
                        throwable,
                        UserAction.REQUESTED_BOOKMARK,
                        playlist.getOrderingName()
                ))));
    }

    private void loadRemotePlaylistQueue(
            final PlaylistRemoteEntity playlist,
            final int action
    ) {
        disposables.add(ExtractorHelper
                .getPlaylistInfo(playlist.getServiceId(), playlist.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> runPlaylistAction(new PlaylistPlayQueue(info), action),
                        throwable -> showError(new ErrorInfo(
                                throwable,
                                UserAction.REQUESTED_BOOKMARK,
                                playlist.getUrl()
                        ))));
    }

    private void runPlaylistAction(final PlayQueue queue, final int action) {
        if (action == PLAYLIST_ACTION_PLAY) {
            NavigationHelper.playOnMainPlayer(requireContext(), queue, false);
        } else if (action == PLAYLIST_ACTION_ENQUEUE_NEXT) {
            NavigationHelper.enqueueNextOnPlayer(requireContext(), queue);
        } else {
            NavigationHelper.enqueueOnPlayer(requireContext(), queue);
        }
    }

    private void enterPlaylistEditMode() {
        editMode = true;
        updateEditMode();
    }

    private boolean isLearningPlaylist(final PlaylistLocalItem item) {
        if (item instanceof PlaylistRemoteEntity remote) {
            return remote.getUrl() != null && learningSources.contains(
                    LearningContentManager.remotePlaylistSourceId(
                            remote.getServiceId(), remote.getUrl()));
        }
        return learningSources.contains(
                LearningContentManager.localPlaylistSourceId(item.getUid()));
    }

    private String learningActionLabel(final PlaylistLocalItem item) {
        return getString(isLearningPlaylist(item)
                ? R.string.learning_remove_content : R.string.learning_mark_content);
    }

    private void toggleLearningPlaylist(final PlaylistLocalItem item) {
        if (!LearningMode.isEnabled(requireContext())) {
            return;
        }
        final LearningContentManager manager = LearningContentManager.getInstance(requireContext());
        final boolean marked = isLearningPlaylist(item);
        final String title = item.getOrderingName() == null
                ? getString(R.string.learning_dashboard_untitled_playlist) : item.getOrderingName();
        final Completable update;
        if (item instanceof PlaylistRemoteEntity remote) {
            if (remote.getUrl() == null) {
                return;
            }
            update = marked ? manager.setRemotePlaylistMarked(remote.getServiceId(),
                    remote.getUrl(), title, remote.getThumbnailUrl(),
                    Collections.emptyList(), false)
                    : LearningPlaylistMarker.markRemote(manager,
                            remote.getServiceId(), remote.getUrl());
            if (!marked) {
                Toast.makeText(requireContext(), R.string.learning_playlist_adding,
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            update = manager.setLocalPlaylistMarked(item.getUid(), title, !marked);
        }
        disposables.add(update.observeOn(AndroidSchedulers.mainThread()).subscribe(
                () -> {
                    showFilteredPlaylists();
                    Toast.makeText(requireContext(), marked
                            ? R.string.learning_content_removed : R.string.learning_content_added,
                            Toast.LENGTH_SHORT).show();
                },
                error -> Toast.makeText(requireContext(), R.string.learning_content_update_error,
                        Toast.LENGTH_LONG).show()));
    }

    private boolean isPlaylistListFiltered() {
        return !PlaylistCategories.allowsReordering(isContextualSearchActive(), selectedCategory);
    }

    private static String categoryKey(final PlaylistLocalItem playlist) {
        if (playlist instanceof PlaylistRemoteEntity) {
            final PlaylistRemoteEntity remote = (PlaylistRemoteEntity) playlist;
            return "remote:" + remote.getServiceId() + ":" + remote.getUrl();
        }
        return "local:" + playlist.getUid();
    }

    private void updateCategoryLabel() {
        if (getView() == null) {
            return;
        }
        final MaterialButton button = getView().findViewById(R.id.playlist_category_filter);
        button.setText(LEARNING_CATEGORY.equals(selectedCategory)
                ? getString(R.string.learning_for_learning)
                : PlaylistCategories.ALL.equals(selectedCategory)
                ? getString(R.string.playlist_categories_all)
                : PlaylistCategories.UNCATEGORIZED.equals(selectedCategory)
                ? getString(R.string.playlist_uncategorized) : categories.name(selectedCategory));
    }

    private void setCategoryFilter(final String id) {
        if (!isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        selectedCategory = PlaylistOpeningPreferences.validateSelection(requireContext(),
                categoryProfileId, categories, id);
        PlaylistOpeningPreferences.rememberCategory(requireContext(),
                categoryProfileId, selectedCategory);
        updateCategoryLabel();
        showFilteredPlaylists();
    }

    private void saveCategories() {
        // Keep the filter consistent with the adapter until the preference listener refreshes it.
        // Switching to All first would let that listener capture a filtered subset as full order.
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putString(PlaylistCategories.preferenceKey(categoryProfileId), categories.toJson())
                .apply();
        selectedCategory = PlaylistOpeningPreferences.validateSelection(requireContext(),
                categoryProfileId, categories, selectedCategory);
        updateCategoryLabel();
        showFilteredPlaylists();
    }

    private void chooseCategory(@Nullable final PlaylistLocalItem playlist) {
        if (!isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        final List<String> ids = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        if (playlist == null) {
            ids.add(PlaylistCategories.ALL);
            labels.add(getString(R.string.playlist_categories_all));
            if (LearningMode.isEnabled(requireContext(), categoryProfileId)) {
                ids.add(LEARNING_CATEGORY);
                labels.add(getString(R.string.learning_for_learning));
            }
        }
        ids.add(PlaylistCategories.UNCATEGORIZED);
        labels.add(getString(R.string.playlist_uncategorized));
        for (final String id : categories.ids()) {
            ids.add(id);
            labels.add(categories.name(id));
        }
        PlaylistOpeningDialog.categoryMenu(requireContext(), categoryProfileId, categories,
                labels, playlist == null, which -> {
                    if (playlist == null) {
                        setCategoryFilter(ids.get(which));
                    } else {
                        categories.assign(categoryKey(playlist), ids.get(which));
                        saveCategories();
                    }
                })
                .setPositiveButton(R.string.playlist_category_create,
                        (dialog, which) -> editCategory(null, playlist))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void manageCategories() {
        if (!isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        final List<String> ids = categories.ids();
        final List<String> names = ids.stream().map(categories::name).collect(Collectors.toList());
        PlaylistOpeningDialog.categoryMenu(requireContext(), categoryProfileId, categories,
                names, true, which -> {
                    final String id = ids.get(which);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(categories.name(id))
                            .setItems(new String[]{getString(R.string.rename),
                                getString(R.string.delete)}, (actionDialog, action) -> {
                                if (action == 0) {
                                    editCategory(id, null);
                                } else {
                                    deleteCategory(id);
                                }
                            }).show();
                })
                .setPositiveButton(R.string.playlist_category_create,
                        (dialog, which) -> editCategory(null, null))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void editCategory(@Nullable final String id,
                              @Nullable final PlaylistLocalItem playlist) {
        final DialogEditTextBinding input = DialogEditTextBinding.inflate(getLayoutInflater());
        input.dialogEditText.setHint(R.string.name);
        input.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (id != null) {
            input.dialogEditText.setText(categories.name(id));
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(id == null ? R.string.playlist_category_create : R.string.rename)
                .setView(input.getRoot())
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        final String name = input.dialogEditText.getText().toString();
                        if (id == null) {
                            final String created = categories.create(name);
                            if (playlist != null) {
                                categories.assign(categoryKey(playlist), created);
                            }
                        } else {
                            categories.rename(id, name);
                        }
                        saveCategories();
                        dialog.dismiss();
                    } catch (final IllegalArgumentException error) {
                        input.dialogEditText.setError(
                                getString(R.string.playlist_category_name_error));
                    }
                }));
        dialog.show();
    }

    private void deleteCategory(final String id) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(categories.name(id))
                .setMessage(R.string.playlist_category_delete_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    categories.delete(id);
                    saveCategories();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void showRenameDialog(final PlaylistMetadataEntry selectedItem) {
        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setText(selectedItem.getOrderingName());

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.rename_playlist, (dialog, which) ->
                        changeLocalPlaylistName(
                                selectedItem.getUid(),
                                dialogBinding.dialogEditText.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(final String name, final PlaylistLocalItem item) {
        if (activity == null || disposables == null) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) -> deleteItem(item))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
