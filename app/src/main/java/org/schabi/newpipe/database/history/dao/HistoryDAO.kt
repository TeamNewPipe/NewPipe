/*
 * SPDX-FileCopyrightText: 2017 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.dao

import org.schabi.newpipe.database.BasicDAO

interface HistoryDAO<T> : BasicDAO<T> {
    val latestEntry: T
}
