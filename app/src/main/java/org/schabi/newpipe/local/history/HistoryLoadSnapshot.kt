/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.history

import java.util.Collections
import org.schabi.newpipe.database.stream.StreamStatisticsEntry

/** A preview is only a display aid, never the source of full-history actions. */
class HistoryLoadSnapshot(entries: List<StreamStatisticsEntry>, val isComplete: Boolean) {
    // Room owns each newly emitted list; neither the loader nor the view mutates it.
    val entries: List<StreamStatisticsEntry> = Collections.unmodifiableList(entries)
}
