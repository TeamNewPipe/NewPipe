package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.FilePickerActivityHelper;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import us.shandian.giga.io.StoredDirectoryHelper;

public class DownloadSettingsFragment extends BasePreferenceFragment {
    private static final int REQUEST_DOWNLOAD_VIDEO_PATH = 0x1235;
    private static final int REQUEST_DOWNLOAD_AUDIO_PATH = 0x1236;
    public static final boolean IGNORE_RELEASE_ON_OLD_PATH = true;

    private String DOWNLOAD_PATH_VIDEO_PREFERENCE;
    private String DOWNLOAD_PATH_AUDIO_PREFERENCE;

    private String DOWNLOAD_STORAGE_ASK;

    private Preference prefPathVideo;
    private Preference prefPathAudio;
    private Preference prefStorageAsk;

    private Context ctx;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DOWNLOAD_PATH_VIDEO_PREFERENCE = getString(R.string.download_path_video_key);
        DOWNLOAD_PATH_AUDIO_PREFERENCE = getString(R.string.download_path_audio_key);
        DOWNLOAD_STORAGE_ASK = getString(R.string.downloads_storage_ask);

        prefPathVideo = findPreference(DOWNLOAD_PATH_VIDEO_PREFERENCE);
        prefPathAudio = findPreference(DOWNLOAD_PATH_AUDIO_PREFERENCE);
        prefStorageAsk = findPreference(DOWNLOAD_STORAGE_ASK);

        updatePreferencesSummary();
        updatePathPickers(!defaultPreferences.getBoolean(DOWNLOAD_STORAGE_ASK, false));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            prefStorageAsk.setSummary(R.string.downloads_storage_ask_summary);
        }

        if (hasInvalidPath(DOWNLOAD_PATH_VIDEO_PREFERENCE) || hasInvalidPath(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
            Toast.makeText(ctx, R.string.download_pick_path, Toast.LENGTH_SHORT).show();
            updatePreferencesSummary();
        }

        prefStorageAsk.setOnPreferenceChangeListener((preference, value) -> {
            updatePathPickers(!(boolean) value);
            return true;
        });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);
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
        prefStorageAsk.setOnPreferenceChangeListener(null);
    }

    private void updatePreferencesSummary() {
        showPathInSummary(DOWNLOAD_PATH_VIDEO_PREFERENCE, R.string.download_path_summary, prefPathVideo);
        showPathInSummary(DOWNLOAD_PATH_AUDIO_PREFERENCE, R.string.download_path_audio_summary, prefPathAudio);
    }

    private void showPathInSummary(String prefKey, @StringRes int defaultString, Preference target) {
        String rawUri = defaultPreferences.getString(prefKey, null);
        if (rawUri == null || rawUri.isEmpty()) {
            target.setSummary(getString(defaultString));
            return;
        }

        if (rawUri.charAt(0) == File.separatorChar) {
            target.setSummary(rawUri);
            return;
        }
        if (rawUri.startsWith(ContentResolver.SCHEME_FILE)) {
            target.setSummary(new File(URI.create(rawUri)).getPath());
            return;
        }

        try {
            rawUri = URLDecoder.decode(rawUri, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // nothing to do
        }

        target.setSummary(rawUri);
    }

    private boolean isFileUri(String path) {
        return path.charAt(0) == File.separatorChar || path.startsWith(ContentResolver.SCHEME_FILE);
    }

    private boolean hasInvalidPath(String prefKey) {
        String value = defaultPreferences.getString(prefKey, null);
        return value == null || value.isEmpty();
    }

    private void updatePathPickers(boolean enabled) {
        prefPathVideo.setEnabled(enabled);
        prefPathAudio.setEnabled(enabled);
    }

    // FIXME: after releasing the old path, all downloads created on the folder becomes inaccessible
    private void forgetSAFTree(Context ctx, String oldPath) {
        if (IGNORE_RELEASE_ON_OLD_PATH) {
            return;
        }

        if (oldPath == null || oldPath.isEmpty() || isFileUri(oldPath)) return;

        try {
            Uri uri = Uri.parse(oldPath);

            ctx.getContentResolver().releasePersistableUriPermission(uri, StoredDirectoryHelper.PERMISSION_FLAGS);
            ctx.revokeUriPermission(uri, StoredDirectoryHelper.PERMISSION_FLAGS);

            Log.i(TAG, "Revoke old path permissions success on " + oldPath);
        } catch (Exception err) {
            Log.e(TAG, "Error revoking old path permissions on " + oldPath, err);
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
        int request;

        if (key.equals(DOWNLOAD_PATH_VIDEO_PREFERENCE)) {
            request = REQUEST_DOWNLOAD_VIDEO_PATH;
        } else if (key.equals(DOWNLOAD_PATH_AUDIO_PREFERENCE)) {
            request = REQUEST_DOWNLOAD_AUDIO_PATH;
        } else {
            return super.onPreferenceTreeClick(preference);
        }

        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .putExtra("android.content.extra.SHOW_ADVANCED", true)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | StoredDirectoryHelper.PERMISSION_FLAGS);
        } else {
            i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_DIR);
        }

        startActivityForResult(i, request);

        return true;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // steps:
            //       1. revoke permissions on the old save path
            //       2. acquire permissions on the new save path
            //       3. save the new path, if step(2) was successful
            final Context ctx = getContext();
            if (ctx == null) throw new NullPointerException("getContext()");

            forgetSAFTree(ctx, defaultPreferences.getString(key, ""));

            try {
                ctx.grantUriPermission(ctx.getPackageName(), uri, StoredDirectoryHelper.PERMISSION_FLAGS);

                StoredDirectoryHelper mainStorage = new StoredDirectoryHelper(ctx, uri, null);
                Log.i(TAG, "Acquiring tree success from " + uri.toString());

                if (!mainStorage.canWrite())
                    throw new IOException("No write permissions on " + uri.toString());
            } catch (IOException err) {
                Log.e(TAG, "Error acquiring tree from " + uri.toString(), err);
                showMessageDialog(R.string.general_error, R.string.no_available_dir);
                return;
            }
        } else {
            File target = Utils.getFileForUri(data.getData());
            if (!target.canWrite()) {
                showMessageDialog(R.string.download_to_sdcard_error_title, R.string.download_to_sdcard_error_message);
                return;
            }
            uri = Uri.fromFile(target);
        }

        defaultPreferences.edit().putString(key, uri.toString()).apply();
        updatePreferencesSummary();
    }
}
