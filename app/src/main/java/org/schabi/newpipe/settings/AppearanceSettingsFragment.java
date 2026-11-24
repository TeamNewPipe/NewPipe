package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.appearance.CustomThemeColor;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();
        org.schabi.newpipe.settings.appearance.LauncherIcons.bind(this);
        org.schabi.newpipe.settings.appearance.AppFontSettings.bind(this);
        org.schabi.newpipe.settings.appearance.NavigationAppearanceSettings.bind(this);

        final String themeKey = getString(R.string.theme_key);
        final ListPreference themes = findPreference(themeKey);
        final CharSequence[] entries = java.util.Arrays.copyOf(themes.getEntries(),
                themes.getEntries().length + 1);
        final CharSequence[] values = java.util.Arrays.copyOf(themes.getEntryValues(),
                themes.getEntryValues().length + 1);
        entries[entries.length - 1] = getString(R.string.eye_comfort_theme_title);
        values[values.length - 1] = getString(R.string.eye_comfort_theme_key);
        themes.setEntries(entries);
        themes.setEntryValues(values);
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
        if (ThemeHelper.isEyeComfortSelected(requireContext())) {
            final Preference themeColor = findPreference(themeColorKey);
            themeColor.setEnabled(false);
            // ListPreference rejects a fixed summary while its simple provider is installed.
            themeColor.setSummaryProvider(null);
            themeColor.setSummary(R.string.eye_comfort_theme_summary);
        }
        final String startThemeColorKey = defaultPreferences
                .getString(themeColorKey, getString(R.string.default_theme_color_value));
        final EditTextPreference customThemeColor = findPreference(
                getString(R.string.custom_theme_color_key));
        customThemeColor.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setSelectAllOnFocus(true);
        });
        customThemeColor.setSummaryProvider(preference -> {
            final String normalized = CustomThemeColor.normalize(
                    ((EditTextPreference) preference).getText());
            return normalized == null ? getString(R.string.custom_theme_color_summary) : normalized;
        });
        customThemeColor.setVisible("custom".equals(startThemeColorKey));
        customThemeColor.setOnPreferenceChangeListener((preference, newValue) -> {
            final String normalized = CustomThemeColor.normalize(newValue.toString());
            if (normalized == null) {
                Toast.makeText(requireContext(), R.string.custom_theme_color_invalid,
                        Toast.LENGTH_LONG).show();
                return false;
            }
            final String previous = CustomThemeColor.normalize(customThemeColor.getText());
            if (normalized.equals(previous)) {
                return false;
            }
            defaultPreferences.edit().putString(
                    getString(R.string.custom_theme_color_key), normalized).apply();
            customThemeColor.setText(normalized);
            showThemeColorRestartDialog(previous == null ? "" : previous, normalized);
            return false;
        });
        findPreference(themeColorKey).setOnPreferenceChangeListener((preference, newValue) -> {
            customThemeColor.setVisible("custom".equals(newValue.toString()));
            applyThemeColorChange(startThemeColorKey, themeColorKey, preference, newValue);
            return false;
        });
        if (ThemeHelper.isEyeComfortSelected(requireContext())) {
            customThemeColor.setEnabled(false);
            customThemeColor.setSummaryProvider(null);
            customThemeColor.setSummary(R.string.eye_comfort_theme_summary);
        }

        if (org.schabi.newpipe.themes.ThemePacks.active(requireContext()) != null) {
            for (final String key : new String[] {themeKey, nightThemeKey, themeColorKey,
                    getString(R.string.custom_theme_color_key)}) {
                final Preference preference = findPreference(key);
                preference.setEnabled(false);
                preference.setSummaryProvider(null);
                preference.setSummary(R.string.theme_pack_managed);
            }
        }

        final boolean showLargeScreenPreferences = DeviceUtils.isTablet(requireContext())
                || DeviceUtils.isTv(requireContext());
        setPreferenceVisible(R.string.tv_related_sidebar_width_key,
                DeviceUtils.isTv(requireContext()));
        setPreferenceVisible(R.string.compact_large_screen_navigation_key,
                showLargeScreenPreferences);
        setPreferenceVisible(R.string.tablet_navigation_portrait_position_key,
                showLargeScreenPreferences);
        setPreferenceVisible(R.string.tablet_navigation_landscape_position_key,
                showLargeScreenPreferences);
    }
    private void setPreferenceVisible(final int key, final boolean visible) {
        final Preference preference = findPreference(getString(key));
        if (preference != null) {
            preference.setVisible(visible);
        }
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

        showThemeColorRestartDialog(beginningThemeColorKey, newThemeColor);
    }

    private void showThemeColorRestartDialog(final String beginningThemeColorKey,
                                             final String newThemeColor) {
        final Activity activity = getActivity();
        if (!newThemeColor.equals(beginningThemeColorKey) && activity != null) {
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
