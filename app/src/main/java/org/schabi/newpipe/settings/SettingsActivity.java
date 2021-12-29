package org.schabi.newpipe.settings;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.jakewharton.rxbinding4.widget.RxTextView;

import org.schabi.newpipe.App;
import org.schabi.newpipe.CheckForNewAppVersion;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.SettingsLayoutBinding;
import org.schabi.newpipe.settings.preferencesearch.PreferenceParser;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchConfiguration;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchFragment;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchItem;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultHighlighter;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultListener;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearcher;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import java.util.concurrent.TimeUnit;

/*
 * Created by Christian Schabesberger on 31.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * SettingsActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        PreferenceSearchResultListener {
    private static final String TAG = "SettingsActivity";
    private static final boolean DEBUG = MainActivity.DEBUG;

    @IdRes
    private static final int FRAGMENT_HOLDER_ID = R.id.settings_fragment_holder;

    private PreferenceSearchFragment searchFragment;

    @Nullable
    private MenuItem menuSearchItem;

    private View searchContainer;
    private EditText searchEditText;

    @Override
    protected void onCreate(final Bundle savedInstanceBundle) {
        setTheme(ThemeHelper.getSettingsThemeStyle(this));
        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceBundle);

        final SettingsLayoutBinding settingsLayoutBinding =
                SettingsLayoutBinding.inflate(getLayoutInflater());
        setContentView(settingsLayoutBinding.getRoot());
        initSearch(settingsLayoutBinding);

        setSupportActionBar(settingsLayoutBinding.settingsToolbarLayout.toolbar);

        if (savedInstanceBundle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment_holder, new MainSettingsFragment())
                    .commit();
        }

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (isSearchActive()) {
            setSearchActive(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            // Check if the search is active and if so: Close it
            if (isSearchActive()) {
                setSearchActive(false);
                return true;
            }

            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                finish();
            } else {
                getSupportFragmentManager().popBackStack();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(final PreferenceFragmentCompat caller,
                                             final Preference preference) {
        showSettingsFragment(instantiateFragment(preference.getFragment()));
        return true;
    }

    private Fragment instantiateFragment(@NonNull final String className) {
        return getSupportFragmentManager()
                .getFragmentFactory()
                .instantiate(this.getClassLoader(), className);
    }

    private void showSettingsFragment(final Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out,
                        R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(FRAGMENT_HOLDER_ID, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    protected void onDestroy() {
        setMenuSearchItem(null);
        searchFragment = null;
        super.onDestroy();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/
    //region Search

    private void initSearch(final SettingsLayoutBinding settingsLayoutBinding) {
        searchContainer =
                settingsLayoutBinding.settingsToolbarLayout.toolbar
                        .findViewById(R.id.toolbar_search_container);

        // Configure input field for search
        searchEditText = searchContainer.findViewById(R.id.toolbar_search_edit_text);
        RxTextView.textChanges(searchEditText)
                // Wait some time after the last input before actually searching
                .debounce(200, TimeUnit.MILLISECONDS)
                .subscribe(v -> runOnUiThread(this::onSearchChanged));

        // Configure clear button
        searchContainer.findViewById(R.id.toolbar_search_clear)
                .setOnClickListener(ev -> resetSearchText());

        prepareSearchConfig();

        // Build search configuration using SettingsResourceRegistry
        final PreferenceSearchConfiguration config = new PreferenceSearchConfiguration();
        SettingsResourceRegistry.getInstance().getAllEntries().stream()
                .filter(SettingsResourceRegistry.SettingRegistryEntry::isSearchable)
                .map(SettingsResourceRegistry.SettingRegistryEntry::getPreferencesResId)
                .forEach(config::index);

        // Build search items
        final PreferenceParser parser = new PreferenceParser(getApplicationContext(), config);
        final PreferenceSearcher searcher = new PreferenceSearcher(config);
        config.getFiles().stream()
                .map(parser::parse)
                .forEach(searcher::add);

        searchFragment = new PreferenceSearchFragment(searcher);
    }

    private void prepareSearchConfig() {
        // Check if the update settings are available
        if (!CheckForNewAppVersion.isReleaseApk(App.getApp())) {
            SettingsResourceRegistry.getInstance()
                    .getEntryByPreferencesResId(R.xml.update_settings)
                    .setSearchable(false);
        }

        // Hide debug preferences in RELEASE build variant
        if (DEBUG) {
            SettingsResourceRegistry.getInstance()
                    .getEntryByPreferencesResId(R.xml.debug_settings)
                    .setSearchable(true);
        }
    }

    public void setMenuSearchItem(final MenuItem menuSearchItem) {
        this.menuSearchItem = menuSearchItem;
    }

    public void setSearchActive(final boolean active) {
        // Ignore if search is already in correct state
        if (isSearchActive() == active) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "setSearchActive called active=" + active);
        }

        searchContainer.setVisibility(active ? View.VISIBLE : View.GONE);
        if (menuSearchItem != null) {
            menuSearchItem.setVisible(!active);
        }

        final FragmentManager fm = getSupportFragmentManager();
        if (active) {
            fm.beginTransaction()
                    .add(FRAGMENT_HOLDER_ID, searchFragment, PreferenceSearchFragment.NAME)
                    .addToBackStack(PreferenceSearchFragment.NAME)
                    .commit();

            KeyboardUtil.showKeyboard(this, searchEditText);
        } else if (searchFragment != null) {
            fm.beginTransaction().remove(searchFragment).commit();
            fm.popBackStack(
                    PreferenceSearchFragment.NAME,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);

            KeyboardUtil.hideKeyboard(this, searchEditText);
        }

        resetSearchText();
    }

    private void resetSearchText() {
        searchEditText.setText("");
    }

    private boolean isSearchActive() {
        return searchContainer.getVisibility() == View.VISIBLE;
    }

    private void onSearchChanged() {
        if (!isSearchActive()) {
            return;
        }

        if (searchFragment != null) {
            searchFragment.updateSearchResults(this.searchEditText.getText().toString());
        }
    }

    @Override
    public void onSearchResultClicked(@NonNull final PreferenceSearchItem result) {
        if (DEBUG) {
            Log.d(TAG, "onSearchResultClicked called result=" + result);
        }

        // Hide the search
        setSearchActive(false);

        // -- Highlight the result --
        // Find out which fragment class we need
        final Class<? extends Fragment> targetedFragmentClass =
                SettingsResourceRegistry.getInstance()
                        .getFragmentClass(result.getSearchIndexItemResId());

        if (targetedFragmentClass == null) {
            // This should never happen
            Log.w(TAG, "Unable to locate fragment class for resId="
                    + result.getSearchIndexItemResId());
            return;
        }

        // Check if the currentFragment is the one which contains the result
        Fragment currentFragment =
                getSupportFragmentManager().findFragmentById(FRAGMENT_HOLDER_ID);
        if (!targetedFragmentClass.equals(currentFragment.getClass())) {
            // If it's not the correct one display the correct one
            currentFragment = instantiateFragment(targetedFragmentClass.getName());
            showSettingsFragment(currentFragment);
        }

        // Run the highlighting
        if (currentFragment instanceof PreferenceFragmentCompat) {
            PreferenceSearchResultHighlighter
                    .highlight(result, (PreferenceFragmentCompat) currentFragment);
        }
    }

    //endregion
}
