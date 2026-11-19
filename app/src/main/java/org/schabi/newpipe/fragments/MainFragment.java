package org.schabi.newpipe.fragments;

import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.BELOW;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_BOTTOM;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_TOP;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior;
import org.schabi.newpipe.settings.tabs.HomeNavigationMode;
import org.schabi.newpipe.settings.tabs.HomeNavigationModeResolver;
import org.schabi.newpipe.settings.tabs.HomeStartupTab;
import org.schabi.newpipe.settings.tabs.HomeTabGroupIconSync;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabletNavigationPositionResolver;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.settings.tabs.TabsJsonHelper;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;

public class MainFragment extends BaseFragment
        implements TabLayout.OnTabSelectedListener, BackPressable {
    private static final int BOTTOM_NAVIGATION_MAX_ITEM_COUNT = 5;
    private static final int NAVIGATION_RAIL_MAX_ITEM_COUNT = 7;
    private static final int BOTTOM_NAVIGATION_ITEM_ID_BASE = 10_000;
    private static final long CONTEXTUAL_SEARCH_DEBOUNCE_MILLIS = 250L;
    private static final String STATE_CONTEXTUAL_SEARCH_OPEN = "contextual_search_open";
    private static final String STATE_CONTEXTUAL_SEARCH_QUERY = "contextual_search_query";
    private static final String STATE_CONTEXTUAL_SEARCH_TAB = "contextual_search_tab";

    private FragmentMainBinding binding;
    private NavigationBarView bottomNavigation;
    private BottomNavigationView bottomNavigationView;
    private NavigationRailView navigationRailView;
    private SelectedTabsPagerAdapter pagerAdapter;
    private Disposable homeGroupIconSubscription;

    private View contextualSearchContainer;
    private EditText contextualSearchEditText;
    private View contextualSearchClose;
    private TextWatcher contextualSearchTextWatcher;
    private ContextualSearchable contextualSearchTarget;
    private final Handler contextualSearchHandler = new Handler(Looper.getMainLooper());
    private boolean contextualSearchOpen;
    private boolean tabsSetupInProgress;
    private String contextualSearchQuery = "";
    private int contextualSearchTabPosition = -1;
    private int contextualSearchFabBaseBottomMargin;
    private int contextualSearchImeBottomInset;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;
    private boolean mainTabsPositionBottom;
    private String mainTabsPositionKey;
    private String bottomNavigationLabelsKey;
    private String bottomNavigationLabelsValue;
    private boolean largeScreenNavigation;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            contextualSearchOpen = savedInstanceState
                    .getBoolean(STATE_CONTEXTUAL_SEARCH_OPEN, false);
            contextualSearchQuery = savedInstanceState
                    .getString(STATE_CONTEXTUAL_SEARCH_QUERY, "");
            contextualSearchTabPosition = savedInstanceState
                    .getInt(STATE_CONTEXTUAL_SEARCH_TAB, -1);
        }
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            hasTabsChanged = true;
            if (isResumed() && binding != null) {
                // Preference listeners run from a snapshot. Finish notifying the current tabs
                // before replacing them, so detached children cannot receive this same change.
                binding.getRoot().post(() -> {
                    if (isResumed() && binding != null && hasTabsChanged) {
                        setupTabs();
                    }
                });
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        mainTabsPositionKey = getString(R.string.main_tabs_position_key);
        mainTabsPositionBottom = prefs.getBoolean(mainTabsPositionKey, true);
        bottomNavigationLabelsKey = getString(R.string.bottom_navigation_labels_key);
        bottomNavigationLabelsValue = getBottomNavigationLabelsValue();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);
        bottomNavigationView = requireActivity().findViewById(R.id.main_bottom_navigation);
        navigationRailView = requireActivity().findViewById(R.id.main_navigation_rail);
        selectMainNavigation();
        updateBottomNavigationLabelVisibility();

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);
        binding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                if (contextualSearchOpen && !tabsSetupInProgress
                        && contextualSearchTabPosition != position) {
                    closeContextualSearch();
                }
                updateTitleForTab(position);
                updateBottomNavigationSelection(position);
                rememberMainTabPosition(position);
                requireActivity().invalidateOptionsMenu();
            }
        });
        setupTabs();
        if (savedInstanceState == null && !tabsList.isEmpty()) {
            binding.pager.setCurrentItem(
                    HomeStartupTab.startupIndex(requireContext(), tabsList), false);
        }
        // Explicit navigation to a Home tab always overrides the startup default.
        restorePendingMainTabSelection();
        rememberMainTabPosition(binding.pager.getCurrentItem());
        initContextualSearchToolbar();
        initContextualSearchInsets();
        if (contextualSearchOpen) {
            binding.pager.post(this::restoreContextualSearch);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean navigationChanged = selectMainNavigation();

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }

        final boolean newMainTabsPosition = prefs.getBoolean(mainTabsPositionKey, true);
        if (mainTabsPositionBottom != newMainTabsPosition) {
            mainTabsPositionBottom = newMainTabsPosition;
        }
        final String newBottomNavigationLabelsValue = getBottomNavigationLabelsValue();
        if (!bottomNavigationLabelsValue.equals(newBottomNavigationLabelsValue)) {
            bottomNavigationLabelsValue = newBottomNavigationLabelsValue;
            updateBottomNavigationLabelVisibility();
        }
        if (navigationChanged) {
            updateBottomNavigationItems();
        }
        updateBottomNavigationLabelVisibility();
        updateMainNavigationMode();
        scheduleBottomNavigationRemeasure();
        observeHomeGroupIcons();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding == null) {
            return;
        }
        binding.getRoot().post(() -> {
            if (binding == null || !isAdded()) {
                return;
            }
            if (selectMainNavigation()) {
                updateBottomNavigationItems();
            }
            updateMainNavigationMode();
            scheduleBottomNavigationRemeasure();
        });
    }

    @Override
    public void onPause() {
        stopObservingHomeGroupIcons();
        setBottomNavigationRequestedVisibility(false);
        if (contextualSearchEditText != null) {
            KeyboardUtil.hideKeyboard(activity, contextualSearchEditText);
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putBoolean(STATE_CONTEXTUAL_SEARCH_OPEN, contextualSearchOpen);
        outState.putString(STATE_CONTEXTUAL_SEARCH_QUERY, contextualSearchQuery);
        outState.putInt(STATE_CONTEXTUAL_SEARCH_TAB, contextualSearchTabPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    @Override
    public void onDestroyView() {
        stopObservingHomeGroupIcons();
        contextualSearchHandler.removeCallbacksAndMessages(null);
        if (binding != null) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), null);
        }
        contextualSearchImeBottomInset = 0;
        setActivityContextualSearchToolbarActive(false);
        if (contextualSearchEditText != null && contextualSearchTextWatcher != null) {
            contextualSearchEditText.removeTextChangedListener(contextualSearchTextWatcher);
        }
        if (contextualSearchContainer != null) {
            contextualSearchContainer.setVisibility(View.GONE);
        }
        contextualSearchContainer = null;
        contextualSearchEditText = null;
        contextualSearchClose = null;
        contextualSearchTextWatcher = null;
        contextualSearchTarget = null;
        clearMainNavigation(bottomNavigationView);
        clearMainNavigation(navigationRailView);
        bottomNavigation = null;
        bottomNavigationView = null;
        navigationRailView = null;
        super.onDestroyView();
        binding = null;
    }

    private void rememberMainTabPosition(final int position) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).rememberMainTabPosition(position);
        }
    }

    private void restorePendingMainTabSelection() {
        if (!(activity instanceof MainActivity) || binding == null) {
            return;
        }
        final int position = ((MainActivity) activity).consumePendingMainTabPosition();
        if (position >= 0 && position < tabsList.size()) {
            binding.pager.setCurrentItem(position, false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            final boolean localSearch = shouldUseContextualSearch(prefersGlobalSearch(),
                    getCurrentContextualSearchable() != null);
            searchItem.setVisible(!contextualSearchOpen);
            if (!localSearch && !KidsMode.isSearchAllowed(requireContext(),
                    ServiceHelper.getSelectedServiceId(requireContext()))) {
                searchItem.setVisible(false);
            }
        }

        updateContextualSearchToolbar(false);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            final ContextualSearchable searchable = getCurrentContextualSearchable();
            if (shouldUseContextualSearch(prefersGlobalSearch(), searchable != null)) {
                openContextualSearch(searchable);
                return true;
            }
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean prefersGlobalSearch() {
        final String defaultValue =
                getString(R.string.search_button_behavior_current_service_value);
        final String configuredValue = prefs.getString(
                getString(R.string.search_button_behavior_key), defaultValue);
        return getString(R.string.search_button_behavior_current_service_value)
                .equals(configuredValue);
    }

    static boolean shouldUseContextualSearch(final boolean preferGlobalSearch,
                                             final boolean contextualSearchAvailable) {
        return !preferGlobalSearch && contextualSearchAvailable;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsSetupInProgress = true;
        tabsList.clear();
        tabsList.addAll(tabsManager.getVisibleTabs());

        final boolean replacePagerAdapter = pagerAdapter == null
                || !pagerAdapter.sameTabs(tabsList);
        if (contextualSearchOpen && pagerAdapter != null && replacePagerAdapter) {
            closeContextualSearch();
        }
        if (replacePagerAdapter) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateBottomNavigationItems();
        updateMainNavigationMode();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
        tabsSetupInProgress = false;

        if (contextualSearchOpen
                && (contextualSearchTabPosition < 0
                || contextualSearchTabPosition >= tabsList.size())) {
            closeContextualSearch();
        }
        if (isResumed()) {
            observeHomeGroupIcons();
        }
    }

    private void observeHomeGroupIcons() {
        stopObservingHomeGroupIcons();
        if (binding == null || !isAdded()) {
            return;
        }
        homeGroupIconSubscription = HomeTabGroupIconSync.observe(
                requireContext(), () -> tabsList, () -> {
                    if (binding != null && isAdded()) {
                        updateTabsIconAndDescription();
                    }
                });
    }

    private void stopObservingHomeGroupIcons() {
        if (homeGroupIconSubscription != null) {
            homeGroupIconSubscription.dispose();
            homeGroupIconSubscription = null;
        }
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                tabToSet.setIcon(getSafeTabIconRes(tab));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
    }

    private int getSafeTabIconRes(final Tab tab) {
        final int iconRes = tab.getTabIconRes(requireContext());
        return iconRes > 0 ? iconRes : R.drawable.ic_asterisk;
    }

    private boolean selectMainNavigation() {
        if (bottomNavigationView == null || navigationRailView == null) {
            return false;
        }

        final int orientation = getResources().getConfiguration().orientation;
        final int positionKey = orientation == Configuration.ORIENTATION_LANDSCAPE
                ? R.string.tablet_navigation_landscape_position_key
                : R.string.tablet_navigation_portrait_position_key;
        final String defaultPosition = getString(
                R.string.tablet_navigation_position_default_value);
        final String position = prefs.getString(getString(positionKey), defaultPosition);
        largeScreenNavigation = DeviceUtils.isTablet(requireContext())
                || DeviceUtils.isTv(requireContext());
        final boolean useNavigationRail = TabletNavigationPositionResolver.useNavigationRail(
                largeScreenNavigation, orientation, position);
        final NavigationBarView selectedNavigation = useNavigationRail
                ? navigationRailView : bottomNavigationView;
        if (bottomNavigation == selectedNavigation) {
            return false;
        }

        if (bottomNavigation != null) {
            setNavigationRailContentInset(false);
            clearMainNavigation(bottomNavigation);
        }
        bottomNavigation = selectedNavigation;
        setBottomNavigationListeners();
        updateBottomNavigationLabelVisibility();
        return true;
    }

    private void setBottomNavigationListeners() {
        if (bottomNavigation == null) {
            return;
        }
        bottomNavigation.setOnItemSelectedListener(item -> {
            final int position = getBottomNavigationItemPosition(item.getItemId());
            if (position < 0 || position >= tabsList.size()) {
                return false;
            }
            if (binding.pager.getCurrentItem() != position) {
                binding.pager.setCurrentItem(position);
            }
            updateTitleForTab(position);
            return true;
        });
        bottomNavigation.setOnItemReselectedListener(item -> {
            final int position = getBottomNavigationItemPosition(item.getItemId());
            if (position >= 0 && position < tabsList.size()) {
                updateTitleForTab(position);
                scrollCurrentFeedToTop(position);
            }
        });
    }

    private static void clearMainNavigation(@Nullable final NavigationBarView navigation) {
        if (navigation == null) {
            return;
        }
        navigation.setOnItemSelectedListener(null);
        navigation.setOnItemReselectedListener(null);
        navigation.getMenu().clear();
        navigation.setTag(Boolean.FALSE);
        navigation.setAlpha(1.0f);
        navigation.setTranslationX(0.0f);
        navigation.setTranslationY(0.0f);
        navigation.setVisibility(View.GONE);
    }

    private void updateTitleForTab(final int tabPosition) {
        if (contextualSearchOpen || tabPosition < 0 || tabPosition >= tabsList.size()) {
            return;
        }
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    private void scrollCurrentFeedToTop(final int position) {
        if (pagerAdapter == null || position < 0 || position >= tabsList.size()
                || tabsList.get(position).getTabId() != Tab.FeedTab.ID) {
            return;
        }
        final Fragment primaryFragment = pagerAdapter.getPrimaryFragment();
        if (primaryFragment instanceof FeedFragment feedFragment) {
            feedFragment.scrollToTop();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contextual local search
    //////////////////////////////////////////////////////////////////////////*/

    private void initContextualSearchInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(view);
            final WindowInsetsCompat effectiveInsets =
                    rootInsets == null ? windowInsets : rootInsets;
            final Insets imeInsets = effectiveInsets.getInsets(
                    WindowInsetsCompat.Type.ime());
            final Insets navigationBarInsets = effectiveInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            final int newImeBottomInset = Math.max(
                    0, imeInsets.bottom - navigationBarInsets.bottom);
            if (contextualSearchImeBottomInset != newImeBottomInset) {
                contextualSearchImeBottomInset = newImeBottomInset;
                updateGlobalSearchFabBottomMargin();
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void initContextualSearchToolbar() {
        contextualSearchContainer = requireActivity()
                .findViewById(R.id.toolbar_contextual_search_container);
        contextualSearchEditText = requireActivity()
                .findViewById(R.id.contextual_search_edit_text);
        contextualSearchClose = requireActivity().findViewById(R.id.contextual_search_close);
        contextualSearchClose.setOnClickListener(view -> closeContextualSearch());
        binding.contextualGlobalSearchFab.setOnClickListener(
                view -> openGlobalSearchFromContext());
        contextualSearchEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                KeyboardUtil.hideKeyboard(activity, contextualSearchEditText);
                contextualSearchEditText.clearFocus();
                return true;
            }
            return false;
        });

        contextualSearchTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence text, final int start,
                                          final int count, final int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(final CharSequence text, final int start,
                                      final int before, final int count) {
                // Changes are normalized in afterTextChanged.
            }

            @Override
            public void afterTextChanged(final Editable text) {
                if (!contextualSearchOpen) {
                    return;
                }
                contextualSearchQuery = ContextualSearchHelper.normalizeQuery(text);
                contextualSearchHandler.removeCallbacksAndMessages(null);
                contextualSearchHandler.postDelayed(
                        MainFragment.this::dispatchContextualSearchQuery,
                        CONTEXTUAL_SEARCH_DEBOUNCE_MILLIS);
            }
        };
        contextualSearchEditText.addTextChangedListener(contextualSearchTextWatcher);
    }

    private void openContextualSearch(@NonNull final ContextualSearchable searchable) {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        contextualSearchTarget = searchable;
        contextualSearchOpen = true;
        contextualSearchQuery = "";
        contextualSearchTabPosition = binding.pager.getCurrentItem();

        contextualSearchEditText.setText("");
        updateContextualSearchToolbar(true);
        dispatchContextualSearchQuery();
        requireActivity().invalidateOptionsMenu();
    }

    private void restoreContextualSearch() {
        if (!contextualSearchOpen || binding == null
                || binding.pager.getCurrentItem() != contextualSearchTabPosition) {
            abandonContextualSearchRestore();
            return;
        }

        contextualSearchTarget = getCurrentContextualSearchable();
        if (contextualSearchTarget == null) {
            abandonContextualSearchRestore();
            return;
        }

        contextualSearchEditText.setText(contextualSearchQuery);
        contextualSearchEditText.setSelection(contextualSearchEditText.length());
        updateContextualSearchToolbar(false);
        dispatchContextualSearchQuery();
        requireActivity().invalidateOptionsMenu();
    }

    private void abandonContextualSearchRestore() {
        clearContextualSearchState();
        updateContextualSearchToolbar(false);
        if (binding != null && !tabsList.isEmpty()) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }
        if (isAdded()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void updateContextualSearchToolbar(final boolean requestKeyboard) {
        if (contextualSearchContainer == null || contextualSearchEditText == null
                || binding == null) {
            return;
        }

        contextualSearchContainer.setVisibility(contextualSearchOpen ? View.VISIBLE : View.GONE);
        binding.contextualGlobalSearchFab.setVisibility(
                contextualSearchOpen && KidsMode.isSearchAllowed(requireContext(),
                        ServiceHelper.getSelectedServiceId(requireContext()))
                        ? View.VISIBLE : View.GONE);
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            org.schabi.newpipe.local.feed.FeedGroupSelector
                    .updateSearchVisibility(actionBar, contextualSearchOpen);
        }
        setActivityContextualSearchToolbarActive(contextualSearchOpen);
        if (!contextualSearchOpen || tabsList.isEmpty()) {
            return;
        }

        final int position = binding.pager.getCurrentItem();
        final String tabName = tabsList.get(position).getTabName(requireContext());
        contextualSearchEditText.setHint(getString(R.string.contextual_search_hint, tabName));

        final String serviceName = ServiceHelper.getSelectedServiceName(requireContext());
        final String globalSearchLabel =
                getString(R.string.search_with_service_name, serviceName);
        binding.contextualGlobalSearchFab.setText(globalSearchLabel);
        binding.contextualGlobalSearchFab.setContentDescription(globalSearchLabel);

        if (requestKeyboard) {
            contextualSearchEditText.requestFocus();
            KeyboardUtil.showKeyboard(activity, contextualSearchEditText);
        }
    }

    private void setActivityContextualSearchToolbarActive(final boolean active) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setContextualSearchToolbarActive(active);
        }
    }

    private void dispatchContextualSearchQuery() {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        if (!contextualSearchOpen || contextualSearchTarget == null) {
            return;
        }
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(
                contextualSearchEditText == null
                        ? contextualSearchQuery : contextualSearchEditText.getText());
        contextualSearchTarget.setContextualSearchQuery(contextualSearchQuery);
    }

    private void openGlobalSearchFromContext() {
        final String query = ContextualSearchHelper.normalizeQuery(
                contextualSearchEditText == null ? "" : contextualSearchEditText.getText());
        closeContextualSearch();
        try {
            NavigationHelper.openSearchFragment(getFM(),
                    ServiceHelper.getSelectedServiceId(activity), query);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
        }
    }

    private void closeContextualSearch() {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        if (contextualSearchTarget != null) {
            contextualSearchTarget.setContextualSearchQuery("");
        }
        clearContextualSearchState();

        if (contextualSearchEditText != null) {
            contextualSearchEditText.setText("");
            KeyboardUtil.hideKeyboard(activity, contextualSearchEditText);
        }
        updateContextualSearchToolbar(false);
        if (binding != null && !tabsList.isEmpty()) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }
        if (isAdded()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void clearContextualSearchState() {
        contextualSearchOpen = false;
        contextualSearchQuery = "";
        contextualSearchTabPosition = -1;
        contextualSearchTarget = null;
    }

    @Nullable
    private ContextualSearchable getCurrentContextualSearchable() {
        if (pagerAdapter == null) {
            return null;
        }
        final Fragment primaryFragment = pagerAdapter.getPrimaryFragment();
        return primaryFragment instanceof ContextualSearchable
                ? (ContextualSearchable) primaryFragment : null;
    }

    @Override
    public boolean onBackPressed() {
        if (!contextualSearchOpen) {
            return false;
        }
        closeContextualSearch();
        return true;
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    private void updateBottomNavigationItems() {
        if (bottomNavigation == null) {
            return;
        }

        final Menu menu = bottomNavigation.getMenu();
        menu.clear();
        if (tabsList.size() > getNavigationItemLimit()) {
            return;
        }

        for (int i = 0; i < tabsList.size(); i++) {
            final Tab tab = tabsList.get(i);
            final String tabName = tab.getTabName(requireContext());
            final MenuItem item = menu.add(Menu.NONE, getBottomNavigationItemId(i), i,
                    getBottomNavigationDisplayLabel(tab, tabName));
            item.setIcon(getSafeTabIconRes(tab));
            item.setCheckable(true);
            MenuItemCompat.setContentDescription(item, tabName);
        }

        updateBottomNavigationSelection(binding.pager.getCurrentItem());
        scheduleBottomNavigationRemeasure();
    }

    private String getBottomNavigationDisplayLabel(final Tab tab, final String tabName) {
        if (isLiveKioskTab(tab, tabName)) {
            return getString(R.string.duration_live);
        }
        if (tab.getTabId() == Tab.BookmarksTab.ID) {
            return getString(R.string.bottom_navigation_tab_bookmarks);
        }
        return tabName;
    }

    private boolean isLiveKioskTab(final Tab tab, final String tabName) {
        if (tab instanceof Tab.KioskTab) {
            return "live".equals(((Tab.KioskTab) tab).getKioskId());
        }
        return tab instanceof Tab.DefaultKioskTab
                && getString(R.string.recommended_lives).equals(tabName);
    }

    private void updateBottomNavigationSelection(final int position) {
        if (binding == null || bottomNavigation == null
                || tabsList.size() > getNavigationItemLimit()
                || position < 0 || position >= tabsList.size()) {
            return;
        }
        final int itemId = getBottomNavigationItemId(position);
        if (bottomNavigation.getSelectedItemId() != itemId) {
            bottomNavigation.setSelectedItemId(itemId);
        }
        scheduleBottomNavigationRemeasure();
    }

    private void scheduleBottomNavigationRemeasure() {
        final NavigationBarView navigation = bottomNavigation;
        if (navigation == null) {
            return;
        }

        navigation.post(() -> {
            if (bottomNavigation != navigation) {
                return;
            }
            navigation.requestLayout();
            for (int i = 0; i < navigation.getChildCount(); i++) {
                navigation.getChildAt(i).requestLayout();
            }
            navigation.invalidate();
        });
    }

    private int getBottomNavigationItemId(final int position) {
        return BOTTOM_NAVIGATION_ITEM_ID_BASE + position;
    }

    private int getBottomNavigationItemPosition(final int itemId) {
        return itemId - BOTTOM_NAVIGATION_ITEM_ID_BASE;
    }

    private String getBottomNavigationLabelsValue() {
        final String defaultValue = getString(R.string.bottom_navigation_labels_default_value);
        final String value = prefs.getString(bottomNavigationLabelsKey, defaultValue);
        return value == null ? defaultValue : value;
    }

    private void updateBottomNavigationLabelVisibility() {
        if (bottomNavigation == null) {
            return;
        }

        if (isNavigationRail() && activity instanceof MainActivity
                && ((MainActivity) activity).isCompactLargeScreenNavigationEnabled()) {
            bottomNavigation.setLabelVisibilityMode(
                    NavigationBarView.LABEL_VISIBILITY_UNLABELED);
            return;
        }

        final int labelVisibilityMode;
        if (getString(R.string.bottom_navigation_labels_always_value)
                .equals(bottomNavigationLabelsValue)) {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED;
        } else if (getString(R.string.bottom_navigation_labels_hidden_value)
                .equals(bottomNavigationLabelsValue)) {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED;
        } else {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_SELECTED;
        }
        bottomNavigation.setLabelVisibilityMode(labelVisibilityMode);
    }

    private void updateMainNavigationMode() {
        if (binding == null || bottomNavigation == null) {
            return;
        }

        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final boolean bottom = mainTabsPositionBottom;
        final boolean navigationRail = isNavigationRail();
        final HomeNavigationMode navigationMode = HomeNavigationModeResolver
                .resolveNavigationMode(tabsList.size(), bottom);
        final boolean showBottomNavigation = largeScreenNavigation
                ? tabsList.size() <= getNavigationItemLimit()
                : navigationMode == HomeNavigationMode.BOTTOM_NAVIGATION;
        final boolean showTabLayout = !showBottomNavigation
                && navigationMode == HomeNavigationMode.SCROLLABLE_TABS;

        final var tabParams = (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        final var pagerParams = (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        tabParams.removeRule(ALIGN_PARENT_TOP);
        tabParams.removeRule(ALIGN_PARENT_BOTTOM);
        tabParams.addRule(bottom ? ALIGN_PARENT_BOTTOM : ALIGN_PARENT_TOP);

        pagerParams.removeRule(BELOW);
        pagerParams.removeRule(ABOVE);
        pagerParams.bottomMargin = 0;
        if (showBottomNavigation && !navigationRail) {
            pagerParams.bottomMargin = getResources()
                    .getDimensionPixelSize(R.dimen.main_bottom_navigation_height);
        } else if (showTabLayout) {
            pagerParams.addRule(bottom ? ABOVE : BELOW, R.id.main_tab_layout);
        }

        tabLayout.setSelectedTabIndicatorGravity(
                bottom ? INDICATOR_GRAVITY_TOP : INDICATOR_GRAVITY_BOTTOM);
        tabLayout.setVisibility(showTabLayout ? View.VISIBLE : View.GONE);
        setBottomNavigationRequestedVisibility(showBottomNavigation);
        updateGlobalSearchFabPosition(showBottomNavigation, showTabLayout && bottom);

        tabLayout.setLayoutParams(tabParams);
        viewPager.setLayoutParams(pagerParams);
    }

    private void updateGlobalSearchFabPosition(final boolean showBottomNavigation,
                                               final boolean showBottomTabs) {
        if (binding == null) {
            return;
        }

        final var fabParams = (RelativeLayout.LayoutParams)
                binding.contextualGlobalSearchFab.getLayoutParams();
        fabParams.removeRule(ABOVE);
        fabParams.removeRule(ALIGN_PARENT_BOTTOM);
        contextualSearchFabBaseBottomMargin = getResources()
                .getDimensionPixelSize(R.dimen.margin_normal);
        if (showBottomTabs) {
            fabParams.addRule(ABOVE, R.id.main_tab_layout);
        } else {
            fabParams.addRule(ALIGN_PARENT_BOTTOM);
            if (showBottomNavigation && !isNavigationRail()) {
                contextualSearchFabBaseBottomMargin += getResources()
                        .getDimensionPixelSize(R.dimen.main_bottom_navigation_height);
            }
        }
        updateGlobalSearchFabBottomMargin();
    }

    private void updateGlobalSearchFabBottomMargin() {
        if (binding == null) {
            return;
        }

        final var fabParams = (RelativeLayout.LayoutParams)
                binding.contextualGlobalSearchFab.getLayoutParams();
        fabParams.bottomMargin =
                contextualSearchFabBaseBottomMargin + contextualSearchImeBottomInset;
        binding.contextualGlobalSearchFab.setLayoutParams(fabParams);
    }

    private void setBottomNavigationRequestedVisibility(final boolean visible) {
        if (bottomNavigation == null) {
            return;
        }

        bottomNavigation.setTag(visible);
        bottomNavigation.setAlpha(1.0f);
        bottomNavigation.setTranslationX(0.0f);
        bottomNavigation.setTranslationY(0.0f);
        setNavigationRailContentInset(visible);
        if (!visible) {
            bottomNavigation.setVisibility(View.GONE);
            notifyBottomNavigationVisibilityChanged();
            return;
        }

        final View playerSheet = requireActivity().findViewById(R.id.fragment_player_holder);
        final int playerState = playerSheet == null
                ? BottomSheetBehavior.STATE_HIDDEN
                : BottomSheetBehavior.from(playerSheet).getState();
        final boolean playerCoversNavigation = playerState == BottomSheetBehavior.STATE_EXPANDED
                || playerState == BottomSheetBehavior.STATE_DRAGGING
                || playerState == BottomSheetBehavior.STATE_SETTLING
                || playerState == BottomSheetBehavior.STATE_HALF_EXPANDED;

        if (playerCoversNavigation) {
            bottomNavigation.setAlpha(0.0f);
            if (isNavigationRail()) {
                bottomNavigation.setTranslationX(-getNavigationRailWidth(bottomNavigation));
            } else {
                bottomNavigation.setTranslationY(bottomNavigation.getHeight() > 0
                        ? bottomNavigation.getHeight()
                        : getResources()
                        .getDimensionPixelSize(R.dimen.main_bottom_navigation_height));
            }
            bottomNavigation.setVisibility(View.INVISIBLE);
        } else {
            bottomNavigation.setVisibility(View.VISIBLE);
        }
        bottomNavigation.bringToFront();
        notifyBottomNavigationVisibilityChanged();
    }

    private int getNavigationItemLimit() {
        return isNavigationRail()
                ? NAVIGATION_RAIL_MAX_ITEM_COUNT : BOTTOM_NAVIGATION_MAX_ITEM_COUNT;
    }

    private boolean isNavigationRail() {
        return bottomNavigation instanceof NavigationRailView;
    }

    private int getNavigationRailWidth(@NonNull final View navigation) {
        if (activity instanceof MainActivity) {
            return ((MainActivity) activity).getMainNavigationRailWidth();
        }
        if (navigation.getLayoutParams().width > 0) {
            return navigation.getLayoutParams().width;
        }
        return getResources().getDimensionPixelSize(R.dimen.main_navigation_rail_width);
    }

    private void setNavigationRailContentInset(final boolean visible) {
        if (!isNavigationRail()) {
            return;
        }
        final int inset = visible ? getNavigationRailWidth(bottomNavigation) : 0;
        setStartMargin(requireActivity().findViewById(R.id.fragment_holder), inset);
        setStartMargin(requireActivity().findViewById(R.id.toolbar_layout), inset);
    }

    private static void setStartMargin(@Nullable final View view, final int margin) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final var params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.getMarginStart() != margin) {
            params.setMarginStart(margin);
            view.setLayoutParams(params);
        }
    }

    private void notifyBottomNavigationVisibilityChanged() {
        final View playerSheet = requireActivity().findViewById(R.id.fragment_player_holder);
        if (playerSheet == null) {
            return;
        }
        final BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(playerSheet);
        if (behavior instanceof CustomBottomSheetBehavior customBehavior) {
            customBehavior.onBottomNavigationVisibilityChanged();
        }
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) { }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
        scrollCurrentFeedToTop(tab.getPosition());
    }

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private static final String STATE_PROFILE = "tab_profile";
        private static final String STATE_LAYOUT = "tab_layout";
        private final String profileId;
        private final Context context;
        private final FragmentManager fragmentManager;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();
        private Fragment primaryFragment;

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.profileId = ProfileManager.getActiveProfileId(context);
            this.fragmentManager = fragmentManager;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final Throwable t) {
                return new BlankFragment(new ErrorInfo(t, UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab " + tab.getClass().getSimpleName() + ":" + tab.getTabName(context)));
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
        }

        @Override
        public void setPrimaryItem(@NonNull final ViewGroup container, final int position,
                                   @NonNull final Object object) {
            super.setPrimaryItem(container, position, object);
            primaryFragment = (Fragment) object;
        }

        @Nullable
        public Fragment getPrimaryFragment() {
            return primaryFragment;
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            final Parcelable saved = super.saveState();
            final Bundle state = saved instanceof Bundle
                    ? new Bundle((Bundle) saved) : new Bundle();
            state.putString(STATE_PROFILE, profileId);
            state.putString(STATE_LAYOUT, TabsJsonHelper.getJsonToSave(internalTabsList));
            return state;
        }

        @VisibleForTesting
        static boolean matchesTabState(final Bundle state, final String profile,
                                       final List<Tab> tabs) {
            return profile.equals(state.getString(STATE_PROFILE))
                    && TabsJsonHelper.getJsonToSave(tabs).equals(state.getString(STATE_LAYOUT));
        }

        @Override
        public void restoreState(@Nullable final Parcelable state,
                                 @Nullable final ClassLoader loader) {
            if (!(state instanceof Bundle)) {
                super.restoreState(state, loader);
                return;
            }

            final Bundle saved = (Bundle) state;
            final ClassLoader stateLoader = resolveStateClassLoader(loader);
            saved.setClassLoader(stateLoader);
            try {
                if (!matchesTabState(saved, profileId, internalTabsList)) {
                    // FragmentStatePagerAdapter keys its fragments and saved states by position.
                    // A hidden Learning tab shifts those positions; a profile switch also changes
                    // which data the fragments may display. Never restore either stale catalog.
                    final var transaction = fragmentManager.beginTransaction();
                    for (final String key : saved.keySet()) {
                        if (key.matches("f[0-9]+")) {
                            try {
                                final Fragment fragment = fragmentManager.getFragment(saved, key);
                                if (fragment != null) {
                                    transaction.remove(fragment);
                                }
                            } catch (final IllegalStateException ignored) {
                                // The fragment was already discarded by FragmentManager.
                            }
                        }
                    }
                    transaction.commitAllowingStateLoss();
                    return;
                }
                super.restoreState(removeMissingFragmentEntries(
                        saved,
                        stateLoader,
                        (bundle, key) -> fragmentManager.getFragment(bundle, key)
                ), stateLoader);
            } catch (final BadParcelableException e) {
                Log.w("MainFragment", "Discarding unreadable main-tab pager state", e);
            }
        }

        @NonNull
        @VisibleForTesting
        static ClassLoader resolveStateClassLoader(@Nullable final ClassLoader loader) {
            return loader != null ? loader : Fragment.class.getClassLoader();
        }

        @VisibleForTesting
        static Bundle removeMissingFragmentEntries(
                @NonNull final Bundle state,
                @Nullable final ClassLoader loader,
                @NonNull final FragmentStateValidator validator) {
            final Bundle cleanedState = new Bundle(state);
            cleanedState.setClassLoader(resolveStateClassLoader(loader));
            for (final String key : new ArrayList<>(cleanedState.keySet())) {
                if (!key.startsWith("f")) {
                    continue;
                }
                try {
                    validator.validate(cleanedState, key);
                } catch (final IllegalStateException e) {
                    Log.w("MainFragment",
                            "Discarding stale main-tab fragment state for " + key, e);
                    cleanedState.remove(key);
                }
            }
            return cleanedState;
        }

        @VisibleForTesting
        interface FragmentStateValidator {
            void validate(@NonNull Bundle state, @NonNull String key);
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return profileId.equals(ProfileManager.getActiveProfileId(context))
                    && internalTabsList.equals(tabsToCompare);
        }
    }
}
