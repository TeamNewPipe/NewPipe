package org.schabi.newpipe.fragments.list;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.StreamItemCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Drives the filter/sort panel ({@code R.layout.stream_filter_panel}) for a paginated list of
 * {@link StreamInfoItem}s shown by a {@link BaseListInfoFragment}.
 * <p>
 * The controller silently preloads a few pages after the first one so the panel is responsive
 * when opened, and once the panel is opened it takes over pagination entirely: all remaining
 * pages are fetched in the background, the full list is filtered and sorted in memory, and the
 * result is pushed to the host's {@link InfoListAdapter}. Fully loaded lists are cached in
 * {@link StreamItemCache} so reopening the same list does not refetch it.
 * </p>
 * <p>
 * The host fragment forwards {@link #onFirstPageLoaded}, {@link #onLoadMoreItems} and
 * {@link #onNextItems} from the corresponding {@code BaseListInfoFragment} callbacks.
 * </p>
 */
public final class StreamListFilterController {
    private static final String TAG = StreamListFilterController.class.getSimpleName();

    /**
     * What the controller needs from the fragment that owns the list.
     */
    public interface Host {
        @NonNull
        InfoListAdapter getAdapter();

        /**
         * @param page the page to fetch
         * @return the page contents, as returned by {@code loadMoreItemsLogic()}
         */
        @NonNull
        Single<? extends ListExtractor.InfoItemsPage<? extends InfoItem>> loadPage(
                @NonNull Page page);

        @Nullable
        Page getNextPage();

        void setNextPage(@Nullable Page page);

        /**
         * Dispose any in-flight scroll-triggered page load, so the controller's background
         * loader does not race with it.
         */
        void cancelPendingLoad();

        void showListFooter(boolean show);

        /**
         * @return key for {@link StreamItemCache}, typically the list URL
         */
        @NonNull
        String getCacheKey();

        /**
         * Called after pages fetched by the controller (preload or background load) were added
         * to {@link #getLoadedItems()}, but did not pass through the host's
         * {@code handleNextItems}. Hosts that derive values from all items (e.g. total
         * duration) can recompute here.
         */
        default void onItemsLoadedInBackground() {
        }
    }

    // Sort constants
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
    private static final Pattern AGE_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private static final int PRELOAD_PAGES = 3;

    private final Fragment fragment;
    private final Host host;

    // Filter/sort state
    private int currentSortField = SORT_DATE;
    private int currentSortOrder = ORDER_DESC;
    private int currentAgePeriodIndex = 0;
    private int currentAgeCustomDays = 0;
    private String currentNameFilter = "";
    private int currentViewsPresetIndex = 0;
    private long currentMinViews = 0;

    // All items accumulated across all pages, in the order the service returned them
    private final List<StreamInfoItem> allLoadedItems = new ArrayList<>();
    // Next page the controller itself would fetch; the host's own next page is only advanced
    // once the preloaded items have actually been shown (see syncHostNextPage)
    @Nullable
    private Page nextPage = null;
    private boolean isFullyLoaded = false;
    // Preload and background-full-load use separate disposables to avoid conflicts
    @Nullable
    private Disposable preloadLoader = null;
    @Nullable
    private Disposable backgroundLoader = null;
    // True while a background page fetch is in progress (distinct from backgroundLoader != null
    // because backgroundLoader is set to null inside the callback before applyFilterAndSort runs)
    private boolean isBackgroundLoading = false;
    // Throttle mid-load redraws to avoid flooding the RecyclerView
    private long lastFilterApplyMs = 0;

    // Filter panel views (cached to avoid repeated findViewById calls)
    @Nullable
    private View filterPanel = null;
    @Nullable
    private View filterLoadingRow = null;
    @Nullable
    private TextView filterLoadingText = null;
    @Nullable
    private View filterLoadingSpinner = null;
    // Guard against listener callbacks firing during spinner/EditText setup
    private boolean filterPanelSettingUp = false;
    private boolean filterPanelInitialized = false;

    public StreamListFilterController(@NonNull final Fragment fragment,
                                      @NonNull final Host host) {
        this.fragment = fragment;
        this.host = host;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // View lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Attach the controller to a freshly inflated header.
     *
     * @param panel     the included {@code R.layout.stream_filter_panel}
     * @param button    the "Filter" toggle in the playlist control bar
     * @param separator the separator preceding {@code button}
     */
    public void bindViews(@NonNull final View panel, @NonNull final View button,
                          @NonNull final View separator) {
        filterPanel = panel;
        filterLoadingRow = panel.findViewById(R.id.filter_loading_row);
        filterLoadingText = panel.findViewById(R.id.filter_loading_text);
        filterLoadingSpinner = panel.findViewById(R.id.filter_loading_progress);
        // the button is hidden in the shared playlist_control layout and only enabled by
        // hosts that actually provide a filter panel
        separator.setVisibility(View.VISIBLE);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> toggleFilterPanel());
        if (filterPanelInitialized) {
            // The view was recreated (e.g. rotation) while the filter was in use: the new
            // panel needs its adapters and listeners again
            setupFilterPanel(panel);
        }
    }

    /**
     * Release view references and stop loaders; call from {@code onDestroyView()}.
     */
    public void unbindViews() {
        // Dispose loaders here rather than in onDestroy so they do not outlive the view and
        // cannot call back into nulled view references or trigger RecyclerView updates
        dispose();
        filterPanel = null;
        filterLoadingRow = null;
        filterLoadingText = null;
        filterLoadingSpinner = null;
    }

    /**
     * Stop any running loaders; safe to call repeatedly.
     */
    public void dispose() {
        if (preloadLoader != null) {
            preloadLoader.dispose();
            preloadLoader = null;
        }
        if (backgroundLoader != null) {
            backgroundLoader.dispose();
            backgroundLoader = null;
        }
        isBackgroundLoading = false;
    }

    /**
     * @return true once the user has opened the panel, i.e. the controller owns pagination
     */
    public boolean isActive() {
        return filterPanelInitialized;
    }

    public boolean isFullyLoaded() {
        return isFullyLoaded;
    }

    /**
     * @return every stream item fetched so far (all pages), unfiltered
     */
    @NonNull
    public List<StreamInfoItem> getLoadedItems() {
        return allLoadedItems;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Host callbacks
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Forward from {@code handleResult()} once the header is bound.
     *
     * @param items         the first page's items
     * @param firstNextPage the page following the first one
     */
    public void onFirstPageLoaded(@NonNull final List<? extends InfoItem> items,
                                  @Nullable final Page firstNextPage) {
        if (!allLoadedItems.isEmpty()) {
            return;
        }
        if (items.isEmpty()) {
            // The host is about to fetch the next page itself; track it via onNextItems
            nextPage = firstNextPage;
            return;
        }
        allLoadedItems.addAll(filterStreams(items));
        final List<StreamInfoItem> cached =
                StreamItemCache.getInstance().getItems(host.getCacheKey());
        if (cached != null) {
            // The fresh first page wins over the cached copy of it, so an upload newer than
            // the cache entry is still part of the list
            final Set<String> firstPageUrls = allLoadedItems.stream()
                    .map(InfoItem::getUrl).collect(Collectors.toSet());
            for (final StreamInfoItem item : cached) {
                if (!firstPageUrls.contains(item.getUrl())) {
                    allLoadedItems.add(item);
                }
            }
            nextPage = null;
            isFullyLoaded = true;
            host.onItemsLoadedInBackground();
            return;
        }
        nextPage = firstNextPage;
        if (!Page.isValid(nextPage)) {
            markFullyLoaded();
            return;
        }
        // Silently preload a few more pages so filter/sort is faster if the user opens the panel
        preloadPages(nextPage, PRELOAD_PAGES);
    }

    /**
     * Forward from {@code loadMoreItems()}.
     *
     * @return true if the controller handled the request and the host must not call its
     * superclass implementation
     */
    public boolean onLoadMoreItems() {
        if (filterPanelInitialized) {
            // Background loader owns all pagination when filter is active.
            // If loading is still in progress and stalled (e.g. transient error), restart it.
            if (!isFullyLoaded && backgroundLoader == null) {
                loadNextPageInBackground(nextPage);
            }
            return true;
        }

        // Show pages that were already preloaded instead of fetching them again
        final int shown = filterStreams(host.getAdapter().getItemsList()).size();
        if (allLoadedItems.size() > shown) {
            host.getAdapter().addInfoItemList(
                    new ArrayList<>(allLoadedItems.subList(shown, allLoadedItems.size())));
            syncHostNextPage();
            host.showListFooter(Page.isValid(host.getNextPage()));
            return true;
        }
        // Nothing preloaded yet but a preload is in flight: wait for it rather than fetching
        // the same page twice
        return preloadLoader != null;
    }

    /**
     * Forward from {@code handleNextItems()} for pages fetched by the host itself.
     *
     * @param result the page
     * @return true if the controller consumed the page and the host must not call its
     * superclass implementation
     */
    public boolean onNextItems(
            @NonNull final ListExtractor.InfoItemsPage<? extends InfoItem> result) {
        // Only accumulate items if background loading has not already fetched everything —
        // a stale worker completing after isFullyLoaded = true would cause duplicates
        if (!isFullyLoaded) {
            allLoadedItems.addAll(filterStreams(result.getItems()));
            nextPage = result.getNextPage();
            if (!Page.isValid(nextPage)) {
                markFullyLoaded();
            }
        }
        if (filterPanelInitialized) {
            applyFilterAndSort();
            return true;
        }
        return false;
    }

    /**
     * Keep only the {@link StreamInfoItem}s of a mixed list.
     *
     * @param items any info items
     * @return the stream items, in order
     */
    @NonNull
    public static List<StreamInfoItem> filterStreams(final List<? extends InfoItem> items) {
        final List<StreamInfoItem> streams = new ArrayList<>();
        for (final InfoItem item : items) {
            if (item instanceof StreamInfoItem) {
                streams.add((StreamInfoItem) item);
            }
        }
        return streams;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Filter panel setup
    //////////////////////////////////////////////////////////////////////////*/

    private void setupSortSpinners(final View panel) {
        final Spinner sortFieldSpinner = panel.findViewById(R.id.filter_sort_field);
        final ArrayAdapter<String> sortFieldAdapter = new ArrayAdapter<>(
                fragment.requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{
                        fragment.getString(R.string.filter_sort_date),
                        fragment.getString(R.string.filter_sort_views),
                        fragment.getString(R.string.filter_sort_name)
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
                fragment.getString(R.string.filter_age_none),
                fragment.getString(R.string.filter_age_1_day),
                fragment.getString(R.string.filter_age_2_days),
                fragment.getString(R.string.filter_age_4_days),
                fragment.getString(R.string.filter_age_1_week),
                fragment.getString(R.string.filter_age_2_weeks),
                fragment.getString(R.string.filter_age_1_month),
                fragment.getString(R.string.filter_age_2_months),
                fragment.getString(R.string.filter_age_4_months),
                fragment.getString(R.string.filter_age_1_year),
                fragment.getString(R.string.filter_age_2_years),
                fragment.getString(R.string.filter_age_5_years),
                fragment.getString(R.string.filter_age_custom)
        };
        assert AGE_PERIOD_DAYS.length == agePeriodLabels.length;
        final ArrayAdapter<String> agePeriodAdapter = new ArrayAdapter<>(
                fragment.requireContext(), android.R.layout.simple_spinner_item,
                agePeriodLabels);
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
                fragment.getString(R.string.filter_views_none),
                fragment.getString(R.string.filter_views_1k),
                fragment.getString(R.string.filter_views_10k),
                fragment.getString(R.string.filter_views_100k),
                fragment.getString(R.string.filter_views_1m),
                fragment.getString(R.string.filter_views_10m),
                fragment.getString(R.string.filter_views_100m),
                fragment.getString(R.string.filter_views_custom)
        };
        assert VIEWS_PRESET_VALUES.length == viewsLabels.length;
        final ArrayAdapter<String> viewsAdapter = new ArrayAdapter<>(
                fragment.requireContext(), android.R.layout.simple_spinner_item, viewsLabels);
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
            // From now on the controller owns pagination
            host.cancelPendingLoad();
        }
        final boolean nowVisible = filterPanel.getVisibility() != View.VISIBLE;
        filterPanel.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
        if (nowVisible) {
            startBackgroundLoadIfNeeded();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Preload and background full-load logic
    //////////////////////////////////////////////////////////////////////////*/

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
        preloadLoader = host.loadPage(startPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(infoItemsPage -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    preloadLoader = null;
                    if (isFullyLoaded) {
                        return;
                    }
                    allLoadedItems.addAll(filterStreams(infoItemsPage.getItems()));
                    nextPage = infoItemsPage.getNextPage();
                    if (!Page.isValid(nextPage)) {
                        markFullyLoaded();
                    }
                    host.onItemsLoadedInBackground();
                    syncHostNextPage();
                    if (Page.isValid(nextPage) && maxPages > 1) {
                        preloadPages(nextPage, maxPages - 1);
                    }
                }, throwable -> {
                    preloadLoader = null;
                    Log.w(TAG, "Preload error", throwable);
                });
    }

    /**
     * Advance the host's next page to the controller's, but only if that does not hide the
     * "more items" footer while preloaded items are still waiting to be shown — the host only
     * asks for more items while it believes there is a next page.
     */
    private void syncHostNextPage() {
        if (filterPanelInitialized || preloadLoader != null) {
            return;
        }
        final int shown = filterStreams(host.getAdapter().getItemsList()).size();
        if (Page.isValid(nextPage) || allLoadedItems.size() <= shown) {
            host.setNextPage(nextPage);
        }
    }

    private void startBackgroundLoadIfNeeded() {
        if (isFullyLoaded) {
            applyFilterAndSort();
            return;
        }
        if (backgroundLoader != null) {
            return;
        }

        // Stop preloading — background loading takes over from here
        if (preloadLoader != null) {
            preloadLoader.dispose();
            preloadLoader = null;
        }

        updateLoadingIndicator(true, allLoadedItems.size());
        loadNextPageInBackground(nextPage);
    }

    private void loadNextPageInBackground(@Nullable final Page page) {
        if (!Page.isValid(page)) {
            // No more pages — loading complete
            isBackgroundLoading = false;
            markFullyLoaded();
            host.setNextPage(null);
            updateLoadingIndicator(false, allLoadedItems.size());
            applyFilterAndSort();
            return;
        }

        isBackgroundLoading = true;
        backgroundLoader = host.loadPage(page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(infoItemsPage -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    backgroundLoader = null;
                    allLoadedItems.addAll(filterStreams(infoItemsPage.getItems()));
                    nextPage = infoItemsPage.getNextPage();
                    host.onItemsLoadedInBackground();
                    updateLoadingIndicator(true, allLoadedItems.size());
                    // Throttle mid-load redraws to avoid flooding the RecyclerView
                    if (System.currentTimeMillis() - lastFilterApplyMs > 500) {
                        lastFilterApplyMs = System.currentTimeMillis();
                        applyFilterAndSort();
                    }
                    loadNextPageInBackground(nextPage);
                }, throwable -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    backgroundLoader = null;
                    isBackgroundLoading = false;
                    Log.w(TAG, "Background load error", throwable);
                    updateLoadingIndicator(false, allLoadedItems.size());
                });
    }

    private void markFullyLoaded() {
        isFullyLoaded = true;
        nextPage = null;
        StreamItemCache.getInstance().putItems(host.getCacheKey(), allLoadedItems);
    }

    private void updateLoadingIndicator(final boolean loading, final int count) {
        if (filterLoadingRow == null || filterPanel == null || filterLoadingText == null) {
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
            filterLoadingText.setText(
                    fragment.getString(R.string.filter_loading_progress, count));
        } else {
            if (filterLoadingSpinner != null) {
                filterLoadingSpinner.setVisibility(View.GONE);
            }
            filterLoadingText.setText(fragment.getString(R.string.filter_loading_done, count));
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
        final InfoListAdapter adapter = host.getAdapter();
        if (isBackgroundLoading) {
            adapter.replaceStreamItems(result);
        } else {
            adapter.clearStreamItemList();
            adapter.addInfoItemList(result);
        }
        // Footer suppressed while filter is active; filter panel's loading row shows progress
        host.showListFooter(false);
    }
}
