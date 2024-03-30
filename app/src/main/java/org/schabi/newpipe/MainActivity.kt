/*
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadActivity.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Spinner
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.navigation.NavigationView
import org.schabi.newpipe.NewVersionWorker.Companion.enqueueNewVersionCheckingWork
import org.schabi.newpipe.databinding.ActivityMainBinding
import org.schabi.newpipe.databinding.DrawerHeaderBinding
import org.schabi.newpipe.databinding.DrawerLayoutBinding
import org.schabi.newpipe.databinding.InstanceSpinnerLayoutBinding
import org.schabi.newpipe.databinding.ToolbarLayoutBinding
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.local.feed.notifications.NotificationWorker.Companion.initialize
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.event.OnKeyDownListener
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.settings.UpdateSettingsFragment
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PeertubeHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ReleaseVersionUtil.isReleaseApk
import org.schabi.newpipe.util.SerializedCache
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.FocusOverlayView
import java.util.Objects

class MainActivity() : AppCompatActivity() {
    private var mainBinding: ActivityMainBinding? = null
    private var drawerHeaderBinding: DrawerHeaderBinding? = null
    private var drawerLayoutBinding: DrawerLayoutBinding? = null
    private var toolbarLayoutBinding: ToolbarLayoutBinding? = null
    private var toggle: ActionBarDrawerToggle? = null
    private var servicesShown: Boolean = false
    private var broadcastReceiver: BroadcastReceiver? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) {
            Log.d(TAG, ("onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]"))
        }
        ThemeHelper.setDayNightMode(this)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))
        Localization.assureCorrectAppLanguage(this)
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater())
        drawerLayoutBinding = mainBinding!!.drawerLayout
        drawerHeaderBinding = DrawerHeaderBinding.bind(drawerLayoutBinding!!.navigation
                .getHeaderView(0))
        toolbarLayoutBinding = mainBinding!!.toolbarLayout
        setContentView(mainBinding!!.getRoot())
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            initFragments()
        }
        setSupportActionBar(toolbarLayoutBinding!!.toolbar)
        try {
            setupDrawer()
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Setting up drawer", e)
        }
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.Companion.setupFocusObserver(this)
        }
        openMiniPlayerUponPlayerStarted()
        if (PermissionHelper.checkPostNotificationsPermission(this,
                        PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE)) {
            // Schedule worker for checking for new streams and creating corresponding notifications
            // if this is enabled by the user.
            initialize(this)
        }
        if ((!UpdateSettingsFragment.Companion.wasUserAskedForConsent(this)
                        && !App.Companion.getApp().isFirstRun()
                        && isReleaseApk)) {
            UpdateSettingsFragment.Companion.askForConsentToUpdateChecks(this)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val app: App = App.Companion.getApp()
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(app)
        if ((prefs.getBoolean(app.getString(R.string.update_app_key), false)
                        && prefs.getBoolean(app.getString(R.string.update_check_consent_key), false))) {
            // Start the worker which is checking all conditions
            // and eventually searching for a new version.
            enqueueNewVersionCheckingWork(app, false)
        }
    }

    @Throws(ExtractionException::class)
    private fun setupDrawer() {
        addDrawerMenuForCurrentService()
        toggle = ActionBarDrawerToggle(this, mainBinding!!.getRoot(),
                toolbarLayoutBinding!!.toolbar, R.string.drawer_open, R.string.drawer_close)
        toggle!!.syncState()
        mainBinding!!.getRoot().addDrawerListener(toggle!!)
        mainBinding!!.getRoot().addDrawerListener(object : SimpleDrawerListener() {
            private var lastService: Int = 0
            public override fun onDrawerOpened(drawerView: View) {
                lastService = ServiceHelper.getSelectedServiceId(this@MainActivity)
            }

            public override fun onDrawerClosed(drawerView: View) {
                if (servicesShown) {
                    toggleServices()
                }
                if (lastService != ServiceHelper.getSelectedServiceId(this@MainActivity)) {
                    ActivityCompat.recreate(this@MainActivity)
                }
            }
        })
        drawerLayoutBinding!!.navigation.setNavigationItemSelectedListener(NavigationView.OnNavigationItemSelectedListener({ item: MenuItem -> drawerItemSelected(item) }))
        setupDrawerHeader()
    }

    /**
     * Builds the drawer menu for the current service.
     *
     * @throws ExtractionException if the service didn't provide available kiosks
     */
    @Throws(ExtractionException::class)
    private fun addDrawerMenuForCurrentService() {
        //Tabs
        val currentServiceId: Int = ServiceHelper.getSelectedServiceId(this)
        val service: StreamingService = NewPipe.getService(currentServiceId)
        var kioskMenuItemId: Int = 0
        for (ks: String in service.getKioskList().getAvailableKiosks()) {
            drawerLayoutBinding!!.navigation.getMenu()
                    .add(R.id.menu_tabs_group, kioskMenuItemId, 0, KioskTranslator.getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcon(ks))
            kioskMenuItemId++
        }
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER,
                        R.string.tab_subscriptions)
                .setIcon(R.drawable.ic_tv)
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_feed_title)
                .setIcon(R.drawable.ic_subscriptions)
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(R.drawable.ic_bookmark)
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(R.drawable.ic_file_download)
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(R.drawable.ic_history)

        //Settings and About
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(R.drawable.ic_settings)
        drawerLayoutBinding!!.navigation.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(R.drawable.ic_info_outline)
    }

    private fun drawerItemSelected(item: MenuItem): Boolean {
        when (item.getGroupId()) {
            R.id.menu_services_group -> changeService(item)
            R.id.menu_tabs_group -> try {
                tabSelected(item)
            } catch (e: Exception) {
                showUiErrorSnackbar(this, "Selecting main page tab", e)
            }

            R.id.menu_options_about_group -> optionsAboutSelected(item)
            else -> return false
        }
        mainBinding!!.getRoot().closeDrawers()
        return true
    }

    private fun changeService(item: MenuItem) {
        drawerLayoutBinding!!.navigation.getMenu()
                .getItem(ServiceHelper.getSelectedServiceId(this))
                .setChecked(false)
        ServiceHelper.setSelectedServiceId(this, item.getItemId())
        drawerLayoutBinding!!.navigation.getMenu()
                .getItem(ServiceHelper.getSelectedServiceId(this))
                .setChecked(true)
    }

    @Throws(ExtractionException::class)
    private fun tabSelected(item: MenuItem) {
        when (item.getItemId()) {
            ITEM_ID_SUBSCRIPTIONS -> NavigationHelper.openSubscriptionFragment(getSupportFragmentManager())
            ITEM_ID_FEED -> openFeedFragment(getSupportFragmentManager())
            ITEM_ID_BOOKMARKS -> NavigationHelper.openBookmarksFragment(getSupportFragmentManager())
            ITEM_ID_DOWNLOADS -> NavigationHelper.openDownloads(this)
            ITEM_ID_HISTORY -> NavigationHelper.openStatisticFragment(getSupportFragmentManager())
            else -> {
                val currentService: StreamingService? = ServiceHelper.getSelectedService(this)
                var kioskMenuItemId: Int = 0
                for (kioskId: String in currentService!!.getKioskList().getAvailableKiosks()) {
                    if (kioskMenuItemId == item.getItemId()) {
                        NavigationHelper.openKioskFragment(getSupportFragmentManager(),
                                currentService.getServiceId(), kioskId)
                        break
                    }
                    kioskMenuItemId++
                }
            }
        }
    }

    private fun optionsAboutSelected(item: MenuItem) {
        when (item.getItemId()) {
            ITEM_ID_SETTINGS -> NavigationHelper.openSettings(this)
            ITEM_ID_ABOUT -> NavigationHelper.openAbout(this)
        }
    }

    private fun setupDrawerHeader() {
        drawerHeaderBinding!!.drawerHeaderActionButton.setOnClickListener(View.OnClickListener({ view: View? -> toggleServices() }))

        // If the current app name is bigger than the default "NewPipe" (7 chars),
        // let the text view grow a little more as well.
        if (getString(R.string.app_name).length > "NewPipe".length) {
            val layoutParams: ViewGroup.LayoutParams = drawerHeaderBinding!!.drawerHeaderNewpipeTitle.getLayoutParams()
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            drawerHeaderBinding!!.drawerHeaderNewpipeTitle.setLayoutParams(layoutParams)
            drawerHeaderBinding!!.drawerHeaderNewpipeTitle.setMaxLines(2)
            drawerHeaderBinding!!.drawerHeaderNewpipeTitle.setMinWidth(getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_newpipe_title_default_width))
            drawerHeaderBinding!!.drawerHeaderNewpipeTitle.setMaxWidth(getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_newpipe_title_max_width))
        }
    }

    private fun toggleServices() {
        servicesShown = !servicesShown
        drawerLayoutBinding!!.navigation.getMenu().removeGroup(R.id.menu_services_group)
        drawerLayoutBinding!!.navigation.getMenu().removeGroup(R.id.menu_tabs_group)
        drawerLayoutBinding!!.navigation.getMenu().removeGroup(R.id.menu_options_about_group)

        // Show up or down arrow
        drawerHeaderBinding!!.drawerArrow.setImageResource(
                if (servicesShown) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_drop_down)
        if (servicesShown) {
            showServices()
        } else {
            try {
                addDrawerMenuForCurrentService()
            } catch (e: Exception) {
                showUiErrorSnackbar(this, "Showing main page tabs", e)
            }
        }
    }

    private fun showServices() {
        for (s: StreamingService in NewPipe.getServices()) {
            val title: String = s.getServiceInfo().getName()
            val menuItem: MenuItem = drawerLayoutBinding!!.navigation.getMenu()
                    .add(R.id.menu_services_group, s.getServiceId(), ORDER, title)
                    .setIcon(ServiceHelper.getIcon(s.getServiceId()))

            // peertube specifics
            if (s.getServiceId() == 3) {
                enhancePeertubeMenu(menuItem)
            }
        }
        drawerLayoutBinding!!.navigation.getMenu()
                .getItem(ServiceHelper.getSelectedServiceId(this))
                .setChecked(true)
    }

    private fun enhancePeertubeMenu(menuItem: MenuItem) {
        val currentInstance: PeertubeInstance? = PeertubeHelper.getCurrentInstance()
        menuItem.setTitle(currentInstance!!.getName())
        val spinner: Spinner = InstanceSpinnerLayoutBinding.inflate(LayoutInflater.from(this))
                .getRoot()
        val instances: List<PeertubeInstance?>? = PeertubeHelper.getInstanceList(this)
        val items: MutableList<String> = ArrayList()
        var defaultSelect: Int = 0
        for (instance: PeertubeInstance? in instances!!) {
            items.add(instance!!.getName())
            if ((instance.getUrl() == currentInstance.getUrl())) {
                defaultSelect = items.size - 1
            }
        }
        val adapter: ArrayAdapter<String> = ArrayAdapter(this,
                R.layout.instance_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.setAdapter(adapter)
        spinner.setSelection(defaultSelect, false)
        spinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            public override fun onItemSelected(parent: AdapterView<*>?, view: View,
                                               position: Int, id: Long) {
                val newInstance: PeertubeInstance? = instances.get(position)
                if ((newInstance!!.getUrl() == PeertubeHelper.getCurrentInstance().getUrl())) {
                    return
                }
                PeertubeHelper.selectInstance(newInstance, getApplicationContext())
                changeService(menuItem)
                mainBinding!!.getRoot().closeDrawers()
                Handler(Looper.getMainLooper()).postDelayed(Runnable({
                    getSupportFragmentManager().popBackStack(null,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    ActivityCompat.recreate(this@MainActivity)
                }), 300)
            }

            public override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
        menuItem.setActionView(spinner)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations()) {
            StateSaver.clearStateFiles()
        }
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
    }

    override fun onResume() {
        Localization.assureCorrectAppLanguage(this)
        // Change the date format to match the selected language on resume
        Localization.initPrettyTime(Localization.resolvePrettyTime(getApplicationContext()))
        super.onResume()

        // Close drawer on return, and don't show animation,
        // so it looks like the drawer isn't open when the user returns to MainActivity
        mainBinding!!.getRoot().closeDrawer(GravityCompat.START, false)
        try {
            val selectedServiceId: Int = ServiceHelper.getSelectedServiceId(this)
            val selectedServiceName: String = NewPipe.getService(selectedServiceId)
                    .getServiceInfo().getName()
            drawerHeaderBinding!!.drawerHeaderServiceView.setText(selectedServiceName)
            drawerHeaderBinding!!.drawerHeaderServiceIcon.setImageResource(ServiceHelper.getIcon(selectedServiceId))
            drawerHeaderBinding!!.drawerHeaderServiceView.post(Runnable({ drawerHeaderBinding!!.drawerHeaderServiceView.setSelected(true) }))
            drawerHeaderBinding!!.drawerHeaderActionButton.setContentDescription(
                    getString(R.string.drawer_header_description) + selectedServiceName)
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Setting up service toggle", e)
        }
        val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (sharedPreferences.getBoolean(KEY_THEME_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "Theme has changed, recreating activity...")
            }
            sharedPreferences.edit().putBoolean(KEY_THEME_CHANGE, false).apply()
            ActivityCompat.recreate(this)
        }
        if (sharedPreferences.getBoolean(KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "main page has changed, recreating main fragment...")
            }
            sharedPreferences.edit().putBoolean(KEY_MAIN_PAGE_CHANGE, false).apply()
            NavigationHelper.openMainActivity(this)
        }
        val isHistoryEnabled: Boolean = sharedPreferences.getBoolean(
                getString(R.string.enable_watch_history_key), true)
        drawerLayoutBinding!!.navigation.getMenu().findItem(ITEM_ID_HISTORY)
                .setVisible(isHistoryEnabled)
    }

    override fun onNewIntent(intent: Intent) {
        if (DEBUG) {
            Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]")
        }
        if (intent != null) {
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            val action: String? = intent.getAction()
            if (((action != null && (action == Intent.ACTION_MAIN))
                            && intent.hasCategory(Intent.CATEGORY_LAUNCHER))) {
                return
            }
        }
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    public override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val fragment: Fragment? = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder)
        if ((fragment is OnKeyDownListener
                        && !bottomSheetHiddenOrCollapsed())) {
            // Provide keyDown event to fragment which then sends this event
            // to the main player service
            return ((fragment as OnKeyDownListener).onKeyDown(keyCode)
                    || super.onKeyDown(keyCode, event))
        }
        return super.onKeyDown(keyCode, event)
    }

    public override fun onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called")
        }
        if (DeviceUtils.isTv(this)) {
            if (mainBinding!!.getRoot().isDrawerOpen(drawerLayoutBinding!!.navigation)) {
                mainBinding!!.getRoot().closeDrawers()
                return
            }
        }

        // In case bottomSheet is not visible on the screen or collapsed we can assume that the user
        // interacts with a fragment inside fragment_holder so all back presses should be
        // handled by it
        if (bottomSheetHiddenOrCollapsed()) {
            val fm: FragmentManager = getSupportFragmentManager()
            val fragment: Fragment? = fm.findFragmentById(R.id.fragment_holder)
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragment is BackPressable) {
                if ((fragment as BackPressable).onBackPressed()) {
                    return
                }
            } else if (fragment is CommentRepliesFragment) {
                // expand DetailsFragment if CommentRepliesFragment was opened
                // to show the top level comments again
                // Expand DetailsFragment if CommentRepliesFragment was opened
                // and no other CommentRepliesFragments are on top of the back stack
                // to show the top level comments again.
                openDetailFragmentFromCommentReplies(fm, false)
            }
        } else {
            val fragmentPlayer: Fragment? = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_player_holder)
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragmentPlayer is BackPressable) {
                if (!(fragmentPlayer as BackPressable).onBackPressed()) {
                    BottomSheetBehavior.from(mainBinding!!.fragmentPlayerHolder)
                            .setState(BottomSheetBehavior.STATE_COLLAPSED)
                }
                return
            }
        }
        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    public override fun onRequestPermissionsResult(requestCode: Int,
                                                   permissions: Array<String>,
                                                   grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i: Int in grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return
            }
        }
        when (requestCode) {
            PermissionHelper.DOWNLOADS_REQUEST_CODE -> NavigationHelper.openDownloads(this)
            PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE -> {
                val fragment: Fragment? = getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_player_holder)
                if (fragment is VideoDetailFragment) {
                    fragment.openDownloadDialog()
                }
            }

            PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE -> initialize(this)
        }
    }

    /**
     * Implement the following diagram behavior for the up button:
     * <pre>
     * +---------------+
     * |  Main Screen  +----+
     * +-------+-------+    |
     * |            |
     * ▲ Up         | Search Button
     * |            |
     * +----+-----+      |
     * +------------+  Search  |◄-----+
     * |            +----+-----+
     * |   Open          |
     * |  something      ▲ Up
     * |                 |
     * |    +------------+-------------+
     * |    |                          |
     * |    |  Video    <->  Channel   |
     * +---►|  Channel  <->  Playlist  |
     * |  Video    <->  ....      |
     * |                          |
     * +--------------------------+
    </pre> *
     */
    private fun onHomeButtonPressed() {
        val fm: FragmentManager = getSupportFragmentManager()
        val fragment: Fragment? = fm.findFragmentById(R.id.fragment_holder)
        if (fragment is CommentRepliesFragment) {
            // Expand DetailsFragment if CommentRepliesFragment was opened
            // and no other CommentRepliesFragments are on top of the back stack
            // to show the top level comments again.
            openDetailFragmentFromCommentReplies(fm, true)
        } else if (!NavigationHelper.tryGotoSearchFragment(fm)) {
            // If search fragment wasn't found in the backstack go to the main fragment
            NavigationHelper.gotoMainFragment(fm)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]")
        }
        super.onCreateOptionsMenu(menu)
        val fragment: Fragment? = getSupportFragmentManager().findFragmentById(R.id.fragment_holder)
        if (!(fragment is SearchFragment)) {
            toolbarLayoutBinding!!.toolbarSearchContainer.getRoot().setVisibility(View.GONE)
        }
        val actionBar: ActionBar? = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false)
        }
        updateDrawerNavigation()
        return true
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]")
        }
        if (item.getItemId() == android.R.id.home) {
            onHomeButtonPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    private fun initFragments() {
        if (DEBUG) {
            Log.d(TAG, "initFragments() called")
        }
        StateSaver.clearStateFiles()
        if (getIntent() != null && getIntent().hasExtra(KEY_LINK_TYPE)) {
            // When user watch a video inside popup and then tries to open the video in main player
            // while the app is closed he will see a blank fragment on place of kiosk.
            // Let's open it first
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                NavigationHelper.openMainFragment(getSupportFragmentManager())
            }
            handleIntent(getIntent())
        } else {
            NavigationHelper.gotoMainFragment(getSupportFragmentManager())
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun updateDrawerNavigation() {
        if (getSupportActionBar() == null) {
            return
        }
        val fragment: Fragment? = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_holder)
        if (fragment is MainFragment) {
            getSupportActionBar()!!.setDisplayHomeAsUpEnabled(false)
            if (toggle != null) {
                toggle!!.syncState()
                toolbarLayoutBinding!!.toolbar.setNavigationOnClickListener(View.OnClickListener({ v: View? ->
                    mainBinding!!.getRoot()
                            .open()
                }))
                mainBinding!!.getRoot().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
            }
        } else {
            mainBinding!!.getRoot().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            getSupportActionBar()!!.setDisplayHomeAsUpEnabled(true)
            toolbarLayoutBinding!!.toolbar.setNavigationOnClickListener(View.OnClickListener({ v: View? -> onHomeButtonPressed() }))
        }
    }

    private fun handleIntent(intent: Intent) {
        try {
            if (DEBUG) {
                Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]")
            }
            if (intent.hasExtra(KEY_LINK_TYPE)) {
                val url: String? = intent.getStringExtra(KEY_URL)
                val serviceId: Int = intent.getIntExtra(KEY_SERVICE_ID, 0)
                var title: String? = intent.getStringExtra(KEY_TITLE)
                if (title == null) {
                    title = ""
                }
                val linkType: LinkType? = (intent
                        .getSerializableExtra(KEY_LINK_TYPE) as LinkType?)
                assert(linkType != null)
                when (linkType) {
                    LinkType.STREAM -> {
                        val intentCacheKey: String? = intent.getStringExtra(
                                Player.Companion.PLAY_QUEUE_KEY)
                        val playQueue: PlayQueue? = if (intentCacheKey != null) SerializedCache.Companion.getInstance()
                                .take<PlayQueue>(intentCacheKey, PlayQueue::class.java) else null
                        val switchingPlayers: Boolean = intent.getBooleanExtra(
                                VideoDetailFragment.Companion.KEY_SWITCHING_PLAYERS, false)
                        NavigationHelper.openVideoDetailFragment(
                                getApplicationContext(), getSupportFragmentManager(),
                                serviceId, url, title, playQueue, switchingPlayers)
                    }

                    LinkType.CHANNEL -> NavigationHelper.openChannelFragment(getSupportFragmentManager(),
                            serviceId, url, title)

                    LinkType.PLAYLIST -> NavigationHelper.openPlaylistFragment(getSupportFragmentManager(),
                            serviceId, url, title)
                }
            } else if (intent.hasExtra(KEY_OPEN_SEARCH)) {
                var searchString: String? = intent.getStringExtra(KEY_SEARCH_STRING)
                if (searchString == null) {
                    searchString = ""
                }
                val serviceId: Int = intent.getIntExtra(KEY_SERVICE_ID, 0)
                NavigationHelper.openSearchFragment(
                        getSupportFragmentManager(),
                        serviceId,
                        searchString)
            } else {
                NavigationHelper.gotoMainFragment(getSupportFragmentManager())
            }
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Handling intent", e)
        }
    }

    private fun openMiniPlayerIfMissing() {
        val fragmentPlayer: Fragment? = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder)
        if (fragmentPlayer == null) {
            // We still don't have a fragment attached to the activity. It can happen when a user
            // started popup or background players without opening a stream inside the fragment.
            // Adding it in a collapsed state (only mini player will be visible).
            NavigationHelper.showMiniPlayer(getSupportFragmentManager())
        }
    }

    private fun openMiniPlayerUponPlayerStarted() {
        if ((getIntent().getSerializableExtra(KEY_LINK_TYPE)
                        === LinkType.STREAM)) {
            // handleIntent() already takes care of opening video detail fragment
            // due to an intent containing a STREAM link
            return
        }
        if (PlayerHolder.Companion.getInstance().isPlayerOpen()) {
            // if the player is already open, no need for a broadcast receiver
            openMiniPlayerIfMissing()
        } else {
            // listen for player start intent being sent around
            broadcastReceiver = object : BroadcastReceiver() {
                public override fun onReceive(context: Context, intent: Intent) {
                    if (Objects.equals(intent.getAction(),
                                    VideoDetailFragment.Companion.ACTION_PLAYER_STARTED)) {
                        openMiniPlayerIfMissing()
                        // At this point the player is added 100%, we can unregister. Other actions
                        // are useless since the fragment will not be removed after that.
                        unregisterReceiver(broadcastReceiver)
                        broadcastReceiver = null
                    }
                }
            }
            val intentFilter: IntentFilter = IntentFilter()
            intentFilter.addAction(VideoDetailFragment.Companion.ACTION_PLAYER_STARTED)
            registerReceiver(broadcastReceiver, intentFilter)
        }
    }

    private fun openDetailFragmentFromCommentReplies(
            fm: FragmentManager,
            popBackStack: Boolean
    ) {
        // obtain the name of the fragment under the replies fragment that's going to be popped
        val fragmentUnderEntryName: String?
        if (fm.getBackStackEntryCount() < 2) {
            fragmentUnderEntryName = null
        } else {
            fragmentUnderEntryName = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 2)
                    .getName()
        }

        // the root comment is the comment for which the user opened the replies page
        val repliesFragment: CommentRepliesFragment? = fm.findFragmentByTag(CommentRepliesFragment.Companion.TAG) as CommentRepliesFragment?
        val rootComment: CommentsInfoItem? = if (repliesFragment == null) null else repliesFragment.getCommentsInfoItem()

        // sometimes this function pops the backstack, other times it's handled by the system
        if (popBackStack) {
            fm.popBackStackImmediate()
        }

        // only expand the bottom sheet back if there are no more nested comment replies fragments
        // stacked under the one that is currently being popped
        if ((CommentRepliesFragment.Companion.TAG == fragmentUnderEntryName)) {
            return
        }
        val behavior: BottomSheetBehavior<FragmentContainerView> = BottomSheetBehavior
                .from(mainBinding!!.fragmentPlayerHolder)
        // do not return to the comment if the details fragment was closed
        if (behavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            return
        }

        // scroll to the root comment once the bottom sheet expansion animation is finished
        behavior.addBottomSheetCallback(object : BottomSheetCallback() {
            public override fun onStateChanged(bottomSheet: View,
                                               newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    val detailFragment: Fragment? = fm.findFragmentById(
                            R.id.fragment_player_holder)
                    if (detailFragment is VideoDetailFragment && rootComment != null) {
                        // should always be the case
                        detailFragment.scrollToComment(rootComment)
                    }
                    behavior.removeBottomSheetCallback(this)
                }
            }

            public override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // not needed, listener is removed once the sheet is expanded
            }
        })
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
    }

    private fun bottomSheetHiddenOrCollapsed(): Boolean {
        val bottomSheetBehavior: BottomSheetBehavior<FrameLayout> = BottomSheetBehavior.from(mainBinding!!.fragmentPlayerHolder)
        val sheetState: Int = bottomSheetBehavior.getState()
        return (sheetState == BottomSheetBehavior.STATE_HIDDEN
                || sheetState == BottomSheetBehavior.STATE_COLLAPSED)
    }

    companion object {
        private val TAG: String = "MainActivity"
        val DEBUG: Boolean = !BuildConfig.BUILD_TYPE.equals("release")
        private val ITEM_ID_SUBSCRIPTIONS: Int = -1
        private val ITEM_ID_FEED: Int = -2
        private val ITEM_ID_BOOKMARKS: Int = -3
        private val ITEM_ID_DOWNLOADS: Int = -4
        private val ITEM_ID_HISTORY: Int = -5
        private val ITEM_ID_SETTINGS: Int = 0
        private val ITEM_ID_ABOUT: Int = 1
        private val ORDER: Int = 0
    }
}
