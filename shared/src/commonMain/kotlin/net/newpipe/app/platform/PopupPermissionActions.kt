/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger

/**
 * Platform-specific actions for the permission the popup player needs to draw over other apps
 */
interface PopupPermissionActions {

    val needsPermissionGuide: Boolean

    val appLabel: String

    fun canDrawOverlays(): Boolean

    fun openOverlayPermissionSettings()
}

internal object NoOpPopupPermissionActions : PopupPermissionActions {

    override val needsPermissionGuide = false

    override val appLabel = ""

    override fun canDrawOverlays() = true

    override fun openOverlayPermissionSettings() = Logger.w(messageString = "Overlay permission settings are unsupported on this platform")
}
