/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.app.Application
import android.content.Intent
import org.koin.core.annotation.Singleton

/**
 * Android implementation of [DebugActions].
 *
 * Receives [AndroidLegacyHooks] through Koin. The host activity
 * (`NewPipeComposeActivity`) overrides the default placeholder binding from
 * [AndroidLegacyHooksModule] with the real implementation (`AppLegacyHooks`)
 * via its `platformModules`.
 */
@Singleton(binds = [DebugActions::class])
class AndroidDebugActions(
    private val application: Application,
    private val legacyHooks: AndroidLegacyHooks
) : DebugActions {

    override fun crashTheApp() {
        throw RuntimeException("Dummy")
    }

    override fun showErrorSnackbar() = legacyHooks.showUiErrorSnackbar()

    override fun createErrorNotification() = legacyHooks.createErrorNotification()

    override fun checkNewStreams() = legacyHooks.runNotificationWorkerNow()

    override fun showMemoryLeaks() {
        legacyHooks.newLeakDisplayActivityIntent()?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(intent)
        }
    }

    override fun isLeakCanaryAvailable(): Boolean =
        legacyHooks.newLeakDisplayActivityIntent() != null
}