package org.schabi.newpipe.settings;

import android.os.Bundle;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;

public class MainSettingsFragment extends BasePreferenceFragment {
    public static final boolean DEBUG = MainActivity.DEBUG;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.main_settings);
    }
}
