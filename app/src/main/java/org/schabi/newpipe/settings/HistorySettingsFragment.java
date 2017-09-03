package org.schabi.newpipe.settings;

import android.os.Bundle;

import org.schabi.newpipe.R;

public class HistorySettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.history_settings);
    }
}
