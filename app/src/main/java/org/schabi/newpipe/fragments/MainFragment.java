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
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    /** YewPlay: true when the home shows the kiosks of the current service as text tabs. */
    private boolean usingServiceKioskTabs = false;
    private static final int YOUTUBE_SERVICE_ID = 0;
    private static final String YOUTUBE_TRENDING_KIOSK = "Trending";
    private static final List<String> HOME_KIOSK_ORDER = Arrays.asList("trending_music",
            "trending_gaming", "trending_movies_and_shows", "trending_podcasts_episodes", "live");

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
        // YewPlay: search lives in the bottom bar; the home action picks the service instead
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchItem.setVisible(false);
        }
        menu.add(Menu.NONE, R.id.menu_item_yp_choose_service, Menu.NONE,
                        R.string.yp_choose_service)
                .setIcon(R.drawable.ic_public)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        if (binding != null && !tabsList.isEmpty()) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_yp_choose_service) {
            showServiceChooser();
            return true;
        }
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

    /**
     * YewPlay: unless the user customized the tabs (advanced option kept from NewPipe), the home
     * shows the kiosks of the current service ("All", "Music", "Gaming", ...) as text tabs.
     *
     * @return the tabs to show in the home
     */
    private List<Tab> getHomeTabs() {
        usingServiceKioskTabs = false;
        if (prefs.contains(getString(R.string.saved_tabs_key))) {
            return tabsManager.getTabs();
        }
        final List<Tab> kioskTabs = new ArrayList<>();
        try {
            final int serviceId = ServiceHelper.getSelectedServiceId(requireContext());
            final StreamingService service = NewPipe.getService(serviceId);
            final String defaultKioskId = service.getKioskList().getDefaultKioskId();
            final List<String> kioskIds = new ArrayList<>();
            kioskIds.add(defaultKioskId);
            // known YouTube kiosks first, in the order of the proposal
            for (final String kioskId : HOME_KIOSK_ORDER) {
                if (!kioskIds.contains(kioskId)
                        && service.getKioskList().getAvailableKiosks().contains(kioskId)) {
                    kioskIds.add(kioskId);
                }
            }
            for (final String kioskId : service.getKioskList().getAvailableKiosks()) {
                if (!kioskIds.contains(kioskId)) {
                    kioskIds.add(kioskId);
                }
            }
            for (final String kioskId : kioskIds) {
                // YouTube discontinued its combined trending page: that kiosk no longer loads
                if (!(serviceId == YOUTUBE_SERVICE_ID && YOUTUBE_TRENDING_KIOSK.equals(kioskId))) {
                    kioskTabs.add(new Tab.KioskTab(serviceId, kioskId));
                }
            }
        } catch (final ExtractionException | RuntimeException e) {
            return tabsManager.getTabs();
        }
        if (kioskTabs.isEmpty()) {
            return tabsManager.getTabs();
        }
        usingServiceKioskTabs = true;
        return kioskTabs;
    }

    /**
     * YewPlay: the tab layout re-applies its own text style (caps, two lines), so each label is
     * a custom view: sentence case, a single line and Instrument Sans SemiBold.
     *
     * @param tab  the tab whose label is set
     * @param name the label
     */
    private void styleTabText(@NonNull final TabLayout.Tab tab, @NonNull final String name) {
        final TextView label = new TextView(requireContext());
        label.setText(name);
        label.setAllCaps(false);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTypeface(ResourcesCompat.getFont(requireContext(),
                R.font.instrument_sans_semibold));
        label.setTextColor(ContextCompat.getColorStateList(requireContext(),
                R.color.yp_tab_text));
        tab.setCustomView(label);
    }

    private String getShortKioskName(final Tab tab) {
        if (tab instanceof Tab.KioskTab) {
            switch (((Tab.KioskTab) tab).getKioskId()) {
                case "trending_music":
                    return getString(R.string.yp_kiosk_music);
                case "trending_gaming":
                    return getString(R.string.yp_kiosk_gaming);
                case "trending_movies_and_shows":
                    return getString(R.string.yp_kiosk_movies);
                case "trending_podcasts_episodes":
                    return getString(R.string.yp_kiosk_podcasts);
                default:
                    break;
            }
        }
        return tab.getTabName(requireContext());
    }

    private void showServiceChooser() {
        final List<StreamingService> services = NewPipe.getServices();
        final int selectedId = ServiceHelper.getSelectedServiceId(requireContext());
        final String[] names = new String[services.size()];
        int checked = 0;
        for (int i = 0; i < services.size(); i++) {
            names[i] = services.get(i).getServiceInfo().getName();
            if (services.get(i).getServiceId() == selectedId) {
                checked = i;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.yp_choose_service)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    final int newId = services.get(which).getServiceId();
                    if (newId != selectedId) {
                        ServiceHelper.setSelectedServiceId(requireContext(), newId);
                        requireActivity().recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getServiceAndRegionSubtitle() {
        try {
            final int serviceId = ServiceHelper.getSelectedServiceId(requireContext());
            final String serviceName = NewPipe.getService(serviceId).getServiceInfo().getName();
            final String countryCode = Localization.getPreferredContentCountry(requireContext())
                    .getCountryCode();
            final String country = new Locale("", countryCode).getDisplayCountry();
            return country.isEmpty() ? serviceName : serviceName + " · " + country;
        } catch (final Exception e) {
            return null;
        }
    }

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(getHomeTabs());

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
                // YewPlay: short text tabs ("Music", "Gaming", ...)
                final String name = usingServiceKioskTabs
                        ? getShortKioskName(tab) : tab.getTabName(requireContext());
                tabToSet.setIcon(null);
                tabToSet.setText(name);
                tabToSet.setContentDescription(name);
                styleTabText(tabToSet, name);
            }
        }
    }

    private void updateTitleForTab(final int tabPosition) {
        if (usingServiceKioskTabs) {
            setTitle(getString(R.string.yp_home_title));
        } else {
            setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
        }
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(getServiceAndRegionSubtitle());
        }
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

        // YewPlay: ícones na cor de ação; o indicador da aba ativa é rubi (cor de estado)
        @ColorInt final int iconColor = ThemeHelper.resolveColorFromAttr(requireContext(),
                android.R.attr.colorAccent);
        tabLayout.setTabRippleColor(ColorStateList.valueOf(iconColor).withAlpha(32));
        tabLayout.setTabIconTint(ColorStateList.valueOf(iconColor));
        tabLayout.setSelectedTabIndicatorColor(
                ContextCompat.getColor(requireContext(), R.color.yp_state));
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
