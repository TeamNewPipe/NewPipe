package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {
    private ViewPager viewPager;
    private SelectedTabsPagerAdapter pagerAdapter;
    private TabLayout tabLayout;

    private List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                updateTabs();
            } else {
                hasTabsChanged = true;
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        tabLayout = rootView.findViewById(R.id.main_tab_layout);
        viewPager = rootView.findViewById(R.id.pager);

        /*  Nested fragment, use child fragment here to maintain backstack in view pager. */
        pagerAdapter = new SelectedTabsPagerAdapter(getChildFragmentManager());
        viewPager.setAdapter(pagerAdapter);

        tabLayout.setupWithViewPager(viewPager);
        tabLayout.addOnTabSelectedListener(this);
        updateTabs();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (hasTabsChanged) {
            hasTabsChanged = false;
            updateTabs();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "], inflater = [" + inflater + "]");
        inflater.inflate(R.menu.main_fragment_menu, menu);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                try {
                    NavigationHelper.openSearchFragment(
                            getFragmentManager(),
                            ServiceHelper.getSelectedServiceId(activity),
                            "");
                } catch (Exception e) {
                    ErrorActivity.reportUiError((AppCompatActivity) getActivity(), e);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    public void updateTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());
        pagerAdapter.notifyDataSetChanged();

        viewPager.setOffscreenPageLimit(pagerAdapter.getCount());
        updateTabsIcon();
        updateCurrentTitle();
    }

    private void updateTabsIcon() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = tabLayout.getTabAt(i);
            if (tabToSet != null) {
                tabToSet.setIcon(tabsList.get(i).getTabIconRes(activity));
            }
        }
    }

    private void updateCurrentTitle() {
        setTitle(tabsList.get(viewPager.getCurrentItem()).getTabName(requireContext()));
    }

    @Override
    public void onTabSelected(TabLayout.Tab selectedTab) {
        if (DEBUG) Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        updateCurrentTitle();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        if (DEBUG) Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        updateCurrentTitle();
    }

    private class SelectedTabsPagerAdapter extends FragmentPagerAdapter {
        private SelectedTabsPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            final Tab tab = tabsList.get(position);

            Throwable throwable = null;
            Fragment fragment = null;
            try {
                fragment = tab.getFragment();
            } catch (ExtractionException e) {
                throwable = e;
            }

            if (throwable != null) {
                ErrorActivity.reportError(activity, throwable, activity.getClass(), null,
                        ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash));
                return new BlankFragment();
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            return fragment;
        }

        @Override
        public int getItemPosition(Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return tabsList.size();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            getChildFragmentManager()
                    .beginTransaction()
                    .remove((Fragment) object)
                    .commitNowAllowingStateLoss();
        }
    }
}
