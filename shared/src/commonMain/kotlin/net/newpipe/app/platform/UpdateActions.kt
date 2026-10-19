/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform-specific actions triggered from the Update settings screen.
 */
interface UpdateActions {
    /**
     * Enqueues a new-version check. [manual] is true when triggered explicitly
     * from the "Check for updates" preference (used e.g. for showing progress).
     */
    fun enqueueUpdateCheck(manual: Boolean)
}