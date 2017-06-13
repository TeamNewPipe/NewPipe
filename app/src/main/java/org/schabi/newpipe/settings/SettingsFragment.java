package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import com.nononsenseapps.filepicker.FilePickerActivity;

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;

import info.guardianproject.netcipher.proxy.OrbotHelper;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_INSTALL_ORBOT = 0x1234;
    private static final int REQUEST_DOWNLOAD_PATH = 0x1235;
    private static final int REQUEST_DOWNLOAD_AUDIO_PATH = 0x1236;

    private String DOWNLOAD_PATH_PREFERENCE;
    private String DOWNLOAD_PATH_AUDIO_PREFERENCE;
    private String USE_TOR_KEY;
    private String THEME;

    private String currentTheme;
    private SharedPreferences defaultPreferences;

    private Activity activity;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        addPreferencesFromResource(R.xml.settings);

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        initKeys();
        updatePreferencesSummary();

        currentTheme = defaultPreferences.getString(THEME, getString(R.string.default_theme_value));
    }

    @Override
    public void onResume() {
        super.onResume();
        defaultPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void initKeys() {
        DOWNLOAD_PATH_PREFERENCE = getString(R.string.download_path_key);
        DOWNLOAD_PATH_AUDIO_PREFERENCE = getString(R.string.download_path_audio_key);
        THEME = getString(R.string.theme_key);
        USE_TOR_KEY = getString(R.string.use_tor_key);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (MainActivity.DEBUG) Log.d("TAG", "onPreferenceTreeClick() called with: preferenceScreen = [" + preferenceScreen + "], preference = [" + preference + "]");
        if (preference.getKey().equals(DOWNLOAD_PATH_PREFERENCE) || preference.getKey().equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
            Intent i = new Intent(activity, FilePickerActivity.class)
                    .putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
            if (preference.getKey().equals(DOWNLOAD_PATH_PREFERENCE)) {
                startActivityForResult(i, REQUEST_DOWNLOAD_PATH);
            } else if (preference.getKey().equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
                startActivityForResult(i, REQUEST_DOWNLOAD_AUDIO_PATH);
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (MainActivity.DEBUG) Log.d("TAG", "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");

        if ((requestCode == REQUEST_DOWNLOAD_PATH || requestCode == REQUEST_DOWNLOAD_AUDIO_PATH) && resultCode == Activity.RESULT_OK) {
            String key = getString(requestCode == REQUEST_DOWNLOAD_PATH ? R.string.download_path_key : R.string.download_path_audio_key);
            String path = data.getData().getPath();
            defaultPreferences.edit().putString(key, path).apply();
            updatePreferencesSummary();
        } else if (requestCode == REQUEST_INSTALL_ORBOT) {
            // try to start tor regardless of resultCode since clicking back after
            // installing the app does not necessarily return RESULT_OK
            App.configureTor(OrbotHelper.requestStartTor(activity));
        }
    }

    /*
     * Update ONLY the summary of some preferences that don't fire in the onSharedPreferenceChanged or CAN'T be update via xml (%s)
     *
     * For example, the download_path use the startActivityForResult, firing the onStop of this fragment,
     * unregistering the listener (unregisterOnSharedPreferenceChangeListener)
     */
    private void updatePreferencesSummary() {
        findPreference(DOWNLOAD_PATH_PREFERENCE).setSummary(defaultPreferences.getString(DOWNLOAD_PATH_PREFERENCE, getString(R.string.download_path_summary)));
        findPreference(DOWNLOAD_PATH_AUDIO_PREFERENCE).setSummary(defaultPreferences.getString(DOWNLOAD_PATH_AUDIO_PREFERENCE, getString(R.string.download_path_audio_summary)));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (MainActivity.DEBUG) Log.d("TAG", "onSharedPreferenceChanged() called with: sharedPreferences = [" + sharedPreferences + "], key = [" + key + "]");
        String summary = null;

        if (key.equals(USE_TOR_KEY)) {
            if (defaultPreferences.getBoolean(USE_TOR_KEY, false)) {
                if (OrbotHelper.isOrbotInstalled(activity)) {
                    App.configureTor(true);
                    OrbotHelper.requestStartTor(activity);
                } else {
                    Intent intent = OrbotHelper.getOrbotInstallIntent(activity);
                    startActivityForResult(intent, REQUEST_INSTALL_ORBOT);
                }
            } else App.configureTor(false);
            return;
        } else if (key.equals(THEME)) {
            summary = sharedPreferences.getString(THEME, getString(R.string.default_theme_value));
            if (!summary.equals(currentTheme)) { // If it's not the current theme
                getActivity().recreate();
            }

            defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        }

        if (!TextUtils.isEmpty(summary)) findPreference(key).setSummary(summary);
    }
}
