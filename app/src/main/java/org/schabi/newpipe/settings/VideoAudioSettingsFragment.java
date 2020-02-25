package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    private ListPreference defaultRes,defaultPopupRes,limitMobDataUsage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initializing R.array.seek_duration_description to display the translation of seconds
        Resources res = getResources();
        String[] durationsValues = res.getStringArray(R.array.seek_duration_value);
        String[] durationsDescriptions = res.getStringArray(R.array.seek_duration_description);
        int currentDurationValue;
        for (int i = 0; i < durationsDescriptions.length; i++) {
            currentDurationValue = Integer.parseInt(durationsValues[i]) / 1000;
            try {
                durationsDescriptions[i] = String.format(
                        res.getQuantityString(R.plurals.dynamic_seek_duration_description, currentDurationValue),
                        currentDurationValue);
            } catch (Resources.NotFoundException ignored) {
                //if this happens, the translation is missing, and the english string will be displayed instead
            }
        }
        ListPreference durations = (ListPreference) findPreference(getString(R.string.seek_duration_key));
        durations.setEntries(durationsDescriptions);

        defaultRes = (ListPreference) findPreference(getString(R.string.default_resolution_key));
        defaultPopupRes = (ListPreference) findPreference(getString(R.string.default_popup_resolution_key));
        limitMobDataUsage = (ListPreference) findPreference(getString(R.string.limit_mobile_data_usage_key));

        listener = (sharedPreferences, s) -> {

            // on M and above, if user chooses to minimise to popup player on exit and the app doesn't have
            // display over other apps permission, show a snackbar to let the user give permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    s.equals(getString(R.string.minimize_on_exit_key))) {

                String newSetting = sharedPreferences.getString(s, null);
                if (newSetting != null
                        && newSetting.equals(getString(R.string.minimize_on_exit_popup_key))
                        && !Settings.canDrawOverlays(getContext())) {

                    Snackbar.make(getListView(), R.string.permission_display_over_apps, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.settings,
                                    view -> PermissionHelper.checkSystemAlertWindowPermission(getContext()))
                            .show();

                }
            }

            //check if "show higher resolutions" was changed
            if(s.equals(getString(R.string.show_higher_resolutions_key))){

                if(checkIfShowHighRes()){
                    showHigherResolutions(true);
                }
                else {

                    //if the setting was turned off and any of the defaults is set to 1440p or 2160p, change them to 1080p60
                    //(the next highest value)
                    if(defaultRes.getValue().equals("1440p") || defaultRes.getValue().equals("2160p")){
                        defaultRes.setValueIndex(3);
                    }
                    if(defaultPopupRes.getValue().equals("1440p") || defaultPopupRes.getValue().equals("2160p")){
                        defaultPopupRes.setValueIndex(3);
                    }
                    if(limitMobDataUsage.getValue().equals("1440p") || limitMobDataUsage.getValue().equals("2160p")){
                        limitMobDataUsage.setValueIndex(3);
                    }

                    showHigherResolutions(false);

                }
            }

        };

        //if "show higher resolutions" is off, remove the higher resolutions from the default resolutions lists
        if(!checkIfShowHighRes()){
            showHigherResolutions(false);
        }
    }

    private boolean checkIfShowHighRes(){
        return getPreferenceManager().getSharedPreferences().getBoolean(getString(R.string.show_higher_resolutions_key),false);
    }

    private void showHigherResolutions(boolean show){

        Resources res = getResources();
        ArrayList<String> resolutions = new ArrayList<String>(Arrays.asList(res.getStringArray(R.array.resolution_list_description)));
        ArrayList<String> resolutionValues = new ArrayList<String>(Arrays.asList(res.getStringArray(R.array.resolution_list_values)));

        ArrayList<String> mobileDataResolutions = new ArrayList<String>(Arrays.asList(res.getStringArray(R.array.limit_data_usage_description_list)));
        ArrayList<String> mobileDataResolutionValues = new ArrayList<String>(Arrays.asList(res.getStringArray(R.array.limit_data_usage_values_list)));

        if(!show) {
            List<String> higherResolutions = Arrays.asList("1440p", "2160p");

            resolutions.removeAll(higherResolutions);
            resolutionValues.removeAll(higherResolutions);

            mobileDataResolutions.removeAll(higherResolutions);
            mobileDataResolutionValues.removeAll(higherResolutions);
        }

        defaultRes.setEntries(resolutions.toArray(new String[resolutions.size()]));
        defaultRes.setEntryValues(resolutionValues.toArray(new String[resolutionValues.size()]));

        defaultPopupRes.setEntries(resolutions.toArray(new String[resolutions.size()]));
        defaultPopupRes.setEntryValues(resolutionValues.toArray(new String[resolutionValues.size()]));

        limitMobDataUsage.setEntries(mobileDataResolutions.toArray(new String[mobileDataResolutions.size()]));
        limitMobDataUsage.setEntryValues(mobileDataResolutionValues.toArray(new String[mobileDataResolutionValues.size()]));

    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.video_audio_settings);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);

    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
    }
}
