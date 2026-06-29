/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [DebugActions::class])
class IOSDebugActions : DebugActions {
    override fun crashTheApp() { throw RuntimeException("Dummy") }
    override fun showErrorSnackbar() { /* no-op */ }
    override fun createErrorNotification() { /* no-op */ }
    override fun checkNewStreams() { /* no-op */ }
    override fun showMemoryLeaks() { /* no-op */ }
    override fun isLeakCanaryAvailable(): Boolean = false
}