package org.schabi.newpipe.local

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PignateFooterBinding
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.list.ListViewContract
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateHideRecyclerViewAllowingScrolling
import org.schabi.newpipe.util.ThemeHelper

/**
 * This fragment is design to be used with persistent data such as
 * [org.schabi.newpipe.database.LocalItem], and does not cache the data contained
 * in the list adapter to avoid extra writes when the it exits or re-enters its lifecycle.
 *
 *
 * This fragment destroys its adapter and views when [Fragment.onDestroyView] is
 * called and is memory efficient when in backstack.
 *
 *
 * @param <I> List of [org.schabi.newpipe.database.LocalItem]s
 * @param <N> [Void]
</N></I> */
abstract class BaseLocalListFragment<I, N>() : BaseStateFragment<I>(), ListViewContract<I, N>, OnSharedPreferenceChangeListener {
    private var headerRootBinding: ViewBinding? = null
    private var footerRootBinding: ViewBinding? = null
    protected var itemListAdapter: LocalItemListAdapter? = null
    protected var itemsList: RecyclerView? = null
    private var updateFlags: Int = 0

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - Creation
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        PreferenceManager.getDefaultSharedPreferences((activity)!!)
                .registerOnSharedPreferenceChangeListener(this)
    }

    public override fun onDestroy() {
        super.onDestroy()
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

    /**
     * Updates the item view mode based on user preference.
     */
    private fun refreshItemViewMode() {
        val itemViewMode: ItemViewMode? = ThemeHelper.getItemViewMode(requireContext())
        itemsList!!.setLayoutManager(if ((itemViewMode == ItemViewMode.GRID)) gridLayoutManager else listLayoutManager)
        itemListAdapter!!.setItemViewMode(itemViewMode)
        itemListAdapter!!.notifyDataSetChanged()
    }

    protected open val listHeader: ViewBinding?
        /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - View
    ////////////////////////////////////////////////////////////////////////// */protected get() {
            return null
        }
    protected val listFooter: ViewBinding
        protected get() {
            return PignateFooterBinding.inflate(activity!!.getLayoutInflater(), itemsList, false)
        }
    protected val gridLayoutManager: RecyclerView.LayoutManager
        protected get() {
            val resources: Resources = activity!!.getResources()
            var width: Int = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
            width = (width + (24 * resources.getDisplayMetrics().density)).toInt()
            val spanCount: Int = Math.floorDiv(resources.getDisplayMetrics().widthPixels, width)
            val lm: GridLayoutManager = GridLayoutManager(activity, spanCount)
            lm.setSpanSizeLookup(itemListAdapter!!.getSpanSizeLookup(spanCount))
            return lm
        }
    protected val listLayoutManager: RecyclerView.LayoutManager
        protected get() {
            return LinearLayoutManager(activity)
        }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        itemListAdapter = LocalItemListAdapter(activity)
        itemsList = rootView.findViewById(R.id.items_list)
        refreshItemViewMode()
        headerRootBinding = listHeader
        if (headerRootBinding != null) {
            itemListAdapter!!.setHeader(headerRootBinding!!.getRoot())
        }
        footerRootBinding = listFooter
        itemListAdapter!!.setFooter(footerRootBinding!!.getRoot())
        itemsList.setAdapter(itemListAdapter)
    }

    override fun initListeners() {
        super.initListeners()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]"))
        }
        val supportActionBar: ActionBar? = activity!!.getSupportActionBar()
        if (supportActionBar == null) {
            return
        }
        supportActionBar.setDisplayShowTitleEnabled(true)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle - Destruction
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onDestroyView() {
        super.onDestroyView()
        itemsList = null
        itemListAdapter = null
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        resetFragment()
    }

    public override fun showLoading() {
        super.showLoading()
        if (itemsList != null) {
            itemsList!!.animateHideRecyclerViewAllowingScrolling()
        }
        if (headerRootBinding != null) {
            headerRootBinding!!.getRoot().animate(false, 200)
        }
    }

    public override fun hideLoading() {
        super.hideLoading()
        if (itemsList != null) {
            itemsList!!.animate(true, 200)
        }
        if (headerRootBinding != null) {
            headerRootBinding!!.getRoot().animate(true, 200)
        }
    }

    public override fun showEmptyState() {
        super.showEmptyState()
        showListFooter(false)
    }

    public override fun showListFooter(show: Boolean) {
        if (itemsList == null) {
            return
        }
        itemsList!!.post(Runnable({
            if (itemListAdapter != null) {
                itemListAdapter!!.showFooter(show)
            }
        }))
    }

    public override fun handleNextItems(result: N) {
        isLoading.set(false)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    ////////////////////////////////////////////////////////////////////////// */
    protected open fun resetFragment() {
        if (itemListAdapter != null) {
            itemListAdapter!!.clearStreamItemList()
        }
    }

    public override fun handleError() {
        super.handleError()
        resetFragment()
        showListFooter(false)
        if (itemsList != null) {
            itemsList!!.animateHideRecyclerViewAllowingScrolling()
        }
        if (headerRootBinding != null) {
            headerRootBinding!!.getRoot().animate(false, 200)
        }
    }

    public override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                                  key: String?) {
        if ((getString(R.string.list_view_mode_key) == key)) {
            updateFlags = updateFlags or LIST_MODE_UPDATE_FLAG
        }
    }

    companion object {
        /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
        private val LIST_MODE_UPDATE_FLAG: Int = 0x32
    }
}
