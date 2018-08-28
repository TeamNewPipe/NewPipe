package org.schabi.newpipe.settings;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;

public class AppearanceSettingsFragment extends BasePreferenceFragment {
    private final static boolean CAPTIONING_SETTINGS_ACCESSIBLE =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    /**
     * Theme that was applied when the settings was opened (or recreated after a theme change)
     */
    private String startThemeKey;
    private String captionSettingsKey;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String themeKey = getString(R.string.theme_key);
        startThemeKey = defaultPreferences.getString(themeKey, getString(R.string.default_theme_value));
        findPreference(themeKey).setOnPreferenceChangeListener(themePreferenceChange);

        captionSettingsKey = getString(R.string.caption_settings_key);
        if (!CAPTIONING_SETTINGS_ACCESSIBLE)  {
            final Preference captionSettings = findPreference(captionSettingsKey);
            getPreferenceScreen().removePreference(captionSettings);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.appearance_settings);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(captionSettingsKey) && CAPTIONING_SETTINGS_ACCESSIBLE) {
            startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        }

        return super.onPreferenceTreeClick(preference);
    }

    private final Preference.OnPreferenceChangeListener themePreferenceChange = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
            defaultPreferences.edit().putString(getString(R.string.theme_key), newValue.toString()).apply();

            if (!newValue.equals(startThemeKey) && getActivity() != null) {
                // If it's not the current theme
                getActivity().recreate();
            }

            return false;
        }
    };
}
