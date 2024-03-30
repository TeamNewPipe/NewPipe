package org.schabi.newpipe.settings

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.util.FilePickerActivityHelper
import org.schabi.newpipe.util.Localization
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URI

class DownloadSettingsFragment() : BasePreferenceFragment() {
    private var downloadPathVideoPreference: String? = null
    private var downloadPathAudioPreference: String? = null
    private var storageUseSafPreference: String? = null
    private var prefPathVideo: Preference? = null
    private var prefPathAudio: Preference? = null
    private var prefStorageAsk: Preference? = null
    private var ctx: Context? = null
    private val requestDownloadVideoPathLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(
            StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestDownloadVideoPathResult(result) }))
    private val requestDownloadAudioPathLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(
            StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestDownloadAudioPathResult(result) }))

    public override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        downloadPathVideoPreference = getString(R.string.download_path_video_key)
        downloadPathAudioPreference = getString(R.string.download_path_audio_key)
        storageUseSafPreference = getString(R.string.storage_use_saf)
        val downloadStorageAsk: String = getString(R.string.downloads_storage_ask)
        prefPathVideo = findPreference(downloadPathVideoPreference!!)
        prefPathAudio = findPreference(downloadPathAudioPreference!!)
        prefStorageAsk = findPreference(downloadStorageAsk)
        val prefUseSaf: SwitchPreferenceCompat? = findPreference(storageUseSafPreference!!)
        prefUseSaf!!.setChecked(NewPipeSettings.useStorageAccessFramework(ctx))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prefUseSaf.setEnabled(false)
            prefUseSaf.setSummary(R.string.downloads_storage_use_saf_summary_api_29)
            prefStorageAsk!!.setSummary(R.string.downloads_storage_ask_summary_no_saf_notice)
        }
        updatePreferencesSummary()
        updatePathPickers(!defaultPreferences!!.getBoolean(downloadStorageAsk, false))
        if ((hasInvalidPath(downloadPathVideoPreference!!)
                        || hasInvalidPath(downloadPathAudioPreference!!))) {
            updatePreferencesSummary()
        }
        prefStorageAsk!!.setOnPreferenceChangeListener(Preference.OnPreferenceChangeListener({ preference: Preference?, value: Any? ->
            updatePathPickers(!value as Boolean)
            true
        }))
    }

    public override fun onAttach(context: Context) {
        super.onAttach(context)
        ctx = context
    }

    public override fun onDetach() {
        super.onDetach()
        ctx = null
        prefStorageAsk!!.setOnPreferenceChangeListener(null)
    }

    private fun updatePreferencesSummary() {
        showPathInSummary(downloadPathVideoPreference, R.string.download_path_summary,
                prefPathVideo)
        showPathInSummary(downloadPathAudioPreference, R.string.download_path_audio_summary,
                prefPathAudio)
    }

    private fun showPathInSummary(prefKey: String?, @StringRes defaultString: Int,
                                  target: Preference?) {
        var rawUri: String? = defaultPreferences!!.getString(prefKey, null)
        if (rawUri == null || rawUri.isEmpty()) {
            target!!.setSummary(getString(defaultString))
            return
        }
        if (rawUri.get(0) == File.separatorChar) {
            target!!.setSummary(rawUri)
            return
        }
        if (rawUri.startsWith(ContentResolver.SCHEME_FILE)) {
            target!!.setSummary(File(URI.create(rawUri)).getPath())
            return
        }
        try {
            rawUri = Utils.decodeUrlUtf8(rawUri)
        } catch (e: UnsupportedEncodingException) {
            // nothing to do
        }
        target!!.setSummary(rawUri)
    }

    private fun isFileUri(path: String): Boolean {
        return path.get(0) == File.separatorChar || path.startsWith(ContentResolver.SCHEME_FILE)
    }

    private fun hasInvalidPath(prefKey: String): Boolean {
        val value: String? = defaultPreferences!!.getString(prefKey, null)
        return value == null || value.isEmpty()
    }

    private fun updatePathPickers(enabled: Boolean) {
        prefPathVideo!!.setEnabled(enabled)
        prefPathAudio!!.setEnabled(enabled)
    }

    // FIXME: after releasing the old path, all downloads created on the folder becomes inaccessible
    private fun forgetSAFTree(context: Context, oldPath: String?) {
        if (IGNORE_RELEASE_ON_OLD_PATH) {
            return
        }
        if ((oldPath == null) || oldPath.isEmpty() || isFileUri(oldPath)) {
            return
        }
        try {
            val uri: Uri = Uri.parse(oldPath)
            context.getContentResolver()
                    .releasePersistableUriPermission(uri, StoredDirectoryHelper.Companion.PERMISSION_FLAGS)
            context.revokeUriPermission(uri, StoredDirectoryHelper.Companion.PERMISSION_FLAGS)
            Log.i(TAG, "Revoke old path permissions success on " + oldPath)
        } catch (err: Exception) {
            Log.e(TAG, "Error revoking old path permissions on " + oldPath, err)
        }
    }

    private fun showMessageDialog(@StringRes title: Int, @StringRes message: Int) {
        AlertDialog.Builder((ctx)!!)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show()
    }

    public override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (BasePreferenceFragment.Companion.DEBUG) {
            Log.d(TAG, ("onPreferenceTreeClick() called with: "
                    + "preference = [" + preference + "]"))
        }
        val key: String = preference.getKey()
        if ((key == storageUseSafPreference)) {
            if (!NewPipeSettings.useStorageAccessFramework(ctx)) {
                NewPipeSettings.saveDefaultVideoDownloadDirectory(ctx)
                NewPipeSettings.saveDefaultAudioDownloadDirectory(ctx)
            } else {
                defaultPreferences!!.edit().putString(downloadPathVideoPreference, null)
                        .putString(downloadPathAudioPreference, null).apply()
            }
            updatePreferencesSummary()
            return true
        } else if ((key == downloadPathVideoPreference)) {
            launchDirectoryPicker(requestDownloadVideoPathLauncher)
        } else if ((key == downloadPathAudioPreference)) {
            launchDirectoryPicker(requestDownloadAudioPathLauncher)
        } else {
            return super.onPreferenceTreeClick(preference)
        }
        return true
    }

    private fun launchDirectoryPicker(launcher: ActivityResultLauncher<Intent>) {
        NoFileManagerSafeGuard.launchSafe<Intent>(
                launcher,
                StoredDirectoryHelper.Companion.getPicker(ctx),
                TAG,
                ctx
        )
    }

    private fun requestDownloadVideoPathResult(result: ActivityResult) {
        requestDownloadPathResult(result, downloadPathVideoPreference)
    }

    private fun requestDownloadAudioPathResult(result: ActivityResult) {
        requestDownloadPathResult(result, downloadPathAudioPreference)
    }

    private fun requestDownloadPathResult(result: ActivityResult, key: String?) {
        Localization.assureCorrectAppLanguage(getContext())
        if (result.getResultCode() != Activity.RESULT_OK) {
            return
        }
        var uri: Uri? = null
        if (result.getData() != null) {
            uri = result.getData()!!.getData()
        }
        if (uri == null) {
            showMessageDialog(R.string.general_error, R.string.invalid_directory)
            return
        }


        // revoke permissions on the old save path (required for SAF only)
        val context: Context = requireContext()
        forgetSAFTree(context, defaultPreferences!!.getString(key, ""))
        if (!FilePickerActivityHelper.Companion.isOwnFileUri(context, uri)) {
            // steps to acquire the selected path:
            //     1. acquire permissions on the new save path
            //     2. save the new path, if step(2) was successful
            try {
                context.grantUriPermission(context.getPackageName(), uri,
                        StoredDirectoryHelper.Companion.PERMISSION_FLAGS)
                val mainStorage: StoredDirectoryHelper = StoredDirectoryHelper(context, uri, null)
                Log.i(TAG, "Acquiring tree success from " + uri.toString())
                if (!mainStorage.canWrite()) {
                    throw IOException("No write permissions on " + uri.toString())
                }
            } catch (err: IOException) {
                Log.e(TAG, "Error acquiring tree from " + uri.toString(), err)
                showMessageDialog(R.string.general_error, R.string.no_available_dir)
                return
            }
        } else {
            val target: File = com.nononsenseapps.filepicker.Utils.getFileForUri(uri)
            if (!target.canWrite()) {
                showMessageDialog(R.string.download_to_sdcard_error_title,
                        R.string.download_to_sdcard_error_message)
                return
            }
            uri = Uri.fromFile(target)
        }
        defaultPreferences!!.edit().putString(key, uri.toString()).apply()
        updatePreferencesSummary()
    }

    companion object {
        val IGNORE_RELEASE_ON_OLD_PATH: Boolean = true
    }
}
