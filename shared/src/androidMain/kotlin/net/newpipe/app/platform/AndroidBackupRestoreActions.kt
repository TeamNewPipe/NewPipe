/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import net.newpipe.app.model.BackupFile
import net.newpipe.app.model.SettingsFormat
import org.koin.core.annotation.Singleton

@Singleton(binds = [BackupRestoreActions::class])
class AndroidBackupRestoreActions(context: Context) : BackupRestoreActions {

    private val host: BackupRestoreHost? = loadHost(context)

    @Composable
    override fun rememberFilePicker(
        file: BackupFile,
        onNoFileManager: () -> Unit,
        onPicked: (String) -> Unit
    ): () -> Unit {
        val currentOnNoFileManager by rememberUpdatedState(onNoFileManager)
        val currentOnPicked by rememberUpdatedState(onPicked)

        val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            val picked = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && picked != null) {
                currentOnPicked(picked.toString())
            }
        }

        return remember(launcher, file) {
            {
                val bridge = host
                if (bridge != null) {
                    try {
                        launcher.launch(bridge.createFilePickerIntent(file))
                    } catch (exception: ActivityNotFoundException) {
                        Logger.w(
                            messageString = "Unable to launch the backup file picker",
                            throwable = exception
                        )
                        currentOnNoFileManager()
                    }
                }
            }
        }
    }

    override suspend fun exportData(file: String) = host?.exportData(file)
        ?: NoOpBackupRestoreActions.exportData(file)

    override suspend fun importDatabase(file: String) = host?.importDatabase(file)
        ?: NoOpBackupRestoreActions.importDatabase(file)

    override suspend fun importSettings(file: String, format: SettingsFormat) {
        host?.importSettings(file, format)
    }

    override fun finishImport(file: String) {
        host?.finishImport(file)
    }

    override fun resetSettings() {
        host?.resetSettings()
    }

    override fun exportSubscriptions(file: String) {
        host?.exportSubscriptions(file)
    }

    override fun importSubscriptions(file: String) {
        host?.importSubscriptions(file)
    }

    private fun loadHost(context: Context): BackupRestoreHost? = runCatching {
        Class.forName(HOST_CLASS)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as BackupRestoreHost
    }.onFailure { error ->
        Logger.w(
            messageString = "No host backup and restore actions found, skipping host-side changes",
            throwable = error
        )
    }.getOrNull()

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeBackupRestoreHost"
    }
}
