package org.schabi.newpipe.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
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
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.util.NavigationHelper.MAIN_FRAGMENT_TAG;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {

    public int currentServiceId = -1;
    private ViewPager viewPager;
    private List<String> tabs = new ArrayList<>();
    static PagerAdapter adapter;
    TabLayout tabLayout;
    private SharedPreferences prefs;
    private Bundle savedInstanceStateBundle;

    private static final String TAB_NUMBER_BLANK = "0";
    private static final String TAB_NUMBER_KIOSK = "1";
    private static final String TAB_NUMBER_SUBSCIRPTIONS = "2";
    private static final String TAB_NUMBER_FEED = "3";
    private static final String TAB_NUMBER_BOOKMARKS = "4";
    private static final String TAB_NUMBER_HISTORY = "5";
    private static final String TAB_NUMBER_CHANNEL = "6";

    SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if(key.equals("saveUsedTabs")) {
            mainPageChanged();
        }
    };

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        savedInstanceStateBundle = savedInstanceState;
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        currentServiceId = ServiceHelper.getSelectedServiceId(activity);

        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.registerOnSharedPreferenceChangeListener(listener);

        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        tabLayout = rootView.findViewById(R.id.main_tab_layout);
        viewPager = rootView.findViewById(R.id.pager);

        /*  Nested fragment, use child fragment here to maintain backstack in view pager. */
        adapter = new PagerAdapter(getChildFragmentManager());
        viewPager.setAdapter(adapter);

        tabLayout.setupWithViewPager(viewPager);

        mainPageChanged();
    }


    public void mainPageChanged() {
        getTabOrder();
        adapter.notifyDataSetChanged();
        viewPager.setOffscreenPageLimit(adapter.getCount());
        setIcons();
        setFirstTitle();
    }

    private void setFirstTitle() {
        if((tabs.size() > 0)
                && activity != null) {
            String tabInformation = tabs.get(0);
                if (tabInformation.startsWith(TAB_NUMBER_KIOSK + "\t")) {
                    String kiosk[] = tabInformation.split("\t");
                    if (kiosk.length == 3) {
                        setTitle(kiosk[1]);
                    }
                } else if (tabInformation.startsWith(TAB_NUMBER_CHANNEL + "\t")) {

                    String channelInfo[] = tabInformation.split("\t");
                    if(channelInfo.length==4) {
                        setTitle(channelInfo[2]);
                    }
                } else {
                    switch (tabInformation) {
                        case TAB_NUMBER_BLANK:
                            setTitle(getString(R.string.app_name));
                            break;
                        case TAB_NUMBER_SUBSCIRPTIONS:
                            setTitle(getString(R.string.tab_subscriptions));
                            break;
                        case TAB_NUMBER_FEED:
                            setTitle(getString(R.string.fragment_whats_new));
                            break;
                        case TAB_NUMBER_BOOKMARKS:
                            setTitle(getString(R.string.tab_bookmarks));
                            break;
                        case TAB_NUMBER_HISTORY:
                            setTitle(getString(R.string.title_activity_history));
                            break;
                    }
                }


        }
    }

    private void setIcons() {
        for (int i = 0; i < tabs.size(); i++) {
            String tabInformation = tabs.get(i);

            TabLayout.Tab tabToSet = tabLayout.getTabAt(i);
            Context c = getContext();

            if (tabToSet != null && c != null) {

                if (tabInformation.startsWith(TAB_NUMBER_KIOSK + "\t")) {
                    String kiosk[] = tabInformation.split("\t");
                    if (kiosk.length == 3) {
                        tabToSet.setIcon(KioskTranslator.getKioskIcons(kiosk[1], getContext()));
                    }
                } else if (tabInformation.startsWith(TAB_NUMBER_CHANNEL + "\t")) {
                    tabToSet.setIcon(ThemeHelper.resolveResourceIdFromAttr(getContext(), R.attr.ic_channel));
                } else {
                    switch (tabInformation) {
                        case TAB_NUMBER_BLANK:
                            tabToSet.setIcon(ThemeHelper.resolveResourceIdFromAttr(getContext(), R.attr.ic_hot));
                            break;
                        case TAB_NUMBER_SUBSCIRPTIONS:
                            tabToSet.setIcon(ThemeHelper.resolveResourceIdFromAttr(getContext(), R.attr.ic_channel));
                            break;
                        case TAB_NUMBER_FEED:
                            tabToSet.setIcon(ThemeHelper.resolveResourceIdFromAttr(getContext(), R.attr.rss));
                            break;
                        case TAB_NUMBER_BOOKMARKS:
                            tabToSet.setIcon(ThemeHelper.resolveResourceIdFromAttr(getContext(), R.attr.ic_bookmark));
                            break;
                        case TAB_NUMBER_HISTORY:
                            tabToSet.setIcon(ThemeHelper.resolveResourceIdFromAttr(getContext(), R.attr.history));
                            break;
                    }
                }

            }
        }
    }


    private void getTabOrder() {
        tabs.clear();

        String save = prefs.getString("saveUsedTabs", "1\tTrending\t0\n2\n4\n");
        String tabsArray[] = save.trim().split("\n");

        Collections.addAll(tabs, tabsArray);
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
            String tabInformation = tabs.get(position);

            if(tabInformation.startsWith(TAB_NUMBER_KIOSK + "\t")) {
                String kiosk[] = tabInformation.split("\t");
                if(kiosk.length==3) {
                    KioskFragment fragment = null;
                    try {
                        fragment = KioskFragment.getInstance(Integer.parseInt(kiosk[2]), kiosk[1]);
                        fragment.useAsFrontPage(true);
                        return fragment;
                    } catch (Exception e) {
                        ErrorActivity.reportError(activity, e,
                                activity.getClass(),
                                null,
                                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                                        "none", "", R.string.app_ui_crash));
                    }
                }
            } else if(tabInformation.startsWith(TAB_NUMBER_CHANNEL + "\t")) {
                String channelInfo[] = tabInformation.split("\t");
                if(channelInfo.length==4) {
                    ChannelFragment fragment = ChannelFragment.getInstance(Integer.parseInt(channelInfo[3]), channelInfo[1], channelInfo[2]);
                    fragment.useAsFrontPage(true);
                    return fragment;
                } else {
                    return new BlankFragment();
                }
            } else {
                    switch (tabInformation) {
                        case TAB_NUMBER_BLANK:
                            return new BlankFragment();
                        case TAB_NUMBER_SUBSCIRPTIONS:
                            SubscriptionFragment sFragment = new SubscriptionFragment();
                            sFragment.useAsFrontPage(true);
                            return sFragment;
                        case TAB_NUMBER_FEED:
                            FeedFragment fFragment = new FeedFragment();
                            fFragment.useAsFrontPage(true);
                            return fFragment;
                        case TAB_NUMBER_BOOKMARKS:
                            BookmarkFragment bFragment = new BookmarkFragment();
                            bFragment.useAsFrontPage(true);
                            return bFragment;
                        case TAB_NUMBER_HISTORY:
                            StatisticsPlaylistFragment cFragment = new StatisticsPlaylistFragment();
                            cFragment.useAsFrontPage(true);
                            return cFragment;
                    }
                }

            return new BlankFragment();
            }

        @Override
        public int getItemPosition(Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return tabs.size();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            getFragmentManager()
                    .beginTransaction()
                    .remove((Fragment)object)
                    .commitNowAllowingStateLoss();
        }
    }
}
