package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ReleaseVersionUtil;

public class MainSettingsFragment extends BasePreferenceFragment {
    public static final boolean DEBUG = MainActivity.DEBUG;

    private SettingsActivity settingsActivity;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        setHasOptionsMenu(true); // Otherwise onCreateOptionsMenu is not called

        // Check if the app is updatable
//        if (!ReleaseVersionUtil.isReleaseApk()) {
//            getPreferenceScreen().removePreference(
//                    findPreference(getString(R.string.update_pref_screen_key)));
//
//            defaultPreferences.edit().putBoolean(getString(R.string.update_app_key), false).apply();
//        }

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            getPreferenceScreen().removePreference(
                    findPreference(getString(R.string.debug_pref_screen_key)));
        }
    }

    @Override
    public void onCreateOptionsMenu(
            @NonNull final Menu menu,
            @NonNull final MenuInflater inflater
    ) {
        super.onCreateOptionsMenu(menu, inflater);

        // -- Link settings activity and register menu --
        settingsActivity = (SettingsActivity) getActivity();

        inflater.inflate(R.menu.menu_settings_main_fragment, menu);

        final MenuItem menuSearchItem = menu.getItem(0);

        settingsActivity.setMenuSearchItem(menuSearchItem);

        menuSearchItem.setOnMenuItemClickListener(ev -> {
            settingsActivity.setSearchActive(true);
            return true;
        });
    }

    @Override
    public void onDestroy() {
        // Unlink activity so that we don't get memory problems
        if (settingsActivity != null) {
            settingsActivity.setMenuSearchItem(null);
            settingsActivity = null;
        }
        super.onDestroy();
    }
}
