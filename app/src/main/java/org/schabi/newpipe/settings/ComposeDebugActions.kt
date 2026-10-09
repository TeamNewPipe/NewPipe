package org.schabi.newpipe.settings

import android.content.Context
import android.content.Intent
import net.newpipe.app.platform.DebugActions
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationWorker

/**
 * Host-side implementation of the debug actions used by the Compose debug settings screen.
 */
@Suppress("unused")
class ComposeDebugActions(private val context: Context) : DebugActions {

    private val leakCanary = loadLeakCanary()

    override val isLeakCanaryAvailable: Boolean
        get() = leakCanary != null

    override fun showMemoryLeaks() {
        // The injected context is the application context, so the activity needs its own task.
        leakCanary?.newLeakDisplayActivityIntent
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    override fun checkNewStreams() = NotificationWorker.runNow(context)

    override fun createErrorNotification() = ErrorUtil.createNotification(context, dummyErrorInfo())

    override fun reportDummyError() = ErrorUtil.openActivity(context, dummyErrorInfo())

    private fun dummyErrorInfo() = ErrorInfo(RuntimeException(DUMMY), UserAction.UI_ERROR, DUMMY)

    private fun loadLeakCanary(): DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI? = runCatching {
        Class.forName(DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS)
            .getDeclaredConstructor()
            .newInstance() as DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI
    }.getOrNull()

    private companion object {
        const val DUMMY = "Dummy"
    }
}
