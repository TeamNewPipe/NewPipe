package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.preference.ListPreference;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {
    // these arrays are all sorted from highest to lowest resolution
    private static final List<String> NORMAL_RESOLUTIONS =
            List.of("1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p");
    private static final List<String> HIGHER_RESOLUTIONS =
            List.of("2160p60", "2160p", "1440p60", "1440p");
    private static final List<String> ALL_RESOLUTIONS =
            Stream.of(HIGHER_RESOLUTIONS, NORMAL_RESOLUTIONS)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private ListPreference defaultResolution;
    private ListPreference defaultPopupResolution;
    private ListPreference limitMobileDataUsage;


    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        updateSeekOptions();
        defaultResolution = findPreference(getString(R.string.default_resolution_key));
        defaultPopupResolution = findPreference(getString(R.string.default_popup_resolution_key));
        limitMobileDataUsage = findPreference(getString(R.string.limit_mobile_data_usage_key));
        updateResolutions();

        listener = (sharedPreferences, s) -> {

            // on M and above, if user chooses to minimise to popup player on exit
            // and the app doesn't have display over other apps permission,
            // show a snackbar to let the user give permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && s.equals(getString(R.string.minimize_on_exit_key))) {
                final String newSetting = sharedPreferences.getString(s, null);
                if (newSetting != null
                        && newSetting.equals(getString(R.string.minimize_on_exit_popup_key))
                        && !Settings.canDrawOverlays(getContext())) {

                    Snackbar.make(getListView(), R.string.permission_display_over_apps,
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.settings, view ->
                                    PermissionHelper.checkSystemAlertWindowPermission(getContext()))
                            .show();

                }
            } else if (s.equals(getString(R.string.use_inexact_seek_key))) {
                updateSeekOptions();
            } else if (s.equals(getString(R.string.show_higher_resolutions_key))) {
                updateResolutions();
        }
        };
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

        final ListPreference durations = findPreference(
                getString(R.string.seek_duration_key));
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


    /**
     * Update resolution options. Defaults to 1080p60 and removes higher options when the "Show
     * higher resolutions" switch is disabled.
     */
    private void updateResolutions() {
        final boolean showHigherResolutions = getPreferenceManager().getSharedPreferences()
            .getBoolean(getString(R.string.show_higher_resolutions_key), false);

        updateResolutions(defaultResolution, showHigherResolutions,
                R.string.best_resolution_key, R.string.best_resolution);
        updateResolutions(defaultPopupResolution, showHigherResolutions,
                R.string.best_resolution_key, R.string.best_resolution);
        updateResolutions(limitMobileDataUsage, showHigherResolutions,
                R.string.limit_mobile_data_usage_none_key,
                R.string.limit_data_usage_none_description);
    }

    private void updateResolutions(final ListPreference preference,
                                   final boolean showHigherResolutions,
                                   @StringRes final int noResolutionValue,
                                   @StringRes final int noResolutionDescription) {
        final List<String> resolutionValues = new ArrayList<>();
        final List<String> resolutionDescriptions = new ArrayList<>();

        // add in the first place the "no resolution chosen" option (i.e. "best" or "no limit")
        resolutionValues.add(getString(noResolutionValue));
        resolutionDescriptions.add(getString(noResolutionDescription));

        if (showHigherResolutions) {
            // set the whole arrays
            resolutionValues.addAll(ALL_RESOLUTIONS);
            resolutionDescriptions.addAll(ALL_RESOLUTIONS);
        } else {
            // reset the current value to the biggest non-higher resolution if needed
            if (HIGHER_RESOLUTIONS.contains(preference.getValue())) {
                preference.setValue(NORMAL_RESOLUTIONS.get(0));
            }

            // only keep non-higher resolutions
            resolutionValues.addAll(NORMAL_RESOLUTIONS);
            resolutionDescriptions.addAll(NORMAL_RESOLUTIONS);
        }

        // finally set the computed arrays to the preference
        preference.setEntryValues(resolutionValues.toArray(new String[0]));
        preference.setEntries(resolutionDescriptions.toArray(new String[0]));
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
