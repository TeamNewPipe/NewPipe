package org.schabi.newpipe.settings.notifications

import org.schabi.newpipe.database.subscription.NotificationMode

/** A mixed list enables all channels; an entirely enabled list disables them. */
internal object NotificationModeBulkPolicy {
    fun nextMode(modes: List<Int>): Int? = when {
        modes.isEmpty() -> null
        modes.all { it != NotificationMode.DISABLED } -> NotificationMode.DISABLED
        else -> NotificationMode.ENABLED
    }
}
