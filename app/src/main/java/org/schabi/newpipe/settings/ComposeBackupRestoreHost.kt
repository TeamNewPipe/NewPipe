/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.jakewharton.processphoenix.ProcessPhoenix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.model.BackupFile
import net.newpipe.app.model.DataExportResult
import net.newpipe.app.model.DataImportResult
import net.newpipe.app.model.ErrorReport
import net.newpipe.app.model.SettingsFormat
import net.newpipe.app.platform.BackupRestoreHost
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.subscription.SubscriptionFragment.Companion.JSON_MIME_TYPE
import org.schabi.newpipe.local.subscription.workers.SubscriptionExportWorker
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportWorker
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

/**
 * Host-side backup and restore for the Compose backup and restore settings screen
 */
class ComposeBackupRestoreHost(private val context: Context) : BackupRestoreHost {

    private val manager = ImportExportManager(BackupFileLocator(context))
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val lastBackupFileKey = context.getString(R.string.import_export_data_path)

    override fun createFilePickerIntent(file: BackupFile): Intent = when (file) {
        BackupFile.DATA_IMPORT -> {
            StoredFileHelper.getPicker(context, ZIP_MIME_TYPE, lastBackupFile())
        }

        BackupFile.DATA_EXPORT -> {
            val name = "NewPipeData-${timestamp(DATA_DATE_PATTERN, Locale.US)}.zip"
            StoredFileHelper.getNewPicker(context, name, ZIP_MIME_TYPE, lastBackupFile())
        }

        BackupFile.SUBSCRIPTIONS_IMPORT -> {
            StoredFileHelper.getPicker(context, JSON_MIME_TYPE)
        }

        BackupFile.SUBSCRIPTIONS_EXPORT -> {
            val name = "newpipe_subscriptions_${timestamp(SUBSCRIPTIONS_DATE_PATTERN, Locale.ENGLISH)}.json"
            StoredFileHelper.getNewPicker(context, name, JSON_MIME_TYPE, null)
        }
    }

    override suspend fun exportData(file: String): DataExportResult = withContext(Dispatchers.IO) {
        try {
            NewPipeDatabase.checkpoint()
            manager.exportDatabase(preferences, zipFile(file))
            // Remembered only on success, so the next picker opens where it worked
            saveLastBackupFile(file)
            DataExportResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            DataExportResult.Failed(errorReport(error, REQUEST_EXPORT))
        }
    }

    override suspend fun importDatabase(file: String): DataImportResult = withContext(Dispatchers.IO) {
        try {
            val backup = zipFile(file)
            if (!ZipHelper.isValidZipFile(backup)) {
                return@withContext DataImportResult.InvalidFile
            }
            manager.ensureDbDirectoryExists()
            val allFilesImported = manager.extractDb(backup)
            val settingsFormat = when {
                manager.exportHasJsonPrefs(backup) -> SettingsFormat.JSON
                manager.exportHasSerializedPrefs(backup) -> SettingsFormat.SERIALIZED
                else -> null
            }
            DataImportResult.Imported(allFilesImported, settingsFormat)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            DataImportResult.Failed(errorReport(error, REQUEST_IMPORT))
        }
    }

    override suspend fun importSettings(file: String, format: SettingsFormat) = withContext(Dispatchers.IO) {
        try {
            val backup = zipFile(file)
            when (format) {
                SettingsFormat.JSON -> manager.loadJsonPrefs(backup, preferences)
                SettingsFormat.SERIALIZED -> manager.loadSerializedPrefs(backup, preferences)
            }
            resetDeviceSpecificSettings()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ErrorUtil.createNotification(
                context,
                ErrorInfo(error, UserAction.DATABASE_IMPORT_EXPORT, REQUEST_IMPORT_SETTINGS)
            )
        }
    }

    override fun finishImport(file: String) {
        saveLastBackupFile(file)
        restartApp()
    }

    override fun resetSettings() {
        preferences.edit { clear() }
        restartApp()
    }

    override fun exportSubscriptions(file: String) = SubscriptionExportWorker.schedule(context, file.toUri())

    override fun importSubscriptions(file: String) {
        val request = OneTimeWorkRequestBuilder<SubscriptionImportWorker>()
            .setInputData(SubscriptionImportInput.PreviousExportMode(file).toData())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SubscriptionImportWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun resetDeviceSpecificSettings() {
        val tunnelingKey = context.getString(R.string.disable_media_tunneling_key)
        val automaticTunnelingKey = context.getString(R.string.disabled_media_tunneling_automatically_key)
        val disabledAutomatically = preferences.getInt(automaticTunnelingKey, -1) == 1 &&
            preferences.getBoolean(tunnelingKey, false)
        if (disabledAutomatically) {
            preferences.edit {
                putInt(automaticTunnelingKey, -1)
                putBoolean(tunnelingKey, false)
            }
            NewPipeSettings.setMediaTunneling(context)
        }
    }

    private fun restartApp() {
        NewPipeDatabase.close()
        ProcessPhoenix.triggerRebirth(context)
    }

    private fun zipFile(file: String) = StoredFileHelper(context, file.toUri(), ZIP_MIME_TYPE)

    private fun lastBackupFile() = preferences.getString(lastBackupFileKey, null)
        ?.takeUnless { it.isBlank() }
        ?.toUri()

    private fun saveLastBackupFile(file: String) = preferences.edit { putString(lastBackupFileKey, file) }

    private fun timestamp(pattern: String, locale: Locale) = SimpleDateFormat(pattern, locale).format(Date())

    private fun errorReport(error: Exception, request: String): ErrorReport {
        val info = ErrorInfo(error, UserAction.DATABASE_IMPORT_EXPORT, request)
        return ErrorReport(
            message = info.getMessage(context).toString(),
            open = { ErrorUtil.openActivity(context, info) }
        )
    }

    private companion object {
        const val ZIP_MIME_TYPE = "application/zip"
        const val DATA_DATE_PATTERN = "yyyyMMdd_HHmmss"
        const val SUBSCRIPTIONS_DATE_PATTERN = "yyyyMMddHHmm"
        const val REQUEST_EXPORT = "Exporting database and settings"
        const val REQUEST_IMPORT = "Importing database and settings"
        const val REQUEST_IMPORT_SETTINGS = "Importing preferences"
    }
}
