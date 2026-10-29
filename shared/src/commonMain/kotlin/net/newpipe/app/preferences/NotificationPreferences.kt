/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Notifications settings screen
 */
object NotificationPreferences {
    const val KEY_ENABLE_STREAMS_NOTIFICATIONS = "enable_streams_notifications"
    const val KEY_STREAMS_NOTIFICATIONS_INTERVAL = "streams_notifications_interval"
    const val KEY_STREAMS_NOTIFICATIONS_NETWORK = "streams_notifications_network"

    const val NETWORK_ANY = "any"
    const val NETWORK_WIFI = "wifi"

    val STREAMS_NOTIFICATIONS_INTERVALS = listOf("900", "1800", "3600", "7200", "14400", "43200", "86400")

    const val DEFAULT_ENABLE_STREAMS_NOTIFICATIONS = false
    const val DEFAULT_STREAMS_NOTIFICATIONS_INTERVAL = "14400"
    const val DEFAULT_STREAMS_NOTIFICATIONS_NETWORK = NETWORK_WIFI
}
