/*
 * SPDX-FileCopyrightText: Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.panic

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import info.guardianproject.panic.Panic
import info.guardianproject.panic.PanicResponder
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.newpipe.app.di.settings.provideSettings
import net.newpipe.app.platform.AndroidPanicActions
import net.newpipe.app.viewmodel.settings.PanicSettingsViewModel
import org.schabi.newpipe.NewPipeDatabase

/**
 * This activity responds to receiving the panic trigger [Panic.ACTION_TRIGGER].
 *
 * Depending on the actions set in [PanicActivity], the search and watch history or the whole app
 * database are cleared and the app is removed from recent apps.
 */
class PanicResponderActivity : Activity() {

    companion object {
        private val panicScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Panic.isTriggerIntent(intent)) {
            finish()
            return
        }

        val receivedTriggerFromConnectedApp = PanicResponder.receivedTriggerFromConnectedApp(this)
        if (!receivedTriggerFromConnectedApp) {
            finish()
            return
        }

        val panicSettingsVm = PanicSettingsViewModel(
            settings = provideSettings(applicationContext),
            panicActions = AndroidPanicActions(applicationContext)
        )

        panicScope.launch {
            val db = NewPipeDatabase.getInstance(applicationContext)
            if (panicSettingsVm.clearAppDatabase.value) {
                db.clearAllTables()
            } else {
                if (panicSettingsVm.clearSearchHistory.value) {
                    db.searchHistoryDAO().deleteAll()
                }
                if (panicSettingsVm.clearWatchHistory.value) {
                    db.streamHistoryDAO().deleteAll()
                }
            }

            withContext(Dispatchers.Main) {
                if (panicSettingsVm.removeFromRecentApps.value && panicSettingsVm.exitApp.value) {
                    ExitActivity.exitAndRemoveFromRecentApps(this@PanicResponderActivity)
                    finishAndRemoveTask()
                } else if (panicSettingsVm.exitApp.value) {
                    finishAndRemoveTask()
                    exitProcess(0)
                } else {
                    finish()
                }
            }
        }
        finish()
    }
}
