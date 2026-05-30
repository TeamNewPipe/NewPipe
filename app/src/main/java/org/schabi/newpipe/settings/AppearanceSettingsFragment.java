package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

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

        final String nightThemeKey = getString(R.string.night_theme_key);
        if (startThemeKey.equals(autoDeviceThemeKey)) {
            final String startNightThemeKey = defaultPreferences
                    .getString(nightThemeKey, getString(R.string.default_night_theme_value));

            findPreference(nightThemeKey).setOnPreferenceChangeListener((preference, newValue) -> {
                applyThemeChange(startNightThemeKey, nightThemeKey, newValue);
                return false;
            });
        } else {
            // disable the night theme selection
            final Preference preference = findPreference(nightThemeKey);
            if (preference != null) {
                preference.setEnabled(false);
                preference.setSummary(getString(R.string.night_theme_available,
                        getString(R.string.auto_device_theme_title)));
            }
        }

        final String themeColorKey = getString(R.string.theme_color_key);
        final String startThemeColorKey = defaultPreferences
                .getString(themeColorKey, getString(R.string.default_theme_color_value));
        findPreference(themeColorKey).setOnPreferenceChangeListener((preference, newValue) -> {
            applyThemeColorChange(startThemeColorKey, themeColorKey, preference, newValue);
            return false;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (getString(R.string.caption_settings_key).equals(preference.getKey())) {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void applyThemeColorChange(final String beginningThemeColorKey,
                                       final String themeColorKey,
                                       final Preference preference,
                                       final Object newValue) {
        final String newThemeColor = newValue.toString();
        defaultPreferences.edit().putString(themeColorKey, newThemeColor).apply();
        if (preference instanceof ListPreference) {
            ((ListPreference) preference).setValue(newThemeColor);
        }

        final Activity activity = getActivity();
        if (!newValue.equals(beginningThemeColorKey) && activity != null) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.theme_color_restart_dialog_title)
                    .setMessage(R.string.theme_color_restart_dialog_message)
                    .setPositiveButton(R.string.theme_color_restart_apply_now,
                            (dialog, which) -> ActivityCompat.recreate(activity))
                    .setNeutralButton(R.string.theme_color_restart_app,
                            (dialog, which) -> restartApplication(activity))
                    .setNegativeButton(R.string.theme_color_restart_later,
                            (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    private void restartApplication(final Activity activity) {
        final Intent restartIntent = activity.getPackageManager()
                .getLaunchIntentForPackage(activity.getPackageName());
        if (restartIntent == null) {
            ActivityCompat.recreate(activity);
            return;
        }

        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(restartIntent);
        activity.finishAffinity();
    }

    private void applyThemeChange(final String beginningThemeKey,
                                  final String themeKey,
                                  final Object newValue) {
        defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        defaultPreferences.edit().putString(themeKey, newValue.toString()).apply();

        ThemeHelper.setDayNightMode(requireContext(), newValue.toString());

        if (!newValue.equals(beginningThemeKey) && getActivity() != null) {
            // if it's not the current theme
            ActivityCompat.recreate(getActivity());
        }
    }
}
