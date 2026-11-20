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

import android.app.AlertDialog;
import android.app.PictureInPictureUiState;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;

import org.schabi.newpipe.about.changelog.ChangelogActivity;
import org.schabi.newpipe.about.changelog.ChangelogPromptController;
import org.schabi.newpipe.databinding.ActivityMainBinding;
import org.schabi.newpipe.databinding.DrawerHeaderBinding;
import org.schabi.newpipe.databinding.DrawerLayoutBinding;
import org.schabi.newpipe.databinding.InstanceSpinnerLayoutBinding;
import org.schabi.newpipe.databinding.ToolbarLayoutBinding;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.local.feed.notifications.NotificationWorker;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.learning.LearningReminders;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior;
import org.schabi.newpipe.player.event.OnKeyDownListener;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.pip.NativePipController;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.profiles.KidsModeDialogs;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.profiles.ProfileRecord;
import org.schabi.newpipe.settings.UpdateSettingsFragment;
import org.schabi.newpipe.settings.tabs.DrawerServiceSectionsPolicy;
import org.schabi.newpipe.settings.tabs.HomeDestinationKey;
import org.schabi.newpipe.settings.tabs.HomeDestinationResolver;
import org.schabi.newpipe.settings.tabs.HomeDrawerPolicy;
import org.schabi.newpipe.settings.tabs.HomeNavigationMode;
import org.schabi.newpipe.settings.tabs.HomeNavigationModeResolver;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabletNavigationPositionResolver;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.settings.migration.MigrationManager;
import org.schabi.newpipe.sync.DeviceSyncListenerService;
import org.schabi.newpipe.support.GitHubStarPromptController;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.EdgeToEdgeHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PeertubeHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ReleaseVersionUtil;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.TvRemoteAction;
import org.schabi.newpipe.util.TvRemoteKeyDispatcher;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.FocusOverlayView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    @SuppressWarnings("ConstantConditions")
    public static final boolean DEBUG = !BuildConfig.BUILD_TYPE.equals("release");

    private ActivityMainBinding mainBinding;
    private DrawerHeaderBinding drawerHeaderBinding;
    private DrawerLayoutBinding drawerLayoutBinding;
    private ToolbarLayoutBinding toolbarLayoutBinding;

    private ActionBarDrawerToggle toggle;

    private boolean servicesShown = false;
    private boolean contextualSearchToolbarActive;
    private boolean activityStarted;

    private BroadcastReceiver broadcastReceiver;
    private final Map<Integer, HomeDrawerPolicy.KioskTarget> drawerKioskTargets = new HashMap<>();

    private static final int ITEM_ID_SUBSCRIPTIONS = -1;
    private static final int ITEM_ID_FEED = -2;
    private static final int ITEM_ID_BOOKMARKS = -3;
    private static final int ITEM_ID_DOWNLOADS = -4;
    private static final int ITEM_ID_HISTORY = -5;
    private static final int ITEM_ID_LEARNING = -6;
    private static final int ITEM_ID_LOCAL_MEDIA = -7;
    private static final int ITEM_ID_LIVE_RADIO = -8;
    private static final int ITEM_ID_LIVE_TV = -9;
    private static final int ITEM_ID_SETTINGS = 0;
    private static final int ITEM_ID_CHANGELOG = 1;
    private static final int ITEM_ID_ABOUT = 2;
    private static final int ITEM_ID_PROFILES = 3;
    private static final int ITEM_ID_KIOSK_BASE = 100;
    private static final int ITEM_ID_YOUTUBE_MUSIC = 10_000;

    private static final int ORDER = 0;
    private static final int SEARCH_BOTTOM_NAVIGATION_MAX_ITEM_COUNT = 5;
    private static final int SEARCH_NAVIGATION_RAIL_MAX_ITEM_COUNT = 7;
    private static final int SEARCH_NAVIGATION_ITEM_ID_BASE = 20_000;
    public static final String KEY_IS_IN_BACKGROUND = "is_in_background";
    public static final String KEY_OPEN_LOCAL_MEDIA_AUDIO = "open_local_media_audio";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor sharedPrefEditor;
    private String activeProfileId;
    private boolean activeKidsMode;
    private NativePipController nativePipController;
    private ChangelogPromptController changelogPromptController;
    private GitHubStarPromptController githubStarPromptController;
    private org.schabi.newpipe.support.CommunityReviewPromptController
            communityReviewPromptController;
    private boolean searchNavigationActive;
    private int lastMainTabPosition;
    private int pendingMainTabPosition = -1;
    private int defaultToolbarHeight;
    private int defaultFragmentTopMargin;
    private int defaultNavigationRailWidth;
    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    private final TvRemoteKeyDispatcher remoteKeys = new TvRemoteKeyDispatcher(this);

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        return remoteKeys.dispatch(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        if (!hasFocus) {
            remoteKeys.clear();
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }

        Localization.migrateAppLanguageSettingIfNecessary(getApplicationContext());
        ThemeHelper.setDayNightMode(this);
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this));

        // Fixes text color turning black in dark/black mode:
        // https://github.com/TeamNewPipe/NewPipe/issues/12016
        // For further reference see: https://issuetracker.google.com/issues/37124582
        if (DeviceUtils.supportsWebView()) {
            try {
                new WebView(this);
            } catch (final Throwable e) {
                if (DEBUG) {
                    Log.e(TAG, "Failed to create WebView", e);
                }
            }
        }

        LearningReminders.selectProfile(this, getIntent());
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefEditor = sharedPreferences.edit();
        activeProfileId = ProfileManager.getActiveProfileId(this);
        activeKidsMode = KidsMode.isEnabled(this);

        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        drawerLayoutBinding = mainBinding.drawerLayout;
        drawerHeaderBinding = DrawerHeaderBinding.bind(drawerLayoutBinding.navigation
                .getHeaderView(0));
        toolbarLayoutBinding = mainBinding.toolbarLayout;
        defaultToolbarHeight = toolbarLayoutBinding.toolbar.getLayoutParams().height;
        defaultFragmentTopMargin = ((ViewGroup.MarginLayoutParams)
                mainBinding.fragmentHolder.getLayoutParams()).topMargin;
        defaultNavigationRailWidth = mainBinding.mainNavigationRail.getLayoutParams().width;
        org.schabi.newpipe.local.history.IncognitoToolbarUi.bindContentInset(
                toolbarLayoutBinding, mainBinding.fragmentHolder);
        applyLargeScreenChrome();
        setContentView(mainBinding.getRoot());
        org.schabi.newpipe.themes.ThemeChrome.apply(mainBinding);
        EdgeToEdgeHelper.applyMainActivitySystemBarInsets(
                mainBinding.getRoot(),
                mainBinding.mainSafeContent,
                mainBinding.toolbarLayout.getRoot(),
                mainBinding.mainBottomNavigation,
                mainBinding.mainNavigationRail,
                mainBinding.mainBottomSystemBarScrim,
                mainBinding.fragmentPlayerHolder,
                drawerLayoutBinding.navigation,
                drawerHeaderBinding.getRoot());
        nativePipController = new NativePipController(this);
        mainBinding.fragmentPlayerHolder.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        nativePipController.updatePictureInPictureParams());

        if (getSupportFragmentManager().getBackStackEntryCount() == 0
                || getIntent().hasExtra(LearningReminders.EXTRA_PROFILE_ID)) {
            initFragments();
        }

        setSupportActionBar(toolbarLayoutBinding.toolbar);
        try {
            setupDrawer();
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Setting up drawer", e);
        }
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
        if (PermissionHelper.checkPostNotificationsPermission(this,
                PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE)) {
            // Schedule worker for checking for new streams and creating corresponding notifications
            // if this is enabled by the user.
            NotificationWorker.initialize(this);
        }
        if (!UpdateSettingsFragment.wasUserAskedForConsent(this)
                && !App.getInstance().isFirstRun()
                && ReleaseVersionUtil.INSTANCE.isReleaseApk()) {
            UpdateSettingsFragment.askForConsentToUpdateChecks(this);
        }

        if (!DEBUG) {
            showApi23RequirementDialog();
        }

        MigrationManager.showUserInfoIfPresent(this);
        changelogPromptController = new ChangelogPromptController(this);
        githubStarPromptController = new GitHubStarPromptController(this);
        communityReviewPromptController =
                new org.schabi.newpipe.support.CommunityReviewPromptController(this);
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        final App app = App.getInstance();

        if (sharedPreferences.getBoolean(app.getString(R.string.update_app_key), true)
                && sharedPreferences
                .getBoolean(app.getString(R.string.update_check_consent_key), false)) {
            // Start the worker which is checking all conditions
            // and eventually searching for a new version.
            NewVersionWorker.enqueueNewVersionCheckingWork(app, false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStarted = true;
        sharedPrefEditor.putBoolean(KEY_IS_IN_BACKGROUND, false).apply();
        Log.d(TAG, "App moved to foreground");
        DeviceSyncListenerService.Companion.startIfEnabled(this);
        nativePipController.onMainActivityStarted();
        openMiniPlayerUponPlayerStarted();
        if (githubStarPromptController != null) {
            githubStarPromptController.onAppStart();
        }
    }

    @Override
    protected void onStop() {
        activityStarted = false;
        unregisterPlayerStartedReceiver();
        super.onStop();
        sharedPrefEditor.putBoolean(KEY_IS_IN_BACKGROUND, true).apply();
        Log.d(TAG, "App moved to background");
    }

    /**
     * Update the native PiP window ratio when Media3 reports the next video's dimensions.
     * The player holder can keep the same dimensions while the video changes aspect ratio.
     */
    public void refreshNativePipAspectRatio() {
        if (nativePipController != null) {
            nativePipController.updatePictureInPictureParams();
        }
    }

    @Override
    public void onUserLeaveHint() {
        nativePipController.onUserLeaveHint();
        super.onUserLeaveHint();
    }

    @Override
    public void onPictureInPictureUiStateChanged(
            @NonNull final PictureInPictureUiState pipState) {
        super.onPictureInPictureUiStateChanged(pipState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            nativePipController.onPictureInPictureUiStateChanged(
                    pipState.isTransitioningToPip());
        }
    }

    @Override
    public void onPictureInPictureModeChanged(final boolean isInPictureInPictureMode,
                                              @NonNull final Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        nativePipController.onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    private void setupDrawer() throws ExtractionException {
        addDrawerMenuForCurrentService();

        toggle = new ActionBarDrawerToggle(this, mainBinding.getRoot(),
                toolbarLayoutBinding.toolbar, R.string.drawer_open, R.string.drawer_close);
        toggle.syncState();
        mainBinding.getRoot().addDrawerListener(toggle);
        mainBinding.getRoot().addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private int lastService;
            private boolean lastYoutubeMusicMode;

            @Override
            public void onDrawerOpened(final View drawerView) {
                lastService = ServiceHelper.getSelectedServiceId(MainActivity.this);
                lastYoutubeMusicMode = ServiceHelper.isYoutubeMusicMode(MainActivity.this);
            }

            @Override
            public void onDrawerClosed(final View drawerView) {
                if (servicesShown) {
                    toggleServices();
                }
                if (lastService != ServiceHelper.getSelectedServiceId(MainActivity.this)
                        || lastYoutubeMusicMode
                        != ServiceHelper.isYoutubeMusicMode(MainActivity.this)) {
                    ActivityCompat.recreate(MainActivity.this);
                }
            }
        });

        drawerLayoutBinding.navigation.setNavigationItemSelectedListener(this::drawerItemSelected);
        setupDrawerHeader();
    }

    /**
     * Builds the drawer menu for the current service.
     *
     * @throws ExtractionException if the service didn't provide available kiosks
     */
    private void addDrawerMenuForCurrentService() throws ExtractionException {
        final Menu menu = drawerLayoutBinding.navigation.getMenu();
        menu.removeGroup(R.id.menu_tabs_group);
        menu.removeGroup(R.id.menu_kiosks_group);
        menu.removeGroup(R.id.menu_options_about_group);
        drawerKioskTargets.clear();

        final Set<HomeDestinationKey> homeDestinations = HomeDestinationResolver.fromTabs(this,
                TabsManager.getManager(this).getVisibleTabs());

        addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.SUBSCRIPTIONS,
                ITEM_ID_SUBSCRIPTIONS, R.string.tab_subscriptions, R.drawable.ic_tv);
        addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.FEED,
                ITEM_ID_FEED, R.string.fragment_feed_title, R.drawable.ic_subscriptions);
        addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.BOOKMARKS,
                ITEM_ID_BOOKMARKS, R.string.tab_bookmarks, R.drawable.ic_bookmark);
        addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.DOWNLOADS,
                ITEM_ID_DOWNLOADS, R.string.downloads, R.drawable.ic_file_download);
        addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.LOCAL_MEDIA,
                ITEM_ID_LOCAL_MEDIA, R.string.local_media, R.drawable.ic_music_note);
        menu.add(R.id.menu_tabs_group, ITEM_ID_LIVE_RADIO, ORDER, R.string.live_radio)
                .setIcon(R.drawable.ic_radio);
        menu.add(R.id.menu_tabs_group, ITEM_ID_LIVE_TV, ORDER, R.string.live_tv)
                .setIcon(R.drawable.ic_tv);
        addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.HISTORY,
                ITEM_ID_HISTORY, R.string.action_history, R.drawable.ic_history);
        if (LearningMode.isEnabled(this)) {
            addDrawerTabIfUnconfigured(homeDestinations, HomeDestinationKey.LEARNING,
                    ITEM_ID_LEARNING, R.string.learning_dashboard_title, R.drawable.ic_school);
        }

        try {
            if (DrawerServiceSectionsPolicy.shouldShow(sharedPreferences,
                    getString(R.string.show_service_sections_key))) {
                addDrawerKiosks(homeDestinations);
            }
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Loading drawer kiosks", e);
        } finally {
            addDrawerUtilityItems();
        }
    }

    private void addDrawerUtilityItems() {
        final Menu menu = drawerLayoutBinding.navigation.getMenu();
        final ProfileRecord activeProfile = ProfileManager.getActiveProfile(this);
        menu.add(
                R.id.menu_options_about_group,
                ITEM_ID_PROFILES,
                ORDER,
                getString(
                        R.string.drawer_profiles_format,
                        ProfileManager.getDisplayName(this, activeProfile))
        ).setIcon(activeProfile.getIconRes());
        menu.add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(R.drawable.ic_settings);
        menu.add(R.id.menu_options_about_group, ITEM_ID_CHANGELOG, ORDER, R.string.changelog_title)
                .setIcon(R.drawable.ic_description_outline);
        menu.add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(R.drawable.ic_info_outline);
    }

    private void addDrawerKiosks(final Set<HomeDestinationKey> homeDestinations)
            throws ExtractionException {
        final Menu menu = drawerLayoutBinding.navigation.getMenu();
        final int currentServiceId = ServiceHelper.getSelectedServiceId(this);
        final StreamingService service = NewPipe.getService(currentServiceId);
        final Map<Integer, HomeDrawerPolicy.KioskTarget> kioskTargets =
                HomeDrawerPolicy.assignKioskMenuIds(homeDestinations, currentServiceId,
                        getDrawerKioskIds(service), ITEM_ID_KIOSK_BASE);
        drawerKioskTargets.putAll(kioskTargets);
        for (final Map.Entry<Integer, HomeDrawerPolicy.KioskTarget> entry
                : kioskTargets.entrySet()) {
            final String kioskId = entry.getValue().getKioskId();
            menu.add(R.id.menu_kiosks_group, entry.getKey(), ORDER,
                    KioskTranslator.getTranslatedKioskName(kioskId, this))
                    .setIcon(KioskTranslator.getKioskIcon(kioskId));
        }
    }

    private void addDrawerTabIfUnconfigured(final Set<HomeDestinationKey> homeDestinations,
                                            final HomeDestinationKey key,
                                            final int itemId,
                                            final int titleRes,
                                            final int iconRes) {
        if (HomeDrawerPolicy.shouldShow(homeDestinations, key)) {
            drawerLayoutBinding.navigation.getMenu()
                    .add(R.id.menu_tabs_group, itemId, ORDER, titleRes)
                    .setIcon(iconRes);
        }
    }

    private boolean drawerItemSelected(final MenuItem item) {
        final int groupId = item.getGroupId();
        if (groupId == R.id.menu_services_group) {
            changeService(item);
        } else if (groupId == R.id.menu_tabs_group) {
            tabSelected(item);
        } else if (groupId == R.id.menu_kiosks_group) {
            try {
                kioskSelected(item);
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Selecting drawer kiosk", e);
            }
        } else if (groupId == R.id.menu_options_about_group) {
            optionsAboutSelected(item);
        } else {
            return false;
        }

        mainBinding.getRoot().closeDrawers();
        return true;
    }

    private void changeService(final MenuItem item) {
        final Menu menu = drawerLayoutBinding.navigation.getMenu();
        final MenuItem oldSelection = menu.findItem(getSelectedServiceMenuItemId());
        if (oldSelection != null) {
            oldSelection.setChecked(false);
        }

        if (item.getItemId() == ITEM_ID_YOUTUBE_MUSIC) {
            ServiceHelper.setYoutubeMusicMode(this);
        } else {
            ServiceHelper.setSelectedServiceId(this, item.getItemId());
        }

        final MenuItem newSelection = menu.findItem(getSelectedServiceMenuItemId());
        if (newSelection != null) {
            newSelection.setChecked(true);
        }
    }

    private int getSelectedServiceMenuItemId() {
        return ServiceHelper.isYoutubeMusicMode(this)
                ? ITEM_ID_YOUTUBE_MUSIC : ServiceHelper.getSelectedServiceId(this);
    }

    private void tabSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case ITEM_ID_SUBSCRIPTIONS:
                NavigationHelper.openSubscriptionFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_FEED:
                NavigationHelper.openFeedFragment(getSupportFragmentManager());
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
            case ITEM_ID_LOCAL_MEDIA:
                NavigationHelper.openLocalMediaFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_LIVE_TV:
                NavigationHelper.openLiveTvFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_LIVE_RADIO:
                NavigationHelper.openRadioFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_LEARNING:
                NavigationHelper.openLearningDashboardFragment(getSupportFragmentManager());
                break;
        }
    }

    private void kioskSelected(final MenuItem item) throws ExtractionException {
        final HomeDrawerPolicy.KioskTarget target = drawerKioskTargets.get(item.getItemId());
        if (target != null) {
            NavigationHelper.openKioskFragment(getSupportFragmentManager(),
                    target.getServiceId(), target.getKioskId());
        }
    }

    /**
     * Returns every kiosk advertised by the selected streaming service in extractor order. The
     * drawer item IDs are assigned from this same list both when building and selecting menu
     * entries, so the selection index stays aligned with the visible drawer rows.
     *
     * @param service selected streaming service used to build drawer kiosk entries
     * @return all available kiosk IDs to show in the drawer
     * @throws ExtractionException if kiosk metadata cannot be loaded
     */
    private List<String> getDrawerKioskIds(final StreamingService service)
            throws ExtractionException {
        if (ServiceHelper.isYoutubeMusicMode(this)
                && service.getServiceId() == ServiceList.YouTube.getServiceId()) {
            return List.of("trending_music");
        }
        return new ArrayList<>(service.getKioskList().getAvailableKiosks());
    }

    private void optionsAboutSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case ITEM_ID_PROFILES:
                showProfileSwitcher();
                break;
            case ITEM_ID_SETTINGS:
                nativePipController.prepareForInternalActivityNavigation();
                NavigationHelper.openSettings(this);
                break;
            case ITEM_ID_CHANGELOG:
                nativePipController.prepareForInternalActivityNavigation();
                startActivity(new Intent(this, ChangelogActivity.class));
                break;
            case ITEM_ID_ABOUT:
                nativePipController.prepareForInternalActivityNavigation();
                NavigationHelper.openAbout(this);
                break;
        }
    }

    private void showProfileSwitcher() {
        final List<ProfileRecord> profiles = ProfileManager.getProfiles(this);
        final String currentProfileId = ProfileManager.getActiveProfileId(this);
        final CharSequence[] names = new CharSequence[profiles.size()];
        int selectedIndex = -1;
        for (int i = 0; i < profiles.size(); i++) {
            final ProfileRecord profile = profiles.get(i);
            names[i] = ProfileManager.getDisplayName(this, profile);
            if (profile.getId().equals(currentProfileId)) {
                selectedIndex = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profiles_title)
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    final ProfileRecord profile = profiles.get(which);
                    KidsModeDialogs.requestAccess(this, () -> {
                        try {
                            if (profile.getId().equals(ProfileManager.getActiveProfileId(this))
                                    || !ProfileManager.setActiveProfile(this, profile.getId())) {
                                return;
                            }
                            Toast.makeText(this, getString(R.string.profile_switched,
                                    ProfileManager.getDisplayName(this, profile)),
                                    Toast.LENGTH_SHORT).show();
                            activeProfileId = profile.getId();
                            mainBinding.getRoot().closeDrawer(GravityCompat.START, false);
                            ActivityCompat.recreate(this);
                        } finally {
                            KidsMode.lock();
                        }
                    }, () -> { });
                })
                .setNeutralButton(R.string.profile_manage_title, (dialog, which) -> {
                    nativePipController.prepareForInternalActivityNavigation();
                    NavigationHelper.openProfilesSettings(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupDrawerHeader() {
        drawerHeaderBinding.drawerHeaderActionButton.setOnClickListener(view -> toggleServices());

        // Let longer product labels grow beyond the compact seven-character baseline.
        if (getString(R.string.app_name).length() > 7) {
            final ViewGroup.LayoutParams layoutParams =
                    drawerHeaderBinding.drawerHeaderWizestreamTitle.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            drawerHeaderBinding.drawerHeaderWizestreamTitle.setLayoutParams(layoutParams);
            drawerHeaderBinding.drawerHeaderWizestreamTitle.setMaxLines(2);
            drawerHeaderBinding.drawerHeaderWizestreamTitle.setMinWidth(getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_wizestream_title_default_width));
            drawerHeaderBinding.drawerHeaderWizestreamTitle.setMaxWidth(getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_wizestream_title_max_width));
        }
    }

    private void toggleServices() {
        servicesShown = !servicesShown;

        drawerLayoutBinding.navigation.getMenu().removeGroup(R.id.menu_services_group);
        drawerLayoutBinding.navigation.getMenu().removeGroup(R.id.menu_tabs_group);
        drawerLayoutBinding.navigation.getMenu().removeGroup(R.id.menu_kiosks_group);
        drawerLayoutBinding.navigation.getMenu().removeGroup(R.id.menu_options_about_group);

        // Show up or down arrow
        drawerHeaderBinding.drawerArrow.setImageResource(
                servicesShown ? R.drawable.ic_arrow_drop_up : R.drawable.ic_arrow_drop_down);

        if (servicesShown) {
            showServices();
        } else {
            try {
                addDrawerMenuForCurrentService();
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Showing main page tabs", e);
            }
        }
    }

    private void showServices() {
        for (final StreamingService s : ServiceHelper.getVisibleServices()) {
            final String title = s.getServiceInfo().getName();

            final MenuItem menuItem = drawerLayoutBinding.navigation.getMenu()
                    .add(R.id.menu_services_group, s.getServiceId(), ORDER, title)
                    .setIcon(ServiceHelper.getIcon(s.getServiceId()));

            // peertube specifics
            if (s.getServiceId() == 3) {
                enhancePeertubeMenu(menuItem);
            }

            if (s.getServiceId() == ServiceList.YouTube.getServiceId()) {
                drawerLayoutBinding.navigation.getMenu()
                        .add(R.id.menu_services_group, ITEM_ID_YOUTUBE_MUSIC, ORDER,
                                R.string.youtube_music)
                        .setIcon(R.drawable.ic_music_note);
            }
        }
        final MenuItem selectedItem = drawerLayoutBinding.navigation.getMenu()
                .findItem(getSelectedServiceMenuItemId());
        if (selectedItem != null) {
            selectedItem.setChecked(true);
        }
    }

    private void enhancePeertubeMenu(final MenuItem menuItem) {
        final PeertubeInstance currentInstance = PeertubeHelper.getCurrentInstance();
        menuItem.setTitle(currentInstance.getName());
        final Spinner spinner = InstanceSpinnerLayoutBinding.inflate(LayoutInflater.from(this))
                .getRoot();
        final List<PeertubeInstance> instances = PeertubeHelper.getInstanceList(this);
        final List<String> items = new ArrayList<>();
        int defaultSelect = 0;
        for (final PeertubeInstance instance : instances) {
            items.add(instance.getName());
            if (instance.getUrl().equals(currentInstance.getUrl())) {
                defaultSelect = items.size() - 1;
            }
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.instance_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(defaultSelect, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                final PeertubeInstance newInstance = instances.get(position);
                if (newInstance.getUrl().equals(PeertubeHelper.getCurrentInstance().getUrl())) {
                    return;
                }
                PeertubeHelper.selectInstance(newInstance, getApplicationContext());
                changeService(menuItem);
                mainBinding.getRoot().closeDrawers();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    getSupportFragmentManager().popBackStack(null,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    ActivityCompat.recreate(MainActivity.this);
                }, 300);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {

            }
        });
        menuItem.setActionView(spinner);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            StateSaver.clearStateFiles();
        }
        unregisterPlayerStartedReceiver();
    }

    @Override
    protected void onResume() {
        // Change the date format to match the selected language on resume
        Localization.initPrettyTime(Localization.resolvePrettyTime());
        super.onResume();
        applyLargeScreenChrome();
        LearningReminders.initialize(this);

        final String currentProfileId = ProfileManager.getActiveProfileId(this);
        if (!Objects.equals(activeProfileId, currentProfileId)
                || activeKidsMode != KidsMode.isEnabled(this)) {
            activeProfileId = currentProfileId;
            if (DEBUG) {
                Log.d(TAG, "Active profile changed, recreating activity...");
            }
            ActivityCompat.recreate(this);
            return;
        }

        // Close drawer on return, and don't show animation,
        // so it looks like the drawer isn't open when the user returns to MainActivity
        mainBinding.getRoot().closeDrawer(GravityCompat.START, false);
        try {
            final String selectedServiceName = ServiceHelper.getSelectedServiceName(this);
            drawerHeaderBinding.drawerHeaderServiceView.setText(selectedServiceName);
            drawerHeaderBinding.drawerHeaderServiceIcon.setImageResource(
                    ServiceHelper.getSelectedServiceIcon(this));

            drawerHeaderBinding.drawerHeaderServiceView.post(() -> drawerHeaderBinding
                    .drawerHeaderServiceView.setSelected(true));
            drawerHeaderBinding.drawerHeaderActionButton.setContentDescription(
                    getString(R.string.drawer_header_description) + selectedServiceName);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Setting up service toggle", e);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "Theme has changed, recreating activity...");
            }
            sharedPrefEditor.putBoolean(Constants.KEY_THEME_CHANGE, false).apply();
            ActivityCompat.recreate(this);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "main page has changed, recreating main fragment...");
            }
            sharedPrefEditor.putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply();
            NavigationHelper.openMainActivity(this);
        }

        if (!servicesShown) {
            try {
                addDrawerMenuForCurrentService();
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Refreshing drawer", e);
            }
        }

        final boolean isHistoryEnabled = sharedPreferences.getBoolean(
                getString(R.string.enable_watch_history_key), true);
        final MenuItem historyItem = drawerLayoutBinding.navigation.getMenu()
                .findItem(ITEM_ID_HISTORY);
        if (historyItem != null) {
            historyItem.setVisible(isHistoryEnabled);
        }
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        }
        if (intent != null) {
            if (changelogPromptController != null) {
                changelogPromptController.onNewIntent(intent);
            }
            if (communityReviewPromptController != null) {
                communityReviewPromptController.onNewIntent(intent);
            }
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            final String action = intent.getAction();
            if ((action != null && action.equals(Intent.ACTION_MAIN))
                    && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                return;
            }
        }

        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder);
        if (fragment instanceof OnKeyDownListener
                && !bottomSheetHiddenOrCollapsed()) {
            // Provide keyDown event to fragment which then sends this event
            // to the main player service
            return ((OnKeyDownListener) fragment).onKeyDown(keyCode)
                    || super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called");
        }

        if (DeviceUtils.isTv(this)) {
            if (mainBinding.getRoot().isDrawerOpen(drawerLayoutBinding.navigation)) {
                mainBinding.getRoot().closeDrawers();
                return;
            }
        }

        // In case bottomSheet is not visible on the screen or collapsed we can assume that the user
        // interacts with a fragment inside fragment_holder so all back presses should be
        // handled by it
        if (bottomSheetHiddenOrCollapsed()) {
            final FragmentManager fm = getSupportFragmentManager();
            final Fragment fragment = fm.findFragmentById(R.id.fragment_holder);
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragment instanceof BackPressable) {
                if (((BackPressable) fragment).onBackPressed()) {
                    return;
                }
            } else if (fragment instanceof CommentRepliesFragment) {
                // expand DetailsFragment if CommentRepliesFragment was opened
                // to show the top level comments again
                // Expand DetailsFragment if CommentRepliesFragment was opened
                // and no other CommentRepliesFragments are on top of the back stack
                // to show the top level comments again.
                openDetailFragmentFromCommentReplies(fm, false);
            }

        } else {
            final Fragment fragmentPlayer = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_player_holder);
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragmentPlayer instanceof BackPressable) {
                if (!((BackPressable) fragmentPlayer).onBackPressed()) {
                    BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder)
                            .setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                return;
            }
        }

        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (final int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return;
            }
        }
        switch (requestCode) {
            case PermissionHelper.DOWNLOADS_REQUEST_CODE:
                NavigationHelper.openDownloads(this);
                break;
            case PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE:
                final Fragment fragment = getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_player_holder);
                if (fragment instanceof VideoDetailFragment) {
                    ((VideoDetailFragment) fragment).openDownloadDialog();
                }
                break;
            case PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE:
                NotificationWorker.initialize(this);
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
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment fragment = fm.findFragmentById(R.id.fragment_holder);

        if (fragment instanceof CommentRepliesFragment) {
            // Expand DetailsFragment if CommentRepliesFragment was opened
            // and no other CommentRepliesFragments are on top of the back stack
            // to show the top level comments again.
            openDetailFragmentFromCommentReplies(fm, true);
        } else if (!NavigationHelper.tryGotoSearchFragment(fm)) {
            // If search fragment wasn't found in the backstack go to the main fragment
            NavigationHelper.gotoMainFragment(fm);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]");
        }
        super.onCreateOptionsMenu(menu);

        final Fragment fragment =
                getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (!(fragment instanceof SearchFragment)) {
            toolbarLayoutBinding.toolbarSearchContainer.getRoot().setVisibility(View.GONE);
        }
        if (!(fragment instanceof MainFragment)) {
            toolbarLayoutBinding.toolbarContextualSearchContainer.getRoot()
                    .setVisibility(View.GONE);
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        updateDrawerNavigation();

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final boolean result = super.onPrepareOptionsMenu(menu);
        org.schabi.newpipe.player.recovery.QueueRecoveryUi.prepareMenu(this, menu);
        org.schabi.newpipe.local.history.IncognitoToolbarUi.prepareMenu(
                this, menu, toolbarLayoutBinding.incognitoStatus);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (DEBUG) {
            Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");
        }

        if (item.getItemId() == R.id.action_recover_queue) {
            org.schabi.newpipe.player.recovery.QueueRecoveryUi.show(this);
            return true;
        }
        if (item.getItemId() == R.id.action_incognito) {
            final boolean enabled = !org.schabi.newpipe.local.history.IncognitoMode.isEnabled(this);
            org.schabi.newpipe.local.history.IncognitoMode.setEnabled(this, enabled);
            invalidateOptionsMenu();
            android.widget.Toast.makeText(this, enabled ? R.string.incognito_enabled_message
                    : R.string.incognito_disabled_message, android.widget.Toast.LENGTH_LONG).show();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            onHomeButtonPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initFragments() {
        if (DEBUG) {
            Log.d(TAG, "initFragments() called");
        }
        StateSaver.clearStateFiles();
        if (getIntent() != null && (getIntent().hasExtra(Constants.KEY_LINK_TYPE)
                || getIntent().getBooleanExtra(KEY_OPEN_LOCAL_MEDIA_AUDIO, false)
                || getIntent().hasExtra(LearningReminders.EXTRA_PROFILE_ID)
                || getIntent().hasExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION))) {
            // When user watch a video inside popup and then tries to open the video in main player
            // while the app is closed he will see a blank fragment on place of kiosk.
            // Let's open it first
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                NavigationHelper.openMainFragment(getSupportFragmentManager());
            }

            handleIntent(getIntent());
        } else {
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateDrawerNavigation() {
        if (getSupportActionBar() == null) {
            return;
        }

        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_holder);
        if (fragment instanceof MainFragment) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            if (contextualSearchToolbarActive) {
                toolbarLayoutBinding.toolbar.setNavigationIcon(null);
                toolbarLayoutBinding.toolbar.setNavigationOnClickListener(null);
                mainBinding.getRoot().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED);
                return;
            }
            if (toggle != null) {
                toggle.syncState();
                toolbarLayoutBinding.toolbar.setNavigationOnClickListener(v -> mainBinding.getRoot()
                        .open());
                mainBinding.getRoot().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED);
            }
        } else {
            mainBinding.getRoot().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbarLayoutBinding.toolbar.setNavigationOnClickListener(v -> onHomeButtonPressed());
        }
    }

    public void setContextualSearchToolbarActive(final boolean active) {
        contextualSearchToolbarActive = active;
        updateDrawerNavigation();
    }

    public void rememberMainTabPosition(final int position) {
        if (position >= 0) {
            lastMainTabPosition = position;
        }
    }

    public int consumePendingMainTabPosition() {
        final int pendingPosition = pendingMainTabPosition;
        pendingMainTabPosition = -1;
        return pendingPosition;
    }

    public void showMainNavigationForSearch() {
        searchNavigationActive = true;
        final List<Tab> tabs = TabsManager.getManager(this).getVisibleTabs();
        if (tabs.isEmpty()) {
            hideSearchNavigationViews();
            return;
        }

        final int orientation = getResources().getConfiguration().orientation;
        final int positionKey = orientation == Configuration.ORIENTATION_LANDSCAPE
                ? R.string.tablet_navigation_landscape_position_key
                : R.string.tablet_navigation_portrait_position_key;
        final String defaultPosition = getString(R.string.tablet_navigation_position_default_value);
        final String configuredPosition = sharedPreferences.getString(
                getString(positionKey), defaultPosition);
        final boolean largeScreen = isLargeScreenNavigationDevice();
        final boolean useNavigationRail = TabletNavigationPositionResolver.useNavigationRail(
                largeScreen, orientation, configuredPosition);
        final NavigationBarView navigation = useNavigationRail
                ? mainBinding.mainNavigationRail : mainBinding.mainBottomNavigation;
        final NavigationBarView otherNavigation = useNavigationRail
                ? mainBinding.mainBottomNavigation : mainBinding.mainNavigationRail;
        clearSearchNavigationView(otherNavigation);

        final int itemLimit = useNavigationRail
                ? SEARCH_NAVIGATION_RAIL_MAX_ITEM_COUNT
                : SEARCH_BOTTOM_NAVIGATION_MAX_ITEM_COUNT;
        final boolean mainTabsAtBottom = sharedPreferences.getBoolean(
                getString(R.string.main_tabs_position_key), true);
        final HomeNavigationMode navigationMode = HomeNavigationModeResolver
                .resolveNavigationMode(tabs.size(), mainTabsAtBottom);
        final boolean showNavigation = shouldShowSearchNavigation(
                largeScreen,
                tabs.size(),
                itemLimit,
                navigationMode == HomeNavigationMode.BOTTOM_NAVIGATION);
        if (!showNavigation) {
            clearSearchNavigationView(navigation);
            resetSearchNavigationContentInsets();
            notifySearchNavigationVisibilityChanged();
            return;
        }

        populateSearchNavigation(navigation, tabs);
        applySearchNavigationContentInsets(useNavigationRail);
        navigation.setTag(Boolean.TRUE);
        if (playerCoversMainNavigation()) {
            navigation.setVisibility(View.INVISIBLE);
        } else {
            navigation.setVisibility(View.VISIBLE);
        }
        navigation.bringToFront();
        notifySearchNavigationVisibilityChanged();
    }

    public void hideMainNavigationForSearch() {
        if (!searchNavigationActive) {
            return;
        }
        searchNavigationActive = false;
        hideSearchNavigationViews();
    }

    private void hideSearchNavigationViews() {
        clearSearchNavigationView(mainBinding.mainBottomNavigation);
        clearSearchNavigationView(mainBinding.mainNavigationRail);
        resetSearchNavigationContentInsets();
        notifySearchNavigationVisibilityChanged();
    }

    private void populateSearchNavigation(@NonNull final NavigationBarView navigation,
                                          @NonNull final List<Tab> tabs) {
        navigation.setOnItemSelectedListener(null);
        navigation.setOnItemReselectedListener(null);
        final Menu menu = navigation.getMenu();
        menu.clear();

        final int selectedPosition = normalizeSearchNavigationTabPosition(
                lastMainTabPosition, tabs.size());
        lastMainTabPosition = selectedPosition;
        for (int index = 0; index < tabs.size(); index++) {
            final Tab tab = tabs.get(index);
            final String tabName = tab.getTabName(this);
            final MenuItem item = menu.add(Menu.NONE,
                    SEARCH_NAVIGATION_ITEM_ID_BASE + index,
                    index,
                    getSearchNavigationDisplayLabel(tab, tabName));
            final int iconRes = tab.getTabIconRes(this);
            item.setIcon(iconRes > 0 ? iconRes : R.drawable.ic_asterisk);
            item.setCheckable(true);
            item.setChecked(index == selectedPosition);
            MenuItemCompat.setContentDescription(item, tabName);
        }
        applySearchNavigationLabelVisibility(navigation);
        navigation.setAlpha(1.0f);
        navigation.setTranslationX(0.0f);
        navigation.setTranslationY(0.0f);
        navigation.setOnItemSelectedListener(item -> {
            final int position = item.getItemId() - SEARCH_NAVIGATION_ITEM_ID_BASE;
            if (position < 0 || position >= tabs.size()) {
                return false;
            }
            lastMainTabPosition = position;
            pendingMainTabPosition = position;
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
            return true;
        });
    }

    private String getSearchNavigationDisplayLabel(@NonNull final Tab tab,
                                                   @NonNull final String tabName) {
        if (tab instanceof Tab.KioskTab
                && "live".equals(((Tab.KioskTab) tab).getKioskId())) {
            return getString(R.string.duration_live);
        }
        if (tab instanceof Tab.DefaultKioskTab
                && getString(R.string.recommended_lives).equals(tabName)) {
            return getString(R.string.duration_live);
        }
        if (tab.getTabId() == Tab.BookmarksTab.ID) {
            return getString(R.string.bottom_navigation_tab_bookmarks);
        }
        return tabName;
    }

    private void applySearchNavigationLabelVisibility(@NonNull final NavigationBarView navigation) {
        if (navigation == mainBinding.mainNavigationRail
                && isCompactLargeScreenNavigationEnabled()) {
            navigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_UNLABELED);
            return;
        }

        final String defaultValue = getString(R.string.bottom_navigation_labels_default_value);
        final String configuredValue = sharedPreferences.getString(
                getString(R.string.bottom_navigation_labels_key), defaultValue);
        final int labelVisibilityMode;
        if (getString(R.string.bottom_navigation_labels_always_value)
                .equals(configuredValue)) {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED;
        } else if (getString(R.string.bottom_navigation_labels_hidden_value)
                .equals(configuredValue)) {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED;
        } else {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_SELECTED;
        }
        navigation.setLabelVisibilityMode(labelVisibilityMode);
    }

    private void clearSearchNavigationView(@NonNull final NavigationBarView navigation) {
        navigation.setOnItemSelectedListener(null);
        navigation.setOnItemReselectedListener(null);
        navigation.getMenu().clear();
        navigation.setTag(Boolean.FALSE);
        navigation.setAlpha(1.0f);
        navigation.setTranslationX(0.0f);
        navigation.setTranslationY(0.0f);
        navigation.setVisibility(View.GONE);
    }

    private boolean playerCoversMainNavigation() {
        final BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(
                mainBinding.fragmentPlayerHolder);
        final int state = behavior.getState();
        return state == BottomSheetBehavior.STATE_EXPANDED
                || state == BottomSheetBehavior.STATE_DRAGGING
                || state == BottomSheetBehavior.STATE_SETTLING
                || state == BottomSheetBehavior.STATE_HALF_EXPANDED;
    }

    private void applySearchNavigationContentInsets(final boolean navigationRail) {
        final int startInset = navigationRail ? getMainNavigationRailWidth() : 0;
        final int bottomInset = navigationRail
                ? 0 : getResources().getDimensionPixelSize(R.dimen.main_bottom_navigation_height);
        setSearchNavigationStartMargin(mainBinding.fragmentHolder, startInset);
        setSearchNavigationStartMargin(mainBinding.toolbarLayout.getRoot(), startInset);
        setSearchNavigationBottomMargin(mainBinding.fragmentHolder, bottomInset);
    }

    private void resetSearchNavigationContentInsets() {
        setSearchNavigationStartMargin(mainBinding.fragmentHolder, 0);
        setSearchNavigationStartMargin(mainBinding.toolbarLayout.getRoot(), 0);
        setSearchNavigationBottomMargin(mainBinding.fragmentHolder, 0);
    }

    private static void setSearchNavigationStartMargin(@NonNull final View view,
                                                       final int margin) {
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.getMarginStart() != margin) {
            params.setMarginStart(margin);
            view.setLayoutParams(params);
        }
    }

    private static void setSearchNavigationBottomMargin(@NonNull final View view,
                                                        final int margin) {
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin;
            view.setLayoutParams(params);
        }
    }

    private void notifySearchNavigationVisibilityChanged() {
        final BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(
                mainBinding.fragmentPlayerHolder);
        if (behavior instanceof CustomBottomSheetBehavior customBehavior) {
            customBehavior.onBottomNavigationVisibilityChanged();
        }
    }

    static boolean shouldShowSearchNavigation(final boolean largeScreen,
                                              final int tabCount,
                                              final int itemLimit,
                                              final boolean phoneUsesBottomNavigation) {
        return largeScreen ? tabCount <= itemLimit : phoneUsesBottomNavigation;
    }

    static int normalizeSearchNavigationTabPosition(final int position, final int tabCount) {
        return position >= 0 && position < tabCount ? position : 0;
    }

    private boolean isLargeScreenNavigationDevice() {
        return DeviceUtils.isTablet(this) || DeviceUtils.isTv(this);
    }

    public boolean isCompactLargeScreenNavigationEnabled() {
        return isLargeScreenNavigationDevice()
                && sharedPreferences.getBoolean(
                        getString(R.string.compact_large_screen_navigation_key), false);
    }

    public int getMainNavigationRailWidth() {
        final int configuredWidth = mainBinding.mainNavigationRail.getLayoutParams().width;
        return configuredWidth > 0 ? configuredWidth : defaultNavigationRailWidth;
    }

    private void applyLargeScreenChrome() {
        final boolean compact = isCompactLargeScreenNavigationEnabled();
        final int toolbarHeight = compact
                ? getResources().getDimensionPixelSize(R.dimen.main_compact_toolbar_height)
                : defaultToolbarHeight;
        final int navigationRailWidth = compact
                ? getResources().getDimensionPixelSize(
                        R.dimen.main_compact_navigation_rail_width)
                : defaultNavigationRailWidth;

        setViewHeight(toolbarLayoutBinding.toolbar, toolbarHeight);
        toolbarLayoutBinding.toolbar.setMinimumHeight(toolbarHeight);
        setViewHeight(toolbarLayoutBinding.toolbarSearchContainer.getRoot(), toolbarHeight);
        setViewHeight(toolbarLayoutBinding.toolbarContextualSearchContainer.getRoot(),
                toolbarHeight);

        final ViewGroup.MarginLayoutParams fragmentParams =
                (ViewGroup.MarginLayoutParams) mainBinding.fragmentHolder.getLayoutParams();
        final int privacyStatusHeight = toolbarLayoutBinding.incognitoStatus.getVisibility()
                == View.VISIBLE ? toolbarLayoutBinding.incognitoStatus.getHeight() : 0;
        final int fragmentTopMargin = (compact ? toolbarHeight : defaultFragmentTopMargin)
                + privacyStatusHeight;
        if (fragmentParams.topMargin != fragmentTopMargin) {
            fragmentParams.topMargin = fragmentTopMargin;
            mainBinding.fragmentHolder.setLayoutParams(fragmentParams);
        }

        final ViewGroup.LayoutParams navigationParams =
                mainBinding.mainNavigationRail.getLayoutParams();
        if (navigationParams.width != navigationRailWidth) {
            navigationParams.width = navigationRailWidth;
            mainBinding.mainNavigationRail.setLayoutParams(navigationParams);
        }

        if (searchNavigationActive) {
            showMainNavigationForSearch();
        }
    }

    private static void setViewHeight(@NonNull final View view, final int height) {
        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    private void handleIntent(final Intent intent) {
        try {
            if (DEBUG) {
                Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
            }

            if (intent.hasExtra(LearningReminders.EXTRA_PROFILE_ID)) {
                if (!LearningReminders.selectProfile(this, intent)) {
                    return;
                }
                if (!Objects.equals(activeProfileId, ProfileManager.getActiveProfileId(this))) {
                    activeProfileId = ProfileManager.getActiveProfileId(this);
                    recreate();
                    return;
                }
                intent.removeExtra(LearningReminders.EXTRA_PROFILE_ID);
                mainBinding.getRoot().closeDrawers();
                PlayerHolder.getInstance().exitMainPlayerFullscreenForMiniPlayer();
                if (!bottomSheetHiddenOrCollapsed()) {
                    BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder)
                            .setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                NavigationHelper.openMainFragment(getSupportFragmentManager());
                NavigationHelper.openLearningDashboardFragment(getSupportFragmentManager());
            } else if (intent.hasExtra(Constants.KEY_LINK_TYPE)) {
                final String url = intent.getStringExtra(Constants.KEY_URL);
                final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                String title = intent.getStringExtra(Constants.KEY_TITLE);
                if (title == null) {
                    title = "";
                }

                final StreamingService.LinkType linkType = ((StreamingService.LinkType) intent
                        .getSerializableExtra(Constants.KEY_LINK_TYPE));
                assert linkType != null;
                switch (linkType) {
                    case STREAM:
                        final String intentCacheKey = intent.getStringExtra(
                                Player.PLAY_QUEUE_KEY);
                        final PlayQueue playQueue = intentCacheKey != null
                                ? SerializedCache.getInstance()
                                .take(intentCacheKey, PlayQueue.class)
                                : null;

                        final boolean switchingPlayers = intent.getBooleanExtra(
                                VideoDetailFragment.KEY_SWITCHING_PLAYERS, false);
                        NavigationHelper.openVideoDetailFragment(
                                getApplicationContext(), getSupportFragmentManager(),
                                serviceId, url, title, playQueue, switchingPlayers);
                        break;
                    case CHANNEL:
                        NavigationHelper.openChannelFragment(getSupportFragmentManager(),
                                serviceId, url, title);
                        break;
                    case PLAYLIST:
                        NavigationHelper.openPlaylistFragment(getSupportFragmentManager(),
                                serviceId, url, title);
                        break;
                }
            } else if (intent.hasExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION)) {
                final TvRemoteAction destination = TvRemoteAction.fromId(
                        intent.getStringExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION));
                intent.removeExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION);
                if (destination != null) {
                    openRemoteDestination(destination);
                }
            } else if (intent.hasExtra(Constants.KEY_OPEN_SEARCH)) {
                String searchString = intent.getStringExtra(Constants.KEY_SEARCH_STRING);
                if (searchString == null) {
                    searchString = "";
                }
                final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                NavigationHelper.openSearchFragment(
                        getSupportFragmentManager(),
                        serviceId,
                        searchString);

            } else if (intent.getBooleanExtra(KEY_OPEN_LOCAL_MEDIA_AUDIO, false)) {
                NavigationHelper.openLocalMediaAudioFragment(getSupportFragmentManager());

            } else {
                NavigationHelper.gotoMainFragment(getSupportFragmentManager());
            }
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Handling intent", e);
        }
    }

    /**
     * Opens a remote shortcut using the same destinations as the navigation drawer.
     * @param destination the app screen to open
     */
    public void openRemoteDestination(@NonNull final TvRemoteAction destination) {
        final FragmentManager manager = getSupportFragmentManager();
        if (manager.isStateSaved() || destination.getPlayback()
                || destination == TvRemoteAction.SETTINGS) {
            return;
        }
        mainBinding.getRoot().closeDrawers();
        PlayerHolder.getInstance().exitMainPlayerFullscreenForMiniPlayer();
        if (!bottomSheetHiddenOrCollapsed()) {
            BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder)
                    .setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        switch (destination) {
            case HOME:
                NavigationHelper.gotoMainFragment(manager);
                break;
            case SEARCH:
                NavigationHelper.openSearchFragment(manager,
                        ServiceHelper.getSelectedServiceId(this), "");
                break;
            case FEED:
                NavigationHelper.openFeedFragment(manager);
                break;
            case SUBSCRIPTIONS:
                NavigationHelper.openSubscriptionFragment(manager);
                break;
            case BOOKMARKS:
                NavigationHelper.openBookmarksFragment(manager);
                break;
            case HISTORY:
                NavigationHelper.openStatisticFragment(manager);
                break;
            default:
                break;
        }
    }

    private void openMiniPlayerIfMissing() {
        final Fragment fragmentPlayer = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder);
        if (fragmentPlayer == null) {
            // We still don't have a fragment attached to the activity. It can happen when a user
            // started popup or background players without opening a stream inside the fragment.
            // Adding it in a collapsed state (only mini player will be visible).
            NavigationHelper.showMiniPlayer(getSupportFragmentManager());
        }
    }

    private void openMiniPlayerUponPlayerStarted() {
        if (getIntent().getSerializableExtra(Constants.KEY_LINK_TYPE)
                == StreamingService.LinkType.STREAM) {
            // handleIntent() already takes care of opening video detail fragment
            // due to an intent containing a STREAM link
            return;
        }

        final boolean playerOpen = PlayerHolder.getInstance().isPlayerOpen();
        if (playerOpen) {
            // if the player is already open, no need for a broadcast receiver
            openMiniPlayerIfMissing();
        } else {
            if (!shouldRegisterPlayerStartedReceiver(
                    activityStarted, playerOpen, broadcastReceiver != null)) {
                return;
            }
            // listen for player start intent being sent around
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    if (Objects.equals(intent.getAction(),
                            VideoDetailFragment.ACTION_PLAYER_STARTED)
                            && PlayerHolder.getInstance().isPlayerOpen()) {
                        openMiniPlayerIfMissing();
                        // At this point the player is added 100%, we can unregister. Other actions
                        // are useless since the fragment will not be removed after that.
                        unregisterPlayerStartedReceiver();
                    }
                }
            };
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(VideoDetailFragment.ACTION_PLAYER_STARTED);
            ContextCompat.registerReceiver(this, broadcastReceiver, intentFilter,
                    ContextCompat.RECEIVER_EXPORTED);

            // If the PlayerHolder is not bound yet, but the service is running, try to bind to it.
            // Once the connection is established, the ACTION_PLAYER_STARTED will be sent.
            PlayerHolder.getInstance().tryBindIfNeeded(this);
        }
    }

    private void unregisterPlayerStartedReceiver() {
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
    }

    static boolean shouldRegisterPlayerStartedReceiver(final boolean activityStarted,
                                                       final boolean playerOpen,
                                                       final boolean receiverRegistered) {
        return activityStarted && !playerOpen && !receiverRegistered;
    }

    private void openDetailFragmentFromCommentReplies(
            @NonNull final FragmentManager fm,
            final boolean popBackStack
    ) {
        // obtain the name of the fragment under the replies fragment that's going to be popped
        @Nullable final String fragmentUnderEntryName;
        if (fm.getBackStackEntryCount() < 2) {
            fragmentUnderEntryName = null;
        } else {
            fragmentUnderEntryName = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 2)
                    .getName();
        }

        // the root comment is the comment for which the user opened the replies page
        @Nullable final CommentRepliesFragment repliesFragment =
                (CommentRepliesFragment) fm.findFragmentByTag(CommentRepliesFragment.TAG);
        @Nullable final CommentsInfoItem rootComment =
                repliesFragment == null ? null : repliesFragment.getCommentsInfoItem();

        // sometimes this function pops the backstack, other times it's handled by the system
        if (popBackStack) {
            fm.popBackStackImmediate();
        }

        // only expand the bottom sheet back if there are no more nested comment replies fragments
        // stacked under the one that is currently being popped
        if (CommentRepliesFragment.TAG.equals(fragmentUnderEntryName)) {
            return;
        }

        final BottomSheetBehavior<FragmentContainerView> behavior = BottomSheetBehavior
                .from(mainBinding.fragmentPlayerHolder);
        // do not return to the comment if the details fragment was closed
        if (behavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            return;
        }

        // scroll to the root comment once the bottom sheet expansion animation is finished
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet,
                                       final int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    final Fragment detailFragment = fm.findFragmentById(
                            R.id.fragment_player_holder);
                    if (detailFragment instanceof VideoDetailFragment && rootComment != null) {
                        // should always be the case
                        ((VideoDetailFragment) detailFragment).scrollToComment(rootComment);
                    }
                    behavior.removeBottomSheetCallback(this);
                }
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                // not needed, listener is removed once the sheet is expanded
            }
        });

        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private boolean bottomSheetHiddenOrCollapsed() {
        final BottomSheetBehavior<FrameLayout> bottomSheetBehavior =
                BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder);

        final int sheetState = bottomSheetBehavior.getState();
        return sheetState == BottomSheetBehavior.STATE_HIDDEN
                || sheetState == BottomSheetBehavior.STATE_COLLAPSED;
    }

    private void showApi23RequirementDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return; // only show dialog on the devices that will stop being supported
        }

        final var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final var shownKey = getString(R.string.api23_requirement_dialog_shown_key);
        if (prefs.getBoolean(shownKey, false)) {
            return; // dialog was already shown in the past, no need to show it again
        }

        final var dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.api23_requirement_dialog_title)
                .setCancelable(false)
                .setMessage(R.string.api23_requirement_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> prefs.edit()
                        .putBoolean(shownKey, true)
                        .apply())
                .setNegativeButton(R.string.api23_requirement_dialog_blogpost, null)
                .show();

        // If we use setNegativeButton, dialog will close after pressing the button,
        // but we want it to close only when positive button is pressed
        final var blogpostUrl = "https://newpipe.net/blog/pinned/announcement/drop-android-5/";
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener(v -> ShareUtils.openUrlInBrowser(this, blogpostUrl));
    }
}
