/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import net.newpipe.app.model.BackupFile
import net.newpipe.app.model.DataExportResult
import net.newpipe.app.model.DataImportResult
import net.newpipe.app.model.ErrorReport
import net.newpipe.app.model.SettingsFormat

/**
 * Platform-specific actions triggered from the Backup and restore settings screen
 */
interface BackupRestoreActions {

    @Composable
    fun rememberFilePicker(file: BackupFile, onNoFileManager: () -> Unit, onPicked: (String) -> Unit): () -> Unit

    suspend fun exportData(file: String): DataExportResult

    suspend fun importDatabase(file: String): DataImportResult

    suspend fun importSettings(file: String, format: SettingsFormat)

    fun finishImport(file: String)

    fun resetSettings()

    fun exportSubscriptions(file: String)

    fun importSubscriptions(file: String)
}

internal object NoOpBackupRestoreActions : BackupRestoreActions {

    private val unsupportedError = ErrorReport(message = "Backup and restore is unsupported on this platform", open = {})

    @Composable
    override fun rememberFilePicker(
        file: BackupFile,
        onNoFileManager: () -> Unit,
        onPicked: (String) -> Unit
    ): () -> Unit = remember {
        { logUnsupported("pickFile") }
    }

    override suspend fun exportData(file: String): DataExportResult {
        logUnsupported("exportData")
        return DataExportResult.Failed(unsupportedError)
    }

    override suspend fun importDatabase(file: String): DataImportResult {
        logUnsupported("importDatabase")
        return DataImportResult.Failed(unsupportedError)
    }

    override suspend fun importSettings(file: String, format: SettingsFormat) = logUnsupported("importSettings")

    override fun finishImport(file: String) = logUnsupported("finishImport")

    override fun resetSettings() = logUnsupported("resetSettings")

    override fun exportSubscriptions(file: String) = logUnsupported("exportSubscriptions")

    override fun importSubscriptions(file: String) = logUnsupported("importSubscriptions")

    private fun logUnsupported(action: String) = Logger.w(messageString = "Backup and restore action '$action' is unsupported on this platform")
}
