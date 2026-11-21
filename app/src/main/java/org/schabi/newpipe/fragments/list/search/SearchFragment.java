package org.schabi.newpipe.fragments.list.search;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;
import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView;
import static java.util.Arrays.asList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity;
import org.schabi.newpipe.download.DownloadedAvailabilityIndex;
import org.schabi.newpipe.databinding.FragmentSearchBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.ktx.ExceptionUtils;
import org.schabi.newpipe.local.feed.SavedSearchFeedManager;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.settings.HistorySettingsFragment;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class SearchFragment extends BaseListFragment<SearchInfo, ListExtractor.InfoItemsPage<?>>
        implements BackPressable {
    private static final String YOUTUBE_MUSIC_SONGS_FILTER = "music_songs";
    private static final String YOUTUBE_MUSIC_VIDEOS_FILTER = "music_videos";
    private static final int MENU_SAVE_SEARCH_FEED = 0x534601;
    private static final int MENU_REFRESH_SEARCH_FEED = 0x534602;
    private static final int MENU_DELETE_SEARCH_FEED = 0x534603;
    private static final int MENU_DEEP_SEARCH = 0x534605;
    private static final int MENU_CLEAR_SEARCH_HISTORY = 0x534604;

    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.SEARCH;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * The suggestions will only be fetched from network if the query meet this threshold (>=).
     * (local ones will be fetched regardless of the length)
     */
    private static final int THRESHOLD_NETWORK_SUGGESTION = 1;

    /**
     * How much time have to pass without emitting a item (i.e. the user stop typing)
     * to fetch/show the suggestions, in milliseconds.
     */
    private static final int SUGGESTIONS_DEBOUNCE = 120; //ms
    private final PublishSubject<String> suggestionPublisher = PublishSubject.create();

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;

    // these three represents the current search query
    @State
    String searchString;

    /**
     * No content filter should add like contentFilter = all
     * be aware of this when implementing an extractor.
     */
    @State
    String[] contentFilter = new String[0];

    @State
    int[] sortFilter = new int[0];

    @State
    String searchAfter = "";

    @State
    String searchBefore = "";

    @State
    String effectiveSearchString = "";

    @State
    long savedSearchFeedId = SavedSearchFeedManager.NO_SAVED_SEARCH_FEED;

    // these represents the last search
    @State
    String lastSearchedString;

    @State
    String searchSuggestion;

    @State
    boolean isCorrectedSearch;

    @State
    MetaInfo[] metaInfo;

    @State
    boolean wasSearchFocused = false;

    private StreamingService service;
    @Nullable
    private Page nextPage;
    private boolean showLocalSuggestions = true;
    private boolean showRemoteSuggestions = true;

    @State
    boolean downloadedOnly = false;

    @State
    int durationOrder = 0;

    @State
    long minimumDuration = 0;

    @State
    long maximumDuration = 0;

    private final List<InfoItem> collectedSearchItems = new ArrayList<>();
    private SearchPageBudget deepSearchBudget;
    private boolean deepSearchRunning;
    private int deepSearchResultLimit = 5000;
    private boolean collectionLimitReached;
    @State
    boolean monthlySearchResults;
    private List<String> deepSearchQueries = Collections.emptyList();
    private int deepSearchMonth;
    private boolean deepSearchFirstPage;
    private List<Integer> deepSearchFilters;
    private long deepSearchDelayMillis = DeepSearchOptions.DEFAULT_DELAY_MILLIS;
    private Disposable searchDisposable;
    private Disposable suggestionDisposable;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private SuggestionListAdapter suggestionListAdapter;
    private HistoryRecordManager historyRecordManager;
    private SavedSearchFeedManager savedSearchFeedManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private FragmentSearchBinding searchBinding;

    private View searchToolbarContainer;
    private EditText searchEditText;
    private View searchClear;
    private View searchFilter;
    private View searchMusicFilters;
    private ChipGroup searchMusicFilterChipGroup;

    private final VoiceSearchController voiceSearch = new VoiceSearchController(this,
            query -> {
                wasLoading.set(false);
                wasSearchFocused = false;
                search(query, new String[0], new int[0]);
            });

    private boolean suggestionsPanelVisible = false;

    /*////////////////////////////////////////////////////////////////////////*/

    /**
     * TextWatcher to remove rich-text formatting on the search EditText when pasting content
     * from the clipboard.
     */
    private TextWatcher textWatcher;

    public static SearchFragment getInstance(final int serviceId, final String searchString) {
        final SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(serviceId, searchString, new String[0], new int[0]);

        if (!TextUtils.isEmpty(searchString)) {
            searchFragment.setSearchOnResume();
        }

        return searchFragment;
    }

    public static SearchFragment getSavedFeedInstance(
            @NonNull final SavedSearchFeedEntity entity) {
        final SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(entity.getServiceId(), entity.getQuery(),
                entity.contentFilters(), entity.sortFilters());
        searchFragment.savedSearchFeedId = entity.getUid();
        return searchFragment;
    }

    /**
     * Set wasLoading to true so when the fragment onResume is called, the initial search is done.
     */
    private void setSearchOnResume() {
        wasLoading.set(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        showLocalSuggestions = NewPipeSettings.showLocalSearchSuggestions(activity, prefs);
        showRemoteSuggestions = NewPipeSettings.showRemoteSearchSuggestions(activity, prefs);
        if (serviceId == ServiceList.YouTube.getServiceId()
                && ServiceHelper.isYoutubeMusicMode(context)
                && (contentFilter.length == 0
                || !contentFilter[0].startsWith("music_"))) {
            contentFilter = new String[]{YOUTUBE_MUSIC_SONGS_FILTER};
        }

        suggestionListAdapter = new SuggestionListAdapter();
        historyRecordManager = new HistoryRecordManager(context);
        savedSearchFeedManager = new SavedSearchFeedManager(context);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        searchBinding = FragmentSearchBinding.bind(rootView);
        super.onViewCreated(rootView, savedInstanceState);
        searchBinding.searchDownloadedOnly.setChecked(downloadedOnly);
        searchBinding.searchDownloadedOnly.setOnCheckedChangeListener((chip, selected) -> {
            downloadedOnly = selected;
            applyDownloadedOnlyFilter();
        });
        DownloadedAvailabilityIndex.INSTANCE.getUpdates().observe(getViewLifecycleOwner(),
                ignored -> {
                    if (downloadedOnly && !collectedSearchItems.isEmpty()) {
                        applyDownloadedOnlyFilter();
                    }
                });
        DownloadedAvailabilityIndex.requestRefresh(requireContext());

        updateService();
        // Add the service name to search string hint
        // to make it more obvious which platform is being searched.
        if (service != null) {
            if (contentFilter.length == 0) {
                searchEditText.setHint(
                        getString(R.string.search_with_service_name,
                                getSearchServiceName()));
            } else {
                updateSearchHint(contentFilter[0]);
            }
        }
        showSearchOnStart();
        initSearchListeners();
    }

    private void updateService() {
        try {
            service = NewPipe.getService(serviceId);
            updateSearchFilterVisibility();
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Getting service for id " + serviceId, e);
        }
    }

    private void updateSearchFilterVisibility() {
        if (searchFilter == null || searchEditText == null) {
            return;
        }
        final boolean hasFilters = SearchFilterDialog.hasFilters(service);
        final boolean youtubeMusicMode = serviceId == ServiceList.YouTube.getServiceId()
                && ServiceHelper.isYoutubeMusicMode(requireContext());
        final List<FilterItem> musicFilters = service == null || !youtubeMusicMode
                ? Collections.emptyList()
                : SearchFilterDialog.getContentFilters(service, true);
        final boolean showMusicFilterChips = shouldShowMusicFilterChips(
                serviceId == ServiceList.YouTube.getServiceId(), youtubeMusicMode,
                !musicFilters.isEmpty());
        searchFilter.setVisibility(hasFilters && !showMusicFilterChips
                ? View.VISIBLE : View.GONE);
        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) searchEditText.getLayoutParams();
        layoutParams.rightMargin = DeviceUtils.dpToPx(
                hasFilters && !showMusicFilterChips ? 144 : 96, requireContext());
        searchEditText.setLayoutParams(layoutParams);
        updateMusicFilterChips(showMusicFilterChips ? musicFilters : Collections.emptyList());
    }

    static boolean shouldShowMusicFilterChips(final boolean youtubeService,
                                              final boolean youtubeMusicMode,
                                              final boolean hasMusicFilters) {
        return youtubeService && youtubeMusicMode && hasMusicFilters;
    }

    private void updateMusicFilterChips(@NonNull final List<FilterItem> musicFilters) {
        if (searchMusicFilters == null || searchMusicFilterChipGroup == null) {
            return;
        }
        searchMusicFilterChipGroup.setOnCheckedStateChangeListener(null);
        searchMusicFilterChipGroup.removeAllViews();
        if (musicFilters.isEmpty()) {
            searchMusicFilters.setVisibility(View.GONE);
            return;
        }

        final String restoredFilter = contentFilter.length == 0 ? "" : contentFilter[0];
        final String selectedFilter = resolveMusicFilterName(musicFilters, contentFilter);
        if (!selectedFilter.equals(restoredFilter) || sortFilter.length > 0) {
            contentFilter = new String[]{selectedFilter};
            sortFilter = new int[0];
        }
        int checkedChipId = View.NO_ID;
        for (final FilterItem filter : musicFilters) {
            final Chip chip = (Chip) getLayoutInflater().inflate(
                    R.layout.item_search_music_filter_chip,
                    searchMusicFilterChipGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(ServiceHelper.getTranslatedFilterString(
                    filter.getName(), requireContext()));
            chip.setTag(filter.getName());
            searchMusicFilterChipGroup.addView(chip);
            if (filter.getName().equals(selectedFilter)) {
                checkedChipId = chip.getId();
            }
        }

        searchMusicFilterChipGroup.check(checkedChipId);
        searchMusicFilterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            final Chip selectedChip = group.findViewById(checkedIds.get(0));
            if (selectedChip != null && selectedChip.getTag() instanceof String) {
                applySearchFilters((String) selectedChip.getTag(), Collections.emptyList());
            }
        });
        searchMusicFilters.setVisibility(View.VISIBLE);
    }

    @NonNull
    static String resolveMusicFilterName(@NonNull final List<FilterItem> musicFilters,
                                         @NonNull final String[] currentContentFilter) {
        final String restoredFilter = currentContentFilter.length == 0
                ? "" : currentContentFilter[0];
        for (final FilterItem filter : musicFilters) {
            if (filter.getName().equals(restoredFilter)) {
                return restoredFilter;
            }
        }
        return musicFilters.isEmpty() ? "" : musicFilters.get(0).getName();
    }

    @Override
    public void onStart() {
        if (DEBUG) {
            Log.d(TAG, "onStart() called");
        }
        super.onStart();

        updateService();
    }

    @Override
    public void onPause() {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).hideMainNavigationForSearch();
        }
        stopDeepSearch();
        super.onPause();

        wasSearchFocused = searchEditText.hasFocus();

        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }
        disposables.clear();
        hideKeyboardSearch();
    }

    @Override
    public void onResume() {
        if (DEBUG) {
            Log.d(TAG, "onResume() called");
        }
        super.onResume();
        if (showKidsModeRestriction()) {
            return;
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showMainNavigationForSearch();
        }

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) {
            initSuggestionObserver();
        }

        if (voiceSearch.submitPendingQuery()) {
            return;
        }

        if (!TextUtils.isEmpty(searchString)) {
            if (wasLoading.getAndSet(false)) {
                search(searchString, contentFilter, sortFilter);
                return;
            } else if (infoListAdapter.getItemsList().isEmpty()
                    && collectedSearchItems.isEmpty()) {
                if (savedState == null) {
                    search(searchString, contentFilter, sortFilter);
                    return;
                } else if (!isLoading.get() && !wasSearchFocused && lastPanelError == null) {
                    infoListAdapter.clearStreamItemList();
                    showEmptyState();
                }
            }
        }

        handleSearchSuggestion();

        showMetaInfoInTextView(metaInfo == null ? null : Arrays.asList(metaInfo),
                searchBinding.searchMetaInfoTextView, searchBinding.searchMetaInfoSeparator,
                disposables);

        if (TextUtils.isEmpty(searchString) || wasSearchFocused) {
            showKeyboardSearch();
            showSuggestionsPanel();
        } else {
            hideKeyboardSearch();
            hideSuggestionsPanel();
        }
        wasSearchFocused = false;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) {
            Log.d(TAG, "onDestroyView() called");
        }
        stopDeepSearch();
        unsetSearchListeners();

        if (searchMusicFilterChipGroup != null) {
            searchMusicFilterChipGroup.setOnCheckedStateChangeListener(null);
            searchMusicFilterChipGroup.removeAllViews();
        }
        if (searchMusicFilters != null) {
            searchMusicFilters.setVisibility(View.GONE);
        }
        searchMusicFilterChipGroup = null;
        searchMusicFilters = null;

        searchBinding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }
        disposables.clear();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST) {
            if (resultCode == Activity.RESULT_OK
                    && !TextUtils.isEmpty(searchString)) {
                search(searchString, contentFilter, sortFilter);
            } else {
                Log.e(TAG, "ReCaptcha failed");
            }
        } else {
            Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        searchBinding.suggestionsList.setAdapter(suggestionListAdapter);
        // animations are just strange and useless, since the suggestions keep changing too much
        searchBinding.suggestionsList.setItemAnimator(null);
        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull final RecyclerView recyclerView,
                                        @NonNull final RecyclerView.ViewHolder viewHolder) {
                return getSuggestionMovementFlags(viewHolder);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder viewHolder,
                                  @NonNull final RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, final int i) {
                onSuggestionItemSwiped(viewHolder);
            }
        }).attachToRecyclerView(searchBinding.suggestionsList);

        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container);
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text);
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear);
        searchFilter = searchToolbarContainer.findViewById(R.id.toolbar_search_filter);
        searchMusicFilters = activity.findViewById(R.id.toolbar_search_music_filters);
        searchMusicFilterChipGroup = searchMusicFilters.findViewById(
                R.id.toolbar_search_music_filter_chip_group);
        updateSearchFilterVisibility();
        searchBinding.deepSearchButton.setOnClickListener(view -> {
            if (deepSearchRunning) {
                stopDeepSearch();
            } else {
                showDeepSearchDialog();
            }
        });
        updateDeepSearchStatus();
        loadSavedSearchCache();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(nextPage);
        objectsToSave.add(new ArrayList<>(collectedSearchItems));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        nextPage = (Page) savedObjects.poll();
        collectedSearchItems.clear();
        final Object collected = savedObjects.poll();
        if (collected instanceof List<?>) {
            collectedSearchItems.addAll((List<InfoItem>) collected);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        searchString = searchEditText != null
                ? getSearchEditString().trim()
                : searchString;
        super.onSaveInstanceState(bundle);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void reloadContent() {
        if (!TextUtils.isEmpty(searchString) || (searchEditText != null
                && !isSearchEditBlank())) {
            search(!TextUtils.isEmpty(searchString)
                    ? searchString
                    : getSearchEditString(), this.contentFilter, this.sortFilter);
        } else {
            if (searchEditText != null) {
                searchEditText.setText("");
                showKeyboardSearch();
            }
            hideErrorPanel();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (!KidsMode.isSearchAllowed(requireContext(), serviceId)) {
            return;
        }
        super.onCreateOptionsMenu(menu, inflater);

        menu.add(Menu.NONE, MENU_CLEAR_SEARCH_HISTORY, Menu.NONE,
                        R.string.clear_search_history_title)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (!TextUtils.isEmpty(searchString)) {
            menu.add(Menu.NONE, MENU_DEEP_SEARCH, Menu.NONE, R.string.search_fetch_more)
                    .setEnabled(!isLoading.get() && (supportsMonthlySearch()
                            || (Page.isValid(nextPage) && collectedSearchItems.size() < 20000)))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            if (savedSearchFeedId == SavedSearchFeedManager.NO_SAVED_SEARCH_FEED) {
                if (!monthlySearchResults && searchAfter.isEmpty()
                        && searchBefore.isEmpty() && !hasDurationOptions()) {
                    menu.add(Menu.NONE, MENU_SAVE_SEARCH_FEED, Menu.NONE,
                                    R.string.save_search_as_feed)
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            } else {
                menu.add(Menu.NONE, MENU_REFRESH_SEARCH_FEED, Menu.NONE,
                                R.string.refresh_saved_search_feed)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                menu.add(Menu.NONE, MENU_DELETE_SEARCH_FEED, Menu.NONE,
                                R.string.delete_saved_search_feed)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == MENU_DEEP_SEARCH) {
            showDeepSearchDialog();
            return true;
        } else if (item.getItemId() == MENU_CLEAR_SEARCH_HISTORY) {
            HistorySettingsFragment.openDeleteSearchHistoryDialog(requireContext(),
                    historyRecordManager, disposables, () -> {
                        if (searchEditText != null) {
                            suggestionPublisher.onNext(getSearchEditString());
                        }
                    });
            return true;
        } else if (item.getItemId() == MENU_SAVE_SEARCH_FEED) {
            showSaveSearchFeedDialog();
            return true;
        } else if (item.getItemId() == MENU_REFRESH_SEARCH_FEED) {
            search(searchString, contentFilter, sortFilter);
            return true;
        } else if (item.getItemId() == MENU_DELETE_SEARCH_FEED) {
            showDeleteSavedSearchFeedDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private void loadSavedSearchCache() {
        if (savedSearchFeedId == SavedSearchFeedManager.NO_SAVED_SEARCH_FEED) {
            return;
        }

        disposables.add(savedSearchFeedManager.getCachedItems(savedSearchFeedId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (!items.isEmpty()) {
                        collectedSearchItems.clear();
                        collectedSearchItems.addAll(items);
                        if (infoListAdapter.getItemsList().isEmpty()) {
                            renderSearchItems();
                        }
                        hideLoading();
                    } else if (!TextUtils.isEmpty(searchString)) {
                        search(searchString, contentFilter, sortFilter);
                    }
                }, throwable -> {
                    if (!TextUtils.isEmpty(searchString)) {
                        search(searchString, contentFilter, sortFilter);
                    }
                }));
    }

    private void showSaveSearchFeedDialog() {
        final EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setText(searchString);
        nameInput.setSelection(nameInput.length());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.save_search_as_feed)
                .setMessage(R.string.saved_search_feed_name)
                .setView(nameInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    final String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.saved_search_feed_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    disposables.add(savedSearchFeedManager.create(name, serviceId, searchString,
                                    contentFilter, sortFilter)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(feedId -> {
                                savedSearchFeedId = feedId;
                                activity.invalidateOptionsMenu();
                                Toast.makeText(requireContext(),
                                        R.string.saved_search_feed_saved,
                                        Toast.LENGTH_SHORT).show();
                                disposables.add(savedSearchFeedManager.replaceCache(
                                                feedId, infoListAdapter.getItemsList())
                                        .subscribe(() -> { }, throwable -> { }));
                            }, throwable -> Toast.makeText(requireContext(),
                                    R.string.saved_search_feed_save_failed,
                                    Toast.LENGTH_LONG).show()));
                })
                .show();
    }

    private void showDeleteSavedSearchFeedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_saved_search_feed)
                .setMessage(R.string.delete_saved_search_feed_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        disposables.add(savedSearchFeedManager.delete(savedSearchFeedId)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> getFM().popBackStack(),
                                        throwable -> Toast.makeText(requireContext(),
                                                R.string.saved_search_feed_delete_failed,
                                                Toast.LENGTH_LONG).show())))
                .show();
    }

    private void cacheSavedSearchResults(final List<? extends InfoItem> items,
                                         final boolean replace) {
        if (savedSearchFeedId == SavedSearchFeedManager.NO_SAVED_SEARCH_FEED) {
            return;
        }

        final List<InfoItem> copiedItems = new ArrayList<>(items);
        final Completable cacheOperation = replace
                ? savedSearchFeedManager.replaceCache(savedSearchFeedId, copiedItems)
                : savedSearchFeedManager.appendCache(savedSearchFeedId, copiedItems);
        disposables.add(cacheOperation.subscribe(() -> { }, throwable -> { }));
    }

    private void showSearchOnStart() {
        if (DEBUG) {
            Log.d(TAG, "showSearchOnStart() called, searchQuery → "
                    + searchString
                    + ", lastSearchedQuery → "
                    + lastSearchedString);
        }
        searchEditText.setText(searchString);

        if (TextUtils.isEmpty(searchString)
                || isSearchEditBlank()) {
            searchToolbarContainer.setTranslationX(100);
            searchToolbarContainer.setAlpha(0.0f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
            searchToolbarContainer.animate()
                    .translationX(0)
                    .alpha(1.0f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            searchToolbarContainer.setTranslationX(0);
            searchToolbarContainer.setAlpha(1.0f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
        }
    }

    private void initSearchListeners() {
        if (DEBUG) {
            Log.d(TAG, "initSearchListeners() called");
        }
        voiceSearch.bind(searchToolbarContainer);
        searchClear.setOnClickListener(v -> {
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }
            if (isSearchEditBlank()) {
                NavigationHelper.gotoMainFragment(getFM());
                return;
            }

            searchBinding.correctSuggestion.setVisibility(View.GONE);

            searchEditText.setText("");
            suggestionListAdapter.submitList(null);
            showKeyboardSearch();
        });

        TooltipCompat.setTooltipText(searchClear, getString(R.string.clear));
        TooltipCompat.setTooltipText(searchFilter, getString(R.string.search_filters));
        searchFilter.setOnClickListener(v -> showSearchFilterDialog());

        searchEditText.setOnClickListener(v -> {
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }
            if ((showLocalSuggestions || showRemoteSuggestions) && !isErrorPanelVisible()) {
                showSuggestionsPanel();
            }
            if (DeviceUtils.isTv(getContext())) {
                showKeyboardSearch();
            }
        });

        searchEditText.setOnFocusChangeListener((final View v, final boolean hasFocus) -> {
            if (DEBUG) {
                Log.d(TAG, "onFocusChange() called with: "
                        + "v = [" + v + "], hasFocus = [" + hasFocus + "]");
            }
            if ((showLocalSuggestions || showRemoteSuggestions)
                    && hasFocus && !isErrorPanelVisible()) {
                showSuggestionsPanel();
            }
        });

        suggestionListAdapter.setListener(new SuggestionListAdapter.OnSuggestionItemSelected() {
            @Override
            public void onSuggestionItemSelected(final SuggestionItem item) {
                search(item.query, new String[0], new int[0]);
                searchEditText.setText(item.query);
            }

            @Override
            public void onSuggestionItemInserted(final SuggestionItem item) {
                searchEditText.setText(item.query);
                searchEditText.setSelection(searchEditText.getText().length());
            }

            @Override
            public void onSuggestionItemLongClick(final SuggestionItem item) {
                if (item.fromHistory) {
                    showDeleteSuggestionDialog(item);
                }
            }
        });

        if (textWatcher != null) {
            searchEditText.removeTextChangedListener(textWatcher);
        }
        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start,
                                          final int count, final int after) {
                // Do nothing, old text is already clean
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start,
                                      final int before, final int count) {
                // Changes are handled in afterTextChanged; CharSequence cannot be changed here.
            }

            @Override
            public void afterTextChanged(final Editable s) {
                // Remove rich text formatting
                for (final CharacterStyle span : s.getSpans(0, s.length(), CharacterStyle.class)) {
                    s.removeSpan(span);
                }

                final String newText = getSearchEditString().trim();
                suggestionPublisher.onNext(newText);
            }
        };
        searchEditText.addTextChangedListener(textWatcher);
        searchEditText.setOnEditorActionListener(
                (final TextView v, final int actionId, final KeyEvent event) -> {
                    if (DEBUG) {
                        Log.d(TAG, "onEditorAction() called with: v = [" + v + "], "
                                + "actionId = [" + actionId + "], event = [" + event + "]");
                    }
                    if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                        hideKeyboardSearch();
                    } else if (event != null
                            && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                        searchEditText.setText(getSearchEditString().trim());
                        search(getSearchEditString(), new String[0], new int[0]);
                        return true;
                    }
                    return false;
                });

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) {
            initSuggestionObserver();
        }
    }

    private void unsetSearchListeners() {
        if (DEBUG) {
            Log.d(TAG, "unsetSearchListeners() called");
        }
        voiceSearch.unbind();
        searchClear.setOnClickListener(null);
        searchClear.setOnLongClickListener(null);
        searchFilter.setOnClickListener(null);
        searchEditText.setOnClickListener(null);
        searchEditText.setOnFocusChangeListener(null);
        searchEditText.setOnEditorActionListener(null);

        if (textWatcher != null) {
            searchEditText.removeTextChangedListener(textWatcher);
        }
        textWatcher = null;
    }

    private void showSuggestionsPanel() {
        if (DEBUG) {
            Log.d(TAG, "showSuggestionsPanel() called");
        }
        suggestionsPanelVisible = true;
        animate(searchBinding.suggestionsPanel, true, 200,
                AnimationType.LIGHT_SLIDE_AND_ALPHA);
    }

    private void hideSuggestionsPanel() {
        if (DEBUG) {
            Log.d(TAG, "hideSuggestionsPanel() called");
        }
        suggestionsPanelVisible = false;
        animate(searchBinding.suggestionsPanel, false, 200,
                AnimationType.LIGHT_SLIDE_AND_ALPHA);
    }

    private void showKeyboardSearch() {
        if (DEBUG) {
            Log.d(TAG, "showKeyboardSearch() called");
        }
        KeyboardUtil.showKeyboard(activity, searchEditText);
    }

    private void hideKeyboardSearch() {
        if (DEBUG) {
            Log.d(TAG, "hideKeyboardSearch() called");
        }

        KeyboardUtil.hideKeyboard(activity, searchEditText);
    }

    private void showDeleteSuggestionDialog(final SuggestionItem item) {
        if (activity == null || historyRecordManager == null || searchEditText == null) {
            return;
        }
        final String query = item.query;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    howManyDeleted -> suggestionPublisher
                                            .onNext(getSearchEditString()),
                                    throwable -> showSnackBarError(new ErrorInfo(throwable,
                                            UserAction.DELETE_FROM_HISTORY,
                                            "Deleting item failed")));
                    disposables.add(onDelete);
                })
                .show();
    }

    @Override
    public boolean onBackPressed() {
        if (deepSearchRunning) {
            stopDeepSearch();
            return true;
        }
        if (suggestionsPanelVisible
                && !infoListAdapter.getItemsList().isEmpty()
                && !isLoading.get()) {
            hideSuggestionsPanel();
            hideKeyboardSearch();
            searchEditText.setText(lastSearchedString);
            return true;
        }
        return false;
    }


    private Observable<List<SuggestionItem>> getLocalSuggestionsObservable(
            final String query, final int similarQueryLimit) {
        return historyRecordManager
                .getRelatedSearches(query, similarQueryLimit, 25)
                .toObservable()
                .map(searchHistoryEntries ->
                        searchHistoryEntries.stream()
                                .map(entry -> new SuggestionItem(true, entry))
                                .collect(Collectors.toList()));
    }

    private Observable<List<SuggestionItem>> getRemoteSuggestionsObservable(final String query) {
        return ExtractorHelper
                .suggestionsFor(serviceId, query)
                .toObservable()
                .map(strings -> {
                    final List<SuggestionItem> result = new ArrayList<>();
                    for (final String entry : strings) {
                        result.add(new SuggestionItem(false, entry));
                    }
                    return result;
                });
    }

    private void initSuggestionObserver() {
        if (DEBUG) {
            Log.d(TAG, "initSuggestionObserver() called");
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }

        suggestionDisposable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
                .startWithItem(searchString == null ? "" : searchString)
                .switchMap(query -> {
                    // Only show remote suggestions if they are enabled in settings and
                    // the query length is at least THRESHOLD_NETWORK_SUGGESTION
                    final boolean shallShowRemoteSuggestionsNow = showRemoteSuggestions
                            && query.length() >= THRESHOLD_NETWORK_SUGGESTION;

                    if (showLocalSuggestions && shallShowRemoteSuggestionsNow) {
                        return Observable.zip(
                                getLocalSuggestionsObservable(query, 3),
                                getRemoteSuggestionsObservable(query),
                                (local, remote) -> {
                                    remote.removeIf(remoteItem -> local.stream().anyMatch(
                                            localItem -> localItem.equals(remoteItem)));
                                    local.addAll(remote);
                                    return local;
                                })
                                .materialize();
                    } else if (showLocalSuggestions) {
                        return getLocalSuggestionsObservable(query, 25)
                                .materialize();
                    } else if (shallShowRemoteSuggestionsNow) {
                        return getRemoteSuggestionsObservable(query)
                                .materialize();
                    } else {
                        return Single.fromCallable(Collections::<SuggestionItem>emptyList)
                                .toObservable()
                                .materialize();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        listNotification -> {
                            if (listNotification.isOnNext()) {
                                if (listNotification.getValue() != null) {
                                    handleSuggestions(listNotification.getValue());
                                }
                            } else if (listNotification.isOnError()
                                    && listNotification.getError() != null
                                    && !ExceptionUtils.isInterruptedCaused(
                                    listNotification.getError())) {
                                showSnackBarError(new ErrorInfo(listNotification.getError(),
                                        UserAction.GET_SUGGESTIONS, searchString, serviceId));
                            }
                        }, throwable -> showSnackBarError(new ErrorInfo(
                                throwable, UserAction.GET_SUGGESTIONS, searchString, serviceId)));
    }

    private boolean showKidsModeRestriction() {
        if (KidsMode.isSearchAllowed(requireContext(), serviceId)) {
            return false;
        }
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }
        stopDeepSearch();
        hideKeyboardSearch();
        hideSuggestionsPanel();
        searchToolbarContainer.setVisibility(View.GONE);
        infoListAdapter.clearStreamItemList();
        showEmptyState();
        setEmptyStateMessage(R.string.kids_mode_search_disabled);
        return true;
    }

    @Override
    protected void doInitialLoadLogic() {
        // no-op
    }

    /**
     * Perform a search.
     * @param theSearchString the trimmed search string
     * @param theContentFilter the content filter to use. FIXME: unused param
     * @param theSortFilter FIXME: unused param
     */
    private void search(@NonNull final String theSearchString,
                        final String[] theContentFilter,
                        final int[] theSortFilter) {
        if (!isSearchUiReady(isAdded(), activity != null, searchBinding != null)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "search() called with: query = [" + theSearchString + "]");
        }
        if (showKidsModeRestriction() || theSearchString.isEmpty()) {
            return;
        }

        // Check if theSearchString is a URL which can be opened by WizeStream directly
        // and open it if possible.
        try {
            final StreamingService streamingService = NewPipe.getServiceByUrl(theSearchString);
            showLoading();
            disposables.add(Observable
                    .fromCallable(() -> NavigationHelper.getIntentByLink(activity,
                            streamingService, theSearchString))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(intent -> {
                        getFM().popBackStackImmediate();
                        activity.startActivity(intent);
                    }, throwable -> showTextError(getString(R.string.unsupported_url))));
            return;
        } catch (final Exception ignored) {
            // Exception occurred, it's not a url
        }

        // prepare search
        if (savedSearchFeedId != SavedSearchFeedManager.NO_SAVED_SEARCH_FEED
                && !theSearchString.equals(this.searchString)) {
            savedSearchFeedId = SavedSearchFeedManager.NO_SAVED_SEARCH_FEED;
            activity.invalidateOptionsMenu();
        }
        lastSearchedString = this.searchString;
        this.searchString = theSearchString;
        activity.invalidateOptionsMenu();
        infoListAdapter.clearStreamItemList();
        hideSuggestionsPanel();
        showMetaInfoInTextView(null, searchBinding.searchMetaInfoTextView,
                searchBinding.searchMetaInfoSeparator, disposables);
        hideKeyboardSearch();

        // store search query if search history is enabled
        disposables.add(historyRecordManager.onSearched(serviceId, theSearchString)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> {
                        },
                        throwable -> showSnackBarError(new ErrorInfo(throwable, UserAction.SEARCHED,
                                theSearchString, serviceId))
                ));

        effectiveSearchString = SearchDateRange.query(searchString,
                searchAfter, searchBefore, LocalDate.now());
        nextPage = null;

        // load search results
        suggestionPublisher.onNext(theSearchString);
        startLoading(false);
    }

    static boolean isSearchUiReady(final boolean fragmentAdded,
                                   final boolean hasActivity,
                                   final boolean hasBinding) {
        return fragmentAdded && hasActivity && hasBinding;
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        if (showKidsModeRestriction()) {
            return;
        }
        stopDeepSearch();
        collectionLimitReached = false;
        monthlySearchResults = false;
        collectedSearchItems.clear();
        infoListAdapter.clearStreamItemList();
        nextPage = null;
        super.startLoading(forceLoad);
        updateDeepSearchStatus();
        disposables.clear();
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        searchDisposable = ExtractorHelper.searchForFilters(serviceId,
                getEffectiveSearchString(),
                Arrays.asList(contentFilter),
                getSelectedSortFilterIds())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((searchResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleResult, this::onItemError);

    }

    @Override
    protected void loadMoreItems() {
        if (deepSearchRunning || collectionLimitReached || !Page.isValid(nextPage)) {
            return;
        }
        isLoading.set(true);
        showListFooter(true);
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        searchDisposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                getEffectiveSearchString(),
                asList(contentFilter),
                getSelectedSortFilterIds(),
                nextPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((nextItemsResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleNextItems, this::onItemError);
    }

    @Override
    protected boolean hasMoreItems() {
        return !collectionLimitReached && Page.isValid(nextPage);
    }

    @Override
    protected void onItemSelected(final InfoItem selectedItem) {
        super.onItemSelected(selectedItem);
        hideKeyboardSearch();
    }

    private void onItemError(final Throwable exception) {
        updateDeepSearchStatus();
        if (!collectedSearchItems.isEmpty()
                || (savedSearchFeedId != SavedSearchFeedManager.NO_SAVED_SEARCH_FEED
                && !infoListAdapter.getItemsList().isEmpty())) {
            hideLoading();
            showListFooter(false);
            if (infoListAdapter.getItemsList().isEmpty()) {
                showEmptyState();
            }
            updateDeepSearchStatus();
            showSnackBarError(new ErrorInfo(exception, UserAction.SEARCHED,
                    searchString, serviceId, getOpenInBrowserUrlForErrors()));
            return;
        }

        if (exception instanceof SearchExtractor.NothingFoundException) {
            infoListAdapter.clearStreamItemList();
            showEmptyState();
        } else {
            showError(new ErrorInfo(exception, UserAction.SEARCHED, searchString, serviceId,
                    getOpenInBrowserUrlForErrors()));
        }
    }

    @Nullable
    private String getOpenInBrowserUrlForErrors() {
        if (TextUtils.isEmpty(searchString)) {
            return null;
        }
        try {
            return service.getSearchQHFactory().getUrl(getEffectiveSearchString(),
                    ExtractorHelper.resolveFilterItems(
                            service.getSearchQHFactory().getAvailableContentFilter(),
                            Arrays.asList(contentFilter)),
                    ExtractorHelper.resolveFilterItemsByIds(
                            service.getSearchQHFactory(),
                            getSelectedSortFilterIds()));
        } catch (final NullPointerException | ParsingException ignored) {
            return null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private boolean hasDurationOptions() {
        return durationOrder != 0 || minimumDuration > 0 || maximumDuration > 0;
    }

    private List<InfoItem> collectSearchItems(final List<? extends InfoItem> items) {
        final int previousSize = collectedSearchItems.size();
        collectedSearchItems.addAll(items);
        final List<InfoItem> unique = SearchResultOrdering.arrange(
                collectedSearchItems, 0, 0, 0);
        collectedSearchItems.clear();
        final int retained = deepSearchRunning
                ? Math.min(unique.size(), deepSearchResultLimit) : unique.size();
        collectedSearchItems.addAll(unique.subList(0, retained));
        return new ArrayList<>(unique.subList(Math.min(previousSize, retained), retained));
    }

    private void renderSearchItems() {
        final List<InfoItem> ordered = SearchResultOrdering.arrange(collectedSearchItems,
                durationOrder, minimumDuration, maximumDuration);
        final List<InfoItem> displayed = downloadedOnly
                ? ordered.stream()
                        .filter(item -> item instanceof StreamInfoItem
                                && DownloadedAvailabilityIndex.isAvailable(
                                        item.getServiceId(), item.getUrl()))
                        .collect(Collectors.toList())
                : ordered;
        infoListAdapter.clearStreamItemList();
        infoListAdapter.addInfoItemList(displayed);
    }

    private void applyDownloadedOnlyFilter() {
        if (searchBinding == null || infoListAdapter == null) {
            return;
        }
        renderSearchItems();
        if (infoListAdapter.getItemsList().isEmpty()) {
            showEmptyState();
        } else {
            hideLoading();
        }
        updateDeepSearchStatus();
    }

    private void updateDeepSearchStatus() {
        if (searchBinding == null) {
            return;
        }
        final Context context = searchBinding.getRoot().getContext();
        searchBinding.deepSearchControls.setVisibility(
                hasDurationOptions() || deepSearchRunning ? View.VISIBLE : View.GONE);
        searchBinding.deepSearchButton.setText(deepSearchRunning
                ? R.string.cancel : R.string.search_fetch_more);
        searchBinding.deepSearchButton.setEnabled(deepSearchRunning
                || (!isLoading.get() && (supportsMonthlySearch()
                || (Page.isValid(nextPage) && collectedSearchItems.size() < 20000))));
        searchBinding.deepSearchStatus.setText(deepSearchRunning
                ? context.getString(R.string.search_deep_progress,
                        deepSearchBudget.getCompletedPages(),
                        deepSearchBudget.getMaximumPages(), collectedSearchItems.size(),
                        infoListAdapter.getItemsList().size())
                : context.getString(R.string.search_collected_count, collectedSearchItems.size(),
                        infoListAdapter.getItemsList().size()));
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    private boolean supportsMonthlySearch() {
        return serviceId == ServiceList.YouTube.getServiceId()
                && savedSearchFeedId == SavedSearchFeedManager.NO_SAVED_SEARCH_FEED
                && Arrays.stream(contentFilter).noneMatch(value -> value.startsWith("music_"));
    }

    private void showDeepSearchDialog() {
        if (isLoading.get() || searchBinding == null || deepSearchRunning) {
            return;
        }
        DeepSearchDialog.show(requireContext(), supportsMonthlySearch(), this::startDeepSearch);
    }

    private void startDeepSearch(final DeepSearchOptions.Request options) {
        if (isLoading.get() || searchBinding == null || deepSearchRunning
                || options.getDelayMillis() < DeepSearchOptions.MIN_DELAY_MILLIS
                || options.getDelayMillis() > DeepSearchOptions.MAX_DELAY_MILLIS) {
            return;
        }
        final Integer year = options.getYear();
        if (year == null && (!Page.isValid(nextPage)
                || collectedSearchItems.size() >= options.getResults())) {
            return;
        }
        deepSearchFilters = getSelectedSortFilterIds();
        if (year != null) {
            if (!supportsMonthlySearch()) {
                return;
            }
            monthlySearchResults = true;
            deepSearchQueries = MonthlySearchQueries.forYear(searchString, year);
            deepSearchFilters = deepSearchFilters.stream().filter(id -> {
                final var filter = service.getSearchQHFactory().getFilterItem(id);
                return filter == null || !MonthlySearchQueries.isUploadDateFilter(filter.getName());
            }).collect(Collectors.toList());
            collectedSearchItems.clear();
            infoListAdapter.clearStreamItemList();
            nextPage = null;
            deepSearchMonth = 0;
            deepSearchFirstPage = true;
        }
        collectionLimitReached = false;
        deepSearchDelayMillis = options.getDelayMillis();
        deepSearchResultLimit = options.getResults();
        deepSearchBudget = new SearchPageBudget(options.getPages(), deepSearchResultLimit);
        deepSearchRunning = true;
        loadDeepSearchPage();
    }

    private void loadDeepSearchPage() {
        if (!deepSearchRunning || searchBinding == null) {
            return;
        }
        final boolean monthly = !deepSearchQueries.isEmpty();
        final String query = monthly ? deepSearchQueries.get(deepSearchMonth)
                : getEffectiveSearchString();
        final boolean first = monthly && deepSearchFirstPage;
        final boolean allowed = first
                ? deepSearchBudget.requestFirst(query, collectedSearchItems.size())
                : deepSearchBudget.requestForQuery(query, nextPage, collectedSearchItems.size());
        if (!allowed) {
            if (monthly && deepSearchBudget.getCompletedPages() < deepSearchBudget.getMaximumPages()
                    && collectedSearchItems.size() < deepSearchResultLimit
                    && ++deepSearchMonth < deepSearchQueries.size()) {
                nextPage = null;
                deepSearchFirstPage = true;
                loadDeepSearchPage();
                return;
            }
            if (!monthly
                    && deepSearchBudget.getCompletedPages() < deepSearchBudget.getMaximumPages()
                    && collectedSearchItems.size() < deepSearchResultLimit) {
                nextPage = null;
            }
            stopDeepSearch();
            return;
        }
        isLoading.set(true);
        updateDeepSearchStatus();
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        final Page continuation = nextPage;
        final long delay = deepSearchBudget.getCompletedPages() == 0 ? 0 : deepSearchDelayMillis;
        searchDisposable = Single.timer(delay, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMap(ignored -> first
                        ? ExtractorHelper.searchForFilters(serviceId, query,
                                asList(contentFilter), deepSearchFilters)
                                .<ListExtractor.InfoItemsPage<?>>map(info -> {
                                    final List<Throwable> errors = info.getErrors().stream()
                                            .filter(error ->
                                                    !(error instanceof NothingFoundException))
                                            .collect(Collectors.toList());
                                    return new ListExtractor.InfoItemsPage<>(info.getRelatedItems(),
                                            info.getNextPage(), errors);
                                })
                        : ExtractorHelper.getMoreSearchItems(serviceId, query,
                                asList(contentFilter), deepSearchFilters, continuation))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (!deepSearchRunning || searchBinding == null) {
                        return;
                    }
                    deepSearchFirstPage = false;
                    handleNextItems(result);
                    deepSearchBudget.completed();
                    loadDeepSearchPage();
                }, throwable -> {
                    if (monthly && throwable instanceof SearchExtractor.NothingFoundException) {
                        nextPage = null;
                        deepSearchFirstPage = false;
                        deepSearchBudget.completed();
                        loadDeepSearchPage();
                        return;
                    }
                    stopDeepSearch();
                    showSnackBarError(new ErrorInfo(throwable, UserAction.SEARCHED,
                            searchString, serviceId, getOpenInBrowserUrlForErrors()));
                });
    }

    private void stopDeepSearch() {
        if (!deepSearchRunning) {
            return;
        }
        deepSearchRunning = false;
        collectionLimitReached = collectedSearchItems.size() >= deepSearchResultLimit;
        if (!deepSearchQueries.isEmpty()) {
            nextPage = null;
            deepSearchQueries = Collections.emptyList();
        }
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        isLoading.set(false);
        showListFooter(false);
        updateDeepSearchStatus();
    }

    private String getEffectiveSearchString() {
        return effectiveSearchString.isEmpty() ? searchString : effectiveSearchString;
    }

    private void showSearchFilterDialog() {
        if (service == null) {
            updateService();
        }
        if (service == null || !SearchFilterDialog.hasFilters(service)) {
            return;
        }
        SearchFilterDialog.showExtended(requireContext(), service, contentFilter, sortFilter,
                serviceId == ServiceList.YouTube.getServiceId()
                        && ServiceHelper.isYoutubeMusicMode(requireContext()),
                searchAfter, searchBefore, durationOrder, minimumDuration, maximumDuration,
                (selectedContent, selectedSort, after, before, order, minimum, maximum) -> {
                    final boolean durationChanged = durationOrder != order
                            || minimumDuration != minimum || maximumDuration != maximum;
                    durationOrder = order;
                    minimumDuration = minimum;
                    maximumDuration = maximum;
                    final boolean datesChanged = !searchAfter.equals(after)
                            || !searchBefore.equals(before);
                    searchAfter = after;
                    searchBefore = before;
                    applySearchFilters(selectedContent, selectedSort,
                            datesChanged || durationChanged);
                });
    }

    private void applySearchFilters(@NonNull final String selectedContentFilter,
                                    @NonNull final List<Integer> selectedSortFilters) {
        applySearchFilters(selectedContentFilter, selectedSortFilters, false);
    }

    private void applySearchFilters(@NonNull final String selectedContentFilter,
                                    @NonNull final List<Integer> selectedSortFilters,
                                    final boolean datesChanged) {
        final boolean contentFilterUnchanged = contentFilter.length == 1
                && selectedContentFilter.equals(contentFilter[0]);
        final int[] newSortFilters = selectedSortFilters.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        if (!datesChanged && contentFilterUnchanged && Arrays.equals(sortFilter, newSortFilters)) {
            return;
        }
        if (savedSearchFeedId != SavedSearchFeedManager.NO_SAVED_SEARCH_FEED) {
            savedSearchFeedId = SavedSearchFeedManager.NO_SAVED_SEARCH_FEED;
            activity.invalidateOptionsMenu();
        }
        contentFilter = selectedContentFilter.isEmpty()
                ? new String[0] : new String[]{selectedContentFilter};
        sortFilter = newSortFilters;
        if (!selectedContentFilter.isEmpty()) {
            updateSearchHint(selectedContentFilter);
        }
        if (!TextUtils.isEmpty(searchString)) {
            search(searchString, contentFilter, sortFilter);
        }
    }

    private void updateSearchHint(@NonNull final String selectedContentFilter) {
        if (service == null || searchEditText == null) {
            return;
        }
        if ("all".equals(selectedContentFilter)) {
            searchEditText.setHint(getString(R.string.search_with_service_name,
                    getSearchServiceName()));
        } else {
            searchEditText.setHint(getString(R.string.search_with_service_name_and_filter,
                    getSearchServiceName(),
                    ServiceHelper.getTranslatedFilterString(
                            selectedContentFilter, requireContext())));
        }
    }

    @NonNull
    private String getSearchServiceName() {
        if (serviceId == ServiceList.YouTube.getServiceId()
                && ServiceHelper.isYoutubeMusicMode(requireContext())) {
            return ServiceHelper.getSelectedServiceName(requireContext());
        }
        return service.getServiceInfo().getName();
    }

    private void setQuery(final int theServiceId,
                          final String theSearchString,
                          final String[] theContentFilter,
                          final int[] theSortFilter) {
        serviceId = theServiceId;
        searchString = theSearchString;
        contentFilter = theContentFilter;
        sortFilter = theSortFilter;
    }

    @NonNull
    private List<Integer> getSelectedSortFilterIds() {
        return Arrays.stream(sortFilter).boxed().collect(Collectors.toList());
    }

    private String getSearchEditString() {
        return searchEditText.getText().toString();
    }

    @Override
    protected boolean shouldPlayOnBackground(@NonNull final StreamInfoItem item) {
        return (contentFilter.length == 0
                || !YOUTUBE_MUSIC_VIDEOS_FILTER.equals(contentFilter[0]))
                && super.shouldPlayOnBackground(item);
    }

    private boolean isSearchEditBlank() {
        return isBlank(getSearchEditString());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    //////////////////////////////////////////////////////////////////////////*/

    public void handleSuggestions(@NonNull final List<SuggestionItem> suggestions) {
        if (DEBUG) {
            Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]");
        }
        suggestionListAdapter.submitList(suggestions,
                () -> {
                    if (searchBinding != null) {
                        searchBinding.suggestionsList.scrollToPosition(0);
                    }
                });

        if (suggestionsPanelVisible && isErrorPanelVisible()) {
            hideLoading();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void hideLoading() {
        super.hideLoading();
        showListFooter(false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        collectSearchItems(result.getRelatedItems());
        cacheSavedSearchResults(result.getRelatedItems(), true);
        final List<Throwable> exceptions = result.getErrors();
        if (!exceptions.isEmpty()
                && !(exceptions.size() == 1
                && exceptions.get(0) instanceof SearchExtractor.NothingFoundException)) {
            showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                    searchString, serviceId, getOpenInBrowserUrlForErrors()));
        }

        searchSuggestion = result.getSearchSuggestion();
        if (searchSuggestion != null) {
            searchSuggestion = searchSuggestion.trim();
        }
        isCorrectedSearch = result.isCorrectedSearch();

        // List<MetaInfo> cannot be bundled without creating some containers
        metaInfo = result.getMetaInfo().toArray(new MetaInfo[0]);
        showMetaInfoInTextView(result.getMetaInfo(), searchBinding.searchMetaInfoTextView,
                searchBinding.searchMetaInfoSeparator, disposables);

        handleSearchSuggestion();

        lastSearchedString = searchString;
        nextPage = result.getNextPage();

        if (infoListAdapter.getItemsList().isEmpty()) {
            renderSearchItems();
            if (infoListAdapter.getItemsList().isEmpty()) {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
                updateDeepSearchStatus();
                return;
            }
        }

        super.handleResult(result);
        updateDeepSearchStatus();
    }

    private void handleSearchSuggestion() {
        if (TextUtils.isEmpty(searchSuggestion)) {
            searchBinding.correctSuggestion.setVisibility(View.GONE);
        } else {
            final String helperText = getString(isCorrectedSearch
                    ? R.string.search_showing_result_for
                    : R.string.did_you_mean);

            final String highlightedSearchSuggestion =
                    "<b><i>" + Html.escapeHtml(searchSuggestion) + "</i></b>";
            final String text = String.format(helperText, highlightedSearchSuggestion);
            searchBinding.correctSuggestion.setText(HtmlCompat.fromHtml(text,
                    HtmlCompat.FROM_HTML_MODE_LEGACY));

            searchBinding.correctSuggestion.setOnClickListener(v -> {
                searchBinding.correctSuggestion.setVisibility(View.GONE);
                search(searchSuggestion, contentFilter, sortFilter);
                searchEditText.setText(searchSuggestion);
            });

            searchBinding.correctSuggestion.setOnLongClickListener(v -> {
                searchEditText.setText(searchSuggestion);
                searchEditText.setSelection(searchSuggestion.length());
                showKeyboardSearch();
                return true;
            });

            searchBinding.correctSuggestion.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<?> result) {
        cacheSavedSearchResults(result.getItems(), false);
        showListFooter(false);
        final List<InfoItem> added = collectSearchItems(result.getItems());
        if (hasDurationOptions() || downloadedOnly) {
            renderSearchItems();
        } else {
            infoListAdapter.addInfoItemList(added);
        }
        if (infoListAdapter.getItemsList().isEmpty()) {
            showEmptyState();
        } else {
            hideLoading();
        }

        if (!result.getErrors().isEmpty()) {
            // nextPage should be non-null at this point, because it refers to the page
            // whose results are handled here, but let's check it anyway
            if (nextPage == null) {
                showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                        "\"" + searchString + "\" → nextPage == null", serviceId,
                        getOpenInBrowserUrlForErrors()));
            } else {
                showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                        "\"" + searchString + "\" → pageUrl: " + nextPage.getUrl() + ", "
                                + "pageIds: " + nextPage.getIds() + ", "
                                + "pageCookies: " + nextPage.getCookies(),
                        serviceId, getOpenInBrowserUrlForErrors()));
            }
        }

        // keep the reassignment of nextPage after the error handling to ensure that nextPage
        // still holds the correct value during the error handling
        nextPage = result.getNextPage();
        super.handleNextItems(result);
        updateDeepSearchStatus();
    }

    @Override
    public void handleError() {
        super.handleError();
        hideSuggestionsPanel();
        hideKeyboardSearch();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion item touch helper
    //////////////////////////////////////////////////////////////////////////*/

    public int getSuggestionMovementFlags(@NonNull final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return 0;
        }

        final SuggestionItem item = suggestionListAdapter.getCurrentList().get(position);
        return item.fromHistory ? makeMovementFlags(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) : 0;
    }

    public void onSuggestionItemSwiped(@NonNull final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getBindingAdapterPosition();
        final String query = suggestionListAdapter.getCurrentList().get(position).query;
        final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> suggestionPublisher
                                .onNext(getSearchEditString()),
                        throwable -> showSnackBarError(new ErrorInfo(throwable,
                                UserAction.DELETE_FROM_HISTORY, "Deleting item failed")));
        disposables.add(onDelete);
    }
}
