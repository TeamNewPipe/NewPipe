package org.schabi.newpipe.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {

    public int currentServiceId = -1;
    private ViewPager viewPager;

    /*//////////////////////////////////////////////////////////////////////////
    // Constants
    //////////////////////////////////////////////////////////////////////////*/

    private static final int FALLBACK_SERVICE_ID = ServiceList.YouTube.getServiceId();
    private static final String FALLBACK_CHANNEL_URL = "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ";
    private static final String FALLBACK_CHANNEL_NAME = "Music";
    private static final String FALLBACK_KIOSK_ID = "Trending";
    private static final int KIOSK_MENU_OFFSET = 2000;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        currentServiceId = ServiceHelper.getSelectedServiceId(activity);
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        TabLayout tabLayout = rootView.findViewById(R.id.main_tab_layout);
        viewPager = rootView.findViewById(R.id.pager);

        /*  Nested fragment, use child fragment here to maintain backstack in view pager. */
        PagerAdapter adapter = new PagerAdapter(getChildFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(adapter.getCount());

        tabLayout.setupWithViewPager(viewPager);

        int channelIcon = ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.ic_channel);
        int whatsHotIcon = ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.ic_hot);
        int bookmarkIcon = ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.ic_bookmark);

        if (isSubscriptionsPageOnlySelected()) {
            tabLayout.getTabAt(0).setIcon(channelIcon);
            tabLayout.getTabAt(1).setIcon(bookmarkIcon);
        } else {
            tabLayout.getTabAt(0).setIcon(whatsHotIcon);
            tabLayout.getTabAt(1).setIcon(channelIcon);
            tabLayout.getTabAt(2).setIcon(bookmarkIcon);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "], inflater = [" + inflater + "]");
        inflater.inflate(R.menu.main_fragment_menu, menu);
        SubMenu kioskMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, 200, getString(R.string.kiosk));
        try {
            createKioskMenu(kioskMenu, inflater);
        } catch (Exception e) {
            ErrorActivity.reportError(activity, e,
                    activity.getClass(),
                    null,
                    ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                            "none", "", R.string.app_ui_crash));
        }

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                NavigationHelper.openSearchFragment(getFragmentManager(), ServiceHelper.getSelectedServiceId(activity), "");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }

    private class PagerAdapter extends FragmentPagerAdapter {
        PagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return isSubscriptionsPageOnlySelected() ? new SubscriptionFragment() : getMainPageFragment();
                case 1:
                    if(PreferenceManager.getDefaultSharedPreferences(getActivity())
                            .getString(getString(R.string.main_page_content_key), getString(R.string.blank_page_key))
                            .equals(getString(R.string.subscription_page_key))) {
                        return new BookmarkFragment();
                    } else {
                        return new SubscriptionFragment();
                    }
                case 2:
                    return new BookmarkFragment();
                default:
                    return new BlankFragment();
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            //return getString(this.tabTitles[position]);
            return "";
        }

        @Override
        public int getCount() {
            return isSubscriptionsPageOnlySelected() ? 2 : 3;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Main page content
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isSubscriptionsPageOnlySelected() {
        return PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(getString(R.string.main_page_content_key), getString(R.string.blank_page_key))
                .equals(getString(R.string.subscription_page_key));
    }

    private Fragment getMainPageFragment() {
        if (getActivity() == null) return new BlankFragment();

        try {
            SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            final String setMainPage = preferences.getString(getString(R.string.main_page_content_key),
                    getString(R.string.main_page_selectd_kiosk_id));
            if (setMainPage.equals(getString(R.string.blank_page_key))) {
                return new BlankFragment();
            } else if (setMainPage.equals(getString(R.string.kiosk_page_key))) {
                int serviceId = preferences.getInt(getString(R.string.main_page_selected_service),
                        FALLBACK_SERVICE_ID);
                String kioskId = preferences.getString(getString(R.string.main_page_selectd_kiosk_id),
                        FALLBACK_KIOSK_ID);
                KioskFragment fragment = KioskFragment.getInstance(serviceId, kioskId);
                fragment.useAsFrontPage(true);
                return fragment;
            } else if (setMainPage.equals(getString(R.string.feed_page_key))) {
                FeedFragment fragment = new FeedFragment();
                fragment.useAsFrontPage(true);
                return fragment;
            } else if (setMainPage.equals(getString(R.string.channel_page_key))) {
                int serviceId = preferences.getInt(getString(R.string.main_page_selected_service),
                        FALLBACK_SERVICE_ID);
                String url = preferences.getString(getString(R.string.main_page_selected_channel_url),
                        FALLBACK_CHANNEL_URL);
                String name = preferences.getString(getString(R.string.main_page_selected_channel_name),
                        FALLBACK_CHANNEL_NAME);
                ChannelFragment fragment = ChannelFragment.getInstance(serviceId, url, name);
                fragment.useAsFrontPage(true);
                return fragment;
            } else {
                return new BlankFragment();
            }

        } catch (Exception e) {
            ErrorActivity.reportError(activity, e,
                    activity.getClass(),
                    null,
                    ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                            "none", "", R.string.app_ui_crash));
            return new BlankFragment();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Select Kiosk
    //////////////////////////////////////////////////////////////////////////*/

    private void createKioskMenu(Menu menu, MenuInflater menuInflater)
            throws Exception {
        StreamingService service = NewPipe.getService(currentServiceId);
        KioskList kl = service.getKioskList();
        int i = 0;
        for (final String ks : kl.getAvailableKiosks()) {
            menu.add(0, KIOSK_MENU_OFFSET + i, Menu.NONE,
                    KioskTranslator.getTranslatedKioskName(ks, getContext()))
                    .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            try {
                                NavigationHelper.openKioskFragment(getFragmentManager(), currentServiceId, ks);
                            } catch (Exception e) {
                                ErrorActivity.reportError(activity, e,
                                        activity.getClass(),
                                        null,
                                        ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                                                "none", "", R.string.app_ui_crash));
                            }
                            return true;
                        }
                    });
            i++;
        }
    }
}
