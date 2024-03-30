package org.schabi.newpipe.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ZipHelper
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

class BackupRestoreSettingsFragment() : BasePreferenceFragment() {
    private val exportDateFormat: SimpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var manager: ContentSettingsManager? = null
    private var importExportDataPathKey: String? = null
    private val requestImportPathLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestImportPathResult(result) }))
    private val requestExportPathLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestExportPathResult(result) }))
    public override fun onCreatePreferences(savedInstanceState: Bundle?,
                                            rootKey: String?) {
        val homeDir: File? = ContextCompat.getDataDir(requireContext())
        Objects.requireNonNull(homeDir)
        manager = ContentSettingsManager(NewPipeFileLocator((homeDir)!!))
        manager!!.deleteSettingsFile()
        importExportDataPathKey = getString(R.string.import_export_data_path)
        addPreferencesFromResourceRegistry()
        val importDataPreference: Preference = requirePreference(R.string.import_data)
        importDataPreference.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ p: Preference? ->
            NoFileManagerSafeGuard.launchSafe<Intent>(
                    requestImportPathLauncher,
                    StoredFileHelper.Companion.getPicker(requireContext(),
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    TAG,
                    getContext()
            )
            true
        }))
        val exportDataPreference: Preference = requirePreference(R.string.export_data)
        exportDataPreference.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ p: Preference? ->
            NoFileManagerSafeGuard.launchSafe<Intent>(
                    requestExportPathLauncher,
                    StoredFileHelper.Companion.getNewPicker(requireContext(),
                            "NewPipeData-" + exportDateFormat.format(Date()) + ".zip",
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    TAG,
                    getContext()
            )
            true
        }))
        val resetSettings: Preference? = findPreference(getString(R.string.reset_settings))
        assert(resetSettings != null)
        resetSettings!!.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ preference: Preference? ->
            // Show Alert Dialogue
            val builder: AlertDialog.Builder = AlertDialog.Builder(getContext())
            builder.setMessage(R.string.reset_all_settings)
            builder.setCancelable(true)
            builder.setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int ->
                // Deletes all shared preferences xml files.
                val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                sharedPreferences.edit().clear().apply()
                // Restarts the app
                if (getActivity() == null) {
                    return@setPositiveButton
                }
                NavigationHelper.restartApp(getActivity())
            }))
            builder.setNegativeButton(R.string.cancel, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int -> }))
            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
            true
        }))
    }

    private fun requestExportPathResult(result: ActivityResult) {
        Localization.assureCorrectAppLanguage(requireContext())
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            // will be saved only on success
            val lastExportDataUri: Uri? = result.getData()!!.getData()
            val file: StoredFileHelper = StoredFileHelper(
                    requireContext(), result.getData()!!.getData(), ZIP_MIME_TYPE)
            exportDatabase(file, lastExportDataUri)
        }
    }

    private fun requestImportPathResult(result: ActivityResult) {
        Localization.assureCorrectAppLanguage(requireContext())
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            // will be saved only on success
            val lastImportDataUri: Uri? = result.getData()!!.getData()
            val file: StoredFileHelper = StoredFileHelper(
                    requireContext(), result.getData()!!.getData(), ZIP_MIME_TYPE)
            androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                    .setMessage(R.string.override_current_data)
                    .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ d: DialogInterface?, id: Int -> importDatabase(file, lastImportDataUri) }))
                    .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener({ d: DialogInterface, id: Int -> d.cancel() }))
                    .show()
        }
    }

    private fun exportDatabase(file: StoredFileHelper, exportDataUri: Uri?) {
        try {
            //checkpoint before export
            NewPipeDatabase.checkpoint()
            val preferences: SharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
            manager!!.exportDatabase(preferences, file)
            saveLastImportExportDataUri(exportDataUri) // save export path only on success
            Toast.makeText(requireContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT)
                    .show()
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Exporting database", e)
        }
    }

    private fun importDatabase(file: StoredFileHelper, importDataUri: Uri?) {
        // check if file is supported
        if (!ZipHelper.isValidZipFile(file)) {
            Toast.makeText(requireContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                    .show()
            return
        }
        try {
            if (!manager!!.ensureDbDirectoryExists()) {
                throw IOException("Could not create databases dir")
            }
            if (!manager!!.extractDb(file)) {
                Toast.makeText(requireContext(), R.string.could_not_import_all_files,
                        Toast.LENGTH_LONG)
                        .show()
            }

            // if settings file exist, ask if it should be imported.
            if (manager!!.extractSettings(file)) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.import_settings)
                        .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener({ dialog: DialogInterface, which: Int ->
                            dialog.dismiss()
                            finishImport(importDataUri)
                        }))
                        .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialog: DialogInterface, which: Int ->
                            dialog.dismiss()
                            val context: Context = requireContext()
                            val prefs: SharedPreferences = PreferenceManager
                                    .getDefaultSharedPreferences(context)
                            manager!!.loadSharedPreferences(prefs)
                            cleanImport(context, prefs)
                            finishImport(importDataUri)
                        }))
                        .show()
            } else {
                finishImport(importDataUri)
            }
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Importing database", e)
        }
    }

    /**
     * Remove settings that are not supposed to be imported on different devices
     * and reset them to default values.
     * @param context the context used for the import
     * @param prefs the preferences used while running the import
     */
    private fun cleanImport(context: Context,
                            prefs: SharedPreferences) {
        // Check if media tunnelling needs to be disabled automatically,
        // if it was disabled automatically in the imported preferences.
        val tunnelingKey: String = context.getString(R.string.disable_media_tunneling_key)
        val automaticTunnelingKey: String = context.getString(R.string.disabled_media_tunneling_automatically_key)
        // R.string.disable_media_tunneling_key should always be true
        // if R.string.disabled_media_tunneling_automatically_key equals 1,
        // but we double check here just to be sure and to avoid regressions
        // caused by possible later modification of the media tunneling functionality.
        // R.string.disabled_media_tunneling_automatically_key == 0:
        //     automatic value overridden by user in settings
        // R.string.disabled_media_tunneling_automatically_key == -1: not set
        val wasMediaTunnelingDisabledAutomatically: Boolean = (prefs.getInt(automaticTunnelingKey, -1) == 1
                && prefs.getBoolean(tunnelingKey, false))
        if (wasMediaTunnelingDisabledAutomatically) {
            prefs.edit()
                    .putInt(automaticTunnelingKey, -1)
                    .putBoolean(tunnelingKey, false)
                    .apply()
            NewPipeSettings.setMediaTunneling(context)
        }
    }

    /**
     * Save import path and restart system.
     *
     * @param importDataUri The import path to save
     */
    private fun finishImport(importDataUri: Uri?) {
        // save import path only on success
        saveLastImportExportDataUri(importDataUri)
        // restart app to properly load db
        NavigationHelper.restartApp(requireActivity())
    }

    private fun getImportExportDataUri(): Uri? {
        val path: String? = defaultPreferences!!.getString(importExportDataPathKey, null)
        return if (Utils.isBlank(path)) null else Uri.parse(path)
    }

    private fun saveLastImportExportDataUri(importExportDataUri: Uri?) {
        val editor: SharedPreferences.Editor = defaultPreferences!!.edit()
                .putString(importExportDataPathKey, importExportDataUri.toString())
        editor.apply()
    }

    companion object {
        private val ZIP_MIME_TYPE: String = "application/zip"
    }
}
