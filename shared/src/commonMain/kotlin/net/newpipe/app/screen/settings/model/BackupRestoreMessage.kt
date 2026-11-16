/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.model

import net.newpipe.app.model.ErrorReport

sealed interface BackupRestoreMessage {

    data object ExportComplete : BackupRestoreMessage

    data object InvalidFile : BackupRestoreMessage

    data object PartialImport : BackupRestoreMessage

    data class Failed(val error: ErrorReport) : BackupRestoreMessage
}
