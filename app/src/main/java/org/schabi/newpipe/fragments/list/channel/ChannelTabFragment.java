package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ReadyChannelTabListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.ui.emptystate.EmptyStateUtil;
import org.schabi.newpipe.util.ChannelItemCache;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PlayButtonHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelTabFragment extends BaseListInfoFragment<InfoItem, ChannelTabInfo>
        implements PlaylistControlViewHolder {

    // states must be protected and not private for State being able to access them
    @State
    protected ListLinkHandler tabHandler;
    @State
    protected String channelName;

    private PlaylistControlBinding playlistControlBinding;
    private View streamsHeader = null;

    // Filter/sort constants
    private static final int SORT_DATE = 0;
    private static final int SORT_VIEWS = 1;
    private static final int SORT_NAME = 2;

    private static final int ORDER_DESC = 0;
    private static final int ORDER_ASC = 1;

    // Days per predefined age period entry; -1 = Custom input
    private static final int[] AGE_PERIOD_DAYS =
            {0, 1, 2, 4, 7, 14, 30, 60, 120, 365, 730, 1825, -1};

    // Minimum view counts per preset entry; -1 = Custom input
    private static final long[] VIEWS_PRESET_VALUES =
            {0, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, -1};

    // Parses "3 days", "2 weeks", "8 months", "1y", etc.
    private static final Pattern AGE_PATTERN =
            Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    // Filter/sort state
    private int currentSortField = SORT_DATE;
    private int currentSortOrder = ORDER_DESC;
    private int currentAgePeriodIndex = 0;
    private int currentAgeCustomDays = 0;
    private String currentNameFilter = "";
    private int currentViewsPresetIndex = 0;
    private long currentMinViews = 0;

    // All items accumulated across all pages for this channel tab
    private final List<StreamInfoItem> allLoadedItems = new ArrayList<>();
    private boolean isFullyLoaded = false;
    // Preload and background-full-load use separate disposables to avoid conflicts
    private Disposable preloadLoader = null;
    private Disposable backgroundLoader = null;
    // True while a background page fetch is in progress (distinct from backgroundLoader != null
    // because backgroundLoader is set to null inside the callback before applyFilterAndSort runs)
    private boolean isBackgroundLoading = false;
    // Throttle mid-load redraws to avoid flooding the RecyclerView
    private long lastFilterApplyMs = 0;

    // Filter panel views (cached to avoid repeated findViewById calls)
    private View filterPanel = null;
    private View filterLoadingRow = null;
    private TextView filterLoadingText = null;
    private View filterLoadingSpinner = null;
    // Guard against listener callbacks firing during spinner/EditText setup
    private boolean filterPanelSettingUp = false;
    private boolean filterPanelInitialized = false;

    @NonNull
    public static ChannelTabFragment getInstance(final int serviceId,
                                                 final ListLinkHandler tabHandler,
                                                 final String channelName) {
        final ChannelTabFragment instance = new ChannelTabFragment();
        instance.serviceId = serviceId;
        instance.tabHandler = tabHandler;
        instance.channelName = channelName;
        return instance;
    }

    public ChannelTabFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        EmptyStateUtil.setEmptyStateComposable(rootView.findViewById(R.id.empty_state_view));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Dispose loaders here rather than onDestroy so they do not outlive the view and
        // cannot call back into nulled view references or trigger RecyclerView updates
        if (preloadLoader != null) {
            preloadLoader.dispose();
            preloadLoader = null;
        }
        if (backgroundLoader != null) {
            backgroundLoader.dispose();
            backgroundLoader = null;
        }
        isBackgroundLoading = false;
        playlistControlBinding = null;
        streamsHeader = null;
        filterPanel = null;
        filterLoadingRow = null;
        filterLoadingText = null;
        filterLoadingSpinner = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Loaders are already disposed in onDestroyView; this is a safety net for the
        // rare case where onDestroyView was not called (e.g. fragment never attached a view)
        if (preloadLoader != null) {
            preloadLoader.dispose();
            preloadLoader = null;
        }
        if (backgroundLoader != null) {
            backgroundLoader.dispose();
            backgroundLoader = null;
        }
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            streamsHeader = activity.getLayoutInflater()
                    .inflate(R.layout.channel_streams_header, itemsList, false);
            playlistControlBinding = PlaylistControlBinding.bind(
                    streamsHeader.findViewById(R.id.playlist_control));
            filterPanel = streamsHeader.findViewById(R.id.channel_filter_panel);
            filterLoadingRow = filterPanel.findViewById(R.id.filter_loading_row);
            filterLoadingText = filterPanel.findViewById(R.id.filter_loading_text);
            filterLoadingSpinner = filterPanel.findViewById(R.id.filter_loading_progress);
            streamsHeader.findViewById(R.id.playlist_ctrl_filter_button)
                    .setOnClickListener(v -> toggleFilterPanel());
            return () -> streamsHeader;
        }
        return null;
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Filter panel setup
    //////////////////////////////////////////////////////////////////////////*/

    private void setupSortSpinners(final View panel) {
        final Spinner sortFieldSpinner = panel.findViewById(R.id.filter_sort_field);
        final ArrayAdapter<String> sortFieldAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.filter_sort_date),
                        getString(R.string.filter_sort_views),
                        getString(R.string.filter_sort_name)
                });
        sortFieldAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortFieldSpinner.setAdapter(sortFieldAdapter);
        sortFieldSpinner.setSelection(currentSortField);
        sortFieldSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                if (currentSortField != position) {
                    currentSortField = position;
                    applyFilterAndSort();
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
            }
        });

        final Button sortOrderButton = panel.findViewById(R.id.filter_sort_order);
        sortOrderButton.setText(currentSortOrder == ORDER_ASC ? "↑" : "↓");
        sortOrderButton.setOnClickListener(v -> {
            currentSortOrder = (currentSortOrder == ORDER_ASC) ? ORDER_DESC : ORDER_ASC;
            sortOrderButton.setText(currentSortOrder == ORDER_ASC ? "↑" : "↓");
            applyFilterAndSort();
        });
    }

    private void setupFilterPanel(final View panel) {
        // filterPanelSettingUp suppresses applyFilterAndSort while setSelection/setText
        // calls below fire their listeners synchronously
        filterPanelSettingUp = true;
        setupSortSpinners(panel);

        // Age period spinner
        final Spinner agePeriodSpinner = panel.findViewById(R.id.filter_age_period);
        final EditText ageCustomInput = panel.findViewById(R.id.filter_age_custom);
        final String[] agePeriodLabels = {
                getString(R.string.filter_age_none),
                getString(R.string.filter_age_1_day),
                getString(R.string.filter_age_2_days),
                getString(R.string.filter_age_4_days),
                getString(R.string.filter_age_1_week),
                getString(R.string.filter_age_2_weeks),
                getString(R.string.filter_age_1_month),
                getString(R.string.filter_age_2_months),
                getString(R.string.filter_age_4_months),
                getString(R.string.filter_age_1_year),
                getString(R.string.filter_age_2_years),
                getString(R.string.filter_age_5_years),
                getString(R.string.filter_age_custom)
        };
        assert AGE_PERIOD_DAYS.length == agePeriodLabels.length;
        final ArrayAdapter<String> agePeriodAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, agePeriodLabels);
        agePeriodAdapter.setDropDownViewResource(R.layout.spinner_item_compact);
        agePeriodSpinner.setAdapter(agePeriodAdapter);
        agePeriodSpinner.setSelection(currentAgePeriodIndex);
        agePeriodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                if (currentAgePeriodIndex != position) {
                    currentAgePeriodIndex = position;
                    final boolean isCustom = AGE_PERIOD_DAYS[position] == -1;
                    ageCustomInput.setVisibility(isCustom ? View.VISIBLE : View.GONE);
                    applyFilterAndSort();
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
            }
        });

        ageCustomInput.addTextChangedListener(new TextWatcher() {
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
                currentAgeCustomDays = parseAgeDays(s.toString().trim());
                applyFilterAndSort();
            }
        });

        // Name filter input
        final EditText nameInput = panel.findViewById(R.id.filter_name_input);
        nameInput.setText(currentNameFilter);
        nameInput.addTextChangedListener(new TextWatcher() {
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
                currentNameFilter = s.toString().trim();
                applyFilterAndSort();
            }
        });

        // Views preset spinner + custom input
        final Spinner viewsPresetSpinner = panel.findViewById(R.id.filter_views_preset);
        final EditText viewsCustomInput = panel.findViewById(R.id.filter_views_custom);
        final String[] viewsLabels = {
                getString(R.string.filter_views_none),
                getString(R.string.filter_views_1k),
                getString(R.string.filter_views_10k),
                getString(R.string.filter_views_100k),
                getString(R.string.filter_views_1m),
                getString(R.string.filter_views_10m),
                getString(R.string.filter_views_100m),
                getString(R.string.filter_views_custom)
        };
        assert VIEWS_PRESET_VALUES.length == viewsLabels.length;
        final ArrayAdapter<String> viewsAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, viewsLabels);
        viewsAdapter.setDropDownViewResource(R.layout.spinner_item_compact);
        viewsPresetSpinner.setAdapter(viewsAdapter);
        viewsPresetSpinner.setSelection(currentViewsPresetIndex);
        viewsPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                if (currentViewsPresetIndex != position) {
                    currentViewsPresetIndex = position;
                    final boolean isCustom = VIEWS_PRESET_VALUES[position] == -1;
                    viewsCustomInput.setVisibility(isCustom ? View.VISIBLE : View.GONE);
                    if (!isCustom) {
                        currentMinViews = VIEWS_PRESET_VALUES[position];
                        applyFilterAndSort();
                    }
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
            }
        });

        viewsCustomInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                currentMinViews = parseViewCount(viewsCustomInput.getText().toString().trim());
                applyFilterAndSort();
            }
        });
        viewsCustomInput.setOnEditorActionListener((v, actionId, event) -> {
            currentMinViews = parseViewCount(v.getText().toString().trim());
            applyFilterAndSort();
            v.clearFocus();
            return false;
        });
        filterPanelSettingUp = false;
    }

    private static long parseViewCount(final String text) {
        try {
            return Long.parseLong(text);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    // Parses "3 days", "2 weeks", "8 months", "1y", etc.; returns 0 on blank/unrecognized input.
    private static int parseAgeDays(final String text) {
        if (text.isEmpty()) {
            return 0;
        }
        final Matcher m = AGE_PATTERN.matcher(text);
        if (!m.find()) {
            return 0;
        }
        final int n;
        try {
            n = Integer.parseInt(m.group(1));
        } catch (final NumberFormatException e) {
            return 0;
        }
        final String unit = m.group(2).toLowerCase();
        if (unit.startsWith("d")) {
            return n;
        } else if (unit.startsWith("w")) {
            return n * 7;
        } else if (unit.startsWith("m")) {
            return n * 30;
        } else if (unit.startsWith("y")) {
            return n * 365;
        }
        return 0;
    }

    private void toggleFilterPanel() {
        if (filterPanel == null) {
            return;
        }
        if (!filterPanelInitialized) {
            setupFilterPanel(filterPanel);
            filterPanelInitialized = true;
        }
        final boolean nowVisible = filterPanel.getVisibility() != View.VISIBLE;
        filterPanel.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
        if (nowVisible) {
            startBackgroundLoadIfNeeded();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Background full-load logic
    //////////////////////////////////////////////////////////////////////////*/

    private void startBackgroundLoadIfNeeded() {
        if (isFullyLoaded || backgroundLoader != null) {
            return;
        }

        // Stop preloading — background loading takes over from here
        if (preloadLoader != null) {
            preloadLoader.dispose();
            preloadLoader = null;
        }

        // Restore from cache if available
        final String cacheKey = tabHandler.getUrl();
        final List<StreamInfoItem> cached = ChannelItemCache.getInstance().getItems(cacheKey);
        if (cached != null) {
            allLoadedItems.clear();
            allLoadedItems.addAll(cached);
            isFullyLoaded = true;
            applyFilterAndSort();
            return;
        }

        updateLoadingIndicator(true, allLoadedItems.size());
        loadNextPageInBackground(currentNextPage);
    }

    private void loadNextPageInBackground(@Nullable final Page page) {
        if (!Page.isValid(page)) {
            // No more pages — loading complete
            isFullyLoaded = true;
            isBackgroundLoading = false;
            currentNextPage = null;
            ChannelItemCache.getInstance().putItems(tabHandler.getUrl(), allLoadedItems);
            updateLoadingIndicator(false, allLoadedItems.size());
            applyFilterAndSort();
            return;
        }

        isBackgroundLoading = true;
        backgroundLoader = ExtractorHelper
                .getMoreChannelTabItems(serviceId, tabHandler, page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(infoItemsPage -> {
                    if (!isAdded()) {
                        return;
                    }
                    backgroundLoader = null;
                    allLoadedItems.addAll(filterStreams(infoItemsPage.getItems()));
                    updateLoadingIndicator(true, allLoadedItems.size());
                    // Throttle mid-load redraws to avoid flooding the RecyclerView
                    if (System.currentTimeMillis() - lastFilterApplyMs > 500) {
                        lastFilterApplyMs = System.currentTimeMillis();
                        applyFilterAndSort();
                    }
                    loadNextPageInBackground(infoItemsPage.getNextPage());
                }, throwable -> {
                    if (!isAdded()) {
                        return;
                    }
                    backgroundLoader = null;
                    isBackgroundLoading = false;
                    Log.w(TAG, "Background load error", throwable);
                    updateLoadingIndicator(false, allLoadedItems.size());
                });
    }

    private void updateLoadingIndicator(final boolean loading, final int count) {
        if (filterLoadingRow == null || filterPanel == null) {
            return;
        }
        if (filterPanel.getVisibility() != View.VISIBLE) {
            filterLoadingRow.setVisibility(View.GONE);
            return;
        }
        filterLoadingRow.setVisibility(View.VISIBLE);
        if (loading) {
            if (filterLoadingSpinner != null) {
                filterLoadingSpinner.setVisibility(View.VISIBLE);
            }
            filterLoadingText.setText(getString(R.string.filter_loading_progress, count));
        } else {
            if (filterLoadingSpinner != null) {
                filterLoadingSpinner.setVisibility(View.GONE);
            }
            filterLoadingText.setText(getString(R.string.filter_loading_done, count));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Filter and sort
    //////////////////////////////////////////////////////////////////////////*/

    private void applyFilterAndSort() {
        if (filterPanelSettingUp || allLoadedItems.isEmpty()) {
            return;
        }

        Stream<StreamInfoItem> stream = allLoadedItems.stream();

        // Age filter
        final int ageDays = AGE_PERIOD_DAYS[currentAgePeriodIndex] == -1
                ? currentAgeCustomDays : AGE_PERIOD_DAYS[currentAgePeriodIndex];
        if (ageDays > 0) {
            final long cutoffMs =
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageDays);
            stream = stream.filter(item -> {
                if (item.getUploadDate() == null) {
                    return true; // keep items with unknown date
                }
                final java.time.OffsetDateTime dt = item.getUploadDate().offsetDateTime();
                return dt != null && dt.toInstant().toEpochMilli() >= cutoffMs;
            });
        }

        // Name filter
        if (!currentNameFilter.isEmpty()) {
            final String pattern = currentNameFilter.toLowerCase();
            final boolean prefixWild = pattern.startsWith("*");
            final boolean suffixWild = pattern.endsWith("*");
            String core = pattern;
            if (core.startsWith("*")) {
                core = core.substring(1);
            }
            if (core.endsWith("*")) {
                core = core.substring(0, core.length() - 1);
            }
            final String finalCore = core;
            stream = stream.filter(item -> {
                final String name = item.getName().toLowerCase();
                if (prefixWild && suffixWild) {
                    return name.contains(finalCore);    // infix: *ell*
                } else if (prefixWild) {
                    return name.endsWith(finalCore);    // suffix: *llo
                } else if (suffixWild) {
                    return name.startsWith(finalCore);  // prefix: hel*
                } else {
                    // whole word match
                    return name.equals(pattern)
                            || name.contains(" " + pattern + " ")
                            || name.startsWith(pattern + " ")
                            || name.endsWith(" " + pattern);
                }
            });
        }

        // Views filter
        if (currentMinViews > 0) {
            stream = stream.filter(item -> item.getViewCount() >= currentMinViews);
        }

        final List<StreamInfoItem> result = stream.collect(Collectors.toList());

        final Comparator<StreamInfoItem> comparator;
        switch (currentSortField) {
            case SORT_VIEWS:
                comparator = Comparator.comparingLong(StreamInfoItem::getViewCount);
                break;
            case SORT_NAME:
                comparator = Comparator.comparing(item -> item.getName().toLowerCase());
                break;
            case SORT_DATE:
            default:
                comparator = Comparator.comparingLong(item -> {
                    if (item.getUploadDate() == null
                            || item.getUploadDate().offsetDateTime() == null) {
                        return 0L;
                    }
                    return item.getUploadDate().offsetDateTime()
                            .toInstant().toEpochMilli();
                });
                break;
        }
        final Comparator<StreamInfoItem> ordered =
                currentSortOrder == ORDER_ASC ? comparator : comparator.reversed();
        result.sort(ordered);

        // During background loading a spinner dropdown may be open, so use replaceStreamItems
        // (DiffUtil-based) to avoid recycling the header ViewHolder which would dismiss it.
        // For user-triggered changes the spinner is already closed, so notifyDataSetChanged
        // is safe and avoids the O(N²) cost of DiffUtil on large reordered lists.
        if (isBackgroundLoading) {
            infoListAdapter.replaceStreamItems(result);
        } else {
            infoListAdapter.clearStreamItemList();
            infoListAdapter.addInfoItemList(result);
        }
        // Footer suppressed while filter is active; filter panel's loading row shows progress
        showListFooter(false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ChannelTabInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getChannelTab(serviceId, tabHandler, forceLoad);
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage);
    }

    @Override
    protected void loadMoreItems() {
        if (filterPanelInitialized) {
            // Background loader owns all pagination when filter is active.
            // If loading is still in progress and stalled (e.g. transient error), restart it.
            if (!isFullyLoaded && backgroundLoader == null) {
                loadNextPageInBackground(currentNextPage);
            }
            // Always skip scroll-triggered loading when filter is active —
            // the background loader (or no-op if fully loaded) handles everything.
            return;
        }
        // Suppress scroll-triggered loading while preloading to prevent fetching the same page
        if (preloadLoader != null) {
            return;
        }
        super.loadMoreItems();
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<InfoItem> result) {
        // Only accumulate items if background loading has not already fetched everything —
        // a stale currentWorker completing after isFullyLoaded = true would cause duplicates
        if (!isFullyLoaded) {
            allLoadedItems.addAll(filterStreams(result.getItems()));
        }

        if (filterPanelInitialized) {
            isLoading.set(false);
            currentNextPage = result.getNextPage();
            applyFilterAndSort();
        } else {
            super.handleNextItems(result);
        }
    }

    @Override
    public void setTitle(final String title) {
        // The channel name is displayed as title in the toolbar.
        // The title is always a description of the content of the tab fragment.
        // It should be unique for each channel because multiple channel tabs
        // can be added to the main page. Therefore, the channel name is used.
        // Using the title variable would cause the title to be the same for all channel tabs.
        super.setTitle(channelName);
    }

    @Override
    public void handleResult(@NonNull final ChannelTabInfo result) {
        super.handleResult(result);

        // FIXME this is a really hacky workaround, to avoid storing useless data in the fragment
        //  state. The problem is, `ReadyChannelTabListLinkHandler` might contain raw JSON data that
        //  uses a lot of memory (e.g. ~800KB for YouTube). While 800KB doesn't seem much, if
        //  you combine just a couple of channel tab fragments you easily go over the 1MB
        //  save&restore transaction limit, and get `TransactionTooLargeException`s. A proper
        //  solution would require rethinking about `ReadyChannelTabListLinkHandler`s.
        if (tabHandler instanceof ReadyChannelTabListLinkHandler) {
            try {
                // once `handleResult` is called, the parsed data was already saved to cache, so
                // we can discard any raw data in ReadyChannelTabListLinkHandler and create a
                // link handler with identical properties, but without any raw data
                final ListLinkHandlerFactory channelTabLHFactory = result.getService()
                        .getChannelTabLHFactory();
                if (channelTabLHFactory != null) {
                    // some services do not not have a ChannelTabLHFactory
                    tabHandler = channelTabLHFactory.fromQuery(tabHandler.getId(),
                            tabHandler.getContentFilters(), tabHandler.getSortFilter());
                }
            } catch (final ParsingException e) {
                // silently ignore the error, as the app can continue to function normally
                Log.w(TAG, "Could not recreate channel tab handler", e);
            }
        }

        if (playlistControlBinding != null) {
            // PlaylistControls should be visible only if there is some item in
            // infoListAdapter other than header
            if (infoListAdapter.getItemCount() > 1) {
                playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
            } else {
                playlistControlBinding.getRoot().setVisibility(View.GONE);
            }

            PlayButtonHelper.initPlaylistControlClickListener(
                    activity, playlistControlBinding, this);
        }

        // Seed allLoadedItems from the first page
        if (allLoadedItems.isEmpty()) {
            allLoadedItems.addAll(filterStreams(result.getRelatedItems()));
        }

        // Silently preload a few more pages so filter/sort is faster if the user opens the panel
        if (!isFullyLoaded && preloadLoader == null) {
            preloadPages(result.getNextPage(), 3);
        }
    }

    /**
     * Silently preloads up to {@code maxPages} additional pages in the background
     * without affecting the displayed list.
     *
     * @param startPage the page to start loading from
     * @param maxPages  the maximum number of additional pages to preload
     */
    private void preloadPages(@Nullable final Page startPage, final int maxPages) {
        if (!Page.isValid(startPage) || maxPages <= 0) {
            return;
        }
        preloadLoader = ExtractorHelper
                .getMoreChannelTabItems(serviceId, tabHandler, startPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(infoItemsPage -> {
                    if (!isAdded()) {
                        return;
                    }
                    preloadLoader = null;
                    if (!isFullyLoaded) {
                        allLoadedItems.addAll(filterStreams(infoItemsPage.getItems()));
                        // Keep currentNextPage in sync so background loading starts from
                        // where preloading stopped, not from the beginning
                        currentNextPage = infoItemsPage.getNextPage();
                    }
                    if (Page.isValid(infoItemsPage.getNextPage()) && maxPages > 1
                            && !isFullyLoaded) {
                        preloadPages(infoItemsPage.getNextPage(), maxPages - 1);
                    }
                }, throwable -> {
                    preloadLoader = null;
                    Log.w(TAG, "Preload error", throwable);
                });
    }

    @Override
    public PlayQueue getPlayQueue() {
        return new ChannelTabPlayQueue(currentInfo.getServiceId(), tabHandler,
                currentInfo.getNextPage(), filterStreams(infoListAdapter.getItemsList()), 0);
    }

    private static List<StreamInfoItem> filterStreams(final List<? extends InfoItem> items) {
        final List<StreamInfoItem> streams = new ArrayList<>();
        for (final InfoItem item : items) {
            if (item instanceof StreamInfoItem) {
                streams.add((StreamInfoItem) item);
            }
        }
        return streams;
    }
}
