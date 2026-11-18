/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSyncIncomingLogTest {
    private val entry = DeviceSyncLogEntry(
        timestampEpochMillis = 1_000,
        background = false,
        succeededDevices = 0,
        failedDevices = 0,
        sentChanges = 0,
        receivedChanges = 0,
        direction = DeviceSyncLogDirection.INCOMING,
        attempts = listOf(
            DeviceSyncLogAttempt("Phone", "peer-1", emptyList(), emptyList())
        )
    )

    @Test
    fun aggregatesIncomingHistoryBatchesByCategoryAndLogsFailure() {
        val first = DeviceSyncIncomingLog.merge(
            entry,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.WATCH, 5, 8)
        )
        val second = DeviceSyncIncomingLog.merge(
            first,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.WATCH, 2, 3, "Connection closed")
        )
        val watch = second.attempts.single().categories.single()
        assertEquals(DeviceSyncLogCategory.WATCH_HISTORY, watch.category)
        assertEquals(DeviceSyncLogStatus.FAILED, watch.status)
        assertEquals(7, watch.sentChanges)
        assertEquals(11, watch.receivedChanges)
        assertEquals("Connection closed", watch.error)

        val search = DeviceSyncIncomingLog.merge(
            second,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.SEARCH, 0, 1)
        )
        assertEquals(2, search.attempts.single().categories.size)
        assertNull(search.attempts.single().categories.last().error)
        assertEquals(0, entry.attempts.single().categories.size)
    }

    @Test
    fun aLaterSuccessfulBatchDoesNotEraseAnEarlierFailure() {
        val failed = DeviceSyncIncomingLog.merge(
            entry,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.WATCH, 0, 0, "Interrupted")
        )
        val next = DeviceSyncIncomingLog.merge(
            failed,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.WATCH, 1, 2)
        )
        val watch = next.attempts.single().categories.single()
        assertEquals(DeviceSyncLogStatus.FAILED, watch.status)
        assertEquals("Interrupted", watch.error)
        assertEquals(1, watch.sentChanges)
        assertEquals(2, watch.receivedChanges)
    }

    @Test
    fun neverAttributesBatchToAnotherPeer() {
        val other = DeviceSyncIncomingLog.merge(
            entry,
            IncomingHistoryBatch("peer-2", HistorySyncCategory.WATCH, 1, 1)
        )
        assertEquals(entry, other)
    }
}
