package org.schabi.newpipe.database.subscription

import androidx.annotation.IntDef
import org.schabi.newpipe.database.subscription.NotificationMode

@IntDef([NotificationMode.DISABLED, NotificationMode.ENABLED])
@Retention(AnnotationRetention.SOURCE)
annotation class NotificationMode() {
    companion object {
        val DISABLED: Int = 0
        val ENABLED: Int = 1 //other values reserved for the future
    }
}
