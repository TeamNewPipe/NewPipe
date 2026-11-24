package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.AppBarLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.jakewharton.rxbinding4.widget.RxTextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.profiles.KidsModeDialogs;
import org.schabi.newpipe.databinding.SettingsLayoutBinding;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchFragment;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchItem;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultHighlighter;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultListener;
import org.schabi.newpipe.settings.preferencesearch.SettingsSearchIndex;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.EdgeToEdgeHelper;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.TvRemoteKeyDispatcher;
import org.schabi.newpipe.views.FocusOverlayView;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

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
    public static final String EXTRA_OPEN_UPDATE_SETTINGS =
            "org.schabi.newpipe.settings.OPEN_UPDATE_SETTINGS";
    public static final String EXTRA_OPEN_PROFILES_SETTINGS =
            "org.schabi.newpipe.settings.OPEN_PROFILES_SETTINGS";
    public static final String EXTRA_OPEN_PLUGIN_SETTINGS =
            "org.schabi.newpipe.settings.OPEN_PLUGIN_SETTINGS";
    private static final String SEARCH_TEXT = "settings_search_text";
    private static final String TWO_PANE = "settings_two_pane";
    private AppBarLayout settingsAppBar;
    private CollapsingToolbarLayout settingsCollapsingToolbar;
    private CharSequence sectionTitle = "";

    private boolean twoPane;
    private View settingsRoot;
    private AlertDialog parentPinDialog;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View searchContainer;
    private EditText searchEditText;
    private String searchText = "";

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
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        setTheme(ThemeHelper.getSettingsThemeStyle(this));
        ThemeHelper.applyThemeColor(this);
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);

        final SettingsLayoutBinding binding = SettingsLayoutBinding.inflate(getLayoutInflater());
        settingsRoot = binding.getRoot();
        settingsAppBar = binding.settingsAppBar;
        settingsCollapsingToolbar = binding.settingsCollapsingToolbar;
        setContentView(settingsRoot);
        twoPane = getResources().getBoolean(R.bool.use_two_pane_settings);
        binding.settingsNavigationHolder.setVisibility(twoPane ? View.VISIBLE : View.GONE);
        EdgeToEdgeHelper.applySystemBarPadding(binding.getRoot());
        setSupportActionBar(binding.settingsToolbarLayout.toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            // The shared collapsing toolbar draws both the expanded and compact title.
            actionBar.setDisplayShowTitleEnabled(false);
        }

        if (savedInstanceState != null) {
            searchText = savedInstanceState.getString(SEARCH_TEXT, "");
        }
        initSearch(binding);
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateSearchUi);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentResumed(@NonNull final FragmentManager manager,
                                                  @NonNull final Fragment fragment) {
                        updateSearchUi();
                    }
                }, false);

        if (savedInstanceState != null
                && savedInstanceState.getBoolean(TWO_PANE) != twoPane) {
            rebuildNavigationForWindow();
        } else if (savedInstanceState == null) {
            final Fragment initial;
            if (getIntent().getBooleanExtra(EXTRA_OPEN_UPDATE_SETTINGS, false)) {
                initial = new UpdateSettingsFragment();
            } else if (getIntent().getBooleanExtra(EXTRA_OPEN_PROFILES_SETTINGS, false)) {
                initial = new ProfilesSettingsFragment();
            } else if (getIntent().getBooleanExtra(EXTRA_OPEN_PLUGIN_SETTINGS, false)) {
                initial = new PluginSettingsFragment();
            } else {
                initial = VimeoSettingsFragment.initialOrDefault(getIntent(),
                        twoPane ? new VideoAudioSettingsFragment() : new MainSettingsFragment());
            }
            if (twoPane) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.settings_navigation_holder, new MainSettingsFragment())
                        .commit();
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment_holder, initial).commit();
        }
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        org.schabi.newpipe.util.PluginProfileSession.restartIfChanged(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (KidsMode.requiresParentAccess(this)) {
            settingsRoot.setVisibility(View.INVISIBLE);
            parentPinDialog = KidsModeDialogs.requestAccess(this,
                    () -> settingsRoot.setVisibility(View.VISIBLE), this::finish);
        } else {
            settingsRoot.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStop() {
        if (parentPinDialog != null) {
            parentPinDialog.dismiss();
            parentPinDialog = null;
        }
        KidsMode.lock();
        if (KidsMode.isEnabled(this)) {
            settingsRoot.setVisibility(View.INVISIBLE);
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putString(SEARCH_TEXT, searchEditText.getText().toString());
        outState.putBoolean(TWO_PANE, twoPane);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings_main_fragment, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem search = menu.findItem(R.id.action_search);
        if (search != null) {
            search.setVisible(!isSearchActive());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            openSettingsSearch();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        KeyboardUtil.hideKeyboard(this, searchEditText);
        super.onBackPressed();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull final PreferenceFragmentCompat caller,
                                             @NonNull final Preference preference) {
        final String destination = preference.getFragment();
        if (destination == null) {
            return false;
        }
        final Fragment fragment = instantiateFragment(destination);
        fragment.setArguments(new Bundle(preference.getExtras()));
        if (twoPane && caller instanceof MainSettingsFragment) {
            getSupportFragmentManager().popBackStackImmediate(null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment_holder, fragment).commit();
        } else {
            showSettingsFragment(fragment);
        }
        return true;
    }

    private void rebuildNavigationForWindow() {
        final FragmentManager manager = getSupportFragmentManager();
        final Fragment current = manager.findFragmentById(R.id.settings_fragment_holder);
        final Fragment destination;
        if (current != null && !(current instanceof MainSettingsFragment)) {
            destination = instantiateFragment(current.getClass().getName());
            destination.setArguments(current.getArguments());
            destination.setInitialSavedState(manager.saveFragmentInstanceState(current));
        } else {
            destination = twoPane ? new VideoAudioSettingsFragment() : null;
        }
        SettingsDialogLifecycle.dismissBeforeNavigationRebuild(manager);
        manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        final Fragment navigation = manager.findFragmentById(R.id.settings_navigation_holder);
        if (navigation != null) {
            manager.beginTransaction().remove(navigation).commitNow();
        }
        if (twoPane) {
            manager.beginTransaction()
                    .replace(R.id.settings_navigation_holder, new MainSettingsFragment())
                    .replace(R.id.settings_fragment_holder, destination).commit();
        } else {
            manager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, new MainSettingsFragment()).commitNow();
            if (destination != null) {
                showSettingsFragment(destination);
            }
        }
    }

    private Fragment instantiateFragment(@NonNull final String className) {
        return getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), className);
    }

    private void showSettingsFragment(@NonNull final Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out,
                        R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.settings_fragment_holder, fragment)
                .addToBackStack(null).commit();
    }

    private void initSearch(final SettingsLayoutBinding binding) {
        searchContainer = binding.settingsToolbarLayout.toolbar
                .findViewById(R.id.toolbar_search_container);
        final android.view.ViewGroup.MarginLayoutParams containerParams =
                (android.view.ViewGroup.MarginLayoutParams) searchContainer.getLayoutParams();
        containerParams.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.margin_normal));
        searchContainer.setLayoutParams(containerParams);
        searchEditText = searchContainer.findViewById(R.id.toolbar_search_edit_text);
        searchEditText.setHint(R.string.settings_search_title);
        searchEditText.setNextFocusDownId(R.id.searchResults);
        searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchEditText.setText(searchText);
        searchContainer.findViewById(R.id.toolbar_search_filter)
                .setVisibility(View.GONE);
        searchContainer.findViewById(R.id.toolbar_search_clear)
                .setOnClickListener(view -> searchEditText.setText(""));
        searchEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                updateSearchResults();
                KeyboardUtil.hideKeyboard(this, searchEditText);
                return true;
            }
            return false;
        });
        disposables.add(RxTextView.textChanges(searchEditText)
                .debounce(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> updateSearchResults()));
    }

    public void openSettingsSearch() {
        final FragmentManager manager = getSupportFragmentManager();
        if (manager.isStateSaved() || isSearchActive()) {
            return;
        }
        // Finish pending navigation before deciding whether search already exists in the stack.
        manager.executePendingTransactions();
        if (isSearchActive()) {
            return;
        }
        if (!manager.popBackStackImmediate(PreferenceSearchFragment.NAME, 0)) {
            final PreferenceSearchFragment fragment = new PreferenceSearchFragment();
            manager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, fragment, PreferenceSearchFragment.NAME)
                    .addToBackStack(PreferenceSearchFragment.NAME).commit();
        }
        searchEditText.post(() -> KeyboardUtil.showKeyboard(this, searchEditText));
    }

    private boolean isSearchActive() {
        return getSupportFragmentManager().findFragmentById(R.id.settings_fragment_holder)
                instanceof PreferenceSearchFragment;
    }

    /**
     * Keeps the expanded and collapsed settings titles synchronized across fragments.
     *
     * @param title current settings section title
     */
    public void setSettingsSectionTitle(@NonNull final CharSequence title) {
        sectionTitle = title;
        if (!isSearchActive() && settingsCollapsingToolbar != null) {
            settingsCollapsingToolbar.setTitle(title);
        }
    }

    private void updateSearchUi() {
        final boolean active = isSearchActive();
        searchContainer.setVisibility(active ? View.VISIBLE : View.GONE);
        if (settingsCollapsingToolbar != null) {
            settingsCollapsingToolbar.setTitle(active ? "" : sectionTitle);
        }
        if (active && settingsAppBar != null) {
            settingsAppBar.setExpanded(false, false);
        }
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
        invalidateOptionsMenu();
        if (active) {
            updateSearchResults();
        } else {
            KeyboardUtil.hideKeyboard(this, searchEditText);
        }
    }

    private void updateSearchResults() {
        searchText = searchEditText.getText().toString();
        final Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.settings_fragment_holder);
        if (current instanceof PreferenceSearchFragment) {
            final PreferenceSearchFragment search = (PreferenceSearchFragment) current;
            if (!search.hasSearcher()) {
                search.setSearcher(SettingsSearchIndex.build(this));
            }
            search.updateSearchResults(searchText);
        }
    }

    @Override
    public void onSearchResultClicked(@NonNull final PreferenceSearchItem result) {
        if (getSupportFragmentManager().isStateSaved()) {
            return;
        }
        final Class<? extends Fragment> destination = SettingsResourceRegistry.getInstance()
                .getFragmentClass(result.getSearchIndexItemResId());
        if (destination == null) {
            return;
        }
        KeyboardUtil.hideKeyboard(this, searchEditText);
        final Fragment fragment = instantiateFragment(destination.getName());
        final Bundle arguments = new Bundle();
        arguments.putString(PreferenceSearchResultHighlighter.ARG_KEY, result.getKey());
        fragment.setArguments(arguments);
        showSettingsFragment(fragment);
    }

    @Override
    protected void onDestroy() {
        disposables.dispose();
        super.onDestroy();
    }
}
