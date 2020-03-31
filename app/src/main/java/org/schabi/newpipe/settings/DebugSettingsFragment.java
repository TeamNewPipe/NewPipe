package org.schabi.newpipe.settings;

import android.os.Bundle;

import org.schabi.newpipe.R;

public class DebugSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.debug_settings);
    }
}
