package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.schabi.newpipe.R;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private Preference.OnPreferenceChangeListener updatePreferenceChange
            = (preference, newValue) -> {
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), (boolean) newValue).apply();
        return true;
    };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String updateToggleKey = getString(R.string.update_app_key);
        findPreference(updateToggleKey).setOnPreferenceChangeListener(updatePreferenceChange);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.update_settings);
    }
}
