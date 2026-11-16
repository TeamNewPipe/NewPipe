/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

sealed interface DataExportResult {

    data object Success : DataExportResult

    data class Failed(val error: ErrorReport) : DataExportResult
}
