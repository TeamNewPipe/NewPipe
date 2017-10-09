package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;

public class AppearanceSettingsFragment extends BasePreferenceFragment {
    /**
     * Theme that was applied when the settings was opened (or recreated after a theme change)
     */
    private String startThemeKey;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String themeKey = getString(R.string.theme_key);
        startThemeKey = defaultPreferences.getString(themeKey, getString(R.string.default_theme_value));
        findPreference(themeKey).setOnPreferenceChangeListener(themePreferenceChange);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.appearance_settings);
    }

    private Preference.OnPreferenceChangeListener themePreferenceChange = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
            defaultPreferences.edit().putString(getString(R.string.theme_key), newValue.toString()).apply();

            if (!newValue.equals(startThemeKey)) { // If it's not the current theme
                getActivity().recreate();
            }

            return false;
        }
    };
}
