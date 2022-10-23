package org.schabi.newpipe.settings;

import android.os.Bundle;

public class ResetSettingsFragment extends BasePreferenceFragment {

    private AppearanceSettingsFragment appearanceSettings;
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        appearanceSettings.resetToDefault();
    }
}
