package org.schabi.newpipe.fragments

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentMainBinding
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.settings.tabs.TabsManager.SavedTabsChangeListener
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.ScrollableTabLayout
import java.util.function.Consumer

class MainFragment() : BaseFragment(), OnTabSelectedListener {
    private var binding: FragmentMainBinding? = null
    private var pagerAdapter: SelectedTabsPagerAdapter? = null
    private val tabsList: MutableList<Tab?> = ArrayList()
    private var tabsManager: TabsManager? = null
    private var hasTabsChanged: Boolean = false
    private var prefs: SharedPreferences? = null
    private var youtubeRestrictedModeEnabled: Boolean = false
    private var youtubeRestrictedModeEnabledKey: String? = null
    private var mainTabsPositionBottom: Boolean = false
    private var mainTabsPositionKey: String? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        tabsManager = TabsManager.Companion.getManager((activity)!!)
        tabsManager!!.setSavedTabsListener(SavedTabsChangeListener({
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, ("TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed()))
            }
            if (isResumed()) {
                setupTabs()
            } else {
                hasTabsChanged = true
            }
        }))
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled)
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false)
        mainTabsPositionKey = getString(R.string.main_tabs_position_key)
        mainTabsPositionBottom = prefs.getBoolean(mainTabsPositionKey, false)
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        binding = FragmentMainBinding.bind(rootView)
        binding!!.mainTabLayout.setupWithViewPager(binding!!.pager)
        binding!!.mainTabLayout.addOnTabSelectedListener(this)
        setupTabs()
        updateTabLayoutPosition()
    }

    public override fun onResume() {
        super.onResume()
        val newYoutubeRestrictedModeEnabled: Boolean = prefs!!.getBoolean(youtubeRestrictedModeEnabledKey, false)
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled
            setupTabs()
        }
        val newMainTabsPosition: Boolean = prefs!!.getBoolean(mainTabsPositionKey, false)
        if (mainTabsPositionBottom != newMainTabsPosition) {
            mainTabsPositionBottom = newMainTabsPosition
            updateTabLayoutPosition()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        tabsManager!!.unsetSavedTabsListener()
        if (binding != null) {
            binding!!.pager.setAdapter(null)
            binding = null
        }
    }

    public override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]"))
        }
        inflater.inflate(R.menu.menu_main_fragment, menu)
        val supportActionBar: ActionBar? = activity!!.getSupportActionBar()
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false)
        }
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.action_search) {
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId((activity)!!), "")
            } catch (e: Exception) {
                showUiErrorSnackbar(this, "Opening search fragment", e)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    ////////////////////////////////////////////////////////////////////////// */
    private fun setupTabs() {
        tabsList.clear()
        tabsList.addAll((tabsManager!!.getTabs())!!)
        if (pagerAdapter == null || !pagerAdapter!!.sameTabs(tabsList)) {
            pagerAdapter = SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList)
        }
        binding!!.pager.setAdapter(null)
        binding!!.pager.setAdapter(pagerAdapter)
        updateTabsIconAndDescription()
        updateTitleForTab(binding!!.pager.getCurrentItem())
        hasTabsChanged = false
    }

    private fun updateTabsIconAndDescription() {
        for (i in tabsList.indices) {
            val tabToSet: TabLayout.Tab? = binding!!.mainTabLayout.getTabAt(i)
            if (tabToSet != null) {
                val tab: Tab? = tabsList.get(i)
                tabToSet.setIcon(tab!!.getTabIconRes(requireContext()))
                tabToSet.setContentDescription(tab.getTabName(requireContext()))
            }
        }
    }

    private fun updateTitleForTab(tabPosition: Int) {
        setTitle(tabsList.get(tabPosition)!!.getTabName(requireContext()))
    }

    fun commitPlaylistTabs() {
        pagerAdapter!!.getLocalPlaylistFragments()
                .stream()
                .forEach(Consumer({ obj: LocalPlaylistFragment? -> obj!!.saveImmediate() }))
    }

    private fun updateTabLayoutPosition() {
        val tabLayout: ScrollableTabLayout = binding!!.mainTabLayout
        val viewPager: ViewPager = binding!!.pager
        val bottom: Boolean = mainTabsPositionBottom

        // change layout params to make the tab layout appear either at the top or at the bottom
        val tabParams: RelativeLayout.LayoutParams = tabLayout.getLayoutParams() as RelativeLayout.LayoutParams
        val pagerParams: RelativeLayout.LayoutParams = viewPager.getLayoutParams() as RelativeLayout.LayoutParams
        tabParams.removeRule(if (bottom) RelativeLayout.ALIGN_PARENT_TOP else RelativeLayout.ALIGN_PARENT_BOTTOM)
        tabParams.addRule(if (bottom) RelativeLayout.ALIGN_PARENT_BOTTOM else RelativeLayout.ALIGN_PARENT_TOP)
        pagerParams.removeRule(if (bottom) RelativeLayout.BELOW else RelativeLayout.ABOVE)
        pagerParams.addRule(if (bottom) RelativeLayout.ABOVE else RelativeLayout.BELOW, R.id.main_tab_layout)
        tabLayout.setSelectedTabIndicatorGravity(
                if (bottom) TabLayout.INDICATOR_GRAVITY_TOP else TabLayout.INDICATOR_GRAVITY_BOTTOM)
        tabLayout.setLayoutParams(tabParams)
        viewPager.setLayoutParams(pagerParams)

        // change the background and icon color of the tab layout:
        // service-colored at the top, app-background-colored at the bottom
        tabLayout.setBackgroundColor(ThemeHelper.resolveColorFromAttr(requireContext(),
                if (bottom) R.attr.colorSecondary else R.attr.colorPrimary))
        @ColorInt val iconColor: Int = if (bottom) ThemeHelper.resolveColorFromAttr(requireContext(), R.attr.colorAccent) else Color.WHITE
        tabLayout.setTabRippleColor(ColorStateList.valueOf(iconColor).withAlpha(32))
        tabLayout.setTabIconTint(ColorStateList.valueOf(iconColor))
        tabLayout.setSelectedTabIndicatorColor(iconColor)
    }

    public override fun onTabSelected(selectedTab: TabLayout.Tab) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]")
        }
        updateTitleForTab(selectedTab.getPosition())
    }

    public override fun onTabUnselected(tab: TabLayout.Tab) {}
    public override fun onTabReselected(tab: TabLayout.Tab) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]")
        }
        updateTitleForTab(tab.getPosition())
    }

    class SelectedTabsPagerAdapter(private val context: Context,
                                   fragmentManager: FragmentManager,
                                   tabsList: List<Tab?>) : FragmentStatePagerAdapterMenuWorkaround(fragmentManager, FragmentStatePagerAdapterMenuWorkaround.Companion.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        private val internalTabsList: List<Tab?>

        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * [LocalPlaylistFragment.saveImmediate].
         * The fragments are removed when [LocalPlaylistFragment.onDestroy] is called.
         */
        private val localPlaylistFragments: MutableList<LocalPlaylistFragment?> = ArrayList()

        init {
            internalTabsList = ArrayList(tabsList)
        }

        public override fun getItem(position: Int): Fragment {
            val tab: Tab? = internalTabsList.get(position)
            val fragment: Fragment
            try {
                fragment = tab!!.getFragment(context)
            } catch (e: ExtractionException) {
                showUiErrorSnackbar(context, "Getting fragment item", e)
                return BlankFragment()
            }
            if (fragment is BaseFragment) {
                fragment.useAsFrontPage(true)
            }
            if (fragment is LocalPlaylistFragment) {
                localPlaylistFragments.add(fragment as LocalPlaylistFragment?)
            }
            return fragment
        }

        fun getLocalPlaylistFragments(): MutableList<LocalPlaylistFragment?> {
            return localPlaylistFragments
        }

        public override fun getItemPosition(`object`: Any): Int {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE
        }

        public override fun getCount(): Int {
            return internalTabsList.size
        }

        fun sameTabs(tabsToCompare: List<Tab?>): Boolean {
            return (internalTabsList == tabsToCompare)
        }
    }
}
