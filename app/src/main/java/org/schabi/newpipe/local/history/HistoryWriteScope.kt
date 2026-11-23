/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.history

import android.content.Context
import org.schabi.newpipe.profiles.ProfileManager

/** Check inside the Room write transaction so completed profile deletion wins over queued work. */
object HistoryWriteScope {
    @JvmStatic
    fun canWrite(context: Context, profileId: String): Boolean = !ProfileManager.isProfileSyncTombstoned(context, profileId)
}
