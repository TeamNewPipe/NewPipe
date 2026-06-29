/*                                                                           
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform side effects triggered from the debug settings screen.
 * Implemented on Android against ErrorUtil / NotificationWorker / LeakCanary.
 * */
interface DebugActions {
    fun crashTheApp()
    fun showErrorSnackbar()
    fun createErrorNotification()
    fun checkNewStreams()
    fun showMemoryLeaks()

    fun isLeakCanaryAvailable(): Boolean
}