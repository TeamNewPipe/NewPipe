package org.schabi.newpipe.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;

public class AppearanceSettingsFragment extends BasePreferenceFragment {
    private static final boolean CAPTIONING_SETTINGS_ACCESSIBLE =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    /**
     * Theme that was applied when the settings was opened (or recreated after a theme change).
     */
    private String startThemeKey;
    private final Preference.OnPreferenceChangeListener themePreferenceChange
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, final Object newValue) {
            defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
            defaultPreferences.edit()
                    .putString(getString(R.string.theme_key), newValue.toString()).apply();

            if (!newValue.equals(startThemeKey) && getActivity() != null) {
                // If it's not the current theme
                getActivity().recreate();
            }

            return false;
        }
    };
    private String captionSettingsKey;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String themeKey = getString(R.string.theme_key);
        startThemeKey = defaultPreferences
                .getString(themeKey, getString(R.string.default_theme_value));
        findPreference(themeKey).setOnPreferenceChangeListener(themePreferenceChange);

        captionSettingsKey = getString(R.string.caption_settings_key);
        if (!CAPTIONING_SETTINGS_ACCESSIBLE) {
            final Preference captionSettings = findPreference(captionSettingsKey);
            getPreferenceScreen().removePreference(captionSettings);
        }
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.appearance_settings);
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(captionSettingsKey) && CAPTIONING_SETTINGS_ACCESSIBLE) {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onPreferenceTreeClick(preference);
    }
}
