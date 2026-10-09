/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger

/**
 * Platform-specific actions triggered from the Debug settings screen.
 *
 */
interface DebugActions {
    /** Whether the running build ships LeakCanary; its two rows are disabled when it does not. */
    val isLeakCanaryAvailable: Boolean

    /** Opens the LeakCanary leak list. */
    fun showMemoryLeaks()

    /** Runs the new-stream notification check now, instead of waiting for its schedule. */
    fun checkNewStreams()

    /** Posts a dummy error notification, to verify error reporting end to end. */
    fun createErrorNotification()

    /** Opens the error report screen for a dummy error. */
    fun reportDummyError()
}

/**
 * Fallback for platforms with no debug tooling to drive, and for Android builds that ship without
 * the host-side bridge.
 */
internal object NoOpDebugActions : DebugActions {
    override val isLeakCanaryAvailable = false

    override fun showMemoryLeaks() = logUnsupported("showMemoryLeaks")

    override fun checkNewStreams() = logUnsupported("checkNewStreams")

    override fun createErrorNotification() = logUnsupported("createErrorNotification")

    override fun reportDummyError() = logUnsupported("reportDummyError")

    private fun logUnsupported(action: String) =
        Logger.w(messageString = "Debug action '$action' is unsupported on this platform")
}
