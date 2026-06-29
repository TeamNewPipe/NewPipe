/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform side-effects triggered from the Updates settings screen.
 *
 * Android: calls into the legacy NewVersionWorker via [AndroidLegacyHooks].
 */
interface UpdateActions {
    /** Trigger an immediate update check; shows a "checking…" toast on Android. */
    fun runManualCheck()
}