/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

sealed interface DataImportResult {

    data object InvalidFile : DataImportResult

    data class Failed(val error: ErrorReport) : DataImportResult

    data class Imported(val allFilesImported: Boolean, val settingsFormat: SettingsFormat?) : DataImportResult
}
