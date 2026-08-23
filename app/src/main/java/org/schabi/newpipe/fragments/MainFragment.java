package org.schabi.newpipe.fragments;

import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.BELOW;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_BOTTOM;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_TOP;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener, BackPressable {
    private static final int BOTTOM_NAVIGATION_MAX_ITEM_COUNT = 5;
    private static final int BOTTOM_NAVIGATION_ITEM_ID_BASE = 10_000;

    private FragmentMainBinding binding;
    private BottomNavigationView bottomNavigation;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;
    private SharedPreferences prefs;
    private boolean mainTabsPositionBottom;
    private String mainTabsPositionKey;


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mainTabsPositionKey = getString(R.string.main_tabs_position_key);
        mainTabsPositionBottom = prefs.getBoolean(mainTabsPositionKey, false);

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
        bottomNavigation = binding.mainBottomNavigation;

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);
        binding.mainTabLayout.setTabRippleColor(binding.mainTabLayout.getTabRippleColor()
                .withAlpha(32));
        binding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                updateTitleForTab(position);
                updateBottomNavigationSelection(position);
            }
        });
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
            }
        });

        setupTabs();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (hasTabsChanged) {
            setupTabs();
        }

        final boolean newMainTabsPosition = prefs.getBoolean(mainTabsPositionKey, false);
        if (mainTabsPositionBottom != newMainTabsPosition) {
            mainTabsPositionBottom = newMainTabsPosition;
            updateMainNavigationMode();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (binding != null) {
            binding.pager.setAdapter(null);
            if (bottomNavigation != null) {
                bottomNavigation.setOnItemSelectedListener(null);
                bottomNavigation.setOnItemReselectedListener(null);
                bottomNavigation.getMenu().clear();
                bottomNavigation = null;
            }
            binding = null;
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

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            try {
                final int selectedServiceId = ServiceHelper.getSelectedServiceId(activity);
                NavigationHelper.openSearchFragment(getFM(),
                        SearchFragment.getPersistedSearchServiceId(activity, selectedServiceId),
                        "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setOffscreenPageLimit(tabsList.size());
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateBottomNavigationItems();
        updateMainNavigationMode();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                tabToSet.setIcon(tab.getTabIconRes(requireContext()));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
    }

    private void updateTitleForTab(final int tabPosition) {
        if (tabPosition < 0 || tabPosition >= tabsList.size()) {
            return;
        }
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    private void updateBottomNavigationItems() {
        if (bottomNavigation == null) {
            return;
        }
        bottomNavigation.getMenu().clear();
        for (int i = 0; i < tabsList.size() && i < BOTTOM_NAVIGATION_MAX_ITEM_COUNT; i++) {
            final Tab tab = tabsList.get(i);
            bottomNavigation.getMenu()
                    .add(0, BOTTOM_NAVIGATION_ITEM_ID_BASE + i, i, tab.getTabName(requireContext()))
                    .setIcon(tab.getTabIconRes(requireContext()));
        }
        updateBottomNavigationSelection(binding == null ? 0 : binding.pager.getCurrentItem());
    }

    private void updateBottomNavigationSelection(final int position) {
        if (bottomNavigation == null || bottomNavigation.getVisibility() != View.VISIBLE
                || position < 0 || position >= bottomNavigation.getMenu().size()) {
            return;
        }
        bottomNavigation.getMenu().getItem(position).setChecked(true);
    }

    private int getBottomNavigationItemPosition(final int itemId) {
        return itemId - BOTTOM_NAVIGATION_ITEM_ID_BASE;
    }

    private boolean shouldUseBottomNavigation() {
        return mainTabsPositionBottom
                && tabsList.size() > 1
                && tabsList.size() <= BOTTOM_NAVIGATION_MAX_ITEM_COUNT;
    }

    private void updateMainNavigationMode() {
        if (binding == null) {
            return;
        }
        final boolean bottomNavigationMode = shouldUseBottomNavigation();
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(bottomNavigationMode ? View.VISIBLE : View.GONE);
        }
        updateTabLayoutPosition(bottomNavigationMode);
        updateBottomNavigationSelection(binding.pager.getCurrentItem());
    }

    private void updateTabLayoutPosition(final boolean bottomNavigationMode) {
        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final boolean showTabLayout = tabsList.size() > 1 && !bottomNavigationMode;
        final boolean bottomTabs = mainTabsPositionBottom && showTabLayout;

        final RelativeLayout.LayoutParams tabParams =
                (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        final RelativeLayout.LayoutParams pagerParams =
                (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        tabParams.removeRule(ALIGN_PARENT_TOP);
        tabParams.removeRule(ALIGN_PARENT_BOTTOM);
        if (bottomTabs) {
            tabParams.addRule(ALIGN_PARENT_BOTTOM);
        } else {
            tabParams.addRule(ALIGN_PARENT_TOP);
        }

        pagerParams.removeRule(BELOW);
        pagerParams.removeRule(ABOVE);
        if (bottomNavigationMode) {
            pagerParams.addRule(ABOVE, R.id.main_bottom_navigation);
        } else if (showTabLayout) {
            pagerParams.addRule(bottomTabs ? ABOVE : BELOW, R.id.main_tab_layout);
        }

        tabLayout.setVisibility(showTabLayout ? View.VISIBLE : View.GONE);
        tabLayout.setSelectedTabIndicatorGravity(
                bottomTabs ? INDICATOR_GRAVITY_TOP : INDICATOR_GRAVITY_BOTTOM);

        tabLayout.setLayoutParams(tabParams);
        viewPager.setLayoutParams(pagerParams);

        tabLayout.setBackgroundColor(ThemeHelper.resolveColorFromAttr(requireContext(),
                bottomTabs ? android.R.attr.windowBackground : R.attr.colorPrimary));

        final int iconColor = bottomTabs
                ? ThemeHelper.resolveColorFromAttr(requireContext(), android.R.attr.colorAccent)
                : Color.WHITE;
        tabLayout.setTabRippleColor(ColorStateList.valueOf(iconColor).withAlpha(32));
        tabLayout.setTabIconTint(ColorStateList.valueOf(iconColor));
        tabLayout.setSelectedTabIndicatorColor(iconColor);
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
    }


    @Override
    public boolean onBackPressed() {
        if (pagerAdapter == null || binding == null) {
            return false;
        }

        final Object currentItem = pagerAdapter.instantiateItem(binding.pager,
                binding.pager.getCurrentItem());
        if (currentItem instanceof BackPressable) {
            return ((BackPressable) currentItem).onBackPressed();
        }
        return false;
    }

    private static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final ExtractionException e) {
                ErrorUtil.showUiErrorSnackbar(context, "Getting fragment item", e);
                return new BlankFragment();
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            return fragment;
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
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
