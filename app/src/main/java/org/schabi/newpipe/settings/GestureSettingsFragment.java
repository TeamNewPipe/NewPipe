package org.schabi.newpipe.settings;

import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;

import java.util.LinkedList;
import java.util.List;

public class GestureSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
        updateSeekOptions();
        setupSpeedGestureMutualExclusion();
    }

    private void setupSpeedGestureMutualExclusion() {
        final SwitchPreferenceCompat fullscreenPref = findPreference(
                getString(R.string.fullscreen_gesture_control_key));
        final SwitchPreferenceCompat speedPref = findPreference(
                getString(R.string.playback_speed_gesture_control_key));

        if (fullscreenPref == null || speedPref == null) {
            return;
        }

        fullscreenPref.setOnPreferenceChangeListener((pref, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                speedPref.setChecked(false);
            }
            return true;
        });

        speedPref.setOnPreferenceChangeListener((pref, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                fullscreenPref.setChecked(false);
            }
            return true;
        });
    }

    private void updateSeekOptions() {
        final Resources res = getResources();
        final String[] durationsValues = res.getStringArray(R.array.seek_duration_value);
        final List<String> displayedDurationValues = new LinkedList<>();
        final List<String> displayedDescriptionValues = new LinkedList<>();
        int currentDurationValue;

        for (final String durationsValue : durationsValues) {
            currentDurationValue =
                    Integer.parseInt(durationsValue) / (int) DateUtils.SECOND_IN_MILLIS;

            displayedDurationValues.add(durationsValue);
            try {
                displayedDescriptionValues.add(String.format(
                        res.getQuantityString(R.plurals.seconds,
                                currentDurationValue),
                        currentDurationValue));
            } catch (final Resources.NotFoundException ignored) {
            }
        }

        final ListPreference durations = findPreference(
                getString(R.string.seek_duration_key));
        durations.setEntryValues(displayedDurationValues.toArray(new CharSequence[0]));
        durations.setEntries(displayedDescriptionValues.toArray(new CharSequence[0]));
    }
}
