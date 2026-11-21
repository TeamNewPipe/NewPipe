package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.evernote.android.state.State;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.databinding.FragmentChannelTabBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.download.DownloadedAvailabilityIndex;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StreamFilterControls;
import org.schabi.newpipe.util.StreamFilterSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

public class ChannelTabFragment extends BaseListInfoFragment<InfoItem, ChannelTabInfo>
        implements PlaylistControlViewHolder, ContextualSearchable {

    // states must be protected and not private for State being able to access them
    @State
    protected ListLinkHandler tabHandler;
    @State
    protected String channelName;
    @State
    protected String selectedStreamFilters = "";
    @State
    protected String selectedChannelVideoSort;
    @State
    protected String contextualSearchQuery = "";

    private FragmentChannelTabBinding binding;
    private PlaylistControlBinding playlistControlBinding;
    private final List<InfoItem> unfilteredItems = new ArrayList<>();
    private final Map<String, StreamStateEntity> streamStates = new HashMap<>();
    private HistoryRecordManager historyRecordManager;
    private Disposable streamStateWorker;
    private ListLinkHandlerFactory channelTabLinkHandlerFactory;
    private List<FilterItem> availableChannelVideoSortFilters = Collections.emptyList();

    @NonNull
    public static ChannelTabFragment getInstance(final int serviceId,
                                                 final ListLinkHandler tabHandler,
                                                 final String channelName,
                                                 @Nullable final String selectedChannelVideoSort) {
        final ChannelTabFragment instance = new ChannelTabFragment();
        instance.serviceId = serviceId;
        instance.tabHandler = tabHandler;
        instance.channelName = channelName;
        instance.selectedChannelVideoSort = selectedChannelVideoSort;
        return instance;
    }

    public ChannelTabFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.CHANNEL_PAGES;
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
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        initChannelVideoSort();
        super.initViews(rootView, savedInstanceState);
        binding = FragmentChannelTabBinding.bind(rootView);
        historyRecordManager = new HistoryRecordManager(requireContext());
        final boolean showStreamFilters = ChannelTabHelper.isStreamsTab(tabHandler);
        final boolean showChannelVideoSort = availableChannelVideoSortFilters.size() > 1;
        binding.channelTabControls.setVisibility(
                showStreamFilters || showChannelVideoSort ? View.VISIBLE : View.GONE);
        binding.streamFilterChips.getRoot().setVisibility(
                showStreamFilters ? View.VISIBLE : View.GONE);
        binding.channelVideoSortButton.setVisibility(
                showChannelVideoSort ? View.VISIBLE : View.GONE);
        if (showChannelVideoSort) {
            updateChannelVideoSortButton();
            binding.channelVideoSortButton.setOnClickListener(
                    ignored -> showChannelVideoSortDialog());
        }
        StreamFilterControls.bind(binding.streamFilterChips, selectedStreamFilters,
                false, selection -> {
                    selectedStreamFilters = selection;
                    applyStreamFilter();
                });
        DownloadedAvailabilityIndex.INSTANCE.getUpdates().observe(getViewLifecycleOwner(),
                ignored -> {
                    if (StreamFilterSelection.parse(selectedStreamFilters).getDownloadedOnly()) {
                        applyStreamFilter();
                    }
                });
    }

    private void initChannelVideoSort() {
        try {
            channelTabLinkHandlerFactory =
                    NewPipe.getService(serviceId).getChannelTabLHFactory();
            availableChannelVideoSortFilters =
                    ChannelTabSortHelper.getAvailableSortFilters(
                            channelTabLinkHandlerFactory, tabHandler);
            if (availableChannelVideoSortFilters.size() <= 1) {
                return;
            }

            final int currentIndex = ChannelTabSortHelper.getSelectedSortFilterIndex(
                    tabHandler, availableChannelVideoSortFilters);
            final int selectedIndex = selectedChannelVideoSort == null
                    ? currentIndex
                    : ChannelTabSortHelper.getSortFilterIndex(
                            selectedChannelVideoSort, availableChannelVideoSortFilters);
            if (selectedChannelVideoSort != null && currentIndex != selectedIndex) {
                tabHandler = ChannelTabSortHelper.withSortFilter(
                        channelTabLinkHandlerFactory,
                        tabHandler,
                        availableChannelVideoSortFilters.get(selectedIndex));
            }
            selectedChannelVideoSort =
                    availableChannelVideoSortFilters.get(selectedIndex).getName();
        } catch (final ExtractionException exception) {
            Log.w(TAG, "Unable to initialize channel video sorting", exception);
            channelTabLinkHandlerFactory = null;
            availableChannelVideoSortFilters = Collections.emptyList();
        }
    }

    private void showChannelVideoSortDialog() {
        final String[] labels = availableChannelVideoSortFilters.stream()
                .map(filter -> ServiceHelper.getTranslatedFilterString(
                        filter.getName(), requireContext()))
                .toArray(String[]::new);
        final int selectedIndex = ChannelTabSortHelper.getSelectedSortFilterIndex(
                tabHandler, availableChannelVideoSortFilters);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    applyChannelVideoSort(which);
                })
                .show();
    }

    private void applyChannelVideoSort(final int selectedIndex) {
        if (channelTabLinkHandlerFactory == null
                || selectedIndex < 0
                || selectedIndex >= availableChannelVideoSortFilters.size()
                || selectedIndex == ChannelTabSortHelper.getSelectedSortFilterIndex(
                        tabHandler, availableChannelVideoSortFilters)) {
            return;
        }

        try {
            final FilterItem selectedFilter =
                    availableChannelVideoSortFilters.get(selectedIndex);
            tabHandler = ChannelTabSortHelper.withSortFilter(
                    channelTabLinkHandlerFactory, tabHandler, selectedFilter);
            selectedChannelVideoSort = selectedFilter.getName();
            final Fragment parent = getParentFragment();
            if (parent instanceof ChannelFragment) {
                ((ChannelFragment) parent).setSelectedChannelVideoSort(
                        selectedChannelVideoSort);
            }
            updateChannelVideoSortButton();
            currentNextPage = null;
            itemsList.scrollToPosition(0);
            startLoading(false);
        } catch (final ParsingException exception) {
            showSnackBarError(new ErrorInfo(
                    exception,
                    UserAction.REQUESTED_CHANNEL,
                    "Changing channel video sort order",
                    serviceId,
                    tabHandler.getUrl()));
        }
    }

    private void updateChannelVideoSortButton() {
        final int selectedIndex = ChannelTabSortHelper.getSelectedSortFilterIndex(
                tabHandler, availableChannelVideoSortFilters);
        if (selectedIndex < 0) {
            return;
        }
        final String label = ServiceHelper.getTranslatedFilterString(
                availableChannelVideoSortFilters.get(selectedIndex).getName(),
                requireContext());
        binding.channelVideoSortButton.setText(label);
        binding.channelVideoSortButton.setContentDescription(
                getString(R.string.channel_video_sort_content_description, label));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
            streamStateWorker = null;
        }
        binding = null;
        playlistControlBinding = null;
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        unfilteredItems.clear();
        streamStates.clear();
        super.startLoading(forceLoad);
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            playlistControlBinding = PlaylistControlBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);
            return playlistControlBinding::getRoot;
        }
        return null;
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getChannelTab(serviceId, tabHandler, forceLoad);
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage);
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
        unfilteredItems.clear();
        unfilteredItems.addAll(result.getRelatedItems());
        refreshStreamStates();

        // Latest WizeStreamExtractor no longer uses raw-data-ready channel tab handlers;
        // keep the fetched ListLinkHandler from ChannelInfo as-is.

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
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<InfoItem> result) {
        super.handleNextItems(result);
        unfilteredItems.addAll(result.getItems());
        refreshStreamStates();
    }

    private void refreshStreamStates() {
        if (!ChannelTabHelper.isStreamsTab(tabHandler)) {
            applyStreamFilter();
            return;
        }
        final List<InfoItem> snapshot = new ArrayList<>(unfilteredItems);
        applyStreamFilter();
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
        }
        streamStateWorker = historyRecordManager.loadStreamStateBatch(snapshot)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(states -> {
                    if (!unfilteredItems.equals(snapshot)) {
                        return;
                    }
                    streamStates.clear();
                    for (int i = 0; i < snapshot.size(); i++) {
                        streamStates.put(snapshot.get(i).getUrl(), states.get(i));
                    }
                    applyStreamFilter();
                }, throwable -> Log.w(TAG, "Unable to load channel stream states", throwable));
    }

    private void applyStreamFilter() {
        if (!ChannelTabHelper.isStreamsTab(tabHandler)
                || infoListAdapter == null
                || currentInfo == null) {
            return;
        }
        final StreamFilterSelection selection = StreamFilterSelection.parse(selectedStreamFilters);
        final List<InfoItem> displayedItems = unfilteredItems.stream()
                .filter(item -> !selection.isActive()
                        || item instanceof StreamInfoItem
                        && selection.matches(
                                (StreamInfoItem) item, streamStates.get(item.getUrl())))
                .filter(item -> ContextualSearchHelper.matchesInfoItem(
                        contextualSearchQuery, item))
                .collect(Collectors.toList());
        infoListAdapter.clearStreamItemList();
        infoListAdapter.addInfoItemList(displayedItems);
        showListFooter(hasMoreItems());
        final boolean isEmpty = infoListAdapter.getItemsList().isEmpty();
        if (isEmpty && !hasMoreItems()) {
            showEmptyState();
            if (ContextualSearchHelper.isActive(contextualSearchQuery)) {
                setEmptyStateMessage(R.string.search_no_results);
            }
        } else {
            hideLoading();
        }
        if (playlistControlBinding != null) {
            playlistControlBinding.getRoot().setVisibility(
                    isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void setContextualSearchQuery(@NonNull final String query) {
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(query);
        applyStreamFilter();
    }

    @Override
    public PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new ChannelTabPlayQueue(currentInfo.getServiceId(), tabHandler,
                ContextualSearchHelper.isActive(contextualSearchQuery)
                        ? null : currentInfo.getNextPage(),
                streamItems, 0);
    }
}
