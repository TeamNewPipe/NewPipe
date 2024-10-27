package org.schabi.newpipe.settings;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
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

import com.evernote.android.state.State;
import com.jakewharton.rxbinding4.widget.RxTextView;
import com.livefront.bridge.Bridge;

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
import org.schabi.newpipe.util.ReleaseVersionUtil;
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

    // State
    @State
    String searchText;
    @State
    boolean wasSearchActive;

    @Override
    protected void onCreate(final Bundle savedInstanceBundle) {
        setTheme(ThemeHelper.getSettingsThemeStyle(this));
        assureCorrectAppLanguage(this);

        super.onCreate(savedInstanceBundle);
        Bridge.restoreInstanceState(this, savedInstanceBundle);
        final boolean restored = savedInstanceBundle != null;

        final SettingsLayoutBinding settingsLayoutBinding =
                SettingsLayoutBinding.inflate(getLayoutInflater());
        setContentView(settingsLayoutBinding.getRoot());
        initSearch(settingsLayoutBinding, restored);

        setSupportActionBar(settingsLayoutBinding.settingsToolbarLayout.toolbar);

        if (restored) {
            // Restore state
            if (this.wasSearchActive) {
                setSearchActive(true);
                if (!TextUtils.isEmpty(this.searchText)) {
                    this.searchEditText.setText(this.searchText);
                }
            }
        } else {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment_holder, new MainSettingsFragment())
                    .commit();
        }

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
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
    public boolean onPreferenceStartFragment(@NonNull final PreferenceFragmentCompat caller,
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

    private void initSearch(
            final SettingsLayoutBinding settingsLayoutBinding,
            final boolean restored
    ) {
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

        ensureSearchRepresentsApplicationState();

        // Build search configuration using SettingsResourceRegistry
        final PreferenceSearchConfiguration config = new PreferenceSearchConfiguration();


        // Build search items
        final Context searchContext = getApplicationContext();
        assureCorrectAppLanguage(searchContext);
        final PreferenceParser parser = new PreferenceParser(searchContext, config);
        final PreferenceSearcher searcher = new PreferenceSearcher(config);

        // Find all searchable SettingsResourceRegistry fragments
        SettingsResourceRegistry.getInstance().getAllEntries().stream()
                .filter(SettingsResourceRegistry.SettingRegistryEntry::isSearchable)
                // Get the resId
                .map(SettingsResourceRegistry.SettingRegistryEntry::getPreferencesResId)
                // Parse
                .map(parser::parse)
                // Add it to the searcher
                .forEach(searcher::add);

        if (restored) {
            searchFragment = (PreferenceSearchFragment) getSupportFragmentManager()
                    .findFragmentByTag(PreferenceSearchFragment.NAME);
            if (searchFragment != null) {
                // Hide/Remove the search fragment otherwise we get an exception
                // when adding it (because it's already present)
                hideSearchFragment();
            }
        }
        if (searchFragment == null) {
            searchFragment = new PreferenceSearchFragment();
        }
        searchFragment.setSearcher(searcher);
    }

    /**
     * Ensures that the search shows the correct/available search results.
     * <br/>
     * Some features are e.g. only available for debug builds, these should not
     * be found when searching inside a release.
     */
    private void ensureSearchRepresentsApplicationState() {
        // Check if the update settings are available
        if (!ReleaseVersionUtil.INSTANCE.isReleaseApk()) {
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

        // Ensure that the item is in the correct state when adding it. This is due to
        // Android's lifecycle (the Activity is recreated before the Fragment that registers this)
        if (menuSearchItem != null) {
            menuSearchItem.setVisible(!isSearchActive());
        }
    }

    public void setSearchActive(final boolean active) {
        if (DEBUG) {
            Log.d(TAG, "setSearchActive called active=" + active);
        }

        // Ignore if search is already in correct state
        if (isSearchActive() == active) {
            return;
        }

        wasSearchActive = active;

        searchContainer.setVisibility(active ? View.VISIBLE : View.GONE);
        if (menuSearchItem != null) {
            menuSearchItem.setVisible(!active);
        }

        if (active) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(FRAGMENT_HOLDER_ID, searchFragment, PreferenceSearchFragment.NAME)
                    .addToBackStack(PreferenceSearchFragment.NAME)
                    .commit();

            KeyboardUtil.showKeyboard(this, searchEditText);
        } else if (searchFragment != null) {
            hideSearchFragment();
            getSupportFragmentManager()
                    .popBackStack(
                        PreferenceSearchFragment.NAME,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE);

            KeyboardUtil.hideKeyboard(this, searchEditText);
        }

        resetSearchText();
    }

    private void hideSearchFragment() {
        getSupportFragmentManager().beginTransaction().remove(searchFragment).commit();
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
            searchText = this.searchEditText.getText().toString();
            searchFragment.updateSearchResults(searchText);
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
