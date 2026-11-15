package org.schabi.newpipe.fragments.list;

import org.schabi.newpipe.recommendations.LocalRecommendations;

import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateHideRecyclerViewAllowingScrolling;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.info_list.StreamSelectionController;
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.subscription.ChannelSubscriptionActions;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.profiles.ProfileRecord;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.GridLayoutManagerHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.SuperScrollLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class BaseListFragment<I, N> extends BaseStateFragment<I>
        implements ListViewContract<I, N>, StateSaver.WriteRead,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int LIST_MODE_UPDATE_FLAG = 0x32;
    protected org.schabi.newpipe.util.SavedState savedState;

    private boolean useDefaultStateSaving = true;
    private boolean commentTextSizeUpdatePending = false;
    private int updateFlags = 0;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    protected InfoListAdapter infoListAdapter;
    protected RecyclerView itemsList;
    private int focusedPosition = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(activity, getContentBlockingTarget());
        }
    }

    @NonNull
    protected abstract ContentBlockingHelper.Target getContentBlockingTarget();

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (useDefaultStateSaving) {
            StateSaver.onDestroy(savedState);
        }
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (itemsList != null) {
            GridLayoutManagerHelper.preserveScrollPosition(itemsList, () -> {
                GridLayoutManagerHelper.reinflateViewHolders(itemsList);
                refreshItemViewMode();
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (updateFlags != 0) {
            if ((updateFlags & LIST_MODE_UPDATE_FLAG) != 0) {
                refreshItemViewMode();
            }
            updateFlags = 0;
        }
        if (commentTextSizeUpdatePending) {
            applyPendingCommentTextSizeUpdate();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * If the default implementation of {@link StateSaver.WriteRead} should be used.
     *
     * @param useDefaultStateSaving Whether the default implementation should be used
     * @see StateSaver
     */
    public void setUseDefaultStateSaving(final boolean useDefaultStateSaving) {
        this.useDefaultStateSaving = useDefaultStateSaving;
    }

    @Override
    public String generateSuffix() {
        // Naive solution, but it's good for now (the items don't change)
        return "." + infoListAdapter.getItemsList().size() + ".list";
    }

    private int getFocusedPosition() {
        try {
            final View focusedItem = itemsList.getFocusedChild();
            final RecyclerView.ViewHolder itemHolder =
                    itemsList.findContainingViewHolder(focusedItem);
            return itemHolder.getBindingAdapterPosition();
        } catch (final NullPointerException e) {
            return -1;
        }
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        if (!useDefaultStateSaving) {
            return;
        }

        objectsToSave.add(infoListAdapter.getItemsList());
        objectsToSave.add(getFocusedPosition());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        if (!useDefaultStateSaving) {
            return;
        }

        infoListAdapter.getItemsList().clear();
        infoListAdapter.getItemsList().addAll((List<InfoItem>) savedObjects.poll());
        restoreFocus((Integer) savedObjects.poll());
    }

    private void restoreFocus(final Integer position) {
        if (position == null || position < 0) {
            return;
        }

        itemsList.post(() -> {
            final RecyclerView.ViewHolder focusedHolder =
                    itemsList.findViewHolderForAdapterPosition(position);

            if (focusedHolder != null) {
                focusedHolder.itemView.requestFocus();
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (useDefaultStateSaving) {
            savedState = StateSaver
                    .tryToSave(activity.isChangingConfigurations(), savedState, bundle, this);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        if (useDefaultStateSaving) {
            savedState = StateSaver.tryToRestore(bundle, this);
        }
    }

    @Override
    public void onStop() {
        focusedPosition = getFocusedPosition();
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        restoreFocus(focusedPosition);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    protected Supplier<View> getListHeaderSupplier() {
        return null;
    }

    protected RecyclerView.LayoutManager getListLayoutManager() {
        return new SuperScrollLayoutManager(activity);
    }

    protected RecyclerView.LayoutManager getGridLayoutManager() {
        final var resources = activity.getResources();
        int width = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width);
        width += (24 * resources.getDisplayMetrics().density);
        return GridLayoutManagerHelper.create(itemsList, width,
                GridLayoutManagerHelper.getPreferredSpanCount(activity),
                infoListAdapter::getSpanSizeLookup);
    }

    /**
     * Updates the item view mode based on user preference.
     */
    private void refreshItemViewMode() {
        final ItemViewMode itemViewMode = getItemViewMode();
        itemsList.setLayoutManager((itemViewMode == ItemViewMode.GRID)
                ? getGridLayoutManager() : getListLayoutManager());
        infoListAdapter.setItemViewMode(itemViewMode);
        infoListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        itemsList = rootView.findViewById(R.id.items_list);
        refreshItemViewMode();

        final Supplier<View> listHeaderSupplier = getListHeaderSupplier();
        if (listHeaderSupplier != null) {
            infoListAdapter.setHeaderSupplier(listHeaderSupplier);
        }

        itemsList.setAdapter(infoListAdapter);
    }

    protected void onItemSelected(final InfoItem selectedItem) {
        if (DEBUG) {
            Log.d(TAG, "onItemSelected() called with: selectedItem = [" + selectedItem + "]");
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        streamSelection = new StreamSelectionController(this, itemsList,
                () -> infoListAdapter.getItemsList().stream()
                        .filter(StreamInfoItem.class::isInstance)
                        .map(StreamInfoItem.class::cast)
                        .collect(java.util.stream.Collectors.toList()),
                infoListAdapter::getStreamAtPosition);
        infoListAdapter.setOnStreamSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final StreamInfoItem selectedItem) {
                onStreamSelected(selectedItem);
            }

            @Override
            public void held(final StreamInfoItem selectedItem) {
                if (!streamSelection.toggleIfActive(selectedItem)) {
                    showInfoItemDialog(selectedItem);
                }
            }
        });

        infoListAdapter.setOnChannelSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final ChannelInfoItem selectedItem) {
                try {
                    onItemSelected(selectedItem);
                    NavigationHelper.openChannelFragment(getFM(), selectedItem.getServiceId(),
                            selectedItem.getUrl(), selectedItem.getName());
                } catch (final Exception e) {
                    ErrorUtil.showUiErrorSnackbar(BaseListFragment.this,
                            "Opening channel fragment", e);
                }
            }

            @Override
            public void held(final ChannelInfoItem selectedItem) {
                showChannelActions(selectedItem);
            }
        });

        infoListAdapter.setOnPlaylistSelectedListener(selectedItem -> {
            try {
                onItemSelected(selectedItem);
                NavigationHelper.openPlaylistFragment(getFM(), selectedItem.getServiceId(),
                        selectedItem.getUrl(), selectedItem.getName());
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening playlist fragment", e);
            }
        });

        infoListAdapter.setOnCommentsSelectedListener(this::onItemSelected);

        // Ensure that there is always a scroll listener (e.g. when rotating the device)
        useNormalItemListScrollListener();
    }

    private void showChannelActions(@NonNull final ChannelInfoItem selectedItem) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (KidsMode.isSubscriptionAllowed(requireContext(), selectedItem.getServiceId())) {
            labels.add(getString(R.string.subscribe_button_title));
            actions.add(() -> ChannelSubscriptionActions.subscribe(requireContext(), selectedItem)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            ignored -> Toast.makeText(requireContext(),
                                    R.string.you_successfully_subscribed,
                                    Toast.LENGTH_SHORT).show(),
                            error -> ErrorUtil.showUiErrorSnackbar(this,
                                    "Subscribing to channel", error)));

            final List<ProfileRecord> otherProfiles =
                    ChannelSubscriptionActions.otherProfiles(requireContext());
            if (!otherProfiles.isEmpty()) {
                labels.add(getString(R.string.channel_subscribe_to_profile));
                actions.add(() -> showSubscribeToProfileDialog(selectedItem, otherProfiles));
            }

            labels.add(getString(R.string.feed_groups_header_title));
            actions.add(() -> showAddToGroupDialog(selectedItem));
        }

        labels.add(getString(R.string.block_channel));
        actions.add(() -> {
            ContentBlockingHelper.blockChannel(requireContext(),
                    selectedItem.getUrl(), selectedItem.getName());
            Toast.makeText(requireContext(), R.string.channel_blocked,
                    Toast.LENGTH_SHORT).show();
        });

        new AlertDialog.Builder(requireContext())
                .setTitle(selectedItem.getName())
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void showAddToGroupDialog(@NonNull final ChannelInfoItem selectedItem) {
        final FeedDatabaseManager feedManager = new FeedDatabaseManager(requireContext());
        feedManager.groups().firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(groups -> {
                    if (groups.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.no_feed_group_created_yet,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final String[] labels = groups.stream()
                            .map(group -> group.getName())
                            .toArray(String[]::new);
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.feed_groups_header_title)
                            .setMultiChoiceItems(labels, null, null)
                            .setPositiveButton(R.string.done, (dialog, which) -> {
                                final AlertDialog groupDialog = (AlertDialog) dialog;
                                final List<Long> selectedGroupIds = new ArrayList<>();
                                for (int index = 0; index < groups.size(); index++) {
                                    if (groupDialog.getListView().isItemChecked(index)) {
                                        selectedGroupIds.add(groups.get(index).getUid());
                                    }
                                }
                                if (selectedGroupIds.isEmpty()) {
                                    return;
                                }
                                ChannelSubscriptionActions.addToGroups(
                                                requireContext(),
                                                selectedItem,
                                                selectedGroupIds)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(
                                                () -> Toast.makeText(
                                                        requireContext(),
                                                        R.string.you_successfully_subscribed,
                                                        Toast.LENGTH_SHORT).show(),
                                                error -> ErrorUtil.showUiErrorSnackbar(
                                                        this,
                                                        "Adding channel to feed group",
                                                        error));
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }, error -> ErrorUtil.showUiErrorSnackbar(
                        this, "Loading feed groups", error));
    }

    private void showSubscribeToProfileDialog(
            @NonNull final ChannelInfoItem selectedItem,
            @NonNull final List<ProfileRecord> profiles) {
        final String[] labels = profiles.stream()
                .map(profile -> ProfileManager.getDisplayName(requireContext(), profile))
                .toArray(String[]::new);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.channel_subscribe_to_profile_title)
                .setItems(labels, (dialog, which) -> {
                    final ProfileRecord profile = profiles.get(which);
                    ChannelSubscriptionActions.subscribeToProfile(
                                    requireContext(), selectedItem, profile)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    ignored -> Toast.makeText(
                                            requireContext(),
                                            getString(R.string.channel_subscribed_to_profile,
                                                    ProfileManager.getDisplayName(
                                                            requireContext(), profile)),
                                            Toast.LENGTH_LONG).show(),
                                    error -> ErrorUtil.showUiErrorSnackbar(
                                            this, "Subscribing channel to profile", error));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Removes all listeners and adds the normal scroll listener to the {@link #itemsList}.
     */
    protected void useNormalItemListScrollListener() {
        if (DEBUG) {
            Log.d(TAG, "useNormalItemListScrollListener called");
        }
        itemsList.clearOnScrollListeners();
        itemsList.addOnScrollListener(new DefaultItemListOnScrolledDownListener());
    }

    /**
     * Removes all listeners and adds the initial scroll listener to the {@link #itemsList}.
     * <br/>
     * Which tries to load more items when not enough are in the view (not scrollable)
     * and more are available.
     * <br/>
     * Note: This method only works because "This callback will also be called if visible
     * item range changes after a layout calculation. In that case, dx and dy will be 0."
     * - which might be unexpected because no actual scrolling occurs...
     * <br/>
     * This listener will be replaced by DefaultItemListOnScrolledDownListener when
     * <ul>
     *     <li>the view was actually scrolled</li>
     *     <li>the view is scrollable</li>
     *     <li>no more items can be loaded</li>
     * </ul>
     */
    protected void useInitialItemListLoadScrollListener() {
        if (DEBUG) {
            Log.d(TAG, "useInitialItemListLoadScrollListener called");
        }
        itemsList.clearOnScrollListeners();
        itemsList.addOnScrollListener(new DefaultItemListOnScrolledDownListener() {
            @Override
            public void onScrolled(@NonNull final RecyclerView recyclerView,
                                   final int dx, final int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy != 0) {
                    log("Vertical scroll occurred");

                    useNormalItemListScrollListener();
                    return;
                }
                if (isLoading.get()) {
                    log("Still loading data -> Skipping");
                    return;
                }
                if (!hasMoreItems()) {
                    log("No more items to load");

                    useNormalItemListScrollListener();
                    return;
                }
                if (itemsList.canScrollVertically(1)
                        || itemsList.canScrollVertically(-1)) {
                    log("View is scrollable");

                    useNormalItemListScrollListener();
                    return;
                }

                log("Loading more data");
                loadMoreItems();
            }

            private void log(final String msg) {
                if (DEBUG) {
                    Log.d(TAG, "initItemListLoadScrollListener - " + msg);
                }
            }
        });
    }

    class DefaultItemListOnScrolledDownListener extends OnScrollBelowItemsListener {
        @Override
        public void onScrolledDown(final RecyclerView recyclerView) {
            onScrollToBottom();
        }
    }

    private void onStreamSelected(final StreamInfoItem selectedItem) {
        if (streamSelection.toggleIfActive(selectedItem)) {
            return;
        }
        onItemSelected(selectedItem);
        final PlayQueue selectionQueue = getPlayQueueForSelection(selectedItem);
        if (shouldPlayOnBackground(selectedItem)) {
            NavigationHelper.playOnBackgroundPlayer(requireContext(),
                    selectionQueue == null ? new SinglePlayQueue(selectedItem) : selectionQueue,
                    true);
            return;
        }
        NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                selectedItem.getServiceId(), selectedItem.getUrl(), selectedItem.getName(),
                selectionQueue, false);
    }

    @Nullable
    protected PlayQueue getPlayQueueForSelection(final StreamInfoItem item) {
        return null;
    }

    protected boolean shouldPlayOnBackground(@NonNull final StreamInfoItem item) {
        return ServiceHelper.isYoutubeMusicMode(requireContext())
                && item.getServiceId() == ServiceList.YouTube.getServiceId();
    }

    protected void onScrollToBottom() {
        if (hasMoreItems() && !isLoading.get()) {
            loadMoreItems();
        }
    }

    protected void showInfoItemDialog(final StreamInfoItem item) {
        try {
            final InfoItemDialog.Builder builder = new InfoItemDialog.Builder(
                    getActivity(), getContext(), this, item);
            if (LocalRecommendations.enabled(requireContext())) {
                builder.addEntry(new StreamDialogEntry(R.string.local_recommendations_exclude,
                        (fragment, selected) -> {
                            LocalRecommendations.exclude(requireContext(), selected);
                            reloadContent();
                        }));
                if (showRecommendationReason()) {
                    builder.addEntry(new StreamDialogEntry(
                            R.string.local_recommendations_why,
                            (fragment, selected) -> new MaterialAlertDialogBuilder(
                                    requireContext())
                                    .setTitle(R.string.local_recommendations_why)
                                    .setMessage(LocalRecommendations.explanation(
                                            requireContext(), selected))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()));
                }
            }
            builder.addEntry(new StreamDialogEntry(R.string.stream_select,
                            (fragment, selected) -> streamSelection.start(selected)))
                    .create().show();
        } catch (final IllegalArgumentException e) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item);
        }
    }

    protected boolean showRecommendationReason() {
        return false;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            supportActionBar.setDisplayHomeAsUpEnabled(!useAsFrontPage);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void startLoading(final boolean forceLoad) {
        if (streamSelection != null) {
            streamSelection.finish();
        }
        useInitialItemListLoadScrollListener();
        super.startLoading(forceLoad);
    }

    private StreamSelectionController streamSelection;

    @Override
    public void onDestroyView() {
        if (streamSelection != null) {
            streamSelection.destroy();
            streamSelection = null;
        }
        super.onDestroyView();
    }

    protected abstract void loadMoreItems();

    protected abstract boolean hasMoreItems();

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        animateHideRecyclerViewAllowingScrolling(itemsList);
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        animate(itemsList, true, 300);
    }

    @Override
    public void showEmptyState() {
        super.showEmptyState();
        showListFooter(false);
        animateHideRecyclerViewAllowingScrolling(itemsList);
    }

    @Override
    public void showListFooter(final boolean show) {
        itemsList.post(() -> {
            if (infoListAdapter != null && itemsList != null) {
                infoListAdapter.showFooter(show);
            }
        });
    }

    @Override
    public void handleNextItems(final N result) {
        isLoading.set(false);
    }

    @Override
    public void handleError() {
        super.handleError();
        showListFooter(false);
        animateHideRecyclerViewAllowingScrolling(itemsList);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (org.schabi.newpipe.util.ThemeHelper.isListLayoutPreference(requireContext(), key)) {
            updateFlags |= LIST_MODE_UPDATE_FLAG;
        } else if (getString(R.string.comment_text_size_key).equals(key)) {
            commentTextSizeUpdatePending = true;
            if (isResumed()) {
                applyPendingCommentTextSizeUpdate();
            }
        } else if (ContentBlockingHelper.shouldReloadForPreference(
                requireContext(), key, getContentBlockingTarget())) {
            reloadContent();
        }
    }

    private void applyPendingCommentTextSizeUpdate() {
        infoListAdapter.notifyDataSetChanged();
        commentTextSizeUpdatePending = false;
    }

    /**
     * Returns preferred item view mode.
     * @return ItemViewMode
     */
    protected ItemViewMode getItemViewMode() {
        return ThemeHelper.getItemViewMode(requireContext());
    }
}
