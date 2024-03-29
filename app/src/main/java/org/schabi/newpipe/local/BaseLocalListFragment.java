package org.schabi.newpipe.local;

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
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PignateFooterBinding;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.list.ListViewContract;
import org.schabi.newpipe.info_list.ItemViewMode;

import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateHideRecyclerViewAllowingScrolling;
import static org.schabi.newpipe.util.ThemeHelper.getItemViewMode;

/**
 * This fragment is design to be used with persistent data such as
 * {@link org.schabi.newpipe.database.LocalItem}, and does not cache the data contained
 * in the list adapter to avoid extra writes when the it exits or re-enters its lifecycle.
 * <p>
 * This fragment destroys its adapter and views when {@link Fragment#onDestroyView()} is
 * called and is memory efficient when in backstack.
 * </p>
 *
 * @param <I> List of {@link org.schabi.newpipe.database.LocalItem}s
 * @param <N> {@link Void}
 */
public abstract class BaseLocalListFragment<I, N> extends BaseStateFragment<I>
        implements ListViewContract<I, N>, SharedPreferences.OnSharedPreferenceChangeListener {

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private static final int LIST_MODE_UPDATE_FLAG = 0x32;
    private ViewBinding headerRootBinding;
    private ViewBinding footerRootBinding;
    protected LocalItemListAdapter itemListAdapter;
    protected RecyclerView itemsList;
    private int updateFlags = 0;

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - Creation
    //////////////////////////////////////////////////////////////////////////*/

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

    /**
     * Updates the item view mode based on user preference.
     */
    private void refreshItemViewMode() {
        final ItemViewMode itemViewMode = getItemViewMode(requireContext());
        itemsList.setLayoutManager((itemViewMode == ItemViewMode.GRID)
                ? getGridLayoutManager() : getListLayoutManager());
        itemListAdapter.setItemViewMode(itemViewMode);
        itemListAdapter.notifyDataSetChanged();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - View
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    protected ViewBinding getListHeader() {
        return null;
    }

    protected ViewBinding getListFooter() {
        return PignateFooterBinding.inflate(activity.getLayoutInflater(), itemsList, false);
    }

    protected RecyclerView.LayoutManager getGridLayoutManager() {
        final Resources resources = activity.getResources();
        int width = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width);
        width += (24 * resources.getDisplayMetrics().density);
        final int spanCount = Math.floorDiv(resources.getDisplayMetrics().widthPixels, width);
        final GridLayoutManager lm = new GridLayoutManager(activity, spanCount);
        lm.setSpanSizeLookup(itemListAdapter.getSpanSizeLookup(spanCount));
        return lm;
    }

    protected RecyclerView.LayoutManager getListLayoutManager() {
        return new LinearLayoutManager(activity);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        itemListAdapter = new LocalItemListAdapter(activity);

        itemsList = rootView.findViewById(R.id.items_list);
        refreshItemViewMode();

        headerRootBinding = getListHeader();
        if (headerRootBinding != null) {
            itemListAdapter.setHeader(headerRootBinding.getRoot());
        }
        footerRootBinding = getListFooter();
        itemListAdapter.setFooter(footerRootBinding.getRoot());

        itemsList.setAdapter(itemListAdapter);
    }

    @Override
    protected void initListeners() {
        super.initListeners();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar == null) {
            return;
        }

        supportActionBar.setDisplayShowTitleEnabled(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - Destruction
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        itemsList = null;
        itemListAdapter = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);
        resetFragment();
    }

    @Override
    public void showLoading() {
        super.showLoading();
        if (itemsList != null) {
            animateHideRecyclerViewAllowingScrolling(itemsList);
        }
        if (headerRootBinding != null) {
            animate(headerRootBinding.getRoot(), false, 200);
        }
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        if (itemsList != null) {
            animate(itemsList, true, 200);
        }
        if (headerRootBinding != null) {
            animate(headerRootBinding.getRoot(), true, 200);
        }
    }

    @Override
    public void showEmptyState() {
        super.showEmptyState();
        showListFooter(false);
    }

    @Override
    public void showListFooter(final boolean show) {
        if (itemsList == null) {
            return;
        }
        itemsList.post(() -> {
            if (itemListAdapter != null) {
                itemListAdapter.showFooter(show);
            }
        });
    }

    @Override
    public void handleNextItems(final N result) {
        isLoading.set(false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    //////////////////////////////////////////////////////////////////////////*/

    protected void resetFragment() {
        if (itemListAdapter != null) {
            itemListAdapter.clearStreamItemList();
        }
    }

    @Override
    public void handleError() {
        super.handleError();
        resetFragment();

        showListFooter(false);

        if (itemsList != null) {
            animateHideRecyclerViewAllowingScrolling(itemsList);
        }
        if (headerRootBinding != null) {
            animate(headerRootBinding.getRoot(), false, 200);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (getString(R.string.list_view_mode_key).equals(key)) {
            updateFlags |= LIST_MODE_UPDATE_FLAG;
        }
    }
}
