package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.AlertDialog;

import com.nononsenseapps.filepicker.FilePickerActivity;

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;

import java.util.ArrayList;

import info.guardianproject.netcipher.proxy.OrbotHelper;

/**
 * Created by david on 15/06/16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SettingsFragment.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class SettingsFragment  extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    public static final int REQUEST_INSTALL_ORBOT = 0x1234;
    SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    // get keys
    String DEFAULT_RESOLUTION_PREFERENCE;
    String DEFAULT_POPUP_RESOLUTION_PREFERENCE;
    String PREFERRED_VIDEO_FORMAT_PREFERENCE;
    String DEFAULT_AUDIO_FORMAT_PREFERENCE;
    String SEARCH_LANGUAGE_PREFERENCE;
    String DOWNLOAD_PATH_PREFERENCE;
    String DOWNLOAD_PATH_AUDIO_PREFERENCE;
    String USE_TOR_KEY;
    String THEME;
    private ListPreference defaultResolutionPreference;
    private ListPreference defaultPopupResolutionPreference;
    private ListPreference preferredVideoFormatPreference;
    private ListPreference defaultAudioFormatPreference;
    private ListPreference searchLanguagePreference;
    private Preference downloadPathPreference;
    private Preference downloadPathAudioPreference;
    private Preference themePreference;
    private SharedPreferences defaultPreferences;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        final Activity activity = getActivity();

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        // get keys
        DEFAULT_RESOLUTION_PREFERENCE = getString(R.string.default_resolution_key);
        DEFAULT_POPUP_RESOLUTION_PREFERENCE = getString(R.string.default_popup_resolution_key);
        PREFERRED_VIDEO_FORMAT_PREFERENCE = getString(R.string.preferred_video_format_key);
        DEFAULT_AUDIO_FORMAT_PREFERENCE = getString(R.string.default_audio_format_key);
        SEARCH_LANGUAGE_PREFERENCE = getString(R.string.search_language_key);
        DOWNLOAD_PATH_PREFERENCE = getString(R.string.download_path_key);
        DOWNLOAD_PATH_AUDIO_PREFERENCE = getString(R.string.download_path_audio_key);
        THEME = getString(R.string.theme_key);
        USE_TOR_KEY = getString(R.string.use_tor_key);

        // get pref objects
        defaultResolutionPreference =
                (ListPreference) findPreference(DEFAULT_RESOLUTION_PREFERENCE);
        defaultPopupResolutionPreference =
                (ListPreference) findPreference(DEFAULT_POPUP_RESOLUTION_PREFERENCE);
        preferredVideoFormatPreference =
                (ListPreference) findPreference(PREFERRED_VIDEO_FORMAT_PREFERENCE);
        defaultAudioFormatPreference =
                (ListPreference) findPreference(DEFAULT_AUDIO_FORMAT_PREFERENCE);
        searchLanguagePreference =
                (ListPreference) findPreference(SEARCH_LANGUAGE_PREFERENCE);
        downloadPathPreference = findPreference(DOWNLOAD_PATH_PREFERENCE);
        downloadPathAudioPreference = findPreference(DOWNLOAD_PATH_AUDIO_PREFERENCE);
        themePreference = findPreference(THEME);

        final String currentTheme = defaultPreferences.getString(THEME, "Light");

        // TODO: Clean this, as the class is already implementing the class
        // and those double equals...

        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                  String key) {
                Activity a = getActivity();
                if(a == null)
                {
                    return;
                }
                if (key == USE_TOR_KEY)
                {
                    if (defaultPreferences.getBoolean(USE_TOR_KEY, false)) {
                        if (OrbotHelper.isOrbotInstalled(a)) {
                            App.configureTor(true);
                            OrbotHelper.requestStartTor(a);
                        } else {
                            Intent intent = OrbotHelper.getOrbotInstallIntent(a);
                            a.startActivityForResult(intent, REQUEST_INSTALL_ORBOT);
                        }
                    } else {
                        App.configureTor(false);
                    }
                }
                else if (key == DOWNLOAD_PATH_PREFERENCE)
                {
                    String downloadPath = sharedPreferences
                            .getString(DOWNLOAD_PATH_PREFERENCE,
                                    getString(R.string.download_path_summary));
                    downloadPathPreference
                            .setSummary(downloadPath);
                }
                else if (key == DOWNLOAD_PATH_AUDIO_PREFERENCE)
                {
                    String downloadPath = sharedPreferences
                            .getString(DOWNLOAD_PATH_AUDIO_PREFERENCE,
                                    getString(R.string.download_path_audio_summary));
                    downloadPathAudioPreference
                            .setSummary(downloadPath);
                }
                else if (key == THEME)
                {
                    String selectedTheme = sharedPreferences.getString(THEME, "Light");
                    themePreference.setSummary(selectedTheme);

                    if(!selectedTheme.equals(currentTheme)) { // If it's not the current theme
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.restart_title)
                                .setMessage(R.string.msg_restart)
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intentToMain = new Intent(activity, MainActivity.class);
                                        intentToMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        activity.startActivity(intentToMain);

                                        activity.finish();
                                        Runtime.getRuntime().exit(0);
                                    }
                                })
                                .setNegativeButton(R.string.later, null)
                                .create().show();
                    }
                }
                updateSummary();
            }
        };
        defaultPreferences.registerOnSharedPreferenceChangeListener(prefListener);

        updateSummary();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

        if(preference.getKey().equals(downloadPathPreference.getKey()) ||
                preference.getKey().equals(downloadPathAudioPreference.getKey()))
        {
            Activity activity = getActivity();
            Intent i = new Intent(activity, FilePickerActivity.class);

            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
            if(preference.getKey().equals(downloadPathPreference.getKey()))
            {
                activity.startActivityForResult(i, R.string.download_path_key);
            }
            else if (preference.getKey().equals(downloadPathAudioPreference.getKey()))
            {
                activity.startActivityForResult(i, R.string.download_path_audio_key);
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Activity a = getActivity();

        if ((requestCode == R.string.download_path_audio_key
                || requestCode == R.string.download_path_key)
                && resultCode == Activity.RESULT_OK) {

            Uri uri = null;
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();

                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            uri = clip.getItemAt(i).getUri();
                        }
                    }
                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra
                            (FilePickerActivity.EXTRA_PATHS);

                    if (paths != null) {
                        for (String path: paths) {
                            uri = Uri.parse(path);
                        }
                    }
                }
            } else {
                uri = data.getData();
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(a);

            //requestCode is equal to  R.string.download_path_key or
            //R.string.download_path_audio_key
            String key = getString(requestCode);
            String path = data.getData().toString().substring(7);
            prefs.edit()
                    .putString(key, path)
                    .apply();

        }
        else if(requestCode == REQUEST_INSTALL_ORBOT)
        {
            // try to start tor regardless of resultCode since clicking back after
            // installing the app does not necessarily return RESULT_OK
            App.configureTor(requestCode == REQUEST_INSTALL_ORBOT
                    && OrbotHelper.requestStartTor(a));
        }

        updateSummary();
        super.onActivityResult(requestCode, resultCode, data);
    }

    // This is used to show the status of some preference in the description
    private void updateSummary() {
        defaultResolutionPreference.setSummary(
                defaultPreferences.getString(DEFAULT_RESOLUTION_PREFERENCE,
                        getString(R.string.default_resolution_value)));
        defaultPopupResolutionPreference.setSummary(
                defaultPreferences.getString(DEFAULT_POPUP_RESOLUTION_PREFERENCE,
                        getString(R.string.default_popup_resolution_value)));
        preferredVideoFormatPreference.setSummary(
                defaultPreferences.getString(PREFERRED_VIDEO_FORMAT_PREFERENCE,
                        getString(R.string.preferred_video_format_default)));
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
        themePreference.setSummary(
                defaultPreferences.getString(THEME,
                        getString(R.string.light_theme_title)));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }
}
