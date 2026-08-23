package org.schabi.newpipe.local.playlist;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.databinding.LocalPlaylistHeaderBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.PlayerService.PlayerType;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.*;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.StreamProcessor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import us.shandian.giga.get.DirectDownloader;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.schabi.newpipe.error.ErrorUtil.showUiErrorSnackbar;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.util.ThemeHelper.shouldUseGridLayout;

public class LocalPlaylistFragment extends BaseLocalListFragment<List<PlaylistStreamEntry>, Void> implements BackPressable {
    // Save the list 10 seconds after the last change occurred
    private static final long SAVE_DEBOUNCE_MILLIS = 10000;
    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;
    protected Long playlistId;
    protected String name;
    Parcelable itemsListState;

    private LocalPlaylistHeaderBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;

    private ItemTouchHelper itemTouchHelper;

    private LocalPlaylistManager playlistManager;
    private Subscription databaseSubscription;

    private PublishSubject<Long> debouncedSaveSignal;
    private CompositeDisposable disposables;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    /* Has the playlist been fully loaded from db */
    private AtomicBoolean isLoadingComplete;
    /* Has the playlist been modified (e.g. items reordered or deleted) */
    private AtomicBoolean isModified;
    /* Is the playlist currently being processed to remove watched videos */
    private boolean isRemovingWatched = false;
    /* Is the playlist currently being processed to remove duplicate streams */
    private boolean isRemovingDuplicateStreams = false;
    private boolean autoBackgroundPlaying = false;
    private boolean randomBackgroundPlaying = false;

    private boolean isShowingAsTab = false;

    private Disposable disposable;
    private EditText editText;
    private View searchClear;
    private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            itemListAdapter.filter(String.valueOf(editText.getText()));
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

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
        debouncedSaveSignal = PublishSubject.create();

        disposables = new CompositeDisposable();

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleFileSelection
        );

        isLoadingComplete = new AtomicBoolean();
        isModified = new AtomicBoolean();
        autoBackgroundPlaying = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.auto_background_play_key), false);
        randomBackgroundPlaying = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.random_music_play_mode_key), false);
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


    private void setupTouchListeners(View view) {
        // Skip if this is our EditText
        if (view == editText) {
            return;
        }

        // For other views, set a touch listener to clear focus
        if (!(view instanceof EditText)) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && editText != null && editText.hasFocus()) {
                    editText.clearFocus();
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }
                // Always return false to allow the event to be processed by the view
                return false;
            });
        }

        // If it's a ViewGroup, apply to all children
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setupTouchListeners(viewGroup.getChildAt(i));
            }
        }

        // Add specific listener for the RecyclerView
        if (view.getId() == R.id.items_list) {
            RecyclerView recyclerView = (RecyclerView) view;
            recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN && editText != null && editText.hasFocus()) {
                        editText.clearFocus();
                        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                    }
                    return false;
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    // Not needed
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                    // Not needed
                }
            });
        }
    }


    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        itemListAdapter.setUseItemHandle(true);
        setTitle(name);
        setupTouchListeners(rootView);
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

        itemListAdapter.setSelectedListener(new OnClickGesture<LocalItem>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistStreamEntry) {
                    final StreamEntity item =
                            ((PlaylistStreamEntry) selectedItem).getStreamEntity();
                    if (autoBackgroundPlaying){
                        final PlayQueue temp = getPlayQueue();
                        temp.setIndex(utils.getIndexInQueue((PlaylistStreamEntry) selectedItem, temp, itemListAdapter.sortMode));
                        if (randomBackgroundPlaying){
                            temp.shuffle();
                        }
                        NavigationHelper.playOnBackgroundPlayer(activity, temp, false);
                    } else {
                        NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                                item.getServiceId(), item.getUrl(), item.getTitle(), null, false);
                    }
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
        disposables.add(getDebouncedSaver());

        isLoadingComplete.set(false);
        isModified.set(false);

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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (playlistId != null) {
            outState.putLong("playlistId", playlistId);
        }
        outState.putString("name", name);
        outState.putParcelable("itemsListState", itemsListState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey("playlistId")) {
            playlistId = savedInstanceState.getLong("playlistId");
        }
        name = savedInstanceState.getString("name");
        itemsListState = savedInstanceState.getParcelable("itemsListState");
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
        MenuItem searchItem = menu.findItem(R.id.action_search);
        isShowingAsTab = !(searchItem == null || !searchItem.isVisible());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if(Objects.requireNonNull(activity.getSupportActionBar()).getCustomView() != null){
            destroyCustomViewInActionBar();
        }
        if (itemListAdapter != null) {
            itemListAdapter.unsetSelectedListener();
        }
        if (playlistControlBinding != null) {
            playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener(null);
            playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener(null);
            playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener(null);

            headerBinding = null;
            playlistControlBinding = null;
        }

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
        if (debouncedSaveSignal != null) {
            debouncedSaveSignal.onComplete();
        }
        if (disposables != null) {
            disposables.dispose();
        }

        debouncedSaveSignal = null;
        playlistManager = null;
        disposables = null;

        isLoadingComplete = null;
        isModified = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playlist Stream Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistStreamEntry>> getPlaylistObserver() {
        return new Subscriber<List<PlaylistStreamEntry>>() {
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
                if (isModified == null || !isModified.get()) {
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
        } else if (item.getItemId() == R.id.menu_item_remove_watched) {
            if (!isRemovingWatched) {
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.remove_watched_popup_warning)
                        .setTitle(R.string.remove_watched_popup_title)
                        .setPositiveButton(R.string.ok,
                                (DialogInterface d, int id) -> removeWatchedStreams(false))
                        .setNeutralButton(
                                R.string.remove_watched_popup_yes_and_partially_watched_videos,
                                (DialogInterface d, int id) -> removeWatchedStreams(true))
                        .setNegativeButton(R.string.cancel,
                                (DialogInterface d, int id) -> d.cancel())
                        .create()
                        .show();
            }
        } else if (item.getItemId() == R.id.menu_item_rename_playlist) {
            createRenameDialog();
        } else if (item.getItemId() == R.id.menu_item_remove_duplicates) {
            if (!isRemovingDuplicateStreams) {
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.remove_duplicates_popup_warning)
                        .setTitle(R.string.remove_duplicates_popup_title)
                        .setPositiveButton(R.string.yes,
                                (DialogInterface d, int id) -> removeDuplicateStreams())
                        .setNegativeButton(R.string.cancel,
                                (DialogInterface d, int id) -> d.cancel())
                        .create()
                        .show();
            } else {
                return super.onOptionsItemSelected(item);
            }
        } else if (item.getItemId() == R.id.action_search_local) {
            ActionBar actionBar = activity.getSupportActionBar();
            View customView = getLayoutInflater().inflate(R.layout.local_playlist_search_toolbar, null, false);
            assert actionBar != null;
            actionBar.setCustomView(customView);
            actionBar.setDisplayShowCustomEnabled(true);
            editText = activity.findViewById(R.id.toolbar_search_edit_text_local);
            editText.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            try {
                activity.findViewById(R.id.action_search_local).setVisibility(View.GONE);
            } catch (Exception e) {
                // ignore
            }

            searchClear = customView.findViewById(R.id.toolbar_search_clear_local);
            searchClear.setOnClickListener(v -> {
                if (TextUtils.isEmpty(editText.getText())) {
                    editText.clearFocus(); // Clear focus when the search is cleared
                    destroyCustomViewInActionBar();
                    return;
                }
                editText.setText("");
            });

            try {
                editText.removeTextChangedListener(textWatcher);
            } catch (Exception e) {
                // ignore
            }
            editText.addTextChangedListener(textWatcher);
        } else if (item.getItemId() == R.id.menu_item_download_all) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

            builder = new AlertDialog.Builder(requireContext());
            builder.setMessage(R.string.download_all_message)
                    .setTitle(R.string.download_all);

            builder.setPositiveButton(R.string.video, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            disposable = playlistManager.getPlaylistStreams(playlistId)
                                    .observeOn(Schedulers.io())
                                    .subscribe(streams -> {
                                        disposable.dispose();
                                         StreamProcessor streamProcessor = new StreamProcessor();
                                         streamProcessor.processStreamsSequentiallyWithProgress(requireContext(),
                                                 streams.stream()
                                                 .map(PlaylistStreamEntry::toStreamInfoItem)
                                                 .collect(Collectors.toList()), DirectDownloader.DownloadType.VIDEO);

                                    }, throwable -> {
                                    });
                        }
                    }
            );
            builder.setNegativeButton(R.string.audio, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    disposable = playlistManager.getPlaylistStreams(playlistId)
                            .observeOn(Schedulers.io())
                            .subscribe(streams -> {
                                disposable.dispose();
                                StreamProcessor streamProcessor = new StreamProcessor();
                                streamProcessor.processStreamsSequentiallyWithProgress(requireContext(),
                                        streams.stream()
                                                .map(PlaylistStreamEntry::toStreamInfoItem)
                                                .collect(Collectors.toList()), DirectDownloader.DownloadType.AUDIO);

                            }, throwable -> {
                            });
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else if (item.getItemId() == R.id.menu_item_import_urls) {
            openFilePicker();
        } else if (item.getItemId() == R.id.menu_item_append_playlist) {
            appendAllToOtherPlaylist();
        } else if (item.getItemId() == R.id.menu_item_sort_origin) {
            itemListAdapter.sortMode = SortMode.ORIGIN;
            itemListAdapter.sort(SortMode.ORIGIN);
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(getString(R.string.playlist_sort_mode_key), SortMode.ORIGIN.name()).apply();
        } else if (item.getItemId() == R.id.menu_item_sort_origin_reverse) {
            itemListAdapter.sortMode = SortMode.ORIGIN_REVERSE;
            itemListAdapter.sort(SortMode.ORIGIN_REVERSE);
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(getString(R.string.playlist_sort_mode_key), SortMode.ORIGIN_REVERSE.name()).apply();
        } else if (item.getItemId() == R.id.menu_item_sort_name) {
            itemListAdapter.sortMode = SortMode.SORT_NAME;
            itemListAdapter.sort(SortMode.SORT_NAME);
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(getString(R.string.playlist_sort_mode_key), SortMode.SORT_NAME.name()).apply();
        } else if (item.getItemId() == R.id.menu_item_sort_name_reverse) {
            itemListAdapter.sortMode = SortMode.SORT_NAME_REVERSE;
            itemListAdapter.sort(SortMode.SORT_NAME_REVERSE);
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(getString(R.string.playlist_sort_mode_key), SortMode.SORT_NAME_REVERSE.name()).apply();
        }
        return true;
    }

    private void openFilePicker() {
        filePickerLauncher.launch(new String[]{"text/plain"});
    }

    private void handleFileSelection(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        Single.fromCallable(() -> readUrlsFromFile(uri))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::processUrls,
                        throwable -> showImportError(throwable.getMessage())
                );
    }

    private List<String> readUrlsFromFile(Uri uri) throws Exception {
        List<String> urls = new ArrayList<>();
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && (line.startsWith("http://") || line.startsWith("https://"))) {
                    urls.add(line);
                }
            }
        }
        return urls;
    }

    private void processUrls(List<String> urls) {
        if (urls.isEmpty()) {
            Toast.makeText(requireContext(), R.string.import_urls_no_valid_urls, Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert URLs to StreamInfoItems
        List<StreamInfoItem> streamItems = new ArrayList<>();
        for (String url : urls) {
            StreamInfoItem item = createStreamInfoItemFromUrl(url);
            if (item != null) {
                streamItems.add(item);
            }
        }

        if (streamItems.isEmpty()) {
            Toast.makeText(requireContext(), R.string.import_urls_no_valid_urls, Toast.LENGTH_SHORT).show();
            return;
        }

        // Use StreamProcessor with custom callback for playlist addition
        StreamProcessor streamProcessor = new StreamProcessor();
        streamProcessor.processStreamsSequentiallyWithProgress(requireContext(), streamItems, null, 
            streamInfo -> {
                // Convert StreamInfo to StreamEntity and add to playlist
                StreamEntity streamEntity = new StreamEntity(streamInfo);
                List<StreamEntity> entityList = Collections.singletonList(streamEntity);
                playlistManager.appendToPlaylist(playlistId, entityList)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        longs -> { /* Success handled by progress notification */ },
                        throwable -> {
                            throw new RuntimeException("Failed to add stream to playlist: " + streamInfo.getUrl(), throwable);
                        }
                    );
            }
        );
    }

    private StreamInfoItem createStreamInfoItemFromUrl(String url) {
        try {
            for (StreamingService service : NewPipe.getServices()) {
                LinkHandlerFactory linkHandlerFactory = service.getStreamLHFactory();
                if (linkHandlerFactory.acceptUrl(url)) {
                    return new StreamInfoItem(
                            service.getServiceId(),
                            url,
                            "Imported Video",
                            StreamType.VIDEO_STREAM
                    );
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error processing URL: " + url, e);
        }
        return null;
    }

    private void showImportError(String message) {
        Toast.makeText(requireContext(), 
                getString(R.string.import_urls_error, message), 
                Toast.LENGTH_LONG).show();
    }

    public void removeDuplicateStreams() {
        if (isRemovingDuplicateStreams) {
            return;
        }
        isRemovingDuplicateStreams = true;
        showLoading();

        disposables.add(playlistManager.getPlaylistStreams(playlistId)
                .subscribeOn(Schedulers.io())
                .map((List<PlaylistStreamEntry> playlist) -> {
                    // Playlist data
                    final Iterator<PlaylistStreamEntry> playlistIter = playlist.iterator();

                    // Remove duplicates, Functionality data
                    final List<PlaylistStreamEntry> uniquePlaylistItems = new ArrayList<>();
                    final Set<Long> uniquePlaylistItemStreamIds = new HashSet<>();
                    boolean thumbnailVideoRemoved = false;

                    while (playlistIter.hasNext()) {
                        final PlaylistStreamEntry playlistItem = playlistIter.next();
                        final Long playlistItemStreamId = playlistItem.getStreamId();

                        if (!uniquePlaylistItemStreamIds.contains(playlistItemStreamId)) {
                            uniquePlaylistItems.add(playlistItem);
                            uniquePlaylistItemStreamIds.add(playlistItemStreamId);
                        } else if (!thumbnailVideoRemoved
                                && playlistManager.getPlaylistThumbnail(playlistId)
                                .equals(playlistItem.getStreamEntity().getThumbnailUrl())) {
                            thumbnailVideoRemoved = true;
                        }
                    }

                    return Flowable.just(uniquePlaylistItems, thumbnailVideoRemoved);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(flow -> {
                    final List<PlaylistStreamEntry> uniquePlaylistItems =
                            (List<PlaylistStreamEntry>) flow.blockingFirst();
                    final boolean thumbnailVideoRemoved = (Boolean) flow.blockingLast();

                    itemListAdapter.clearStreamItemList();
                    itemListAdapter.addItems(uniquePlaylistItems);
                    saveChanges();

                    if (thumbnailVideoRemoved) {
                        updateThumbnailUrl();
                    }

                    final long videoCount = itemListAdapter.getItemsList().size();
                    setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
                    if (videoCount == 0) {
                        showEmptyState();
                    }

                    hideLoading();
                    isRemovingDuplicateStreams = false;
                }));
    }

    public void removeWatchedStreams(final boolean removePartiallyWatched) {
        if (isRemovingWatched) {
            return;
        }
        isRemovingWatched = true;
        showLoading();

        disposables.add(playlistManager.getPlaylistStreams(playlistId)
                .subscribeOn(Schedulers.io())
                .map((List<PlaylistStreamEntry> playlist) -> {
                    // Playlist data
                    final Iterator<PlaylistStreamEntry> playlistIter = playlist.iterator();

                    // History data
                    final HistoryRecordManager recordManager
                            = new HistoryRecordManager(getContext());
                    final Iterator<StreamHistoryEntry> historyIter = recordManager
                            .getStreamHistorySortedById().blockingFirst().iterator();

                    // Remove Watched, Functionality data
                    final List<PlaylistStreamEntry> notWatchedItems = new ArrayList<>();
                    boolean thumbnailVideoRemoved = false;

                    // already sorted by ^ getStreamHistorySortedById(), binary search can be used
                    final ArrayList<Long> historyStreamIds = new ArrayList<>();
                    while (historyIter.hasNext()) {
                        historyStreamIds.add(historyIter.next().getStreamId());
                    }

                    if (removePartiallyWatched) {
                        while (playlistIter.hasNext()) {
                            final PlaylistStreamEntry playlistItem = playlistIter.next();
                            final int indexInHistory = Collections.binarySearch(historyStreamIds,
                                    playlistItem.getStreamId());

                            if (indexInHistory < 0) {
                                notWatchedItems.add(playlistItem);
                            } else if (!thumbnailVideoRemoved
                                    && playlistManager.getPlaylistThumbnail(playlistId)
                                    .equals(playlistItem.getStreamEntity().getThumbnailUrl())) {
                                thumbnailVideoRemoved = true;
                            }
                        }
                    } else {
                        final Iterator<StreamStateEntity> streamStatesIter = recordManager
                                .loadLocalStreamStateBatch(playlist).blockingGet().iterator();

                        while (playlistIter.hasNext()) {
                            final PlaylistStreamEntry playlistItem = playlistIter.next();
                            final int indexInHistory = Collections.binarySearch(historyStreamIds,
                                    playlistItem.getStreamId());
                            final StreamStateEntity streamStateEntity = streamStatesIter.next();
                            final long duration = playlistItem.toStreamInfoItem().getDuration();

                            if (indexInHistory < 0 || (streamStateEntity != null
                                    && !streamStateEntity.isFinished(duration))) {
                                notWatchedItems.add(playlistItem);
                            } else if (!thumbnailVideoRemoved
                                    && playlistManager.getPlaylistThumbnail(playlistId)
                                    .equals(playlistItem.getStreamEntity().getThumbnailUrl())) {
                                thumbnailVideoRemoved = true;
                            }
                        }
                    }

                    return Flowable.just(notWatchedItems, thumbnailVideoRemoved);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(flow -> {
                    final List<PlaylistStreamEntry> notWatchedItems =
                            (List<PlaylistStreamEntry>) flow.blockingFirst();
                    final boolean thumbnailVideoRemoved = (Boolean) flow.blockingLast();

                    itemListAdapter.clearStreamItemList();
                    itemListAdapter.addItems(notWatchedItems);
                    saveChanges();


                    if (thumbnailVideoRemoved) {
                        updateThumbnailUrl();
                    }

                    final long videoCount = itemListAdapter.getItemsList().size();
                    setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
                    if (videoCount == 0) {
                        showEmptyState();
                    }

                    hideLoading();
                    isRemovingWatched = false;
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Removing watched videos, partially watched=" + removePartiallyWatched))));
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistStreamEntry> result) {
        super.handleResult(result);
        if (itemListAdapter == null || playlistControlBinding == null) {
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

        playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener(view ->
                NavigationHelper.playOnMainPlayer(activity, getPlayQueue()));
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener(view ->
                NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(), false));



        playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener(view -> {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    requireContext());
            if (prefs.getBoolean(getString(R.string.random_music_play_mode_key), false)) {
                NavigationHelper.playOnBackgroundPlayerShuffled(activity, getPlayQueue(), false);
            } else {
                NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue(), false);
            }

        });

        playlistControlBinding.playlistCtrlPlayPopupButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, getPlayQueue(), PlayerType.POPUP);
            return true;
        });

        playlistControlBinding.playlistCtrlPlayBgButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, getPlayQueue(), PlayerType.AUDIO);
            return true;
        });

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

    private void appendAllToOtherPlaylist() {
        disposables.add(playlistManager.getPlaylistStreams(playlistId)
                .firstOrError()
                .map(streams -> streams.stream()
                        .map(PlaylistStreamEntry::getStreamEntity)
                        .collect(Collectors.toList()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(streamEntities -> {
                    disposables.add(PlaylistDialog.createCorrespondingDialog(
                            getContext(),
                            streamEntities,
                            dialog -> dialog.show(getParentFragmentManager(), TAG)
                    ));
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Loading local playlist for append to other playlist"))));
    }

    private void createRenameDialog() {
        if (playlistId == null || name == null || getContext() == null) {
            return;
        }

        final DialogEditTextBinding dialogBinding
                = DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setSelection(dialogBinding.dialogEditText.getText().length());
        dialogBinding.dialogEditText.setText(name);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.rename_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.rename, (dialogInterface, i) ->
                        changePlaylistName(dialogBinding.dialogEditText.getText().toString()));

        dialogBuilder.show();
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

    private void changeThumbnailUrl(final String thumbnailUrl) {
        if (playlistManager == null) {
            return;
        }

        final Toast successToast = Toast.makeText(getActivity(),
                R.string.playlist_thumbnail_change_success,
                Toast.LENGTH_SHORT);

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with new thumbnail url=[" + thumbnailUrl + "]");
        }

        final Disposable disposable = playlistManager
                .changePlaylistThumbnail(playlistId, thumbnailUrl)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignore -> successToast.show(), throwable ->
                        showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                "Changing playlist thumbnail")));
        disposables.add(disposable);
    }

    private void updateThumbnailUrl() {
        final String newThumbnailUrl;

        if (!itemListAdapter.getItemsList().isEmpty()) {
            newThumbnailUrl = ((PlaylistStreamEntry) itemListAdapter.getItemsList().get(0))
                    .getStreamEntity().getThumbnailUrl();
        } else {
            newThumbnailUrl = "drawable://" + R.drawable.dummy_thumbnail_playlist;
        }

        changeThumbnailUrl(newThumbnailUrl);
    }

    private void deleteItem(final PlaylistStreamEntry item) {
        if (itemListAdapter == null) {
            return;
        }

        itemListAdapter.removeItem(item);
        if (playlistManager.getPlaylistThumbnail(playlistId)
                .equals(item.getStreamEntity().getThumbnailUrl())) {
            updateThumbnailUrl();
        }

        setStreamCountAndOverallDuration(itemListAdapter.getItemsList());
        saveChanges();
    }

    private void showDeleteConfirmationDialog(final PlaylistStreamEntry item, 
                                            final RecyclerView.ViewHolder viewHolder) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.play_queue_remove)
                .setMessage(R.string.remove_from_playlist_confirmation)
                .setPositiveButton(R.string.play_queue_remove, (dialog, which) -> deleteItem(item))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // Reset the item position if user cancels
                    if (itemListAdapter != null) {
                        itemListAdapter.notifyItemChanged(viewHolder.getBindingAdapterPosition());
                    }
                })
                .setOnCancelListener(dialog -> {
                    // Reset the item position if dialog is cancelled
                    if (itemListAdapter != null) {
                        itemListAdapter.notifyItemChanged(viewHolder.getBindingAdapterPosition());
                    }
                })
                .show();
    }

    private void handleSwipeAction(final PlaylistStreamEntry item,
                                 final RecyclerView.ViewHolder viewHolder) {
        final String swipeAction = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.local_playlist_swipe_action_key), "delete");
        
        switch (swipeAction) {
            case "delete":
                showDeleteConfirmationDialog(item, viewHolder);
                break;
            case "enqueue":
                final SinglePlayQueue queue = new SinglePlayQueue(item.toStreamInfoItem());
                NavigationHelper.enqueueOnPlayer(requireContext(), queue);
                if (itemListAdapter != null) {
                    itemListAdapter.notifyItemChanged(viewHolder.getBindingAdapterPosition());
                }
                break;
            case "disable":
            default:
                if (itemListAdapter != null) {
                    itemListAdapter.notifyItemChanged(viewHolder.getBindingAdapterPosition());
                }
                break;
        }
    }

    private void saveChanges() {
        if (isModified == null || debouncedSaveSignal == null) {
            return;
        }

        isModified.set(true);
        debouncedSaveSignal.onNext(System.currentTimeMillis());
    }

    private Disposable getDebouncedSaver() {
        if (debouncedSaveSignal == null) {
            return Disposable.empty();
        }

        return debouncedSaveSignal
                .debounce(SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> saveImmediate(), throwable ->
                        showError(new ErrorInfo(throwable, UserAction.SOMETHING_ELSE,
                                "Debounced saver")));
    }

    private void saveImmediate() {
        if (playlistManager == null || itemListAdapter == null) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || isModified == null
                || !isLoadingComplete.get() || !isModified.get()) {
            Log.w(TAG, "Attempting to save playlist when local playlist "
                    + "is not loaded or not modified: playlist id=[" + playlistId + "]");
            return;
        }

        if(itemListAdapter.sortMode != SortMode.ORIGIN && itemListAdapter.sortMode != SortMode.ORIGIN_REVERSE) {
            itemListAdapter.sort(SortMode.ORIGIN);
        }

        final List<LocalItem> items = itemListAdapter.getItemsList();
        final List<Long> streamIds = new ArrayList<>(items.size());
        for (final LocalItem item : items) {
            if (item instanceof PlaylistStreamEntry) {
                streamIds.add(((PlaylistStreamEntry) item).getStreamId());
            }
        }

        if(itemListAdapter.sortMode == SortMode.ORIGIN_REVERSE) {
            Collections.reverse(streamIds);
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with [" + streamIds.size() + "] items");
        }

        final Disposable disposable = playlistManager.updateJoin(playlistId, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (isModified != null) {
                                isModified.set(false);
                            }
                        },
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                );
        disposables.add(disposable);
    }


    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeDirections;
        if (shouldUseGridLayout(requireContext())) {
            directions |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
            // In grid layout, disable swipe to avoid conflict with drag
            swipeDirections = ItemTouchHelper.ACTION_STATE_IDLE;
        } else {
            // In list layout, use RIGHT for swipe (like PlayQueue)
            swipeDirections = ItemTouchHelper.RIGHT;
        }
        return new ItemTouchHelper.SimpleCallback(directions, swipeDirections) {
            @Override
            public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView,
                        viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                
                // Allow slower speeds for precise positioning when dragging across pages
                // Only apply minimum velocity for very slow drags to prevent stalling
                final int absStandardSpeed = Math.abs(standardSpeed);
                if (absStandardSpeed < 3) {
                    // Use minimum velocity only for extremely slow drags to prevent stalling
                    return 3 * (int) Math.signum(viewSizeOutOfBounds);
                }
                
                // For normal speeds, use the standard speed to allow precise control
                return standardSpeed;
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
                    saveChanges();
                }
                return isSwapped;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return !isShowingAsTab;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, 
                                   @NonNull RecyclerView.ViewHolder viewHolder) {
                // Disable swipe for header items (position 0)
                if (itemListAdapter != null && viewHolder.getBindingAdapterPosition() == 0) {
                    return 0; // No swipe directions allowed for header
                }
                
                // Check if swipe is disabled in preferences
                final String swipeAction = PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getString(getString(R.string.local_playlist_swipe_action_key), "delete");
                if ("disable".equals(swipeAction)) {
                    return 0; // No swipe directions allowed when disabled
                }
                
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                if (itemListAdapter != null) {
                    final int index = viewHolder.getBindingAdapterPosition() - 1;
                    // Get the correct list based on whether filtering is enabled
                    final List<LocalItem> currentItems = itemListAdapter.getCurrentItemsList();
                    if (index != -1 && index < currentItems.size()) {
                        final Object item = currentItems.get(index);
                        if (item instanceof PlaylistStreamEntry) {
                            handleSwipeAction((PlaylistStreamEntry) item, viewHolder);
                        }
                    }
                }
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

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    requireContext());
            if (prefs.getBoolean(getString(R.string.auto_background_play_key), false)) {
                dialogBuilder.addEntry(StreamDialogDefaultEntry.SHOW_STREAM_DETAILS);
            }


            if(itemListAdapter.isFilterEnabled) {
                dialogBuilder.addEntry(StreamDialogDefaultEntry.NAVIGATE_TO);
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
                                    changeThumbnailUrl(item.getStreamEntity().getThumbnailUrl()))
                    .setAction(
                            StreamDialogDefaultEntry.DELETE,
                            (f, i) -> deleteItem(item))
                    .setAction(
                            StreamDialogDefaultEntry.NAVIGATE_TO,
                            (f, i) -> {
                                destroyCustomViewInActionBar();
                                int targetIndex = utils.getIndexInQueue(item, getPlayQueue(), itemListAdapter.sortMode) + 1;
                                if (targetIndex <= 100) {
                                    itemsList.smoothScrollToPosition(targetIndex);
                                } else {
                                    itemsList.scrollToPosition(targetIndex);
                                }
                            })
                    .setAction(
                            StreamDialogDefaultEntry.SHOW_STREAM_DETAILS,
                            (f, i) -> NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                                    i.getServiceId(), i.getUrl(), i.getName(), null, false))
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

    private PlayQueue getPlayQueue() {
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

    @Override
    public boolean onBackPressed() {
        if(Objects.requireNonNull(activity.getSupportActionBar()).getCustomView() != null){
            destroyCustomViewInActionBar();
            return true;
        }
        return false;
    }
    public void destroyCustomViewInActionBar(){
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setCustomView(null);
        actionBar.setDisplayShowCustomEnabled(false);
        View searchLocal = activity.findViewById(R.id.action_search_local);
        // they will both be null if back button is pressed
        if(searchLocal != null){
            searchLocal.setVisibility(View.VISIBLE);
        }
        if(itemListAdapter != null){
            itemListAdapter.clearFilter();
        }
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
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
}
