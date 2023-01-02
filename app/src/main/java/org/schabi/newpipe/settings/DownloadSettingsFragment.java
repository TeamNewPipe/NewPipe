package org.schabi.newpipe.settings;

import static org.schabi.newpipe.extractor.utils.Utils.decodeUrlUtf8;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.R;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.util.FilePickerActivityHelper;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;

public class DownloadSettingsFragment extends BasePreferenceFragment {
    public static final boolean IGNORE_RELEASE_ON_OLD_PATH = true;
    private String downloadPathVideoPreference;
    private String downloadPathAudioPreference;
    private String storageUseSafPreference;

    private Preference prefPathVideo;
    private Preference prefPathAudio;
    private Preference prefStorageAsk;

    private Context ctx;
    private final ActivityResultLauncher<Intent> requestDownloadVideoPathLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadVideoPathResult);
    private final ActivityResultLauncher<Intent> requestDownloadAudioPathLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadAudioPathResult);

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        downloadPathVideoPreference = getString(R.string.download_path_video_key);
        downloadPathAudioPreference = getString(R.string.download_path_audio_key);
        storageUseSafPreference = getString(R.string.storage_use_saf);
        final String downloadStorageAsk = getString(R.string.downloads_storage_ask);

        prefPathVideo = findPreference(downloadPathVideoPreference);
        prefPathAudio = findPreference(downloadPathAudioPreference);
        prefStorageAsk = findPreference(downloadStorageAsk);

        final SwitchPreferenceCompat prefUseSaf = findPreference(storageUseSafPreference);
        prefUseSaf.setChecked(NewPipeSettings.useStorageAccessFramework(ctx));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prefUseSaf.setEnabled(false);
            prefUseSaf.setSummary(R.string.downloads_storage_use_saf_summary_api_29);
            prefStorageAsk.setSummary(R.string.downloads_storage_ask_summary_no_saf_notice);
        }

        updatePreferencesSummary();
        updatePathPickers(!defaultPreferences.getBoolean(downloadStorageAsk, false));

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
    public void onAttach(@NonNull final Context context) {
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
            rawUri = decodeUrlUtf8(rawUri);
        } catch (final UnsupportedEncodingException e) {
            // nothing to do
        }

        target.setSummary(rawUri);
    }

    private boolean isFileUri(final String path) {
        return path.charAt(0) == File.separatorChar || path.startsWith(ContentResolver.SCHEME_FILE);
    }

    private boolean hasInvalidPath(final String prefKey) {
        final String value = defaultPreferences.getString(prefKey, null);
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
            final Uri uri = Uri.parse(oldPath);

            context.getContentResolver()
                    .releasePersistableUriPermission(uri, StoredDirectoryHelper.PERMISSION_FLAGS);
            context.revokeUriPermission(uri, StoredDirectoryHelper.PERMISSION_FLAGS);

            Log.i(TAG, "Revoke old path permissions success on " + oldPath);
        } catch (final Exception err) {
            Log.e(TAG, "Error revoking old path permissions on " + oldPath, err);
        }
    }

    private void showMessageDialog(@StringRes final int title, @StringRes final int message) {
        final AlertDialog.Builder msg = new AlertDialog.Builder(ctx);
        msg.setTitle(title);
        msg.setMessage(message);
        msg.setPositiveButton(getString(R.string.ok), null);
        msg.show();
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull final Preference preference) {
        if (DEBUG) {
            Log.d(TAG, "onPreferenceTreeClick() called with: "
                    + "preference = [" + preference + "]");
        }

        final String key = preference.getKey();

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
            launchDirectoryPicker(requestDownloadVideoPathLauncher);
        } else if (key.equals(downloadPathAudioPreference)) {
            launchDirectoryPicker(requestDownloadAudioPathLauncher);
        } else {
            return super.onPreferenceTreeClick(preference);
        }

        return true;
    }

    private void launchDirectoryPicker(final ActivityResultLauncher<Intent> launcher) {
        NoFileManagerSafeGuard.launchSafe(
                launcher,
                StoredDirectoryHelper.getPicker(ctx),
                TAG,
                ctx
        );
    }

    private void requestDownloadVideoPathResult(final ActivityResult result) {
        requestDownloadPathResult(result, downloadPathVideoPreference);
    }

    private void requestDownloadAudioPathResult(final ActivityResult result) {
        requestDownloadPathResult(result, downloadPathAudioPreference);
    }

    private void requestDownloadPathResult(final ActivityResult result, final String key) {
        assureCorrectAppLanguage(getContext());

        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }

        Uri uri = null;
        if (result.getData() != null) {
            uri = result.getData().getData();
        }
        if (uri == null) {
            showMessageDialog(R.string.general_error, R.string.invalid_directory);
            return;
        }


        // revoke permissions on the old save path (required for SAF only)
        final Context context = requireContext();

        forgetSAFTree(context, defaultPreferences.getString(key, ""));

        if (!FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            // steps to acquire the selected path:
            //     1. acquire permissions on the new save path
            //     2. save the new path, if step(2) was successful
            try {
                context.grantUriPermission(context.getPackageName(), uri,
                        StoredDirectoryHelper.PERMISSION_FLAGS);

                final StoredDirectoryHelper mainStorage =
                        new StoredDirectoryHelper(context, uri, null);
                Log.i(TAG, "Acquiring tree success from " + uri.toString());

                if (!mainStorage.canWrite()) {
                    throw new IOException("No write permissions on " + uri.toString());
                }
            } catch (final IOException err) {
                Log.e(TAG, "Error acquiring tree from " + uri.toString(), err);
                showMessageDialog(R.string.general_error, R.string.no_available_dir);
                return;
            }
        } else {
            final File target = Utils.getFileForUri(uri);
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
