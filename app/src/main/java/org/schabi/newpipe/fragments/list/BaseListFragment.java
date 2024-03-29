package org.schabi.newpipe.fragments.list;

import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateHideRecyclerViewAllowingScrolling;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.SuperScrollLayoutManager;

import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

public abstract class BaseListFragment<I, N> extends BaseStateFragment<I>
        implements ListViewContract<I, N>, StateSaver.WriteRead,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int LIST_MODE_UPDATE_FLAG = 0x32;
    protected org.schabi.newpipe.util.SavedState savedState;

    private boolean useDefaultStateSaving = true;
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
            infoListAdapter = new InfoListAdapter(activity);
        }
    }

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
    public void onResume() {
        super.onResume();

        if (updateFlags != 0) {
            if ((updateFlags & LIST_MODE_UPDATE_FLAG) != 0) {
                refreshItemViewMode();
            }
            updateFlags = 0;
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
        final Resources resources = activity.getResources();
        int width = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width);
        width += (24 * resources.getDisplayMetrics().density);
        final int spanCount = Math.floorDiv(resources.getDisplayMetrics().widthPixels, width);
        final GridLayoutManager lm = new GridLayoutManager(activity, spanCount);
        lm.setSpanSizeLookup(infoListAdapter.getSpanSizeLookup(spanCount));
        return lm;
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
        infoListAdapter.setOnStreamSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final StreamInfoItem selectedItem) {
                onStreamSelected(selectedItem);
            }

            @Override
            public void held(final StreamInfoItem selectedItem) {
                showInfoItemDialog(selectedItem);
            }
        });

        infoListAdapter.setOnChannelSelectedListener(selectedItem -> {
            try {
                onItemSelected(selectedItem);
                NavigationHelper.openChannelFragment(getFM(), selectedItem.getServiceId(),
                        selectedItem.getUrl(), selectedItem.getName());
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e);
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
        onItemSelected(selectedItem);
        NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                selectedItem.getServiceId(), selectedItem.getUrl(), selectedItem.getName(),
                null, false);
    }

    protected void onScrollToBottom() {
        if (hasMoreItems() && !isLoading.get()) {
            loadMoreItems();
        }
    }

    protected void showInfoItemDialog(final StreamInfoItem item) {
        try {
            new InfoItemDialog.Builder(getActivity(), getContext(), this, item).create().show();
        } catch (final IllegalArgumentException e) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item);
        }
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
        useInitialItemListLoadScrollListener();
        super.startLoading(forceLoad);
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
        if (getString(R.string.list_view_mode_key).equals(key)) {
            updateFlags |= LIST_MODE_UPDATE_FLAG;
        }
    }

    /**
     * Returns preferred item view mode.
     * @return ItemViewMode
     */
    protected ItemViewMode getItemViewMode() {
        return ThemeHelper.getItemViewMode(requireContext());
    }
}
