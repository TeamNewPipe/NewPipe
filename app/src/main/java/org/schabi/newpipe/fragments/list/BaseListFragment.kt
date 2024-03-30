package org.schabi.newpipe.fragments.list

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener
import org.schabi.newpipe.info_list.InfoListAdapter
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateHideRecyclerViewAllowingScrolling
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.StateSaver.WriteRead
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.SuperScrollLayoutManager
import java.util.Queue
import java.util.function.Supplier

abstract class BaseListFragment<I, N>() : BaseStateFragment<I>(), ListViewContract<I, N>, WriteRead, OnSharedPreferenceChangeListener {
    protected var savedState: org.schabi.newpipe.util.SavedState? = null
    private var useDefaultStateSaving: Boolean = true
    private var updateFlags: Int = 0

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    protected var infoListAdapter: InfoListAdapter? = null
    protected var itemsList: RecyclerView? = null
    private var focusedPosition: Int = -1

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onAttach(context: Context) {
        super.onAttach(context)
        if (infoListAdapter == null) {
            infoListAdapter = InfoListAdapter((activity)!!)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        PreferenceManager.getDefaultSharedPreferences((activity)!!)
                .registerOnSharedPreferenceChangeListener(this)
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (useDefaultStateSaving) {
            StateSaver.onDestroy(savedState)
        }
        PreferenceManager.getDefaultSharedPreferences((activity)!!)
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    public override fun onResume() {
        super.onResume()
        if (updateFlags != 0) {
            if ((updateFlags and LIST_MODE_UPDATE_FLAG) != 0) {
                refreshItemViewMode()
            }
            updateFlags = 0
        }
    }
    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * If the default implementation of [StateSaver.WriteRead] should be used.
     *
     * @param useDefaultStateSaving Whether the default implementation should be used
     * @see StateSaver
     */
    fun setUseDefaultStateSaving(useDefaultStateSaving: Boolean) {
        this.useDefaultStateSaving = useDefaultStateSaving
    }

    public override fun generateSuffix(): String? {
        // Naive solution, but it's good for now (the items don't change)
        return "." + infoListAdapter!!.getItemsList().size + ".list"
    }

    private fun getFocusedPosition(): Int {
        try {
            val focusedItem: View = itemsList!!.getFocusedChild()
            val itemHolder: RecyclerView.ViewHolder? = itemsList!!.findContainingViewHolder(focusedItem)
            return itemHolder!!.getBindingAdapterPosition()
        } catch (e: NullPointerException) {
            return -1
        }
    }

    public override fun writeTo(objectsToSave: Queue<Any?>) {
        if (!useDefaultStateSaving) {
            return
        }
        objectsToSave.add(infoListAdapter!!.getItemsList())
        objectsToSave.add(getFocusedPosition())
    }

    @Throws(Exception::class)
    public override fun readFrom(savedObjects: Queue<Any>) {
        if (!useDefaultStateSaving) {
            return
        }
        infoListAdapter!!.getItemsList().clear()
        infoListAdapter!!.getItemsList().addAll((savedObjects.poll() as List<InfoItem?>?)!!)
        restoreFocus(savedObjects.poll() as Int?)
    }

    private fun restoreFocus(position: Int?) {
        if (position == null || position < 0) {
            return
        }
        itemsList!!.post(Runnable({
            val focusedHolder: RecyclerView.ViewHolder? = itemsList!!.findViewHolderForAdapterPosition(position)
            if (focusedHolder != null) {
                focusedHolder.itemView.requestFocus()
            }
        }))
    }

    public override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        if (useDefaultStateSaving) {
            savedState = StateSaver.tryToSave(activity!!.isChangingConfigurations(), savedState, bundle, this)
        }
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        super.onRestoreInstanceState(bundle)
        if (useDefaultStateSaving) {
            savedState = StateSaver.tryToRestore(bundle, this)
        }
    }

    public override fun onStop() {
        focusedPosition = getFocusedPosition()
        super.onStop()
    }

    public override fun onStart() {
        super.onStart()
        restoreFocus(focusedPosition)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    protected open fun getListHeaderSupplier(): Supplier<View>? {
        return null
    }

    protected fun getListLayoutManager(): RecyclerView.LayoutManager {
        return SuperScrollLayoutManager(activity)
    }

    protected fun getGridLayoutManager(): RecyclerView.LayoutManager {
        val resources: Resources = activity!!.getResources()
        var width: Int = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
        width = (width + (24 * resources.getDisplayMetrics().density)).toInt()
        val spanCount: Int = Math.floorDiv(resources.getDisplayMetrics().widthPixels, width)
        val lm: GridLayoutManager = GridLayoutManager(activity, spanCount)
        lm.setSpanSizeLookup(infoListAdapter!!.getSpanSizeLookup(spanCount))
        return lm
    }

    /**
     * Updates the item view mode based on user preference.
     */
    private fun refreshItemViewMode() {
        val itemViewMode: ItemViewMode? = getItemViewMode()
        itemsList!!.setLayoutManager(if ((itemViewMode == ItemViewMode.GRID)) getGridLayoutManager() else getListLayoutManager())
        infoListAdapter!!.setItemViewMode(itemViewMode)
        infoListAdapter!!.notifyDataSetChanged()
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        itemsList = rootView.findViewById(R.id.items_list)
        refreshItemViewMode()
        val listHeaderSupplier: Supplier<View>? = getListHeaderSupplier()
        if (listHeaderSupplier != null) {
            infoListAdapter!!.setHeaderSupplier(listHeaderSupplier)
        }
        itemsList.setAdapter(infoListAdapter)
    }

    protected open fun onItemSelected(selectedItem: InfoItem) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "onItemSelected() called with: selectedItem = [" + selectedItem + "]")
        }
    }

    override fun initListeners() {
        super.initListeners()
        infoListAdapter!!.setOnStreamSelectedListener(object : OnClickGesture<StreamInfoItem> {
            public override fun selected(selectedItem: StreamInfoItem) {
                onStreamSelected(selectedItem)
            }

            public override fun held(selectedItem: StreamInfoItem) {
                showInfoItemDialog(selectedItem)
            }
        })
        infoListAdapter!!.setOnChannelSelectedListener(OnClickGesture({ selectedItem: ChannelInfoItem ->
            try {
                onItemSelected(selectedItem)
                NavigationHelper.openChannelFragment(getFM(), selectedItem.getServiceId(),
                        selectedItem.getUrl(), selectedItem.getName())
            } catch (e: Exception) {
                showUiErrorSnackbar(this, "Opening channel fragment", e)
            }
        }))
        infoListAdapter!!.setOnPlaylistSelectedListener(OnClickGesture({ selectedItem: PlaylistInfoItem ->
            try {
                onItemSelected(selectedItem)
                NavigationHelper.openPlaylistFragment(getFM(), selectedItem.getServiceId(),
                        selectedItem.getUrl(), selectedItem.getName())
            } catch (e: Exception) {
                showUiErrorSnackbar(this, "Opening playlist fragment", e)
            }
        }))
        infoListAdapter!!.setOnCommentsSelectedListener(OnClickGesture<CommentsInfoItem>({ selectedItem: T -> onItemSelected(selectedItem) }))

        // Ensure that there is always a scroll listener (e.g. when rotating the device)
        useNormalItemListScrollListener()
    }

    /**
     * Removes all listeners and adds the normal scroll listener to the [.itemsList].
     */
    protected fun useNormalItemListScrollListener() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "useNormalItemListScrollListener called")
        }
        itemsList!!.clearOnScrollListeners()
        itemsList!!.addOnScrollListener(DefaultItemListOnScrolledDownListener())
    }

    /**
     * Removes all listeners and adds the initial scroll listener to the [.itemsList].
     * <br></br>
     * Which tries to load more items when not enough are in the view (not scrollable)
     * and more are available.
     * <br></br>
     * Note: This method only works because "This callback will also be called if visible
     * item range changes after a layout calculation. In that case, dx and dy will be 0."
     * - which might be unexpected because no actual scrolling occurs...
     * <br></br>
     * This listener will be replaced by DefaultItemListOnScrolledDownListener when
     *
     *  * the view was actually scrolled
     *  * the view is scrollable
     *  * no more items can be loaded
     *
     */
    protected fun useInitialItemListLoadScrollListener() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "useInitialItemListLoadScrollListener called")
        }
        itemsList!!.clearOnScrollListeners()
        itemsList!!.addOnScrollListener(object : DefaultItemListOnScrolledDownListener() {
            public override fun onScrolled(recyclerView: RecyclerView,
                                           dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    log("Vertical scroll occurred")
                    useNormalItemListScrollListener()
                    return
                }
                if (isLoading.get()) {
                    log("Still loading data -> Skipping")
                    return
                }
                if (!hasMoreItems()) {
                    log("No more items to load")
                    useNormalItemListScrollListener()
                    return
                }
                if ((itemsList!!.canScrollVertically(1)
                                || itemsList!!.canScrollVertically(-1))) {
                    log("View is scrollable")
                    useNormalItemListScrollListener()
                    return
                }
                log("Loading more data")
                loadMoreItems()
            }

            private fun log(msg: String) {
                if (BaseFragment.Companion.DEBUG) {
                    Log.d(TAG, "initItemListLoadScrollListener - " + msg)
                }
            }
        })
    }

    internal open inner class DefaultItemListOnScrolledDownListener() : OnScrollBelowItemsListener() {
        public override fun onScrolledDown(recyclerView: RecyclerView?) {
            onScrollToBottom()
        }
    }

    private fun onStreamSelected(selectedItem: StreamInfoItem) {
        onItemSelected(selectedItem)
        NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                selectedItem.getServiceId(), selectedItem.getUrl(), selectedItem.getName(),
                null, false)
    }

    protected fun onScrollToBottom() {
        if (hasMoreItems() && !isLoading.get()) {
            loadMoreItems()
        }
    }

    protected open fun showInfoItemDialog(item: StreamInfoItem) {
        try {
            InfoItemDialog.Builder((getActivity())!!, (getContext())!!, this, item).create().show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.Companion.reportErrorDuringInitialization(e, item)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]"))
        }
        super.onCreateOptionsMenu(menu, inflater)
        val supportActionBar: ActionBar? = activity!!.getSupportActionBar()
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true)
            supportActionBar.setDisplayHomeAsUpEnabled(!useAsFrontPage)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    ////////////////////////////////////////////////////////////////////////// */
    override fun startLoading(forceLoad: Boolean) {
        useInitialItemListLoadScrollListener()
        super.startLoading(forceLoad)
    }

    protected abstract fun loadMoreItems()
    protected abstract fun hasMoreItems(): Boolean

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun showLoading() {
        super.showLoading()
        itemsList!!.animateHideRecyclerViewAllowingScrolling()
    }

    public override fun hideLoading() {
        super.hideLoading()
        itemsList!!.animate(true, 300)
    }

    public override fun showEmptyState() {
        super.showEmptyState()
        showListFooter(false)
        itemsList!!.animateHideRecyclerViewAllowingScrolling()
    }

    public override fun showListFooter(show: Boolean) {
        itemsList!!.post(Runnable({
            if (infoListAdapter != null && itemsList != null) {
                infoListAdapter!!.showFooter(show)
            }
        }))
    }

    public override fun handleNextItems(result: N) {
        isLoading.set(false)
    }

    public override fun handleError() {
        super.handleError()
        showListFooter(false)
        itemsList!!.animateHideRecyclerViewAllowingScrolling()
    }

    public override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                                  key: String?) {
        if ((getString(R.string.list_view_mode_key) == key)) {
            updateFlags = updateFlags or LIST_MODE_UPDATE_FLAG
        }
    }

    /**
     * Returns preferred item view mode.
     * @return ItemViewMode
     */
    protected open fun getItemViewMode(): ItemViewMode? {
        return ThemeHelper.getItemViewMode(requireContext())
    }

    companion object {
        private val LIST_MODE_UPDATE_FLAG: Int = 0x32
    }
}
