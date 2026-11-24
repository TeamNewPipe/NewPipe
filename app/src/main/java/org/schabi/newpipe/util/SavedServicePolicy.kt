/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.schabi.newpipe.extractor.ServiceList

/** Saved records remain useful when their plugin is temporarily unavailable on this device. */
object SavedServicePolicy {
    @JvmStatic
    fun canPreserve(serviceId: Int, registeredIds: Set<Int>): Boolean = serviceId in registeredIds ||
        serviceId in ServiceList.FIRST_DYNAMIC_PLUGIN_ID until Int.MAX_VALUE

    /** A background refresh must not borrow the active profile's plugin configuration. */
    fun canRefresh(
        serviceId: Int,
        registeredIds: Set<Int>,
        pluginProfileId: String?,
        targetProfileId: String
    ): Boolean = serviceId in registeredIds &&
        (serviceId < ServiceList.FIRST_DYNAMIC_PLUGIN_ID || pluginProfileId == targetProfileId)
}
