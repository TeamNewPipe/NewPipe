package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.preference.ListPreference;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private ListPreference defaultRes;
    private ListPreference defaultPopupRes;
    private ListPreference limitMobDataUsage;


    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        updateSeekOptions();

        //fetch resolution options
        defaultRes = findPreference(getString(R.string.default_resolution_key));
        defaultPopupRes = findPreference(getString(R.string.default_popup_resolution_key));
        limitMobDataUsage = findPreference(getString(R.string.limit_mobile_data_usage_key));

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


    /***
     * Update resolutions when user toggles "Show higher resolutions".
     * if the user toggles the value off and the settings are set to 1440p or above,
     * change them to 1080p60 (highest value available).
     */
    private void updateResolutions() {


        if (getPreferenceManager().getSharedPreferences()
                .getBoolean(getString(R.string.show_higher_resolutions_key), false)) {

            //if setting was turned on, enable additional resolutions
            showHigherResolutions(true);

        } else {

            showHigherResolutions(false);


        }
    }

    private void showHigherResolutions(final boolean isShown) {


        final Resources res = getResources();

        final ArrayList<String> resolutions = new ArrayList<>(Arrays.asList(res.
                getStringArray(R.array.resolution_list_description)));

        final ArrayList<String> additionalResolutions = new ArrayList<>(Arrays.asList(res.
                getStringArray(R.array.resolution_list_description_additional_resolutions)));


        final ArrayList<String> resolutionValues = new ArrayList<>(Arrays.asList(res.
                getStringArray(R.array.resolution_list_values)));

        final ArrayList<String> additionalResolutionValues = new ArrayList<>(Arrays.
                asList(res.getStringArray(R.array.resolution_list_values_additional_resolutions)));


        final ArrayList<String> mobileDataResolutions = new ArrayList<>(Arrays.asList(res.
                getStringArray(R.array.limit_data_usage_description_list)));

        final ArrayList<String> additionalMobileDataResolutions =
                new ArrayList<String>(Arrays.asList(res.
                        getStringArray(R.array.
                                limit_data_usage_description_list_additional_resolutions)));


        final ArrayList<String> mobileDataResolutionValues = new ArrayList<>(Arrays.
                asList(res.getStringArray(R.array.limit_data_usage_values_list)));

        final ArrayList<String> additionalMobileDataResolutionValues =
                new ArrayList<>(Arrays.asList(res.getStringArray(R.array.
                        limit_data_usage_values_list_additional_resolutions)));

        if (isShown) {

            //index 1 because of the best resolution option
            resolutions.addAll(1, additionalResolutions);
            resolutionValues.addAll(1, additionalResolutionValues);

            //index 1 because of the no limit option
            mobileDataResolutions.addAll(1, additionalMobileDataResolutions);
            mobileDataResolutionValues.addAll(1, additionalMobileDataResolutionValues);

        }

        //apply changed to the list
        defaultRes.setEntries(resolutions.toArray(new String[0]));
        defaultRes.setEntryValues(resolutionValues.toArray(new String[0]));

        defaultPopupRes.setEntries(resolutions.toArray(new String[0]));
        defaultPopupRes.setEntryValues(resolutionValues.toArray(new String[0]));

        limitMobDataUsage.setEntries(mobileDataResolutions.toArray(new String[0]));
        limitMobDataUsage.setEntryValues(mobileDataResolutionValues.toArray(new String[0]));

        //make sure user hasn't selected any higher resolutions before turning higher
        //resolutions off.

        if (additionalResolutionValues.contains(defaultRes.getValue())) {
            defaultRes.setValueIndex(1);
        }

        if (additionalResolutionValues.contains(defaultPopupRes.getValue())) {
            defaultPopupRes.setValueIndex(1);
        }

        if (additionalMobileDataResolutionValues.contains(limitMobDataUsage.getValue())) {
            limitMobDataUsage.setValueIndex(1);
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
