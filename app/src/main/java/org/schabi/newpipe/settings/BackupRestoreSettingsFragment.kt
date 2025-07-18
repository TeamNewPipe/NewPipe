package org.schabi.newpipe.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.grack.nanojson.JsonParserException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ZipHelper
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class BackupRestoreSettingsFragment : BasePreferenceFragment() {
    private val exportDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
    private lateinit var manager: ImportExportManager
    private val requestImportPathLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestImportPathResult)
    private val requestExportPathLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestExportPathResult)

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val activity = requireActivity()
        val dbDir = activity.getDatabasePath(BackupFileLocator.FILE_NAME_DB).toPath().parent
        manager = ImportExportManager(BackupFileLocator(dbDir))

        addPreferencesFromResourceRegistry()

        requirePreference(R.string.import_data).setOnPreferenceClickListener {
            val picker = StoredFileHelper.getPicker(activity, ZIP_MIME_TYPE, importExportDataUri)
            NoFileManagerSafeGuard.launchSafe(requestImportPathLauncher, picker, TAG, activity)
            true
        }

        requirePreference(R.string.export_data).setOnPreferenceClickListener {
            val filename = "NewPipeData-${exportDateFormat.format(LocalDateTime.now())}.zip"
            val picker = StoredFileHelper.getNewPicker(activity, filename, ZIP_MIME_TYPE, importExportDataUri)
            NoFileManagerSafeGuard.launchSafe(requestExportPathLauncher, picker, TAG, activity)
            true
        }

        requirePreference(R.string.reset_settings).setOnPreferenceClickListener {
            // Show Alert Dialogue
            AlertDialog
                .Builder(activity)
                .setMessage(R.string.reset_all_settings)
                .setCancelable(true)
                .setPositiveButton(R.string.ok) { _, _ ->
                    // Deletes all shared preferences xml files.
                    defaultPreferences.edit { clear() }
                    NavigationHelper.restartApp(activity)
                }.setNegativeButton(R.string.cancel) { _, _ -> }
                .show()
            true
        }
    }

    private fun requestExportPathResult(result: ActivityResult) {
        val context = requireContext()
        val lastExportDataUri = result.data?.data

        Localization.assureCorrectAppLanguage(context)
        if (result.resultCode == Activity.RESULT_OK && lastExportDataUri != null) {
            // will be saved only on success
            val file = StoredFileHelper(context, lastExportDataUri, ZIP_MIME_TYPE)
            exportDatabase(file, lastExportDataUri)
        }
    }

    private fun requestImportPathResult(result: ActivityResult) {
        val context = requireContext()
        val lastImportDataUri = result.data?.data

        Localization.assureCorrectAppLanguage(context)
        if (result.resultCode == Activity.RESULT_OK && lastImportDataUri != null) {
            // will be saved only on success
            val file = StoredFileHelper(context, lastImportDataUri, ZIP_MIME_TYPE)

            AlertDialog.Builder(context)
                .setMessage(R.string.override_current_data)
                .setPositiveButton(R.string.ok) { _, _ -> importDatabase(file, lastImportDataUri) }
                .setNegativeButton(R.string.cancel) { dialog, id -> dialog.cancel() }
                .show()
        }
    }

    private fun exportDatabase(file: StoredFileHelper, exportDataUri: Uri) {
        lifecycleScope.launch(
            CoroutineExceptionHandler { context, throwable ->
                showErrorSnackbar(throwable, "Exporting database and settings")
            }
        ) {
            // checkpoint before export
            withContext(Dispatchers.IO) {
                NewPipeDatabase.checkpoint()
            }

            manager.exportDatabase(defaultPreferences, file)

            saveLastImportExportDataUri(exportDataUri) // save export path only on success
            Toast.makeText(requireContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun importDatabase(file: StoredFileHelper, importDataUri: Uri) {
        val context = requireContext()

        // check if file is supported
        if (!ZipHelper.isValidZipFile(file)) {
            Toast.makeText(context, R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                .show()
            return
        }

        try {
            manager.ensureDbDirectoryExists()

            // replace the current database
            if (!manager.extractDb(file)) {
                Toast.makeText(context, R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                    .show()
            }

            // if settings file exist, ask if it should be imported.
            val hasJsonPrefs = manager.exportHasJsonPrefs(file)
            if (hasJsonPrefs || manager.exportHasSerializedPrefs(file)) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.import_settings)
                    .setMessage(if (hasJsonPrefs) null else getString(R.string.import_settings_vulnerable_format))
                    .setOnDismissListener { finishImport(importDataUri) }
                    .setNegativeButton(R.string.cancel) { dialog, which ->
                        dialog.dismiss()
                        finishImport(importDataUri)
                    }
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        dialog.dismiss()
                        try {
                            if (hasJsonPrefs) {
                                manager.loadJsonPrefs(file, defaultPreferences)
                            } else {
                                manager.loadSerializedPrefs(file, defaultPreferences)
                            }
                        } catch (e: IOException) {
                            createErrorNotification(e, "Importing preferences")
                            return@setPositiveButton
                        } catch (e: ClassNotFoundException) {
                            createErrorNotification(e, "Importing preferences")
                            return@setPositiveButton
                        } catch (e: JsonParserException) {
                            createErrorNotification(e, "Importing preferences")
                            return@setPositiveButton
                        }
                        cleanImport(requireContext(), defaultPreferences)
                        finishImport(importDataUri)
                    }
                    .show()
            } else {
                finishImport(importDataUri)
            }
        } catch (e: Exception) {
            showErrorSnackbar(e, "Importing database and settings")
        }
    }

    /**
     * Remove settings that are not supposed to be imported on different devices
     * and reset them to default values.
     * @param context the context used for the import
     * @param prefs the preferences used while running the import
     */
    private fun cleanImport(
        context: Context,
        prefs: SharedPreferences
    ) {
        // Check if media tunnelling needs to be disabled automatically,
        // if it was disabled automatically in the imported preferences.
        val tunnelingKey = getString(R.string.disable_media_tunneling_key)
        val automaticTunnelingKey = getString(R.string.disabled_media_tunneling_automatically_key)
        // R.string.disable_media_tunneling_key should always be true
        // if R.string.disabled_media_tunneling_automatically_key equals 1,
        // but we double check here just to be sure and to avoid regressions
        // caused by possible later modification of the media tunneling functionality.
        // R.string.disabled_media_tunneling_automatically_key == 0:
        //     automatic value overridden by user in settings
        // R.string.disabled_media_tunneling_automatically_key == -1: not set
        val wasMediaTunnelingDisabledAutomatically =
            prefs.getInt(automaticTunnelingKey, -1) == 1 &&
                prefs.getBoolean(tunnelingKey, false)
        if (wasMediaTunnelingDisabledAutomatically) {
            prefs.edit {
                putInt(automaticTunnelingKey, -1)
                putBoolean(tunnelingKey, false)
            }
            NewPipeSettings.setMediaTunneling(context)
        }
    }

    /**
     * Save import path and restart app.
     *
     * @param importDataUri The import path to save
     */
    private fun finishImport(importDataUri: Uri) {
        // save import path only on success
        saveLastImportExportDataUri(importDataUri)
        // restart app to properly load db
        NavigationHelper.restartApp(requireActivity())
    }

    private val importExportDataUri: Uri?
        get() = defaultPreferences
            .getString(getString(R.string.import_export_data_path), null)?.toUri()

    private fun saveLastImportExportDataUri(importExportDataUri: Uri) {
        defaultPreferences.edit {
            putString(getString(R.string.import_export_data_path), importExportDataUri.toString())
        }
    }

    private fun showErrorSnackbar(e: Throwable, request: String) {
        showSnackbar(this, ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, request))
    }

    private fun createErrorNotification(e: Throwable, request: String) {
        createNotification(
            requireContext(),
            ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, request)
        )
    }

    companion object {
        private const val ZIP_MIME_TYPE = "application/zip"
    }
}
