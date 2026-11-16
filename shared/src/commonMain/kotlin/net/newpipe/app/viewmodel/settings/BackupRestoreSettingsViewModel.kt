/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.DataExportResult
import net.newpipe.app.model.DataImportResult
import net.newpipe.app.platform.BackupRestoreActions
import net.newpipe.app.screen.settings.model.BackupRestoreMessage
import net.newpipe.app.screen.settings.model.SettingsImport
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BackupRestoreSettingsViewModel(
    private val backupRestoreActions: BackupRestoreActions
) : ViewModel() {

    val message: StateFlow<BackupRestoreMessage?>
        field = MutableStateFlow<BackupRestoreMessage?>(null)

    val pendingSettingsImport: StateFlow<SettingsImport?>
        field = MutableStateFlow<SettingsImport?>(null)

    fun exportData(file: String) {
        viewModelScope.launch {
            message.value = when (val result = backupRestoreActions.exportData(file)) {
                DataExportResult.Success -> BackupRestoreMessage.ExportComplete
                is DataExportResult.Failed -> BackupRestoreMessage.Failed(result.error)
            }
        }
    }

    fun importData(file: String) {
        viewModelScope.launch {
            when (val result = backupRestoreActions.importDatabase(file)) {
                DataImportResult.InvalidFile -> {
                    message.value = BackupRestoreMessage.InvalidFile
                }

                is DataImportResult.Failed -> {
                    message.value = BackupRestoreMessage.Failed(result.error)
                }

                is DataImportResult.Imported -> {
                    onDatabaseImported(file, result)
                }
            }
        }
    }

    fun importSettings(settingsImport: SettingsImport) {
        pendingSettingsImport.value = null
        viewModelScope.launch {
            backupRestoreActions.importSettings(settingsImport.file, settingsImport.format)
            backupRestoreActions.finishImport(settingsImport.file)
        }
    }

    fun skipSettingsImport(settingsImport: SettingsImport) {
        pendingSettingsImport.value = null
        backupRestoreActions.finishImport(settingsImport.file)
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun onDatabaseImported(file: String, result: DataImportResult.Imported) {
        if (!result.allFilesImported) {
            message.value = BackupRestoreMessage.PartialImport
        }
        val format = result.settingsFormat
        if (format == null) {
            backupRestoreActions.finishImport(file)
        } else {
            pendingSettingsImport.value = SettingsImport(file, format)
        }
    }
}
