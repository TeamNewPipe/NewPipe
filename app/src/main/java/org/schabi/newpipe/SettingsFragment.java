package org.schabi.newpipe;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import com.nononsenseapps.filepicker.FilePickerActivity;

import info.guardianproject.netcipher.proxy.OrbotHelper;

/**
 * Created by david on 15/06/16.
 */
public class SettingsFragment  extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    // get keys
    String DEFAULT_RESOLUTION_PREFERENCE;
    String DEFAULT_AUDIO_FORMAT_PREFERENCE;
    String SEARCH_LANGUAGE_PREFERENCE;
    String DOWNLOAD_PATH_PREFERENCE;
    String DOWNLOAD_PATH_AUDIO_PREFERENCE;
    String USE_TOR_KEY;

    private ListPreference defaultResolutionPreference;
    private ListPreference defaultAudioFormatPreference;
    private ListPreference searchLanguagePreference;
    private Preference downloadPathPreference;
    private EditTextPreference downloadPathAudioPreference;
    private CheckBoxPreference useTorCheckBox;
    private SharedPreferences defaultPreferences;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        final Activity activity = getActivity();

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        // get keys
        DEFAULT_RESOLUTION_PREFERENCE = getString(R.string.default_resolution_key);
        DEFAULT_AUDIO_FORMAT_PREFERENCE = getString(R.string.default_audio_format_key);
        SEARCH_LANGUAGE_PREFERENCE = getString(R.string.search_language_key);
        DOWNLOAD_PATH_PREFERENCE = getString(R.string.download_path_key);
        DOWNLOAD_PATH_AUDIO_PREFERENCE = getString(R.string.download_path_audio_key);
        USE_TOR_KEY = getString(R.string.use_tor_key);

        // get pref objects
        defaultResolutionPreference =
                (ListPreference) findPreference(DEFAULT_RESOLUTION_PREFERENCE);
        defaultAudioFormatPreference =
                (ListPreference) findPreference(DEFAULT_AUDIO_FORMAT_PREFERENCE);
        searchLanguagePreference =
                (ListPreference) findPreference(SEARCH_LANGUAGE_PREFERENCE);
        downloadPathPreference = findPreference(DOWNLOAD_PATH_PREFERENCE);
        downloadPathAudioPreference =
                (EditTextPreference) findPreference(DOWNLOAD_PATH_AUDIO_PREFERENCE);
        useTorCheckBox = (CheckBoxPreference) findPreference(USE_TOR_KEY);

        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                  String key) {
                Activity a = getActivity();

                if(a != null) {
                    updateSummary();

                    if (defaultPreferences.getBoolean(USE_TOR_KEY, false)) {
                        if (OrbotHelper.isOrbotInstalled(a)) {
                            App.configureTor(true);
                            OrbotHelper.requestStartTor(a);
                        } else {
                            Intent intent = OrbotHelper.getOrbotInstallIntent(a);
                            a.startActivityForResult(intent, SettingsActivity.REQUEST_INSTALL_ORBOT);
                        }
                    } else {
                        App.configureTor(false);
                    }
                }
            }
        };
        defaultPreferences.registerOnSharedPreferenceChangeListener(prefListener);


        updateSummary();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if(preference.getKey() == downloadPathPreference.getKey())
        {
            Activity activity = getActivity();
            Intent i = new Intent(activity, FilePickerActivity.class);

            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);

            activity.startActivityForResult(i, 233);
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    // This is used to show the status of some preference in the description
    private void updateSummary() {
        defaultResolutionPreference.setSummary(
                defaultPreferences.getString(DEFAULT_RESOLUTION_PREFERENCE,
                        getString(R.string.default_resolution_value)));
        defaultAudioFormatPreference.setSummary(
                defaultPreferences.getString(DEFAULT_AUDIO_FORMAT_PREFERENCE,
                        getString(R.string.default_audio_format_value)));
        searchLanguagePreference.setSummary(
                defaultPreferences.getString(SEARCH_LANGUAGE_PREFERENCE,
                        getString(R.string.default_language_value)));
        downloadPathPreference.setSummary(
                defaultPreferences.getString(DOWNLOAD_PATH_PREFERENCE,
                        getString(R.string.download_path_summary)));
        downloadPathAudioPreference.setSummary(
                defaultPreferences.getString(DOWNLOAD_PATH_AUDIO_PREFERENCE,
                        getString(R.string.download_path_audio_summary)));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }
}
