package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.core.app.ActivityCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;

public class ResetSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        // reset appearance to light theme
        defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        defaultPreferences.edit().putString(getString(R.string.theme_key),
                getString(R.string.light_theme_key)).apply();
        ThemeHelper.setDayNightMode(requireContext(), "light_theme");
    }
}
