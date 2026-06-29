/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package org.schabi.newpipe.platform

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Process-wide registry of the SAF launchers (open + create) used by
 * Backup & Restore. Bound by [org.schabi.newpipe.NewPipeComposeActivity] in
 * `onCreate`, dropped on `DESTROYED`.
 *
 * The Compose screen invokes a hook on [org.schabi.newpipe.platform.AppLegacyHooks]
 * which (a) builds the SAF intent, (b) stashes a one-shot callback in this
 * registry, (c) launches the intent through the right launcher. When the user
 * picks/saves a file, the registry invokes the callback.
 */
object ImportExportLauncherRegistry {

    @Volatile
    internal var openLauncher: ActivityResultLauncher<Intent>? = null

    @Volatile
    internal var createLauncher: ActivityResultLauncher<Intent>? = null

    @Volatile
    internal var pendingCallback: ((ActivityResult) -> Unit)? = null

    fun bindTo(activity: ComponentActivity) {
        val onResult: (ActivityResult) -> Unit = { result ->
            val cb = pendingCallback
            pendingCallback = null
            cb?.invoke(result)
        }
        val open = activity.registerForActivityResult(StartActivityForResult(), onResult)
        val create = activity.registerForActivityResult(StartActivityForResult(), onResult)
        openLauncher = open
        createLauncher = create

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (openLauncher === open) openLauncher = null
                if (createLauncher === create) createLauncher = null
                pendingCallback = null
            }
        })
    }
}
