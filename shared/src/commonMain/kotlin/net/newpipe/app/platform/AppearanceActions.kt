/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Platform-specific actions triggered from the Appearance settings screen.
 */
interface AppearanceActions {
    /**
     * Opens the system captioning settings, where supported.
     */
    fun openCaptionSettings()
}
