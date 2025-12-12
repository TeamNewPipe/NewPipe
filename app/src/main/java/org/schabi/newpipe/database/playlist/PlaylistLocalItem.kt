/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist

import org.schabi.newpipe.database.LocalItem

interface PlaylistLocalItem : LocalItem {
    val orderingName: String?
    val displayIndex: Long?
    val uid: Long
    val thumbnailUrl: String?
}
