/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.views

import android.os.Build
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.Window
import androidx.annotation.RequiresApi

open class SimpleWindowCallback(private val baseCallback: Window.Callback) :
    Window.Callback by baseCallback {

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return baseCallback.dispatchKeyEvent(event)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        baseCallback.onPointerCaptureChanged(hasCapture)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(
        data: List<KeyboardShortcutGroup?>?,
        menu: Menu?,
        deviceId: Int
    ) {
        baseCallback.onProvideKeyboardShortcuts(data, menu, deviceId)
    }
}
