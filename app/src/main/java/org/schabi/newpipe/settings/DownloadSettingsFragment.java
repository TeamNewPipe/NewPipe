package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.FilePickerActivityHelper;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import us.shandian.giga.io.StoredDirectoryHelper;

public class DownloadSettingsFragment extends BasePreferenceFragment {
    private static final int REQUEST_DOWNLOAD_VIDEO_PATH = 0x1235;
    private static final int REQUEST_DOWNLOAD_AUDIO_PATH = 0x1236;

    private String DOWNLOAD_PATH_VIDEO_PREFERENCE;
    private String DOWNLOAD_PATH_AUDIO_PREFERENCE;

    private String DOWNLOAD_STORAGE_API;
    private String DOWNLOAD_STORAGE_API_DEFAULT;

    private Preference prefPathVideo;
    private Preference prefPathAudio;
    
    private Context ctx;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initKeys();
        updatePreferencesSummary();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);

        prefPathVideo = findPreference(DOWNLOAD_PATH_VIDEO_PREFERENCE);
        prefPathAudio = findPreference(DOWNLOAD_PATH_AUDIO_PREFERENCE);

        updatePathPickers(usingJavaIO());

        findPreference(DOWNLOAD_STORAGE_API).setOnPreferenceChangeListener((preference, value) -> {
            boolean javaIO = DOWNLOAD_STORAGE_API_DEFAULT.equals(value);

            if (!javaIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Toast.makeText(ctx, R.string.download_pick_path, Toast.LENGTH_LONG).show();

                // forget save paths
                forgetSAFTree(DOWNLOAD_PATH_VIDEO_PREFERENCE);
                forgetSAFTree(DOWNLOAD_PATH_AUDIO_PREFERENCE);

                defaultPreferences.edit()
                        .putString(DOWNLOAD_PATH_VIDEO_PREFERENCE, "")
                        .putString(DOWNLOAD_PATH_AUDIO_PREFERENCE, "")
                        .apply();

                updatePreferencesSummary();
            }

            updatePathPickers(javaIO);
            return true;
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ctx = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ctx = null;
        findPreference(DOWNLOAD_STORAGE_API).setOnPreferenceChangeListener(null);
    }

    private void initKeys() {
        DOWNLOAD_PATH_VIDEO_PREFERENCE = getString(R.string.download_path_video_key);
        DOWNLOAD_PATH_AUDIO_PREFERENCE = getString(R.string.download_path_audio_key);
        DOWNLOAD_STORAGE_API = getString(R.string.downloads_storage_api);
        DOWNLOAD_STORAGE_API_DEFAULT = getString(R.string.downloads_storage_api_default);
    }

    private void updatePreferencesSummary() {
        prefPathVideo.setSummary(
                defaultPreferences.getString(DOWNLOAD_PATH_VIDEO_PREFERENCE, getString(R.string.download_path_summary))
        );
        prefPathAudio.setSummary(
                defaultPreferences.getString(DOWNLOAD_PATH_AUDIO_PREFERENCE, getString(R.string.download_path_audio_summary))
        );
    }

    private void updatePathPickers(boolean useJavaIO) {
        boolean enabled = useJavaIO || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        prefPathVideo.setEnabled(enabled);
        prefPathAudio.setEnabled(enabled);
    }

    private boolean usingJavaIO() {
        return DOWNLOAD_STORAGE_API_DEFAULT.equals(
                defaultPreferences.getString(DOWNLOAD_STORAGE_API, DOWNLOAD_STORAGE_API_DEFAULT)
        );
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private void forgetSAFTree(String prefKey) {

        String oldPath = defaultPreferences.getString(prefKey, "");

        if (oldPath != null && !oldPath.isEmpty() && oldPath.charAt(0) != File.separatorChar) {
            try {
                StoredDirectoryHelper mainStorage = new StoredDirectoryHelper(ctx, Uri.parse(oldPath), null);
                if (!mainStorage.isDirect()) {
                    mainStorage.revokePermissions();
                    Log.i(TAG, "revokePermissions()  [uri=" + oldPath + "]  ¡success!");
                }
            } catch (IOException err) {
                Log.e(TAG, "Error revoking Tree uri permissions  [uri=" + oldPath + "]", err);
            }
        }
    }

    private void showMessageDialog(@StringRes int title, @StringRes int message) {
        AlertDialog.Builder msg = new AlertDialog.Builder(ctx);
        msg.setTitle(title);
        msg.setMessage(message);
        msg.setPositiveButton(android.R.string.ok, null);
        msg.show();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (DEBUG) {
            Log.d(TAG, "onPreferenceTreeClick() called with: preference = [" + preference + "]");
        }

        String key = preference.getKey();

        if (key.equals(DOWNLOAD_PATH_VIDEO_PREFERENCE) || key.equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
            boolean safPick = !usingJavaIO();

            int request = 0;
            if (key.equals(DOWNLOAD_PATH_VIDEO_PREFERENCE)) {
                request = REQUEST_DOWNLOAD_VIDEO_PATH;
            } else if (key.equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
                request = REQUEST_DOWNLOAD_AUDIO_PATH;
            }

            Intent i;
            if (safPick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .putExtra("android.content.extra.SHOW_ADVANCED", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | StoredDirectoryHelper.PERMISSION_FLAGS);
            } else {
                i = new Intent(getActivity(), FilePickerActivityHelper.class)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                        .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_DIR);
            }

            startActivityForResult(i, request);
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: requestCode = [" + requestCode + "], " +
                    "resultCode = [" + resultCode + "], data = [" + data + "]"
            );
        }

        if (resultCode != Activity.RESULT_OK) return;

        String key;
        if (requestCode == REQUEST_DOWNLOAD_VIDEO_PATH)
            key = DOWNLOAD_PATH_VIDEO_PREFERENCE;
        else if (requestCode == REQUEST_DOWNLOAD_AUDIO_PATH)
            key = DOWNLOAD_PATH_AUDIO_PREFERENCE;
        else
            return;

        Uri uri = data.getData();
        if (uri == null) {
            showMessageDialog(R.string.general_error, R.string.invalid_directory);
            return;
        }

        if (!usingJavaIO() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // steps:
            //       1. acquire permissions on the new save path
            //       2. save the new path, if step(1) was successful

            try {
                StoredDirectoryHelper mainStorage = new StoredDirectoryHelper(ctx, uri, null);
                mainStorage.acquirePermissions();
                Log.i(TAG, "acquirePermissions()  [uri=" + uri.toString() + "]  ¡success!");
            } catch (IOException err) {
                Log.e(TAG, "Error acquiring permissions on " + uri.toString());
                showMessageDialog(R.string.general_error, R.string.no_available_dir);
                return;
            }

            defaultPreferences.edit().putString(key, uri.toString()).apply();
        } else {
            defaultPreferences.edit().putString(key, uri.toString()).apply();
            updatePreferencesSummary();

            File target = new File(URI.create(uri.toString()));
            if (!target.canWrite())
                showMessageDialog(R.string.download_to_sdcard_error_title, R.string.download_to_sdcard_error_message);
        }
    }
}
