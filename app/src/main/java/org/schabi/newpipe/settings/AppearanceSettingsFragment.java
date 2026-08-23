package org.schabi.newpipe.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            removePreference(getString(R.string.player_notification_screen_key));
        }

        final String themeKey = getString(R.string.theme_key);
        // the key of the active theme when settings were opened (or recreated after theme change)
        final String startThemeKey = defaultPreferences
                .getString(themeKey, getString(R.string.default_theme_value));
        final String autoDeviceThemeKey = getString(R.string.auto_device_theme_key);
        findPreference(themeKey).setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.toString().equals(autoDeviceThemeKey)) {
                Toast.makeText(getContext(), getString(R.string.select_night_theme_toast),
                        Toast.LENGTH_LONG).show();
            }

            applyThemeChange(startThemeKey, themeKey, newValue);
            return false;
        });

        final String themeColorKey = getString(R.string.theme_color_key);
        final String startThemeColorKey = defaultPreferences
                .getString(themeColorKey, getString(R.string.default_theme_color_value));
        final Preference themeColorPreference = findPreference(themeColorKey);
        if (themeColorPreference != null) {
            themeColorPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
                defaultPreferences.edit().putString(themeColorKey, newValue.toString()).apply();

                if (!newValue.equals(startThemeColorKey) && getActivity() != null) {
                    ActivityCompat.recreate(getActivity());
                }
                return false;
            });
        }

        final String nightThemeKey = getString(R.string.night_theme_key);
        if (startThemeKey.equals(autoDeviceThemeKey)) {
            final String startNightThemeKey = defaultPreferences
                    .getString(nightThemeKey, getString(R.string.default_night_theme_value));

            findPreference(nightThemeKey).setOnPreferenceChangeListener((preference, newValue) -> {
                applyThemeChange(startNightThemeKey, nightThemeKey, newValue);
                return false;
            });
        } else {
            removePreference(nightThemeKey);
        }
    }

    private void removePreference(final String preferenceKey) {
        final Preference preference = findPreference(preferenceKey);
        if (preference != null) {
            getPreferenceScreen().removePreference(preference);
        }
    }

    private void applyThemeChange(final String beginningThemeKey,
                                  final String themeKey,
                                  final Object newValue) {
        defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        defaultPreferences.edit().putString(themeKey, newValue.toString()).apply();

        ThemeHelper.setDayNightMode(getContext(), newValue.toString());

        if (!newValue.equals(beginningThemeKey) && getActivity() != null) {
            // if it's not the current theme
            ActivityCompat.recreate(getActivity());
        }
    }
}
