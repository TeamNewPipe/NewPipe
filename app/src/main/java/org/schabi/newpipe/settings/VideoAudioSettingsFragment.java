package org.schabi.newpipe.settings;

import static org.schabi.newpipe.util.PermissionHelper.checkSystemAlertWindowPermission;
import static org.schabi.newpipe.util.PictureInPictureHelper.isAndroidPictureInPictureEnabled;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.preference.ListPreference;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;

import java.util.LinkedList;
import java.util.List;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (sharedPreferences, key) -> {
                // on M and above, if user chooses to minimise to popup player on exit
                // and the app doesn't have display over other apps permission,
                // show a snackbar to let the user give permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && key.equals(getString(R.string.minimize_on_exit_key))
                        // If Android picture-in-picture is enabled, this permission is not needed.
                        && !isAndroidPictureInPictureEnabled(requireContext())) {
                    final String newSetting = sharedPreferences.getString(key, null);
                    if (getString(R.string.minimize_on_exit_popup_key).equals(newSetting)
                            && !Settings.canDrawOverlays(requireContext())) {
                        Snackbar.make(getListView(), R.string.permission_display_over_apps,
                                        BaseTransientBottomBar.LENGTH_INDEFINITE)
                                .setAction(R.string.settings,
                                        view -> checkSystemAlertWindowPermission(requireContext()))
                                .show();
                    }
                } else if (getString(R.string.use_inexact_seek_key).equals(key)) {
                    updateSeekOptions();
                }
            };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        updateSeekOptions();

        final boolean isPipUnavailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || !requireContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);

        // Disable PiP configuration if the device is running Android < 7.0 or Android Go.
        if (isPipUnavailable) {
            final ListPreference popupConfig =
                    findPreference(getString(R.string.popup_configuration_key));
            if (popupConfig != null) {
                popupConfig.setEnabled(false);
                // If the Android version is >= 7.0, then PiP is disabled when this point is
                // reached.
                popupConfig.setSummary(Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                        ? R.string.pip_unavailable : R.string.pip_disabled);
            }
        }
    }

    /**
     * Update fast-forward/-rewind seek duration options
     * according to language and inexact seek setting.
     * Exoplayer can't seek 5 seconds in audio when using inexact seek.
     */
    private void updateSeekOptions() {
        // initializing R.array.seek_duration_description to display the translation of seconds
        final Resources res = getResources();
        final String[] durationsValues = res.getStringArray(R.array.seek_duration_value);
        final List<String> displayedDurationValues = new LinkedList<>();
        final List<String> displayedDescriptionValues = new LinkedList<>();
        int currentDurationValue;
        final boolean inexactSeek = getPreferenceManager().getSharedPreferences()
                .getBoolean(res.getString(R.string.use_inexact_seek_key), false);

        for (final String durationsValue : durationsValues) {
            currentDurationValue =
                    Integer.parseInt(durationsValue) / (int) DateUtils.SECOND_IN_MILLIS;
            if (inexactSeek && currentDurationValue % 10 == 5) {
                continue;
            }

            displayedDurationValues.add(durationsValue);
            try {
                displayedDescriptionValues.add(String.format(
                        res.getQuantityString(R.plurals.seconds,
                                currentDurationValue),
                        currentDurationValue));
            } catch (final Resources.NotFoundException ignored) {
                // if this happens, the translation is missing,
                // and the english string will be displayed instead
            }
        }

        final ListPreference durations = findPreference(getString(R.string.seek_duration_key));
        durations.setEntryValues(displayedDurationValues.toArray(new CharSequence[0]));
        durations.setEntries(displayedDescriptionValues.toArray(new CharSequence[0]));
        final int selectedDuration = Integer.parseInt(durations.getValue());
        if (inexactSeek && selectedDuration / (int) DateUtils.SECOND_IN_MILLIS % 10 == 5) {
            final int newDuration = selectedDuration / (int) DateUtils.SECOND_IN_MILLIS + 5;
            durations.setValue(Integer.toString(newDuration * (int) DateUtils.SECOND_IN_MILLIS));

            final Toast toast = Toast
                    .makeText(getContext(),
                            getString(R.string.new_seek_duration_toast, newDuration),
                            Toast.LENGTH_LONG);
            toast.show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(listener);

    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(listener);
    }
}
