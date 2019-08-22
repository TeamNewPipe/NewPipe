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

package org.schabi.newpipe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final boolean DEBUG = !BuildConfig.BUILD_TYPE.equals("release");

    private ActionBarDrawerToggle toggle = null;
    private DrawerLayout drawer = null;
    private NavigationView drawerItems = null;
    private TextView headerServiceView = null;

    private boolean servicesShown = false;
    private ImageView serviceArrow;

    private static final int ITEM_ID_SUBSCRIPTIONS = - 1;
    private static final int ITEM_ID_FEED = - 2;
    private static final int ITEM_ID_BOOKMARKS = - 3;
    private static final int ITEM_ID_DOWNLOADS = - 4;
    private static final int ITEM_ID_HISTORY = - 5;
    private static final int ITEM_ID_SETTINGS = 0;
    private static final int ITEM_ID_ABOUT = 1;

    private static final int ORDER = 0;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");

        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        if (getSupportFragmentManager() != null && getSupportFragmentManager().getBackStackEntryCount() == 0) {
            initFragments();
        }

        setSupportActionBar(findViewById(R.id.toolbar));
        try {
            setupDrawer();
        } catch (Exception e) {
            ErrorActivity.reportUiError(this, e);
        }
    }

    private void setupDrawer() throws Exception {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        drawer = findViewById(R.id.drawer_layout);
        drawerItems = findViewById(R.id.navigation);

        //Tabs
        int currentServiceId = ServiceHelper.getSelectedServiceId(this);
        StreamingService service = NewPipe.getService(currentServiceId);

        int kioskId = 0;

        for (final String ks : service.getKioskList().getAvailableKiosks()) {
            drawerItems.getMenu()
                    .add(R.id.menu_tabs_group, kioskId, 0, KioskTranslator.getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcons(ks, this));
            kioskId ++;
        }

        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER, R.string.tab_subscriptions)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_channel));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_whats_new)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.rss));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_bookmark));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.download));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.history));

        //Settings and About
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.settings));
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.info));

        toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close);
        toggle.syncState();
        drawer.addDrawerListener(toggle);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private int lastService;

            @Override
            public void onDrawerOpened(View drawerView) {
                lastService = ServiceHelper.getSelectedServiceId(MainActivity.this);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if(servicesShown) {
                    toggleServices();
                }
                if (lastService != ServiceHelper.getSelectedServiceId(MainActivity.this)) {
                    new Handler(Looper.getMainLooper()).post(MainActivity.this::recreate);
                }
            }
        });

        drawerItems.setNavigationItemSelectedListener(this::drawerItemSelected);
        setupDrawerHeader();
    }

    private boolean drawerItemSelected(MenuItem item) {
        switch (item.getGroupId()) {
            case R.id.menu_services_group:
                changeService(item);
                break;
            case R.id.menu_tabs_group:
                try {
                    tabSelected(item);
                } catch (Exception e) {
                    ErrorActivity.reportUiError(this, e);
                }
                break;
            case R.id.menu_options_about_group:
                optionsAboutSelected(item);
                break;
            default:
                return false;
        }

        drawer.closeDrawers();
        return true;
    }

    private  void changeService(MenuItem item) {
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(this)).setChecked(false);
        ServiceHelper.setSelectedServiceId(this, item.getItemId());
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(this)).setChecked(true);
    }

    private void tabSelected(MenuItem item) throws ExtractionException {
        switch(item.getItemId()) {
            case ITEM_ID_SUBSCRIPTIONS:
                NavigationHelper.openSubscriptionFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_FEED:
                NavigationHelper.openWhatsNewFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_BOOKMARKS:
                NavigationHelper.openBookmarksFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_DOWNLOADS:
                NavigationHelper.openDownloads(this);
                break;
            case ITEM_ID_HISTORY:
                NavigationHelper.openStatisticFragment(getSupportFragmentManager());
                break;
            default:
                int currentServiceId = ServiceHelper.getSelectedServiceId(this);
                StreamingService service = NewPipe.getService(currentServiceId);
                String serviceName = "";

                int kioskId = 0;
                for (final String ks : service.getKioskList().getAvailableKiosks()) {
                    if(kioskId == item.getItemId()) {
                        serviceName = ks;
                    }
                    kioskId ++;
                }

                NavigationHelper.openKioskFragment(getSupportFragmentManager(), currentServiceId, serviceName);
                break;
        }
    }

    private void optionsAboutSelected(MenuItem item) {
        switch(item.getItemId()) {
            case ITEM_ID_SETTINGS:
                NavigationHelper.openSettings(this);
                break;
            case ITEM_ID_ABOUT:
                NavigationHelper.openAbout(this);
                break;
        }
    }

    private void setupDrawerHeader() {
        NavigationView navigationView = findViewById(R.id.navigation);
        View hView =  navigationView.getHeaderView(0);

        serviceArrow = hView.findViewById(R.id.drawer_arrow);
        headerServiceView = hView.findViewById(R.id.drawer_header_service_view);
        Button action = hView.findViewById(R.id.drawer_header_action_button);
        action.setOnClickListener(view -> {
            toggleServices();
        });
    }

    private void toggleServices() {
        servicesShown = !servicesShown;

        drawerItems.getMenu().removeGroup(R.id.menu_services_group);
        drawerItems.getMenu().removeGroup(R.id.menu_tabs_group);
        drawerItems.getMenu().removeGroup(R.id.menu_options_about_group);

        if(servicesShown) {
            showServices();
        } else {
            try {
                showTabs();
            } catch (Exception e) {
                ErrorActivity.reportUiError(this, e);
            }
        }
    }

    private void showServices() {
        serviceArrow.setImageResource(R.drawable.ic_arrow_up_white);

        for(StreamingService s : NewPipe.getServices()) {
            final String title = s.getServiceInfo().getName() +
                    (ServiceHelper.isBeta(s) ? " (beta)" : "");

            drawerItems.getMenu()
                    .add(R.id.menu_services_group, s.getServiceId(), ORDER, title)
                    .setIcon(ServiceHelper.getIcon(s.getServiceId()));
        }
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(this)).setChecked(true);
    }

    private void showTabs() throws ExtractionException {
        serviceArrow.setImageResource(R.drawable.ic_arrow_down_white);

        //Tabs
        int currentServiceId = ServiceHelper.getSelectedServiceId(this);
        StreamingService service = NewPipe.getService(currentServiceId);

        int kioskId = 0;

        for (final String ks : service.getKioskList().getAvailableKiosks()) {
            drawerItems.getMenu()
                    .add(R.id.menu_tabs_group, kioskId, ORDER, KioskTranslator.getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcons(ks, this));
            kioskId ++;
        }

        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER, R.string.tab_subscriptions)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_channel));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_whats_new)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.rss));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_bookmark));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.download));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.history));

        //Settings and About
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.settings));
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.info));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            StateSaver.clearStateFiles();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // close drawer on return, and don't show animation, so its looks like the drawer isn't open
        // when the user returns to MainActivity
        drawer.closeDrawer(Gravity.START, false);
        try {
            String selectedServiceName = NewPipe.getService(
                    ServiceHelper.getSelectedServiceId(this)).getServiceInfo().getName();
            headerServiceView.setText(selectedServiceName);
        } catch (Exception e) {
            ErrorActivity.reportUiError(this, e);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) Log.d(TAG, "Theme has changed, recreating activity...");
            sharedPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, false).apply();
            // https://stackoverflow.com/questions/10844112/runtimeexception-performing-pause-of-activity-that-is-not-resumed
            // Briefly, let the activity resume properly posting the recreate call to end of the message queue
            new Handler(Looper.getMainLooper()).post(MainActivity.this::recreate);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) Log.d(TAG, "main page has changed, recreating main fragment...");
            sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply();
            NavigationHelper.openMainActivity(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        if (intent != null) {
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            String action = intent.getAction();
            if ((action != null && action.equals(Intent.ACTION_MAIN)) && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) return;
        }

        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        // If current fragment implements BackPressable (i.e. can/wanna handle back press) delegate the back press to it
        if (fragment instanceof BackPressable) {
            if (((BackPressable) fragment).onBackPressed()) return;
        }


        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish();
        } else super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for (int i: grantResults){
            if (i == PackageManager.PERMISSION_DENIED){
                return;
            }
        }
        switch (requestCode) {
            case PermissionHelper.DOWNLOADS_REQUEST_CODE:
                NavigationHelper.openDownloads(this);
                break;
            case PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE:
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
                if (fragment instanceof VideoDetailFragment) {
                    ((VideoDetailFragment) fragment).openDownloadDialog();
                }
                break;
        }
    }

    /**
     * Implement the following diagram behavior for the up button:
     * <pre>
     *              +---------------+
     *              |  Main Screen  +----+
     *              +-------+-------+    |
     *                      |            |
     *                      ▲ Up         | Search Button
     *                      |            |
     *                 +----+-----+      |
     *    +------------+  Search  |◄-----+
     *    |            +----+-----+
     *    |   Open          |
     *    |  something      ▲ Up
     *    |                 |
     *    |    +------------+-------------+
     *    |    |                          |
     *    |    |  Video    <->  Channel   |
     *    +---►|  Channel  <->  Playlist  |
     *         |  Video    <->  ....      |
     *         |                          |
     *         +--------------------------+
     * </pre>
     */
    private void onHomeButtonPressed() {
        // If search fragment wasn't found in the backstack...
        if (!NavigationHelper.tryGotoSearchFragment(getSupportFragmentManager())) {
            // ...go to the main fragment
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]");
        super.onCreateOptionsMenu(menu);

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (!(fragment instanceof VideoDetailFragment)) {
            findViewById(R.id.toolbar).findViewById(R.id.toolbar_spinner).setVisibility(View.GONE);
        }

        if (!(fragment instanceof SearchFragment)) {
            findViewById(R.id.toolbar).findViewById(R.id.toolbar_search_container).setVisibility(View.GONE);

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_menu, menu);
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        updateDrawerNavigation();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                onHomeButtonPressed();
                return true;
            case R.id.action_show_downloads:
                    return NavigationHelper.openDownloads(this);
            case R.id.action_history:
                    NavigationHelper.openStatisticFragment(getSupportFragmentManager());
                    return true;
            case R.id.action_settings:
                    NavigationHelper.openSettings(this);
                    return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initFragments() {
        if (DEBUG) Log.d(TAG, "initFragments() called");
        StateSaver.clearStateFiles();
        if (getIntent() != null && getIntent().hasExtra(Constants.KEY_LINK_TYPE)) {
            handleIntent(getIntent());
        } else NavigationHelper.gotoMainFragment(getSupportFragmentManager());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateDrawerNavigation() {
        if (getSupportActionBar() == null) return;

        final Toolbar toolbar = findViewById(R.id.toolbar);
        final DrawerLayout drawer = findViewById(R.id.drawer_layout);

        final Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (fragment instanceof MainFragment) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            if (toggle != null) {
                toggle.syncState();
                toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED);
            }
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onHomeButtonPressed());
        }
    }

    private void handleIntent(Intent intent) {
        try {
            if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");

            if (intent.hasExtra(Constants.KEY_LINK_TYPE)) {
                String url = intent.getStringExtra(Constants.KEY_URL);
                int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                String title = intent.getStringExtra(Constants.KEY_TITLE);
                switch (((StreamingService.LinkType) intent.getSerializableExtra(Constants.KEY_LINK_TYPE))) {
                    case STREAM:
                        boolean autoPlay = intent.getBooleanExtra(VideoDetailFragment.AUTO_PLAY, false);
                        NavigationHelper.openVideoDetailFragment(getSupportFragmentManager(), serviceId, url, title, autoPlay);
                        break;
                    case CHANNEL:
                        NavigationHelper.openChannelFragment(getSupportFragmentManager(),
                                serviceId,
                                url,
                                title);
                        break;
                    case PLAYLIST:
                        NavigationHelper.openPlaylistFragment(getSupportFragmentManager(),
                                serviceId,
                                url,
                                title);
                        break;
                }
            } else if (intent.hasExtra(Constants.KEY_OPEN_SEARCH)) {
                String searchString = intent.getStringExtra(Constants.KEY_SEARCH_STRING);
                if (searchString == null) searchString = "";
                int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                NavigationHelper.openSearchFragment(
                        getSupportFragmentManager(),
                        serviceId,
                        searchString);

            } else {
                NavigationHelper.gotoMainFragment(getSupportFragmentManager());
            }
        } catch (Exception e) {
            ErrorActivity.reportUiError(this, e);
        }
    }
}
