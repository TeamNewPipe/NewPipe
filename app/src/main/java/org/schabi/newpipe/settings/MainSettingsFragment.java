package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.support.v7.preference.Preference;

import org.schabi.newpipe.R;

public class MainSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.main_settings);
    }
}
