/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import org.schabi.newpipe.R
import org.schabi.newpipe.sync.DeviceSyncSetupDiagnostic
import org.schabi.newpipe.sync.DeviceSyncSetupFailure
import org.schabi.newpipe.sync.DeviceSyncSetupOperation

/** Setup records deliberately have no synchronization category or device counters. */
internal fun DeviceSyncSetupDiagnostic.displayText(context: Context): String = buildString {
    append(
        context.getString(
            when (operation) {
                DeviceSyncSetupOperation.CREATE_INVITATION -> R.string.device_sync_setup_create
                DeviceSyncSetupOperation.PAIR -> R.string.device_sync_setup_pair
                DeviceSyncSetupOperation.START_LISTENER -> R.string.device_sync_setup_listener
            }
        )
    )
    append(" — ")
    append(
        context.getString(
            if (succeeded) R.string.device_sync_setup_succeeded else R.string.device_sync_setup_failed
        )
    )
    if (!succeeded) {
        append("\n")
        append(
            context.getString(
                when (failure ?: DeviceSyncSetupFailure.UNKNOWN) {
                    DeviceSyncSetupFailure.INVALID_CODE -> R.string.device_sync_setup_invalid_code
                    DeviceSyncSetupFailure.EXPIRED_OR_USED -> R.string.device_sync_setup_expired
                    DeviceSyncSetupFailure.CLOCK_MISMATCH -> R.string.device_sync_setup_clock
                    DeviceSyncSetupFailure.SAME_DEVICE -> R.string.device_sync_setup_same_device
                    DeviceSyncSetupFailure.NO_LOCAL_ADDRESS -> R.string.device_sync_setup_no_address
                    DeviceSyncSetupFailure.UNREACHABLE -> R.string.device_sync_setup_unreachable
                    DeviceSyncSetupFailure.TIMEOUT -> R.string.device_sync_setup_timeout
                    DeviceSyncSetupFailure.LISTENER_UNAVAILABLE -> R.string.device_sync_setup_listener_unavailable
                    DeviceSyncSetupFailure.REJECTED -> R.string.device_sync_setup_rejected
                    DeviceSyncSetupFailure.UNKNOWN -> R.string.device_sync_setup_unknown
                }
            )
        )
    }
    localAppVersion?.let { version ->
        append("\n")
        append(
            context.getString(
                R.string.device_sync_log_local_version,
                context.getString(R.string.device_sync_log_version_value, version.name, version.code)
            )
        )
    }
}
