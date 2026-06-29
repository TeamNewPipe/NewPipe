/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [BackupRestoreActions::class])
class AndroidBackupRestoreActions(
    private val legacyHooks: AndroidLegacyHooks
) : BackupRestoreActions {
    override fun importDatabase() = legacyHooks.importDatabase()
    override fun exportDatabase() = legacyHooks.exportDatabase()
    override fun resetAllSettings() = legacyHooks.resetAllSettings()
    override fun importSubscriptions() = legacyHooks.importSubscriptions()
    override fun exportSubscriptions() = legacyHooks.exportSubscriptions()
}



