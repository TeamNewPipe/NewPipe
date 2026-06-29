/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings.backuprestore

import androidx.lifecycle.ViewModel
import net.newpipe.app.platform.BackupRestoreActions
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BackupRestoreSettingsViewModel(
    private val actions: BackupRestoreActions
) : ViewModel() {
    fun importDatabase() = actions.importDatabase()
    fun exportDatabase() = actions.exportDatabase()
    fun resetAllSettings() = actions.resetAllSettings()
    fun importSubscriptions() = actions.importSubscriptions()
    fun exportSubscriptions() = actions.exportSubscriptions()
}