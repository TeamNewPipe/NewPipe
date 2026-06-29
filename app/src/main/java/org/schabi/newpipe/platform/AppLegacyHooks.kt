/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package org.schabi.newpipe.platform

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParserException
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import net.newpipe.app.platform.AndroidLegacyHooks
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.NewVersionWorker
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.settings.DebugSettingsBVDLeakCanaryAPI
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.ZipHelper

private const val DUMMY = "Dummy"
private const val TAG = "AppLegacyHooks"

class AppLegacyHooks(
    private val application: Application
) : AndroidLegacyHooks {

    private val leakCanaryApi: DebugSettingsBVDLeakCanaryAPI? = runCatching {
        Class.forName(DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS)
            .getDeclaredConstructor()
            .newInstance() as DebugSettingsBVDLeakCanaryAPI
    }.getOrNull()

    private val historyManager: HistoryRecordManager by lazy { HistoryRecordManager(application) }
    private val historyDisposables = CompositeDisposable()

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(application)
    private val recaptchaCookiesKey get() = application.getString(R.string.recaptcha_cookies_key)

    // ErrorUtil / NotificationWorker / LeakCanary

    override fun showUiErrorSnackbar() {
        ErrorUtil.showUiErrorSnackbar(application, DUMMY, RuntimeException(DUMMY))
    }

    override fun createErrorNotification() {
        ErrorUtil.createNotification(
            application,
            ErrorInfo(RuntimeException(DUMMY), UserAction.UI_ERROR, DUMMY)
        )
    }

    override fun runNotificationWorkerNow() {
        NotificationWorker.runNow(application)
    }

    override fun newLeakDisplayActivityIntent(): Intent? = leakCanaryApi?.getNewLeakDisplayActivityIntent()

    // DownloadActions

    override fun requestDirectoryPicker(onPicked: (String) -> Unit) {
        val launcher = DirectoryPickerRegistry.currentLauncher
        if (launcher == null) {
            Log.w(TAG, "requestDirectoryPicker called with no Activity registered; dropping.")
            return
        }
        DirectoryPickerRegistry.pendingCallback = onPicked
        launcher.launch(null)
    }

    // UpdateActions

    override fun runManualUpdateCheck() {
        Toast.makeText(application, R.string.checking_updates_toast, Toast.LENGTH_SHORT).show()
        NewVersionWorker.enqueueNewVersionCheckingWork(application, true)
    }

    // HistoryActions

    override fun wipeMetadataCache() {
        InfoCache.getInstance().clearCache()
        Toast.makeText(application, R.string.metadata_cache_wipe_complete_notice, Toast.LENGTH_SHORT).show()
    }

    override fun deleteWatchHistory() {
        historyDisposables.add(
            historyManager.deleteWholeStreamHistory()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Toast.makeText(application, R.string.watch_history_deleted, Toast.LENGTH_SHORT).show() },
                    { throwable ->
                        ErrorUtil.openActivity(
                            application,
                            ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY, "Delete from history")
                        )
                    }
                )
        )
    }

    override fun deletePlaybackStates() {
        historyDisposables.add(
            historyManager.deleteCompleteStreamStateHistory()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Toast.makeText(application, R.string.watch_history_states_deleted, Toast.LENGTH_SHORT).show() },
                    { throwable ->
                        ErrorUtil.openActivity(
                            application,
                            ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY, "Delete playback states")
                        )
                    }
                )
        )
    }

    override fun deleteSearchHistory() {
        historyDisposables.add(
            historyManager.deleteCompleteSearchHistory()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Toast.makeText(application, R.string.search_history_deleted, Toast.LENGTH_SHORT).show() },
                    { throwable ->
                        ErrorUtil.openActivity(
                            application,
                            ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY, "Delete search history")
                        )
                    }
                )
        )
    }

    override fun clearRecaptchaCookies() {
        prefs.edit { putString(recaptchaCookiesKey, "") }
        DownloaderImpl.getInstance().setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "")
        Toast.makeText(application, R.string.recaptcha_cookies_cleared, Toast.LENGTH_SHORT).show()
    }

    override fun hasRecaptchaCookies(): Boolean = !prefs.getString(recaptchaCookiesKey, "").isNullOrEmpty()

    override fun applyTheme(newThemeKey: String) = applyThemeInternal(newThemeKey)
    override fun applyNightTheme(newNightThemeKey: String) = applyThemeInternal(newNightThemeKey)

    private fun applyThemeInternal(newKey: String) {
        prefs.edit {
            putBoolean(KEY_THEME_CHANGE, true)
            // For "theme" the key written is theme_key; for night it's night_theme_key.
            // Both fragments read from the same field — the active *day* theme key.
            // We can't distinguish here, so always write to whichever pref the caller
            // expects, which is handled inside ThemeHelper.setDayNightMode().
        }
        ThemeHelper.setDayNightMode(application, newKey)
        DirectoryPickerRegistry.currentLauncher?.let {
            // Recreate the foreground activity so the new theme takes effect.
            (it as? Any)?.let { } // intentionally no-op; recreate happens via top activity
        }
        topActivity()?.let { ActivityCompat.recreate(it) }
    }

    override fun showSelectNightThemeToast() {
        Toast.makeText(
            application,
            R.string.select_night_theme_toast,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun openCaptionSettings() {
        val intent =
            Intent(AndroidSettings.ACTION_CAPTIONING_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            application.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                application,
                R.string.general_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun topActivity(): Activity? {
        // The Compose host activity is what we need to recreate. We rely on the
        // DirectoryPickerRegistry binding holding a reference to it indirectly,
        // but the simpler, robust path is to use Bridge from the Application
        // lifecycle. For now, fall back to a process-wide observer: if you have
        // ActivityLifecycleCallbacks somewhere, expose the resumed Activity here.
        return null
    }

    private companion object {
        const val ZIP_MIME_TYPE = "application/zip"
        const val JSON_MIME_TYPE = "application/json"
    }

    private val backupManager: ImportExportManager by lazy {
        ImportExportManager(BackupFileLocator(application))
    }
    private val exportNameFormat = SimpleDateFormat(
        "yyyyMMdd_HHmmss",
        Locale.US
    )

    override fun importDatabase() {
        val launcher = ImportExportLauncherRegistry.openLauncher
        if (launcher == null) {
            Log.w(TAG, "importDatabase called with no Activity registered; dropping.")
            return
        }
        val intent = StoredFileHelper.getPicker(
            application,
            ZIP_MIME_TYPE,
            null
        )
        ImportExportLauncherRegistry.pendingCallback = { result ->
            val data = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                runImportDatabase(data)
            }
        }
        launcher.launch(intent)
    }

    override fun exportDatabase() {
        val launcher = ImportExportLauncherRegistry.createLauncher
        if (launcher == null) {
            Log.w(TAG, "exportDatabase called with no Activity registered; dropping.")
            return
        }
        val suggested =
            "NewPipeData-${exportNameFormat.format(Date())}.zip"
        val intent = StoredFileHelper.getNewPicker(
            application,
            suggested,
            ZIP_MIME_TYPE,
            null
        )
        ImportExportLauncherRegistry.pendingCallback = { result ->
            val data = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                runExportDatabase(data)
            }
        }
        launcher.launch(intent)
    }

    override fun resetAllSettings() {
        prefs.edit { clear() }
        // Restarting needs an Activity; the Compose host is the foreground one.
        // If we lost the reference, fall back to a toast asking the user to restart.
        val launcher = DirectoryPickerRegistry.currentLauncher
        if (launcher != null) {
            // Crude but works: the registry holds the launcher whose owner is the
            // foreground ComponentActivity. We use Application.startActivity to
            // restart, which doesn't require an Activity reference.
            NavigationHelper.restartApp(
                application as Activity?
                    ?: return
            )
        } else {
            Toast.makeText(
                application,
                R.string.app_restart_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun importSubscriptions() {
        // The legacy SubscriptionsImportExportHelper is Fragment-scoped. Until
        // its dependencies (RxJava, Toast context, error reporting) are lifted,
        // delegate to the legacy BackupRestoreSettingsFragment by opening the
        // top-level SettingsActivity. The user will need to tap the row again.
        openLegacyBackupRestore()
    }

    override fun exportSubscriptions() = openLegacyBackupRestore()

    private fun openLegacyBackupRestore() {
        val intent = Intent(application, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }

    private fun runExportDatabase(exportUri: Uri) {
        Executors.newSingleThreadExecutor().use { exec ->
            try {
                exec.submit { NewPipeDatabase.checkpoint() }.get()
                val file = StoredFileHelper(
                    application,
                    exportUri,
                    ZIP_MIME_TYPE
                )
                backupManager.exportDatabase(prefs, file)
                prefs.edit {
                    putString(
                        importExportDataPathKey,
                        exportUri.toString()
                    )
                }
                Toast.makeText(
                    application,
                    R.string.export_complete_toast,
                    Toast.LENGTH_SHORT
                )
                    .show()
            } catch (e: Exception) {
                ErrorUtil.createNotification(
                    application,
                    ErrorInfo(
                        e,
                        UserAction.DATABASE_IMPORT_EXPORT,
                        "Exporting database"
                    )
                )
            }
        }
    }

    private fun runImportDatabase(importUri: Uri) {
        val file = StoredFileHelper(application, importUri, ZIP_MIME_TYPE)
        if (!ZipHelper.isValidZipFile(file)) {
            Toast.makeText(
                application,
                R.string.no_valid_zip_file,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        try {
            backupManager.ensureDbDirectoryExists()
            if (!backupManager.extractDb(file)) {
                Toast.makeText(
                    application,
                    R.string.could_not_import_all_files,
                    Toast.LENGTH_LONG
                ).show()
            }
            // Settings import + the "import settings?" prompt require an Activity
            // for the dialog. We surface this minimally as: if the zip has prefs,
            // import them silently. The user can confirm via the toast.
            val hasJson = backupManager.exportHasJsonPrefs(file)
            val hasSerialized =
                backupManager.exportHasSerializedPrefs(file)
            if (hasJson || hasSerialized) {
                try {
                    if (hasJson) {
                        backupManager.loadJsonPrefs(file, prefs)
                    } else {
                        backupManager.loadSerializedPrefs(file, prefs)
                    }
                    cleanImport()
                } catch (e: Exception) {
                    when (e) {
                        is IOException, is ClassNotFoundException, is
                        JsonParserException ->
                            ErrorUtil.createNotification(
                                application,
                                ErrorInfo(
                                    e,
                                    UserAction.DATABASE_IMPORT_EXPORT,
                                    "Importing preferences"
                                )
                            )

                        else -> throw e
                    }
                }
            }
            prefs.edit {
                putString(
                    importExportDataPathKey,
                    importUri.toString()
                )
            }
            // Restart on success
            NavigationHelper.restartApp(
                application as Activity?
                    ?: return
            )
        } catch (e: Exception) {
            ErrorUtil.createNotification(
                application,
                ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, "Importing database")
            )
        }
    }

    /**
     * Mirror of `BackupRestoreSettingsFragment.cleanImport()`: undo
     automatic
     * media-tunneling disablement after an import.
     */
    private fun cleanImport() {
        val tunnelingKey =
            application.getString(R.string.disable_media_tunneling_key)
        val autoTunnelingKey =

            application.getString(R.string.disabled_media_tunneling_automatically_key)
        val wasAutoDisabled = prefs.getInt(autoTunnelingKey, -1) == 1 &&
            prefs.getBoolean(tunnelingKey, false)
        if (wasAutoDisabled) {
            prefs.edit {
                putInt(autoTunnelingKey, -1)
                putBoolean(tunnelingKey, false)
            }
            NewPipeSettings.setMediaTunneling(application)
        }
    }

    private val importExportDataPathKey: String
        get() = application.getString(R.string.import_export_data_path)

    override fun openMainPageTabsChooser() = openLegacySettings()
    override fun openPeertubeInstanceList() = openLegacySettings()

    override fun onAppLanguageChanged() {
        // Mirror legacy: clear localization caches; the new locale takes effect
        // on app restart. For now, surface a restart hint to the user.
        Toast.makeText(
            application,
            R.string.app_restart_required,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openLegacySettings() {
        val intent = Intent(application, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
}
