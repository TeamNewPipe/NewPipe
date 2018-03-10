package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.util.Log;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.FilePickerActivityHelper;

public class DownloadSettingsFragment extends BasePreferenceFragment {
    private static final int REQUEST_DOWNLOAD_PATH = 0x1235;
    private static final int REQUEST_DOWNLOAD_AUDIO_PATH = 0x1236;

    private String DOWNLOAD_PATH_PREFERENCE;
    private String DOWNLOAD_PATH_AUDIO_PREFERENCE;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initKeys();
        updatePreferencesSummary();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);
    }

    private void initKeys() {
        DOWNLOAD_PATH_PREFERENCE = getString(R.string.download_path_key);
        DOWNLOAD_PATH_AUDIO_PREFERENCE = getString(R.string.download_path_audio_key);
    }

    private void updatePreferencesSummary() {
        findPreference(DOWNLOAD_PATH_PREFERENCE).setSummary(defaultPreferences.getString(DOWNLOAD_PATH_PREFERENCE, getString(R.string.download_path_summary)));
        findPreference(DOWNLOAD_PATH_AUDIO_PREFERENCE).setSummary(defaultPreferences.getString(DOWNLOAD_PATH_AUDIO_PREFERENCE, getString(R.string.download_path_audio_summary)));
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (DEBUG) {
             Log.d(TAG, "onPreferenceTreeClick() called with: preference = [" + preference + "]");
        }

        if (preference.getKey().equals(DOWNLOAD_PATH_PREFERENCE)
                || preference.getKey().equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
            Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_DIR);
            if (preference.getKey().equals(DOWNLOAD_PATH_PREFERENCE)) {
                startActivityForResult(i, REQUEST_DOWNLOAD_PATH);
            } else if (preference.getKey().equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
                startActivityForResult(i, REQUEST_DOWNLOAD_AUDIO_PATH);
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
        }

        if ((requestCode == REQUEST_DOWNLOAD_PATH || requestCode == REQUEST_DOWNLOAD_AUDIO_PATH)
                && resultCode == Activity.RESULT_OK && data.getData() != null) {
            String key = getString(requestCode == REQUEST_DOWNLOAD_PATH ? R.string.download_path_key : R.string.download_path_audio_key);
            String path = Utils.getFileForUri(data.getData()).getAbsolutePath();

            defaultPreferences.edit().putString(key, path).apply();
            updatePreferencesSummary();
        }
    }
}
