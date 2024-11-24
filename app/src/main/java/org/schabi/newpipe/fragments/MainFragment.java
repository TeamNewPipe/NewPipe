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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {
    private FragmentMainBinding binding;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;
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
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
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

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);

        setupTabs();
        updateTabLayoutPosition();
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }

        final boolean newMainTabsPosition = prefs.getBoolean(mainTabsPositionKey, false);
        if (mainTabsPositionBottom != newMainTabsPosition) {
            mainTabsPositionBottom = newMainTabsPosition;
            updateTabLayoutPosition();
        }
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
        super.onDestroyView();
        binding = null;
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
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
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
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
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
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    private void updateTabLayoutPosition() {
        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final boolean bottom = mainTabsPositionBottom;

        // change layout params to make the tab layout appear either at the top or at the bottom
        final var tabParams = (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        final var pagerParams = (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        tabParams.removeRule(bottom ? ALIGN_PARENT_TOP : ALIGN_PARENT_BOTTOM);
        tabParams.addRule(bottom ? ALIGN_PARENT_BOTTOM : ALIGN_PARENT_TOP);
        pagerParams.removeRule(bottom ? BELOW : ABOVE);
        pagerParams.addRule(bottom ? ABOVE : BELOW, R.id.main_tab_layout);
        tabLayout.setSelectedTabIndicatorGravity(
                bottom ? INDICATOR_GRAVITY_TOP : INDICATOR_GRAVITY_BOTTOM);

        tabLayout.setLayoutParams(tabParams);
        viewPager.setLayoutParams(pagerParams);

        // change the background and icon color of the tab layout:
        // service-colored at the top, app-background-colored at the bottom
        tabLayout.setBackgroundColor(ThemeHelper.resolveColorFromAttr(requireContext(),
                bottom ? android.R.attr.windowBackground : R.attr.colorPrimary));

        @ColorInt final int iconColor = bottom
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

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();

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

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
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
