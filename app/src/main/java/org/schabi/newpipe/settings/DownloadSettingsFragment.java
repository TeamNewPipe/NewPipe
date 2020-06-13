package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

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

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class DownloadSettingsFragment extends BasePreferenceFragment {
    public static final boolean IGNORE_RELEASE_ON_OLD_PATH = true;
    private static final int REQUEST_DOWNLOAD_VIDEO_PATH = 0x1235;
    private static final int REQUEST_DOWNLOAD_AUDIO_PATH = 0x1236;
    private String downloadPathVideoPreference;
    private String downloadPathAudioPreference;
    private String storageUseSafPreference;

    private Preference prefPathVideo;
    private Preference prefPathAudio;
    private Preference prefStorageAsk;

    private Context ctx;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        downloadPathVideoPreference = getString(R.string.download_path_video_key);
        downloadPathAudioPreference = getString(R.string.download_path_audio_key);
        storageUseSafPreference = getString(R.string.storage_use_saf);
        final String downloadStorageAsk = getString(R.string.downloads_storage_ask);

        prefPathVideo = findPreference(downloadPathVideoPreference);
        prefPathAudio = findPreference(downloadPathAudioPreference);
        prefStorageAsk = findPreference(downloadStorageAsk);

        final SwitchPreference prefUseSaf = findPreference(storageUseSafPreference);
        prefUseSaf.setDefaultValue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        prefUseSaf.setChecked(NewPipeSettings.useStorageAccessFramework(ctx));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            prefUseSaf.setEnabled(false);
        }

        updatePreferencesSummary();
        updatePathPickers(!defaultPreferences.getBoolean(downloadStorageAsk, false));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            prefStorageAsk.setSummary(R.string.downloads_storage_ask_summary);
        }

        if (hasInvalidPath(downloadPathVideoPreference)
                || hasInvalidPath(downloadPathAudioPreference)) {
            updatePreferencesSummary();
        }

        prefStorageAsk.setOnPreferenceChangeListener((preference, value) -> {
            updatePathPickers(!(boolean) value);
            return true;
        });
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);
    }

    @Override
    public void onAttach(final Context context) {
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
        showPathInSummary(downloadPathVideoPreference, R.string.download_path_summary,
                prefPathVideo);
        showPathInSummary(downloadPathAudioPreference, R.string.download_path_audio_summary,
                prefPathAudio);
    }

    private void showPathInSummary(final String prefKey, @StringRes final int defaultString,
                                   final Preference target) {
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

    private boolean isFileUri(final String path) {
        return path.charAt(0) == File.separatorChar || path.startsWith(ContentResolver.SCHEME_FILE);
    }

    private boolean hasInvalidPath(final String prefKey) {
        String value = defaultPreferences.getString(prefKey, null);
        return value == null || value.isEmpty();
    }

    private void updatePathPickers(final boolean enabled) {
        prefPathVideo.setEnabled(enabled);
        prefPathAudio.setEnabled(enabled);
    }

    // FIXME: after releasing the old path, all downloads created on the folder becomes inaccessible
    private void forgetSAFTree(final Context context, final String oldPath) {
        if (IGNORE_RELEASE_ON_OLD_PATH) {
            return;
        }

        if (oldPath == null || oldPath.isEmpty() || isFileUri(oldPath)) {
            return;
        }

        try {
            Uri uri = Uri.parse(oldPath);

            context.getContentResolver()
                    .releasePersistableUriPermission(uri, StoredDirectoryHelper.PERMISSION_FLAGS);
            context.revokeUriPermission(uri, StoredDirectoryHelper.PERMISSION_FLAGS);

            Log.i(TAG, "Revoke old path permissions success on " + oldPath);
        } catch (Exception err) {
            Log.e(TAG, "Error revoking old path permissions on " + oldPath, err);
        }
    }

    private void showMessageDialog(@StringRes final int title, @StringRes final int message) {
        AlertDialog.Builder msg = new AlertDialog.Builder(ctx);
        msg.setTitle(title);
        msg.setMessage(message);
        msg.setPositiveButton(getString(R.string.finish), null);
        msg.show();
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (DEBUG) {
            Log.d(TAG, "onPreferenceTreeClick() called with: "
                    + "preference = [" + preference + "]");
        }

        final String key = preference.getKey();
        final int request;

        if (key.equals(storageUseSafPreference)) {
            if (!NewPipeSettings.useStorageAccessFramework(ctx)) {
                NewPipeSettings.saveDefaultVideoDownloadDirectory(ctx);
                NewPipeSettings.saveDefaultAudioDownloadDirectory(ctx);
            } else {
                defaultPreferences.edit().putString(downloadPathVideoPreference, null)
                        .putString(downloadPathAudioPreference, null).apply();
            }
            updatePreferencesSummary();
            return true;
        } else if (key.equals(downloadPathVideoPreference)) {
            request = REQUEST_DOWNLOAD_VIDEO_PATH;
        } else if (key.equals(downloadPathAudioPreference)) {
            request = REQUEST_DOWNLOAD_AUDIO_PATH;
        } else {
            return super.onPreferenceTreeClick(preference);
        }

        startActivityForResult(StoredDirectoryHelper.getPicker(ctx), request);

        return true;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        assureCorrectAppLanguage(getContext());
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: "
                    + "requestCode = [" + requestCode + "], "
                    + "resultCode = [" + resultCode + "], data = [" + data + "]"
            );
        }

        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        String key;
        if (requestCode == REQUEST_DOWNLOAD_VIDEO_PATH) {
            key = downloadPathVideoPreference;
        } else if (requestCode == REQUEST_DOWNLOAD_AUDIO_PATH) {
            key = downloadPathAudioPreference;
        } else {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            showMessageDialog(R.string.general_error, R.string.invalid_directory);
            return;
        }


        // revoke permissions on the old save path (required for SAF only)
        final Context context = getContext();
        if (context == null) {
            throw new NullPointerException("getContext()");
        }

        forgetSAFTree(context, defaultPreferences.getString(key, ""));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && !FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            // steps to acquire the selected path:
            //     1. acquire permissions on the new save path
            //     2. save the new path, if step(2) was successful
            try {
                context.grantUriPermission(context.getPackageName(), uri,
                        StoredDirectoryHelper.PERMISSION_FLAGS);

                StoredDirectoryHelper mainStorage = new StoredDirectoryHelper(context, uri, null);
                Log.i(TAG, "Acquiring tree success from " + uri.toString());

                if (!mainStorage.canWrite()) {
                    throw new IOException("No write permissions on " + uri.toString());
                }
            } catch (IOException err) {
                Log.e(TAG, "Error acquiring tree from " + uri.toString(), err);
                showMessageDialog(R.string.general_error, R.string.no_available_dir);
                return;
            }
        } else {
            File target = Utils.getFileForUri(uri);
            if (!target.canWrite()) {
                showMessageDialog(R.string.download_to_sdcard_error_title,
                        R.string.download_to_sdcard_error_message);
                return;
            }
            uri = Uri.fromFile(target);
        }

        defaultPreferences.edit().putString(key, uri.toString()).apply();
        updatePreferencesSummary();
    }
}
