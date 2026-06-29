/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [BackupRestoreActions::class])
class JVMBackupRestoreActions : BackupRestoreActions {
    override fun importDatabase() = log("importDatabase")
    override fun exportDatabase() = log("exportDatabase")
    override fun resetAllSettings() = log("resetAllSettings")
    override fun importSubscriptions() = log("importSubscriptions")
    override fun exportSubscriptions() = log("exportSubscriptions")
    private fun log(name: String) =
        Logger.i(messageString = "$name not implemented on JVM")
}