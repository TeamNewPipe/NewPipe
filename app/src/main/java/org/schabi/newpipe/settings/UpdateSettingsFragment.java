package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;

import static org.schabi.newpipe.CheckForNewAppVersion.startNewVersionCheckService;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private final Preference.OnPreferenceChangeListener updatePreferenceChange
            = (preference, checkForUpdates) -> {
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), (boolean) checkForUpdates).apply();

                if ((boolean) checkForUpdates) {
                    // Search for updates immediately when update checks are enabled.
                    // Reset the expire time. This is necessary to check for an update immediately.
                    defaultPreferences.edit()
                            .putLong(getString(R.string.update_expiry_key), 0).apply();
                    startNewVersionCheckService();
                }
        return true;
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.update_settings);

        final String updateToggleKey = getString(R.string.update_app_key);
        findPreference(updateToggleKey).setOnPreferenceChangeListener(updatePreferenceChange);
    }
}
