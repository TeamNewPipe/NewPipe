/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.platform

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

object DirectoryPickerRegistry {

    @Volatile
    internal var currentLauncher: ActivityResultLauncher<Uri?>? = null

    @Volatile
    internal var pendingCallback: ((String) -> Unit)? = null

    fun bindTo(activity: ComponentActivity) {
        val launcher = activity.registerForActivityResult(OpenDocumentTree()) { uri: Uri? ->
            val callback = pendingCallback
            pendingCallback = null
            if (uri != null) {
                callback?.invoke(uri.toString())
            }
        }
        currentLauncher = launcher

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (currentLauncher === launcher) {
                    currentLauncher = null
                    pendingCallback = null
                }
            }
        })
    }
}
