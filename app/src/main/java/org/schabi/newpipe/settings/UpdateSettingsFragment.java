package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import org.schabi.newpipe.NewVersionWorker;
import org.schabi.newpipe.R;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private final Preference.OnPreferenceChangeListener updatePreferenceChange = (p, nVal) -> {
        final boolean checkForUpdates = (boolean) nVal;
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), checkForUpdates)
                .apply();

        if (checkForUpdates) {
            checkNewVersionNow();
        }
        return true;
    };

    private final Preference.OnPreferenceClickListener manualUpdateClick = preference -> {
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show();
        checkNewVersionNow();
        return true;
    };

    private void checkNewVersionNow() {
        // Search for updates immediately when update checks are enabled.
        // Reset the expire time. This is necessary to check for an update immediately.
        defaultPreferences.edit()
                .putLong(getString(R.string.update_expiry_key), 0).apply();
        NewVersionWorker.enqueueNewVersionCheckingWork(getContext());
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        findPreference(getString(R.string.update_app_key))
                .setOnPreferenceChangeListener(updatePreferenceChange);
        findPreference(getString(R.string.manual_update_key))
                .setOnPreferenceClickListener(manualUpdateClick);
    }
}
