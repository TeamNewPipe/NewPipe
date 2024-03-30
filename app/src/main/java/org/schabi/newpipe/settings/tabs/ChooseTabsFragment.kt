package org.schabi.newpipe.settings.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.settings.SelectChannelFragment
import org.schabi.newpipe.settings.SelectKioskFragment
import org.schabi.newpipe.settings.SelectPlaylistFragment
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem
import org.schabi.newpipe.settings.tabs.Tab.ChannelTab
import org.schabi.newpipe.settings.tabs.Tab.KioskTab
import org.schabi.newpipe.settings.tabs.Tab.PlaylistTab
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class ChooseTabsFragment() : Fragment() {
    private var tabsManager: TabsManager? = null
    private val tabList: MutableList<Tab?> = ArrayList()
    private var selectedTabsAdapter: SelectedTabsAdapter? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabsManager = TabsManager.Companion.getManager(requireContext())
        updateTabList()
        setHasOptionsMenu(true)
    }

    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_choose_tabs, container, false)
    }

    public override fun onViewCreated(rootView: View,
                                      savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        initButton(rootView)
        val listSelectedTabs: RecyclerView = rootView.findViewById(R.id.selectedTabs)
        listSelectedTabs.setLayoutManager(LinearLayoutManager(requireContext()))
        val itemTouchHelper: ItemTouchHelper = ItemTouchHelper(getItemTouchCallback())
        itemTouchHelper.attachToRecyclerView(listSelectedTabs)
        selectedTabsAdapter = SelectedTabsAdapter(requireContext(), itemTouchHelper)
        listSelectedTabs.setAdapter(selectedTabsAdapter)
    }

    public override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(getActivity(),
                getString(R.string.main_page_content))
    }

    public override fun onPause() {
        super.onPause()
        saveChanges()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_chooser_fragment, menu)
        menu.findItem(R.id.menu_item_restore_default).setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener({ item: MenuItem? ->
            restoreDefaults()
            true
        }))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun updateTabList() {
        tabList.clear()
        tabList.addAll((tabsManager!!.getTabs())!!)
    }

    private fun saveChanges() {
        tabsManager!!.saveTabs(tabList)
    }

    private fun restoreDefaults() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
                    tabsManager!!.resetTabs()
                    updateTabList()
                    selectedTabsAdapter!!.notifyDataSetChanged()
                }))
                .show()
    }

    private fun initButton(rootView: View) {
        val fab: FloatingActionButton = rootView.findViewById(R.id.addTabsButton)
        fab.setOnClickListener(View.OnClickListener({ v: View? ->
            val availableTabs: Array<ChooseTabListItem> = getAvailableTabs(requireContext())
            if (availableTabs.size == 0) {
                //Toast.makeText(requireContext(), "No available tabs", Toast.LENGTH_SHORT).show();
                return@setOnClickListener
            }
            val actionListener: DialogInterface.OnClickListener = DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
                val selected: ChooseTabListItem = availableTabs.get(which)
                addTab(selected.tabId)
            })
            AddTabDialog(requireContext(), availableTabs, actionListener)
                    .show()
        }))
    }

    private fun addTab(tab: Tab?) {
        tabList.add(tab)
        selectedTabsAdapter!!.notifyDataSetChanged()
    }

    private fun addTab(tabId: Int) {
        val type: Tab.Type? = Tab.Companion.typeFrom(tabId)
        if (type == null) {
            showSnackbar(this,
                    ErrorInfo(IllegalStateException("Tab id not found: " + tabId),
                            UserAction.SOMETHING_ELSE, "Choosing tabs on settings"))
            return
        }
        when (type) {
            Tab.Type.KIOSK -> {
                val selectKioskFragment: SelectKioskFragment = SelectKioskFragment()
                selectKioskFragment.setOnSelectedListener(SelectKioskFragment.OnSelectedListener({ serviceId: Int, kioskId: String?, kioskName: String? -> addTab(KioskTab(serviceId, kioskId)) }))
                selectKioskFragment.show(getParentFragmentManager(), "select_kiosk")
                return
            }

            Tab.Type.CHANNEL -> {
                val selectChannelFragment: SelectChannelFragment = SelectChannelFragment()
                selectChannelFragment.setOnSelectedListener(SelectChannelFragment.OnSelectedListener({ serviceId: Int, url: String?, name: String? -> addTab(ChannelTab(serviceId, url, name)) }))
                selectChannelFragment.show(getParentFragmentManager(), "select_channel")
                return
            }

            Tab.Type.PLAYLIST -> {
                val selectPlaylistFragment: SelectPlaylistFragment = SelectPlaylistFragment()
                selectPlaylistFragment.setOnSelectedListener(
                        object : SelectPlaylistFragment.OnSelectedListener {
                            public override fun onLocalPlaylistSelected(id: Long, name: String?) {
                                addTab(PlaylistTab(id, name))
                            }

                            public override fun onRemotePlaylistSelected(
                                    serviceId: Int, url: String?, name: String?) {
                                addTab(PlaylistTab(serviceId, url, name))
                            }
                        })
                selectPlaylistFragment.show(getParentFragmentManager(), "select_playlist")
                return
            }

            else -> addTab(type.getTab())
        }
    }

    private fun getAvailableTabs(context: Context): Array<ChooseTabListItem> {
        val returnList: ArrayList<ChooseTabListItem> = ArrayList()
        for (type: Tab.Type in Tab.Type.entries) {
            val tab: Tab? = type.getTab()
            when (type) {
                Tab.Type.BLANK -> if (!tabList.contains(tab)) {
                    returnList.add(ChooseTabListItem(tab!!.getTabId(),
                            getString(R.string.blank_page_summary),
                            tab.getTabIconRes(context)))
                }

                Tab.Type.KIOSK -> returnList.add(ChooseTabListItem(tab!!.getTabId(),
                        getString(R.string.kiosk_page_summary),
                        R.drawable.ic_whatshot))

                Tab.Type.CHANNEL -> returnList.add(ChooseTabListItem(tab!!.getTabId(),
                        getString(R.string.channel_page_summary),
                        tab.getTabIconRes(context)))

                Tab.Type.DEFAULT_KIOSK -> if (!tabList.contains(tab)) {
                    returnList.add(ChooseTabListItem(tab!!.getTabId(),
                            getString(R.string.default_kiosk_page_summary),
                            R.drawable.ic_whatshot))
                }

                Tab.Type.PLAYLIST -> returnList.add(ChooseTabListItem(tab!!.getTabId(),
                        getString(R.string.playlist_page_summary),
                        tab.getTabIconRes(context)))

                else -> if (!tabList.contains(tab)) {
                    returnList.add(ChooseTabListItem(context, tab))
                }
            }
        }
        return returnList.toTypedArray<ChooseTabListItem>()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    ////////////////////////////////////////////////////////////////////////// */
    private fun getItemTouchCallback(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.START or ItemTouchHelper.END) {
            public override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView,
                                                             viewSize: Int,
                                                             viewSizeOutOfBounds: Int,
                                                             totalSize: Int,
                                                             msSinceStartScroll: Long): Int {
                val standardSpeed: Int = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll)
                val minimumAbsVelocity: Int = max(12.0, abs(standardSpeed.toDouble())).toInt()
                return minimumAbsVelocity * sign(viewSizeOutOfBounds.toDouble()).toInt()
            }

            public override fun onMove(recyclerView: RecyclerView,
                                       source: RecyclerView.ViewHolder,
                                       target: RecyclerView.ViewHolder): Boolean {
                if ((source.getItemViewType() != target.getItemViewType()
                                || selectedTabsAdapter == null)) {
                    return false
                }
                val sourceIndex: Int = source.getBindingAdapterPosition()
                val targetIndex: Int = target.getBindingAdapterPosition()
                selectedTabsAdapter!!.swapItems(sourceIndex, targetIndex)
                return true
            }

            public override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            public override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }

            public override fun onSwiped(viewHolder: RecyclerView.ViewHolder,
                                         swipeDir: Int) {
                val position: Int = viewHolder.getBindingAdapterPosition()
                tabList.removeAt(position)
                selectedTabsAdapter!!.notifyItemRemoved(position)
                if (tabList.isEmpty()) {
                    tabList.add(Tab.Type.BLANK.getTab())
                    selectedTabsAdapter!!.notifyItemInserted(0)
                }
            }
        }
    }

    private inner class SelectedTabsAdapter internal constructor(context: Context?, private val itemTouchHelper: ItemTouchHelper?) : RecyclerView.Adapter<SelectedTabsAdapter.TabViewHolder>() {
        private val inflater: LayoutInflater

        init {
            inflater = LayoutInflater.from(context)
        }

        fun swapItems(fromPosition: Int, toPosition: Int) {
            Collections.swap(tabList, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }

        public override fun onCreateViewHolder(
                parent: ViewGroup, viewType: Int): TabViewHolder {
            val view: View = inflater.inflate(R.layout.list_choose_tabs, parent, false)
            return TabViewHolder(view)
        }

        public override fun onBindViewHolder(
                holder: TabViewHolder,
                position: Int) {
            holder.bind(position, holder)
        }

        public override fun getItemCount(): Int {
            return tabList.size
        }

        internal inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tabIconView: AppCompatImageView
            private val tabNameView: TextView
            private val handle: ImageView

            init {
                tabNameView = itemView.findViewById(R.id.tabName)
                tabIconView = itemView.findViewById(R.id.tabIcon)
                handle = itemView.findViewById(R.id.handle)
            }

            @SuppressLint("ClickableViewAccessibility")
            fun bind(position: Int, holder: TabViewHolder) {
                handle.setOnTouchListener(getOnTouchListener(holder))
                val tab: Tab = (tabList.get(position))!!
                val type: Tab.Type? = Tab.Companion.typeFrom(tab.getTabId())
                if (type == null) {
                    return
                }
                tabNameView.setText(getTabName(type, tab))
                tabIconView.setImageResource(tab.getTabIconRes(requireContext()))
            }

            private fun getTabName(type: Tab.Type, tab: Tab): String? {
                when (type) {
                    Tab.Type.BLANK -> return getString(R.string.blank_page_summary)
                    Tab.Type.DEFAULT_KIOSK -> return getString(R.string.default_kiosk_page_summary)
                    Tab.Type.KIOSK -> return (ServiceHelper.getNameOfServiceById((tab as KioskTab).getKioskServiceId())
                            + "/" + tab.getTabName(requireContext()))

                    Tab.Type.CHANNEL -> return (ServiceHelper.getNameOfServiceById((tab as ChannelTab).getChannelServiceId())
                            + "/" + tab.getTabName(requireContext()))

                    Tab.Type.PLAYLIST -> {
                        val serviceId: Int = (tab as PlaylistTab).getPlaylistServiceId()
                        val serviceName: String = if (serviceId == -1) getString(R.string.local) else ServiceHelper.getNameOfServiceById(serviceId)
                        return serviceName + "/" + tab.getTabName(requireContext())
                    }

                    else -> return tab.getTabName(requireContext())
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            private fun getOnTouchListener(item: RecyclerView.ViewHolder): OnTouchListener {
                return OnTouchListener({ view: View?, motionEvent: MotionEvent ->
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && getItemCount() > 1) {
                            itemTouchHelper.startDrag(item)
                            return@OnTouchListener true
                        }
                    }
                    false
                })
            }
        }
    }
}
