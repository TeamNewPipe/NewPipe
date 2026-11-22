package org.schabi.newpipe.local.history;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.FragmentPlaylistBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.databinding.StatisticPlaylistControlBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.LocalUploaderNavigation;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.settings.HistorySettingsFragment;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.StreamFilterControls;
import org.schabi.newpipe.util.StreamFilterSelection;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class StatisticsPlaylistFragment
        extends BaseLocalListFragment<List<StreamStatisticsEntry>, Void>
        implements PlaylistControlViewHolder, ContextualSearchable {
    private static final int MIN_FAST_SCROLL_ITEMS = 50;
    private static final long FAST_SCROLL_HIDE_DELAY_MILLIS = 1_800L;
    private static final long FAST_SCROLL_FADE_IN_MILLIS = 140L;
    private static final long FAST_SCROLL_FADE_OUT_MILLIS = 220L;
    private static final String DATE_PICKER_TAG = "history_date_picker";
    private static final String DATE_RANGE_PICKER_TAG = "history_date_range_picker";

    private final CompositeDisposable disposables = new CompositeDisposable();
    @State
    Parcelable itemsListState;
    @State
    String historySortMode = StatisticSortMode.LAST_PLAYED.name();
    @State
    long historyDateFrom = Long.MIN_VALUE;
    @State
    long historyDateTo = Long.MAX_VALUE;
    private StatisticSortMode sortMode = StatisticSortMode.LAST_PLAYED;
    private List<StreamStatisticsEntry> completeHistory = Collections.emptyList();
    private List<StreamStatisticsEntry> displayedHistory = Collections.emptyList();
    private String contextualSearchQuery = "";
    private boolean dateFastScrollEnabled;
    @State
    String selectedStreamFilters = "";

    private FragmentPlaylistBinding contentBinding;
    private StatisticPlaylistControlBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;
    private RecyclerView.OnScrollListener historyScrollListener;
    private Disposable historyProcessingDisposable;

    private final Runnable hideFastScrollerRunnable = this::hideFastScrollerAnimated;

    /* Used for independent events */
    private Subscription databaseSubscription;
    private HistoryRecordManager recordManager;

    @NonNull
    private static HistoryViewData processHistory(
            @NonNull final List<StreamStatisticsEntry> completeHistory,
            @NonNull final String selectedStreamFilters,
            @NonNull final String contextualSearchQuery,
            @NonNull final StatisticSortMode sortMode,
            final long dateFrom,
            final long dateTo) {
        final String normalizedQuery = contextualSearchQuery.toLowerCase(Locale.ROOT);
        final boolean hasFilters = !selectedStreamFilters.isEmpty()
                || !normalizedQuery.isEmpty();
        if (!hasFilters && sortMode == StatisticSortMode.LAST_PLAYED
                && dateFrom == Long.MIN_VALUE && dateTo == Long.MAX_VALUE) {
            return new HistoryViewData(completeHistory);
        }
        final List<StreamStatisticsEntry> filteredHistory = new ArrayList<>();
        final StreamFilterSelection selection = StreamFilterSelection.parse(selectedStreamFilters);
        for (final StreamStatisticsEntry item : completeHistory) {
            if (!HistoryViewOptions.inDateRange(item.getLatestAccessDate().toLocalDate(),
                    dateFrom, dateTo) || !selection.matches(item)) {
                continue;
            }
            if (!normalizedQuery.isEmpty()) {
                final String title = item.getStreamEntity().getTitle();
                final String uploader = item.getStreamEntity().getUploader();
                final boolean matchesTitle = title != null
                        && title.toLowerCase(Locale.ROOT).contains(normalizedQuery);
                final boolean matchesUploader = uploader != null
                        && uploader.toLowerCase(Locale.ROOT).contains(normalizedQuery);
                if (!matchesTitle && !matchesUploader) {
                    continue;
                }
            }
            filteredHistory.add(item);
        }

        return new HistoryViewData(HistoryViewOptions.sort(filteredHistory, sortMode.name()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recordManager = new HistoryRecordManager(getContext());
        try {
            sortMode = StatisticSortMode.valueOf(historySortMode);
        } catch (final IllegalArgumentException ignored) {
            sortMode = StatisticSortMode.LAST_PLAYED;
            historySortMode = sortMode.name();
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null) {
            setTitle(activity.getString(R.string.title_activity_history));
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_history, menu);
        menu.findItem(R.id.action_history_clear_date_range).setVisible(
                historyDateFrom != Long.MIN_VALUE || historyDateTo != Long.MAX_VALUE);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        contentBinding = FragmentPlaylistBinding.bind(rootView);
        contentBinding.historyDateFastScroller.setOnPositionChangedListener(
                this::scrollToHistoryPosition);
        contentBinding.historyDateFastScroller.setLabelProvider(this::getFastScrollLabel);
        contentBinding.historyDateFastScroller.setOnDragStateChangedListener(dragging -> {
            if (dragging) {
                showFastScroller();
            } else {
                scheduleFastScrollerHide();
            }
        });

        StreamFilterControls.bind(contentBinding.streamFilterChips, selectedStreamFilters,
                true, true, true, true, selection -> {
                    selectedStreamFilters = selection;
                    showFilteredHistory();
                });
        if (!useAsFrontPage) {
            setTitle(getString(R.string.title_last_played));
        }
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        headerBinding = StatisticPlaylistControlBinding.inflate(activity.getLayoutInflater(),
                itemsList, false);
        playlistControlBinding = headerBinding.playlistControl;

        return headerBinding::getRoot;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        contentBinding.historyDateJumpButton.setOnClickListener(view -> showDatePicker());
        historyScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull final RecyclerView recyclerView,
                                   final int dx,
                                   final int dy) {
                updateFastScrollPosition();
                if (dy != 0) {
                    showFastScroller();
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull final RecyclerView recyclerView,
                                             final int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleFastScrollerHide();
                } else {
                    showFastScroller();
                }
            }
        };
        itemsList.addOnScrollListener(historyScrollListener);

        itemListAdapter.setUploaderSelectedListener(selectedItem -> {
            if (selectedItem instanceof StreamStatisticsEntry) {
                LocalUploaderNavigation.openChannel(this,
                        ((StreamStatisticsEntry) selectedItem).getStreamEntity());
            }
        });

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                if (selectedItem instanceof StreamStatisticsEntry) {
                    final StreamEntity item =
                            ((StreamStatisticsEntry) selectedItem).getStreamEntity();
                    if (item.isRadio()) {
                        NavigationHelper.playOnBackgroundPlayer(requireContext(),
                                getPlayQueueStartingAt((StreamStatisticsEntry) selectedItem),
                                false);
                        return;
                    }
                    if (item.isLocalMedia()) {
                        NavigationHelper.playOnMainPlayer(requireContext(),
                                getPlayQueueStartingAt((StreamStatisticsEntry) selectedItem),
                                false);
                        return;
                    }
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            item.getServiceId(), item.getUrl(), item.getTitle(), null, false);
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (selectedItem instanceof StreamStatisticsEntry) {
                    showInfoItemDialog((StreamStatisticsEntry) selectedItem);
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_history_date_range) {
            showDateRangePicker();
        } else if (item.getItemId() == R.id.action_history_clear_date_range) {
            historyDateFrom = Long.MIN_VALUE;
            historyDateTo = Long.MAX_VALUE;
            requireActivity().invalidateOptionsMenu();
            showFilteredHistory();
        } else if (item.getItemId() == R.id.action_history_clear) {
            HistorySettingsFragment
                    .openDeleteWatchHistoryDialog(requireContext(), recordManager, disposables);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);
        recordManager.getStreamStatistics()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getHistoryObserver());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = Objects.requireNonNull(itemsList.getLayoutManager()).onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        if (itemsList != null && historyScrollListener != null) {
            itemsList.removeOnScrollListener(historyScrollListener);
        }
        historyScrollListener = null;

        if (contentBinding != null) {
            hideFastScrollerImmediately();
            contentBinding.historyDateFastScroller.dismissBubble();
        }
        if (historyProcessingDisposable != null) {
            historyProcessingDisposable.dispose();
            historyProcessingDisposable = null;
        }

        if (itemListAdapter != null) {
            itemListAdapter.unsetSelectedListener();
            itemListAdapter.unsetUploaderSelectedListener();
        }

        super.onDestroyView();

        contentBinding = null;
        headerBinding = null;
        playlistControlBinding = null;
        completeHistory = Collections.emptyList();
        displayedHistory = Collections.emptyList();
        dateFastScrollEnabled = false;

        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }
        databaseSubscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recordManager = null;
        itemsListState = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Statistics Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<StreamStatisticsEntry>> getHistoryObserver() {
        return new Subscriber<List<StreamStatisticsEntry>>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();

                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<StreamStatisticsEntry> streams) {
                handleResult(streams);
                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(
                        new ErrorInfo(exception, UserAction.SOMETHING_ELSE, "History Statistics"));
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull final List<StreamStatisticsEntry> result) {
        super.handleResult(result);
        completeHistory = result;
        showFilteredHistory();
    }

    private void showFilteredHistory() {
        if (itemListAdapter == null) {
            return;
        }

        playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
        setEmptyStateMessage(ContextualSearchHelper.isActive(contextualSearchQuery)
                ? R.string.search_no_results : R.string.empty_view_no_videos);

        if (historyProcessingDisposable != null) {
            historyProcessingDisposable.dispose();
        }

        final List<StreamStatisticsEntry> historySnapshot = completeHistory;
        final String filterSnapshot = selectedStreamFilters;
        final String querySnapshot = contextualSearchQuery;
        final StatisticSortMode sortModeSnapshot = sortMode;
        final long dateFrom = historyDateFrom;
        final long dateTo = historyDateTo;

        historyProcessingDisposable = Single.fromCallable(() -> processHistory(
                        historySnapshot, filterSnapshot, querySnapshot, sortModeSnapshot,
                        dateFrom, dateTo))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyProcessedHistory,
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.SOMETHING_ELSE, "Processing history")));
    }

    private void applyProcessedHistory(@NonNull final HistoryViewData historyViewData) {
        if (itemListAdapter == null || contentBinding == null) {
            return;
        }

        itemListAdapter.clearStreamItemList();
        displayedHistory = historyViewData.entries;

        if (displayedHistory.isEmpty()) {
            updateDateNavigation();
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(displayedHistory);
        if (itemsListState != null && itemsList.getLayoutManager() != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }

        PlayButtonHelper.initPlaylistControlClickListener(activity, playlistControlBinding, this);
        headerBinding.sortButton.setOnClickListener(view -> showSortPicker());
        updateSortHeader();

        updateDateNavigation();
        hideLoading();
    }

    @Override
    public void setContextualSearchQuery(@NonNull final String query) {
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(query);
        showFilteredHistory();
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
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSortPicker() {
        final String[] titles = {
            getString(R.string.title_last_played),
            getString(R.string.history_sort_earliest),
            getString(R.string.title_most_played),
            getString(R.string.history_sort_least),
            getString(R.string.history_sort_title)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort)
                .setSingleChoiceItems(titles, sortMode.ordinal(), (dialog, index) -> {
                    sortMode = StatisticSortMode.values()[index];
                    historySortMode = sortMode.name();
                    updateSortHeader();
                    hideDateNavigation();
                    showFilteredHistory();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateSortHeader() {
        if (headerBinding == null) {
            return;
        }
        final int label;
        switch (sortMode) {
            case EARLIEST_PLAYED:
                label = R.string.history_sort_earliest;
                break;
            case MOST_PLAYED:
                label = R.string.title_most_played;
                break;
            case LEAST_PLAYED:
                label = R.string.history_sort_least;
                break;
            case TITLE:
                label = R.string.history_sort_title;
                break;
            case LAST_PLAYED:
            default:
                label = R.string.title_last_played;
                break;
        }
        headerBinding.sortButtonText.setText(label);
        headerBinding.sortButton.setContentDescription(getString(label));
    }

    private void showDateRangePicker() {
        if (getParentFragmentManager().findFragmentByTag(DATE_RANGE_PICKER_TAG) != null) {
            return;
        }
        final MaterialDatePicker.Builder<Pair<Long, Long>> builder =
                MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText(R.string.history_date_range_title);
        if (historyDateFrom != Long.MIN_VALUE && historyDateTo != Long.MAX_VALUE) {
            builder.setSelection(new Pair<>(
                    toUtcMillis(LocalDate.ofEpochDay(historyDateFrom)),
                    toUtcMillis(LocalDate.ofEpochDay(historyDateTo))));
        }
        final MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(range -> {
            if (range.first == null || range.second == null) {
                return;
            }
            historyDateFrom = utcMillisToDate(range.first).toEpochDay();
            historyDateTo = utcMillisToDate(range.second).toEpochDay();
            requireActivity().invalidateOptionsMenu();
            showFilteredHistory();
        });
        picker.show(getParentFragmentManager(), DATE_RANGE_PICKER_TAG);
    }

    private void hideDateNavigation() {
        dateFastScrollEnabled = false;
        if (contentBinding == null) {
            return;
        }
        contentBinding.historyDateJumpButton.setVisibility(View.GONE);
        hideFastScrollerImmediately();
        contentBinding.historyDateFastScroller.dismissBubble();
        if (itemsList != null) {
            itemsList.setVerticalScrollBarEnabled(true);
        }
    }

    private void updateDateNavigation() {
        if (contentBinding == null) {
            return;
        }

        final boolean chronological = sortMode == StatisticSortMode.LAST_PLAYED
                && displayedHistory.size() > 1;
        contentBinding.historyDateJumpButton.setVisibility(
                chronological ? View.VISIBLE : View.GONE);

        dateFastScrollEnabled = chronological
                && displayedHistory.size() >= MIN_FAST_SCROLL_ITEMS;
        hideFastScrollerImmediately();
        contentBinding.historyDateFastScroller.setItemCount(displayedHistory.size());
        itemsList.setVerticalScrollBarEnabled(!dateFastScrollEnabled);

        if (!chronological) {
            contentBinding.historyDateFastScroller.dismissBubble();
            return;
        }

        itemsList.post(this::updateFastScrollPosition);
    }

    private void showFastScroller() {
        if (!dateFastScrollEnabled || contentBinding == null) {
            return;
        }
        final HistoryDateFastScroller fastScroller =
                contentBinding.historyDateFastScroller;
        fastScroller.removeCallbacks(hideFastScrollerRunnable);
        fastScroller.animate().cancel();
        fastScroller.animate().withEndAction(null);
        if (fastScroller.getVisibility() != View.VISIBLE) {
            fastScroller.setAlpha(0f);
            fastScroller.setVisibility(View.VISIBLE);
        }
        fastScroller.animate()
                .alpha(1f)
                .setDuration(FAST_SCROLL_FADE_IN_MILLIS)
                .start();
    }

    private void hideFastScrollerAnimated() {
        if (!dateFastScrollEnabled || contentBinding == null) {
            return;
        }
        final HistoryDateFastScroller fastScroller =
                contentBinding.historyDateFastScroller;
        if (fastScroller.isDragging() || fastScroller.getVisibility() != View.VISIBLE) {
            return;
        }
        final FragmentPlaylistBinding bindingAtStart = contentBinding;
        fastScroller.animate().cancel();
        fastScroller.animate()
                .alpha(0f)
                .setDuration(FAST_SCROLL_FADE_OUT_MILLIS)
                .withEndAction(() -> {
                    if (contentBinding == bindingAtStart && !fastScroller.isDragging()) {
                        fastScroller.setVisibility(View.GONE);
                        fastScroller.setAlpha(1f);
                    }
                })
                .start();
    }

    private void hideFastScrollerImmediately() {
        if (contentBinding == null) {
            return;
        }
        final HistoryDateFastScroller fastScroller =
                contentBinding.historyDateFastScroller;
        fastScroller.removeCallbacks(hideFastScrollerRunnable);
        fastScroller.animate().cancel();
        fastScroller.animate().withEndAction(null);
        fastScroller.setAlpha(1f);
        fastScroller.setVisibility(View.GONE);
    }

    private void scheduleFastScrollerHide() {
        if (!dateFastScrollEnabled || contentBinding == null
                || contentBinding.historyDateFastScroller.isDragging()) {
            return;
        }
        contentBinding.historyDateFastScroller.removeCallbacks(hideFastScrollerRunnable);
        contentBinding.historyDateFastScroller.postDelayed(
                hideFastScrollerRunnable, FAST_SCROLL_HIDE_DELAY_MILLIS);
    }

    private void updateFastScrollPosition() {
        if (contentBinding == null || displayedHistory.isEmpty()
                || !(itemsList.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }

        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) itemsList.getLayoutManager();
        int itemIndex = itemListAdapter.getItemIndex(
                layoutManager.findFirstVisibleItemPosition());
        if (itemIndex < 0) {
            itemIndex = 0;
        }
        contentBinding.historyDateFastScroller.setPosition(itemIndex);
    }

    private void scrollToHistoryPosition(final int itemIndex) {
        if (itemListAdapter == null || itemsList == null
                || !(itemsList.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }

        final int adapterPosition =
                itemListAdapter.getAdapterPositionForItemIndex(itemIndex);
        if (adapterPosition == RecyclerView.NO_POSITION) {
            return;
        }

        itemsList.stopScroll();
        ((LinearLayoutManager) itemsList.getLayoutManager())
                .scrollToPositionWithOffset(adapterPosition, 0);
        contentBinding.historyDateFastScroller.setPosition(itemIndex);
    }

    private String getFastScrollLabel(final int itemIndex) {
        if (itemIndex < 0 || itemIndex >= displayedHistory.size()) {
            return "";
        }
        return HistoryDateNavigator.formatLabel(
                historyDateAt(itemIndex),
                org.schabi.newpipe.util.Localization.getPreferredLocale(requireContext()));
    }

    private void showDatePicker() {
        if (sortMode != StatisticSortMode.LAST_PLAYED || displayedHistory.isEmpty()
                || getParentFragmentManager().findFragmentByTag(DATE_PICKER_TAG) != null) {
            return;
        }

        final LocalDate newestDate = historyDateAt(0);
        final LocalDate oldestDate = historyDateAt(displayedHistory.size() - 1);
        final long newestMillis = toUtcMillis(newestDate);
        final long oldestMillis = toUtcMillis(oldestDate);
        final int visibleIndex = getVisibleHistoryIndex();
        final long selectedMillis = toUtcMillis(historyDateAt(visibleIndex));

        final CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(oldestMillis)
                .setEnd(newestMillis)
                .setOpenAt(selectedMillis)
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(oldestMillis),
                        DateValidatorPointBackward.before(newestMillis))))
                .build();

        final MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.history_jump_to_date)
                .setCalendarConstraints(constraints)
                .setSelection(selectedMillis)
                .build();
        picker.addOnPositiveButtonClickListener(selection ->
                jumpToDate(utcMillisToDate(selection)));
        picker.show(getParentFragmentManager(), DATE_PICKER_TAG);
    }

    private int getVisibleHistoryIndex() {
        if (itemsList.getLayoutManager() instanceof LinearLayoutManager) {
            final int adapterPosition = ((LinearLayoutManager) itemsList.getLayoutManager())
                    .findFirstVisibleItemPosition();
            final int itemIndex = itemListAdapter.getItemIndex(adapterPosition);
            if (itemIndex >= 0 && itemIndex < displayedHistory.size()) {
                return itemIndex;
            }
        }
        return 0;
    }

    private void jumpToDate(@NonNull final LocalDate selectedDate) {
        final int itemIndex = HistoryDateNavigator.findClosestIndex(
                displayedHistory.size(), this::historyDateAt, selectedDate);
        if (itemIndex >= 0) {
            scrollToHistoryPosition(itemIndex);
        }
    }

    private static long toUtcMillis(@NonNull final LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    @NonNull
    private LocalDate historyDateAt(final int index) {
        return displayedHistory.get(index).getLatestAccessDate().toLocalDate();
    }

    @NonNull
    private static LocalDate utcMillisToDate(final long millis) {
        return java.time.Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }

    private PlayQueue getPlayQueueStartingAt(final StreamStatisticsEntry infoItem) {
        return getPlayQueue(Math.max(itemListAdapter.getItemsList().indexOf(infoItem), 0));
    }

    private void showInfoItemDialog(final StreamStatisticsEntry item) {
        if (item.getStreamEntity().isLocalMedia() || item.getStreamEntity().isRadio()) {
            final String[] actions = {
                    getString(R.string.play),
                    getString(R.string.local_media_play_background),
                    getString(R.string.delete)
            };
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(item.getStreamEntity().getTitle())
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0 && item.getStreamEntity().isRadio()) {
                            NavigationHelper.playOnBackgroundPlayer(requireContext(),
                                    getPlayQueueStartingAt(item), false);
                        } else if (which == 0) {
                            NavigationHelper.playOnMainPlayer(requireContext(),
                                    getPlayQueueStartingAt(item), false);
                        } else if (which == 1) {
                            NavigationHelper.playOnBackgroundPlayer(requireContext(),
                                    getPlayQueueStartingAt(item), true);
                        } else if (which == 2) {
                            confirmDeleteEntry(item);
                        }
                    })
                    .show();
            return;
        }
        final Context context = getContext();
        final StreamInfoItem infoItem = item.toStreamInfoItem();

        try {
            final InfoItemDialog.Builder dialogBuilder =
                    new InfoItemDialog.Builder(getActivity(), context, this, infoItem);

            // set entries in the middle; the others are added automatically
            dialogBuilder
                    .addEntry(StreamDialogDefaultEntry.DELETE)
                    .setAction(
                            StreamDialogDefaultEntry.DELETE,
                            (f, i) -> confirmDeleteEntry(item))
                    .create()
                    .show();
        } catch (final IllegalArgumentException e) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, infoItem);
        }
    }

    @VisibleForTesting
    AlertDialog confirmDeleteEntry(final StreamStatisticsEntry entry) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(entry.getStreamEntity().getTitle())
                .setMessage(R.string.delete_item_watch_history)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteEntry(entry))
                .show();
    }

    private void deleteEntry(final StreamStatisticsEntry entry) {
        final Disposable onDelete = recordManager
                .deleteStreamHistoryAndState(entry.getStreamId())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (getView() != null) {
                                Snackbar.make(getView(), R.string.one_item_deleted,
                                        Snackbar.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(),
                                        R.string.one_item_deleted,
                                        Toast.LENGTH_SHORT).show();
                            }
                        },
                        throwable -> showSnackBarError(new ErrorInfo(throwable,
                                UserAction.DELETE_FROM_HISTORY, "Deleting item")));

        disposables.add(onDelete);
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
        final List<PlayQueueItem> queueItems = new ArrayList<>(infoItems.size());
        for (final LocalItem item : infoItems) {
            if (item instanceof StreamStatisticsEntry) {
                queueItems.add(((StreamStatisticsEntry) item).toPlayQueueItem());
            }
        }
        return new LocalMediaPlayQueue(queueItems, index);
    }

    private static final class HistoryViewData {
        private final List<StreamStatisticsEntry> entries;

        private HistoryViewData(@NonNull final List<StreamStatisticsEntry> entries) {
            this.entries = entries;
        }
    }

    private enum StatisticSortMode {
        LAST_PLAYED,
        EARLIEST_PLAYED,
        MOST_PLAYED,
        LEAST_PLAYED,
        TITLE
    }
}
