/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Intent
import net.newpipe.app.model.BackupFile
import net.newpipe.app.model.DataExportResult
import net.newpipe.app.model.DataImportResult
import net.newpipe.app.model.SettingsFormat

interface BackupRestoreHost {

    fun createFilePickerIntent(file: BackupFile): Intent

    suspend fun exportData(file: String): DataExportResult

    suspend fun importDatabase(file: String): DataImportResult

    suspend fun importSettings(file: String, format: SettingsFormat)

    fun finishImport(file: String)

    fun resetSettings()

    fun exportSubscriptions(file: String)

    fun importSubscriptions(file: String)
}
