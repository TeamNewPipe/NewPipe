/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Platform side-effects triggered from download-related settings.
 */
interface DownloadActions {
    fun pickDirectory(onPicked: (uri: String) -> Unit)
}