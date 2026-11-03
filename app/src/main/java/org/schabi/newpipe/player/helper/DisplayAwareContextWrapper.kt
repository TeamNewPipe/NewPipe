/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.helper

import android.content.Context
import android.content.ContextWrapper
import android.view.View

/**
 * Context for the player views, which are inflated with the player service context. That context
 * is bound to the default display, so windows added on behalf of a view (e.g. popup menus and
 * hover tooltips) would be added to the wrong display when the player is shown on a secondary
 * display, like Samsung DeX, causing a [android.view.WindowManager.BadTokenException]. This
 * wrapper returns the [android.view.WindowManager] of the display the anchor view is on.
 */
class DisplayAwareContextWrapper(base: Context) : ContextWrapper(base) {
    var anchor: View? = null

    override fun getSystemService(name: String): Any? {
        if (name == WINDOW_SERVICE) {
            anchor?.display?.let { display ->
                return baseContext.createDisplayContext(display).getSystemService(name)
            }
        }
        return super.getSystemService(name)
    }
}
