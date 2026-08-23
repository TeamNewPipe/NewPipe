package org.schabi.newpipe.fragments.list.search;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
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
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic;
import org.schabi.newpipe.fragments.list.search.filter.SearchFilterUI;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.ktx.ExceptionUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class SearchFragment extends BaseListFragment<SearchInfo, ListExtractor.InfoItemsPage<?>>
        implements BackPressable, SearchFilterLogic.Callback, SearchFilterDialog.Callback {
    private static final String YOUTUBE_MUSIC_FILTER_PREFIX = "music_";
    private static final String SEARCH_FILTER_LAST_SERVICE_KEY = "search_filter_last_service";
    private static final String SEARCH_FILTER_LAST_UI_SERVICE_KEY_PREFIX =
            "search_filter_last_ui_service_";
    private static final String SEARCH_FILTER_CONTENT_KEY_PREFIX = "search_filter_content_";
    private static final String SEARCH_FILTER_SORT_KEY_PREFIX = "search_filter_sort_";

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

    protected int serviceId = Constants.NO_SERVICE_ID;

    // these three represents the current search query
    String searchString;

    /**
     * No content filter should add like contentFilter = all
     * be aware of this when implementing an extractor.
     */
    ArrayList<FilterItem> selectedContentFilter = new ArrayList<>();

    ArrayList<FilterItem> selectedSortFilter;

    // these represents the last search
    String lastSearchedString;

    String searchSuggestion;

    boolean isCorrectedSearch;

    MetaInfo[] metaInfo;

    boolean channelSearchMode;

    String channelUrl;

    String channelName;

    ListLinkHandler channelSearchHandler;

    boolean wasSearchFocused = false;

    private StreamingService service;
    private Page nextPage;
    private boolean showLocalSuggestions = true;
    private boolean showRemoteSuggestions = true;

    private Disposable searchDisposable;
    private Disposable suggestionDisposable;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private SuggestionListAdapter suggestionListAdapter;
    private HistoryRecordManager historyRecordManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private FragmentSearchBinding searchBinding;

    private View searchToolbarContainer;
    private EditText searchEditText;
    private View searchClear;
    private View searchFilter;
    private View searchSubmit;

    private SearchFilterUI searchFilterUi;
    private boolean isTv;
    private boolean useOldSearchFilter;

    private boolean suggestionsPanelVisible = false;

    /*////////////////////////////////////////////////////////////////////////*/

    private TextWatcher textWatcher;

    public ArrayList<Integer> userSelectedContentFilterList;

    ArrayList<Integer> userSelectedSortFilterList = null;

    public static SearchFragment getInstance(final int serviceId, final String searchString) {
        final SearchFragment searchFragment = new SearchFragment();

        List<FilterItem> defaultContentFilter = new ArrayList<>();
        List<FilterItem> defaultSortFilter = new ArrayList<>();

        try {
            StreamingService service = NewPipe.getService(serviceId);
            searchFragment.service = service;
            defaultContentFilter.add(service.getSearchQHFactory().getFilterItem(0)); // 默认 "all"
        } catch (Exception e) {
            Log.e("Search", "Failed to initialize default filters", e);
        }

        searchFragment.setQuery(serviceId, searchString, defaultContentFilter, defaultSortFilter);
        searchFragment.restorePersistedSearchFilters(PreferenceManager
                .getDefaultSharedPreferences(App.getApp()));
        searchFragment.restoreSelectedFilters();

        if (!TextUtils.isEmpty(searchString)) {
            searchFragment.setSearchOnResume();
        }

        return searchFragment;
    }

    public static int getPersistedSearchServiceId(final Context context,
                                                  final int fallbackServiceId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int serviceId = prefs.getInt(SEARCH_FILTER_LAST_SERVICE_KEY, fallbackServiceId);
        try {
            NewPipe.getService(serviceId);
            return serviceId;
        } catch (final Exception ignored) {
            return fallbackServiceId;
        }
    }

    public static void setPersistedSearchServiceId(final Context context, final int serviceId) {
        try {
            NewPipe.getService(serviceId);
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt(SEARCH_FILTER_LAST_SERVICE_KEY, serviceId)
                    .apply();
        } catch (final Exception ignored) {
        }
    }

    public static int getPersistedSearchContentFilterId(final Context context,
                                                        final int filterServiceId,
                                                        final int fallbackFilterId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(getSearchFilterContentKey(filterServiceId), fallbackFilterId);
    }

    public static ArrayList<Integer> getPersistedSearchSortFilterIds(final Context context,
                                                                     final int filterServiceId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return deserializeFilterIds(prefs.getString(getSearchFilterSortKey(filterServiceId), ""));
    }

    public static SearchFragment getYouTubeMusicInstance(final String searchString) {
        final SearchFragment searchFragment = getInstance(ServiceList.YouTube.getServiceId(), searchString);
        final FilterItem musicFilter = findYouTubeMusicDefaultFilter();
        if (musicFilter != null) {
            searchFragment.userSelectedContentFilterList = new ArrayList<>();
            searchFragment.userSelectedContentFilterList.add(musicFilter.getIdentifier());
            searchFragment.userSelectedSortFilterList = new ArrayList<>();
            searchFragment.restoreSelectedFilters();
        }
        return searchFragment;
    }

    public static SearchFragment getYouTubeShortsInstance() {
        return getInstance(ServiceList.YouTube.getServiceId(), "#shorts");
    }

    @Nullable
    private static FilterItem findYouTubeMusicDefaultFilter() {
        try {
            final StreamingService service = NewPipe.getService(ServiceList.YouTube.getServiceId());
            final String[] preferredFilterNames = {
                    "music_songs", "music_videos", "music_albums", "music_playlists",
                    "music_artists"
            };
            for (final String preferredFilterName : preferredFilterNames) {
                final FilterItem filterItem = findFilterByName(service, preferredFilterName);
                if (filterItem != null) {
                    return filterItem;
                }
            }
        } catch (final Exception e) {
            Log.e("Search", "Failed to initialize YouTube Music filters", e);
        }
        return null;
    }

    @Nullable
    private static FilterItem findFilterByName(final StreamingService service, final String name) {
        for (int i = 0; i < 200; i++) {
            try {
                final FilterItem filterItem = service.getSearchQHFactory().getFilterItem(i);
                if (filterItem != null && name.equals(filterItem.getName())) {
                    return filterItem;
                }
            } catch (final Exception ignored) {
                // Some extractor versions throw for unknown filter identifiers.
            }
        }
        return null;
    }

    public static SearchFragment getChannelInstance(final int serviceId,
                                                    final String channelUrl,
                                                    final String channelName) {
        final SearchFragment searchFragment = getInstance(serviceId, "");
        searchFragment.channelSearchMode = true;
        searchFragment.channelUrl = channelUrl;
        searchFragment.channelName = channelName;
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

        suggestionListAdapter = new SuggestionListAdapter(activity);
        historyRecordManager = new HistoryRecordManager(context);

        isTv = DeviceUtils.isTv(context);

        useOldSearchFilter = isTv || prefs.getBoolean(
                context.getString(R.string.use_old_search_filter_key), false);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        if (useOldSearchFilter && !channelSearchMode) {
            searchFilterUi = new SearchFilterUI(this, getContext());
        }
        updateService();
        restorePersistedSearchFilters();
        if (useOldSearchFilter && !channelSearchMode) {
            searchFilterUi.restorePreviouslySelectedFilters(
                    userSelectedContentFilterList,
                    userSelectedSortFilterList);
        }
        restoreSelectedFilters();
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        searchBinding = FragmentSearchBinding.bind(rootView);
        super.onViewCreated(rootView, savedInstanceState);
        showSearchOnStart();
        initSearchListeners();

        if (!TextUtils.isEmpty(searchString) && infoListAdapter.getItemsList().isEmpty()) {
            searchEditText.setText(searchString);
            search(searchString);
        }
    }

    private void updateService() {
        try {
            service = NewPipe.getService(serviceId);
            if (useOldSearchFilter && searchFilterUi != null) {
                searchFilterUi.updateService(service);
            }
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Getting service for id " + serviceId, e);
        }
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

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) {
            initSuggestionObserver();
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
        unsetSearchListeners();

        updateSearchActionLayout(searchSubmit, 48, 0);
        updateSearchActionLayout(searchFilter, 48, 48);
        updateSearchActionLayout(searchClear, 48, 0);
        searchClear.setVisibility(View.VISIBLE);
        searchFilter.setVisibility(View.GONE);
        searchSubmit.setVisibility(View.GONE);
        updateSearchEditTextMargin();

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
                search();
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
        searchSubmit = searchToolbarContainer.findViewById(R.id.toolbar_search_submit);

        updateSearchActionLayout(searchSubmit, 40, 0);
        updateSearchActionLayout(searchFilter, 40, 40);
        updateSearchActionLayout(searchClear, 40,
                useOldSearchFilter || channelSearchMode ? 40 : 80);
        searchClear.setVisibility(View.GONE);
        searchFilter.setVisibility(useOldSearchFilter || channelSearchMode
                ? View.GONE : View.VISIBLE);
        searchSubmit.setVisibility(View.VISIBLE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(nextPage);
        objectsToSave.add(metaInfo);
        objectsToSave.add(channelSearchHandler);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        nextPage = (Page) savedObjects.poll();
        metaInfo = (MetaInfo[]) savedObjects.poll();
        channelSearchHandler = (ListLinkHandler) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        try{
            searchString = searchEditText != null
                    ? searchEditText.getText().toString()
                    : searchString;

            userSelectedContentFilterList = new ArrayList<>();
            if (!selectedContentFilter.isEmpty()) {
                userSelectedContentFilterList.add(selectedContentFilter.get(0).getIdentifier());
            }
            userSelectedSortFilterList = new ArrayList<>();
            if (selectedSortFilter != null) {
                for (final FilterItem filterItem : selectedSortFilter) {
                    userSelectedSortFilterList.add(filterItem.getIdentifier());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onSaveInstanceState(bundle);
        bundle.putInt("serviceId", serviceId);
        bundle.putString("searchString", searchString);
        bundle.putString("lastSearchedString", lastSearchedString);
        bundle.putString("searchSuggestion", searchSuggestion);
        bundle.putBoolean("isCorrectedSearch", isCorrectedSearch);
        bundle.putBoolean("channelSearchMode", channelSearchMode);
        bundle.putString("channelUrl", channelUrl);
        bundle.putString("channelName", channelName);
        bundle.putBoolean("wasSearchFocused", wasSearchFocused);
        bundle.putIntegerArrayList("userSelectedContentFilterList", userSelectedContentFilterList);
        bundle.putIntegerArrayList("userSelectedSortFilterList", userSelectedSortFilterList);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceId = savedInstanceState.getInt("serviceId", Constants.NO_SERVICE_ID);
        searchString = savedInstanceState.getString("searchString");
        lastSearchedString = savedInstanceState.getString("lastSearchedString");
        searchSuggestion = savedInstanceState.getString("searchSuggestion");
        isCorrectedSearch = savedInstanceState.getBoolean("isCorrectedSearch", false);
        channelSearchMode = savedInstanceState.getBoolean("channelSearchMode", false);
        channelUrl = savedInstanceState.getString("channelUrl");
        channelName = savedInstanceState.getString("channelName");
        wasSearchFocused = savedInstanceState.getBoolean("wasSearchFocused", false);
        userSelectedContentFilterList = savedInstanceState.getIntegerArrayList("userSelectedContentFilterList");
        userSelectedSortFilterList = savedInstanceState.getIntegerArrayList("userSelectedSortFilterList");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void reloadContent() {

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (service == null) {
            Log.w(TAG, "onCreateOptionsMenu() called with null service");
            updateService();
        }

        if (useOldSearchFilter && !channelSearchMode && searchFilterUi != null
                && service != null) {
            searchFilterUi.createSearchUI(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (useOldSearchFilter && !channelSearchMode && searchFilterUi != null) {
            return searchFilterUi.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private void showSearchOnStart() {
        if (DEBUG) {
            Log.d(TAG, "showSearchOnStart() called, searchQuery → "
                    + searchString
                    + ", lastSearchedQuery → "
                    + lastSearchedString);
        }
        searchEditText.setText(searchString);
        searchEditText.setHint(channelSearchMode
                ? getString(R.string.search_inside_channel, channelName)
                : getString(R.string.search));

        if (TextUtils.isEmpty(searchString) || TextUtils.isEmpty(searchEditText.getText())) {
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

        updateToolbarActionViews();
    }

    private void updateToolbarActionViews() {
        final boolean hasQuery = !TextUtils.isEmpty(searchEditText.getText());
        searchClear.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
        if (channelSearchMode) {
            searchFilter.setVisibility(View.GONE);
        }
        updateSearchEditTextMargin();
    }

    private void updateSearchEditTextMargin() {
        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) searchEditText.getLayoutParams();
        final int rightMargin = Math.max(getSearchActionEnd(searchSubmit),
                Math.max(getSearchActionEnd(searchFilter), getSearchActionEnd(searchClear)));

        if (layoutParams.rightMargin != rightMargin) {
            layoutParams.rightMargin = rightMargin;
            searchEditText.setLayoutParams(layoutParams);
        }
    }

    private int getSearchActionEnd(final View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return 0;
        }

        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        return layoutParams.rightMargin + layoutParams.width;
    }

    private void updateSearchActionLayout(final View view,
                                          final int widthDp,
                                          final int marginDp) {
        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int width = DeviceUtils.dpToPx(widthDp, activity);
        final int rightMargin = DeviceUtils.dpToPx(marginDp, activity);

        if (layoutParams.width != width || layoutParams.rightMargin != rightMargin) {
            layoutParams.width = width;
            layoutParams.rightMargin = rightMargin;
            view.setLayoutParams(layoutParams);
        }
    }

    private void initSearchListeners() {
        if (DEBUG) {
            Log.d(TAG, "initSearchListeners() called");
        }
        searchClear.setOnClickListener(v -> {
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }
            searchBinding.correctSuggestion.setVisibility(View.GONE);

            searchEditText.setText("");
            searchString = "";
            suggestionListAdapter.setItems(new ArrayList<>());
            showKeyboardSearch();
        });

        TooltipCompat.setTooltipText(searchClear, getString(R.string.clear));
        if (!channelSearchMode) {
            TooltipCompat.setTooltipText(searchFilter, getString(R.string.filter));
        }
        TooltipCompat.setTooltipText(searchSubmit, getString(R.string.search));

        if (!channelSearchMode) {
            searchFilter.setOnClickListener(v -> showFilterDialog());
        }
        searchSubmit.setOnClickListener(v -> search());

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

        searchEditText.setOnFocusChangeListener((View v, boolean hasFocus) -> {
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
                search(item.query);
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
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start,
                                      final int before, final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                // Remove rich text formatting
                for (final CharacterStyle span : s.getSpans(0, s.length(), CharacterStyle.class)) {
                    s.removeSpan(span);
                }

                updateToolbarActionViews();

                final String newText = searchEditText.getText().toString();
                suggestionPublisher.onNext(newText);
            }
        };
        searchEditText.addTextChangedListener(textWatcher);
        searchEditText.setOnEditorActionListener(
                (TextView v, int actionId, KeyEvent event) -> {
                    if (DEBUG) {
                        Log.d(TAG, "onEditorAction() called with: v = [" + v + "], "
                                + "actionId = [" + actionId + "], event = [" + event + "]");
                    }
                    if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                        hideKeyboardSearch();
                    } else if (event != null
                            && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                        search(searchEditText.getText().toString());
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
        searchClear.setOnClickListener(null);
        searchClear.setOnLongClickListener(null);
        searchFilter.setOnClickListener(null);
        searchSubmit.setOnClickListener(null);
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
        new AlertDialog.Builder(activity)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    howManyDeleted -> suggestionPublisher
                                            .onNext(searchEditText.getText().toString()),
                                    throwable -> showSnackBarError(new ErrorInfo(throwable,
                                            UserAction.DELETE_FROM_HISTORY,
                                            "Deleting item failed")));
                    disposables.add(onDelete);
                })
                .show();
    }

    @Override
    public boolean onBackPressed() {
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
        final int suggestionsCount = NewPipeSettings.getSearchSuggestionsCount(
                requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()));
        return historyRecordManager
                .getRelatedSearches(query, similarQueryLimit, suggestionsCount)
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
                    final boolean shallShowRemoteSuggestionsNow = !channelSearchMode
                            && showRemoteSuggestions
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

    @Override
    protected void doInitialLoadLogic() {
        // no-op
    }

    public void search() {
        search(searchEditText.getText().toString());
    }

    private void search(final String theSearchString) {
        if (DEBUG) {
            Log.d(TAG, "search() called with: query = [" + theSearchString + "]");
        }
        if (theSearchString.isEmpty()) {
            return;
        }

        if (!channelSearchMode) {
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
        }

        lastSearchedString = this.searchString;
        this.searchString = theSearchString;
        infoListAdapter.clearStreamItemList();
        hideSuggestionsPanel();
        showMetaInfoInTextView(null, searchBinding.searchMetaInfoTextView,
                searchBinding.searchMetaInfoSeparator, disposables);
        hideKeyboardSearch();
        disposables.add(historyRecordManager.onSearched(serviceId, theSearchString)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> { },
                        throwable -> showSnackBarError(new ErrorInfo(throwable, UserAction.SEARCHED,
                                theSearchString, serviceId))
                ));
        suggestionPublisher.onNext(theSearchString);
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        try{
            super.startLoading(forceLoad);
            disposables.clear();
            if (searchDisposable != null) {
                searchDisposable.dispose();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        if (channelSearchMode) {
            try {
                channelSearchHandler = getChannelSearchHandler();
            } catch (final Exception e) {
                onItemError(e);
                return;
            }
            searchDisposable = ExtractorHelper.getChannelTab(serviceId, channelSearchHandler, true)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnEvent((channelTabInfo, throwable) -> isLoading.set(false))
                    .subscribe(this::handleChannelSearchResult, this::onItemError);
            return;
        }
        searchDisposable = ExtractorHelper.searchFor(serviceId,
                searchString,
                selectedContentFilter,
                selectedSortFilter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((searchResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleResult, this::onItemError);
    }

    @Override
    protected void loadMoreItems() {
        if (!Page.isValid(nextPage)) {
            return;
        }
        isLoading.set(true);
        showListFooter(true);
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        if (channelSearchMode) {
            searchDisposable = ExtractorHelper.getMoreChannelTabItems(
                    serviceId,
                    channelSearchHandler,
                    nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnEvent((nextItemsResult, throwable) -> isLoading.set(false))
                    .subscribe(this::handleNextItems, this::onItemError);
            return;
        }
        searchDisposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                searchString,
                selectedContentFilter,
                selectedSortFilter,
                nextPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((nextItemsResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleNextItems, this::onItemError);
    }

    @Override
    protected boolean hasMoreItems() {
        return Page.isValid(nextPage);
    }

    @Override
    protected void onItemSelected(final InfoItem selectedItem) {
        super.onItemSelected(selectedItem);
        hideKeyboardSearch();
    }

    private void onItemError(final Throwable exception) {
        if (exception instanceof SearchExtractor.NothingFoundException) {
            infoListAdapter.clearStreamItemList();
            showEmptyState();
        } else {
            showError(new ErrorInfo(exception, UserAction.SEARCHED, searchString, serviceId));
        }
    }

    private ListLinkHandler getChannelSearchHandler() throws Exception {
        final StreamingService streamingService = NewPipe.getService(serviceId);
        final String channelId = streamingService.getChannelLHFactory()
                .fromUrl(channelUrl)
                .getId();
        final List<FilterItem> contentFilters = Collections.singletonList(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.SEARCH));
        final ListLinkHandler searchHandler = streamingService.getChannelTabLHFactory()
                .fromQuery(channelId, contentFilters, null);
        final String searchUrl = searchHandler.getUrl()
                + "?query=" + URLEncoder.encode(searchString, "UTF-8").replace("+", "%20");
        return new ListLinkHandler(searchUrl, searchUrl, searchHandler.getId(),
                searchHandler.getContentFilters(), searchHandler.getSortFilter());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void selectedFilters(final List<FilterItem> theSelectedContentFilter,
                                final List<FilterItem> theSelectedSortFilter,
                                final boolean isInit) {

        selectedContentFilter = (ArrayList<FilterItem>) theSelectedContentFilter;
        selectedSortFilter = (ArrayList<FilterItem>) theSelectedSortFilter;
        if (!isInit) {
            savePersistedSearchFilters(getCurrentSearchFilterUiServiceId(),
                    getFilterIds(selectedContentFilter), getFilterIds(selectedSortFilter));
        }
    }

    @Override
    public void onSearchFilterSelected(final int selectedServiceId,
                                       final int contentFilterId,
                                       final ArrayList<Integer> sortFilterIds) {
        final String currentQuery = searchEditText == null
                ? searchString
                : searchEditText.getText().toString();
        final int resolvedServiceId = selectedServiceId == SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID
                ? ServiceList.YouTube.getServiceId()
                : selectedServiceId;
        final boolean serviceChanged = serviceId != resolvedServiceId;
        serviceId = resolvedServiceId;
        updateService();

        userSelectedContentFilterList = new ArrayList<>();
        userSelectedContentFilterList.add(contentFilterId);
        userSelectedSortFilterList = sortFilterIds;
        savePersistedSearchFilters(selectedServiceId,
                userSelectedContentFilterList, userSelectedSortFilterList);
        restoreSelectedFilters();

        if (serviceChanged || !TextUtils.isEmpty(currentQuery)) {
            search(currentQuery);
        }
    }

    private void setQuery(final int theServiceId,
                          final String theSearchString,
                          final List<FilterItem> theContentFilter,
                          final List<FilterItem> theSortFilter) {
        serviceId = theServiceId;
        searchString = theSearchString;
        // TODO evermind-zz casting better assert before
        selectedContentFilter = (ArrayList<FilterItem>) theContentFilter;
        selectedSortFilter = (ArrayList<FilterItem>) theSortFilter;
    }

    private void restoreSelectedFilters() {
        if (service == null) {
            return;
        }

        if (userSelectedContentFilterList == null || userSelectedContentFilterList.isEmpty()) {
            userSelectedContentFilterList = new ArrayList<>();
            final FilterItem defaultFilter = service.getSearchQHFactory().getFilterItem(0);
            if (defaultFilter != null) {
                userSelectedContentFilterList.add(defaultFilter.getIdentifier());
            }
        }

        if (userSelectedSortFilterList == null) {
            userSelectedSortFilterList = new ArrayList<>();
        }

        final ArrayList<FilterItem> restoredContentFilters = new ArrayList<>();
        for (final Integer filterId : userSelectedContentFilterList) {
            final FilterItem filterItem = service.getSearchQHFactory().getFilterItem(filterId);
            if (filterItem != null) {
                restoredContentFilters.add(filterItem);
            }
        }

        if (restoredContentFilters.isEmpty()) {
            final FilterItem defaultFilter = service.getSearchQHFactory().getFilterItem(0);
            if (defaultFilter != null) {
                restoredContentFilters.add(defaultFilter);
                userSelectedContentFilterList.clear();
                userSelectedContentFilterList.add(defaultFilter.getIdentifier());
            }
        }

        final ArrayList<FilterItem> restoredSortFilters = new ArrayList<>();
        for (final Integer filterId : userSelectedSortFilterList) {
            final FilterItem filterItem = service.getSearchQHFactory().getFilterItem(filterId);
            if (filterItem != null) {
                restoredSortFilters.add(filterItem);
            }
        }

        selectedContentFilter = restoredContentFilters;
        selectedSortFilter = restoredSortFilters;
    }

    private void restorePersistedSearchFilters() {
        if (channelSearchMode || activity == null) {
            return;
        }

        restorePersistedSearchFilters(PreferenceManager.getDefaultSharedPreferences(activity));
    }

    private void restorePersistedSearchFilters(final SharedPreferences prefs) {
        if (userSelectedContentFilterList != null || userSelectedSortFilterList != null) {
            return;
        }

        final int filterServiceId = getPersistedSearchFilterUiServiceId(prefs);
        final String contentKey = getSearchFilterContentKey(filterServiceId);
        if (!prefs.contains(contentKey)) {
            return;
        }

        userSelectedContentFilterList = new ArrayList<>();
        userSelectedContentFilterList.add(prefs.getInt(contentKey, 0));
        userSelectedSortFilterList = deserializeFilterIds(
                prefs.getString(getSearchFilterSortKey(filterServiceId), ""));
    }

    private int getPersistedSearchFilterUiServiceId(final SharedPreferences prefs) {
        int filterServiceId = prefs.getInt(
                SEARCH_FILTER_LAST_UI_SERVICE_KEY_PREFIX + serviceId, serviceId);
        if (serviceId != ServiceList.YouTube.getServiceId()
                && filterServiceId == SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID) {
            filterServiceId = serviceId;
        }
        return filterServiceId;
    }

    private void savePersistedSearchFilters(final int filterServiceId,
                                            final ArrayList<Integer> contentFilterIds,
                                            final ArrayList<Integer> sortFilterIds) {
        if (channelSearchMode || activity == null || contentFilterIds == null
                || contentFilterIds.isEmpty()) {
            return;
        }

        final int resolvedServiceId = filterServiceId == SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID
                ? ServiceList.YouTube.getServiceId()
                : filterServiceId;
        final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(
                activity).edit();
        editor.putInt(SEARCH_FILTER_LAST_SERVICE_KEY, resolvedServiceId);
        editor.putInt(SEARCH_FILTER_LAST_UI_SERVICE_KEY_PREFIX + resolvedServiceId,
                filterServiceId);
        editor.putInt(getSearchFilterContentKey(filterServiceId), contentFilterIds.get(0));
        editor.putString(getSearchFilterSortKey(filterServiceId), serializeFilterIds(sortFilterIds));
        editor.apply();
    }

    private int getCurrentSearchFilterUiServiceId() {
        return shouldUseYoutubeMusicUiService()
                ? SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID
                : serviceId;
    }

    private static String getSearchFilterContentKey(final int filterServiceId) {
        return SEARCH_FILTER_CONTENT_KEY_PREFIX + filterServiceId;
    }

    private static String getSearchFilterSortKey(final int filterServiceId) {
        return SEARCH_FILTER_SORT_KEY_PREFIX + filterServiceId;
    }

    private ArrayList<Integer> getFilterIds(final List<FilterItem> filterItems) {
        final ArrayList<Integer> filterIds = new ArrayList<>();
        if (filterItems != null) {
            for (final FilterItem filterItem : filterItems) {
                filterIds.add(filterItem.getIdentifier());
            }
        }
        return filterIds;
    }

    private String serializeFilterIds(final List<Integer> filterIds) {
        if (filterIds == null || filterIds.isEmpty()) {
            return "";
        }
        return filterIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static ArrayList<Integer> deserializeFilterIds(final String serializedFilterIds) {
        final ArrayList<Integer> filterIds = new ArrayList<>();
        if (TextUtils.isEmpty(serializedFilterIds)) {
            return filterIds;
        }

        for (final String filterId : serializedFilterIds.split(",")) {
            try {
                filterIds.add(Integer.parseInt(filterId));
            } catch (final NumberFormatException ignored) {
            }
        }
        return filterIds;
    }

    private void showFilterDialog() {
        if (useOldSearchFilter) {
            return;
        }

        if (getChildFragmentManager().findFragmentByTag(SearchFilterDialog.TAG) != null) {
            return;
        }

        final String currentQuery = searchEditText == null
                ? (searchString == null ? "" : searchString)
                : searchEditText.getText().toString();

        final ArrayList<Integer> serviceIds = new ArrayList<>();
        final List<StreamingService> sortedServices = new ArrayList<>(NewPipe.getServices());
        sortedServices.sort(Comparator
                .comparingInt((StreamingService streamingService) -> {
                    if (streamingService.getServiceId() == ServiceList.YouTube.getServiceId()) {
                        return 0;
                    }
                    return ServiceHelper.isBeta(streamingService) ? 3 : 2;
                })
                .thenComparing(streamingService -> streamingService.getServiceInfo().getName()));
        for (final StreamingService streamingService : sortedServices) {
            serviceIds.add(streamingService.getServiceId());
            if (streamingService.getServiceId() == ServiceList.YouTube.getServiceId()) {
                serviceIds.add(SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID);
            }
        }

        final int dialogServiceId = shouldUseYoutubeMusicUiService()
                ? SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID
                : serviceId;

        final int selectedContentFilterId = selectedContentFilter.isEmpty()
                ? -1
                : selectedContentFilter.get(0).getIdentifier();
        final ArrayList<Integer> selectedSortFilterIds = new ArrayList<>();
        if (selectedSortFilter != null) {
            for (final FilterItem filterItem : selectedSortFilter) {
                selectedSortFilterIds.add(filterItem.getIdentifier());
            }
        }

        SearchFilterDialog.newInstance(
                serviceIds,
                dialogServiceId,
                selectedContentFilterId,
                selectedSortFilterIds,
                currentQuery
        ).show(getChildFragmentManager(), SearchFilterDialog.TAG);
    }

    private boolean shouldUseYoutubeMusicUiService() {
        if (serviceId != ServiceList.YouTube.getServiceId() || selectedContentFilter.isEmpty()) {
            return false;
        }

        final String filterName = selectedContentFilter.get(0).getName();
        return filterName != null && filterName.startsWith(YOUTUBE_MUSIC_FILTER_PREFIX);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    //////////////////////////////////////////////////////////////////////////*/

    public void handleSuggestions(@NonNull final List<SuggestionItem> suggestions) {
        if (DEBUG) {
            Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]");
        }
        try {
            searchBinding.suggestionsList.smoothScrollToPosition(0);
            searchBinding.suggestionsList.post(() -> suggestionListAdapter.setItems(suggestions));

            if (suggestionsPanelVisible && isErrorPanelVisible()) {
                hideLoading();
            }
        } catch (Exception ignore) {
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

    private void handleChannelSearchResult(@NonNull final ChannelTabInfo result) {
        if (!result.getErrors().isEmpty()) {
            showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                    searchString, serviceId));
        }

        nextPage = result.getNextPage();
        hideLoading();

        if (infoListAdapter.getItemsList().isEmpty()) {
            if (!result.getRelatedItems().isEmpty()) {
                infoListAdapter.addInfoItemList(result.getRelatedItems());
                showListFooter(hasMoreItems());
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
            }
        }
    }

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        final List<Throwable> exceptions = result.getErrors();
        if (!exceptions.isEmpty()
                && !(exceptions.size() == 1
                && exceptions.get(0) instanceof SearchExtractor.NothingFoundException)) {
            showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                    searchString, serviceId));
        }

        searchSuggestion = result.getSearchSuggestion();
        isCorrectedSearch = result.isCorrectedSearch();

        // List<MetaInfo> cannot be bundled without creating some containers
        metaInfo = result.getMetaInfo().toArray(new MetaInfo[0]);
        showMetaInfoInTextView(result.getMetaInfo(), searchBinding.searchMetaInfoTextView,
                searchBinding.searchMetaInfoSeparator, disposables);

        handleSearchSuggestion();

        lastSearchedString = searchString;
        nextPage = result.getNextPage();

        if (infoListAdapter.getItemsList().isEmpty()) {
            if (!result.getRelatedItems().isEmpty()) {
                infoListAdapter.addInfoItemList(result.getRelatedItems());
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
                return;
            }
        }

        super.handleResult(result);
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
                search(searchSuggestion);
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
        showListFooter(false);
        infoListAdapter.addInfoItemList(result.getItems());
        nextPage = result.getNextPage();

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                    "\"" + searchString + "\" → pageUrl: " + nextPage.getUrl() + ", "
                            + "pageIds: " + nextPage.getIds() + ", "
                            + "pageCookies: " + nextPage.getCookies(),
                    serviceId));
        }
        super.handleNextItems(result);
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

        final SuggestionItem item = suggestionListAdapter.getItem(position);
        return item.fromHistory ? makeMovementFlags(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) : 0;
    }

    public void onSuggestionItemSwiped(@NonNull final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getBindingAdapterPosition();
        final String query = suggestionListAdapter.getItem(position).query;
        final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> suggestionPublisher
                                .onNext(searchEditText.getText().toString()),
                        throwable -> showSnackBarError(new ErrorInfo(throwable,
                                UserAction.DELETE_FROM_HISTORY, "Deleting item failed")));
        disposables.add(onDelete);
    }
}
