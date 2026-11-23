/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @Test
    fun sumsOnlyMeasuredBatchesWithinTheirCategoryAndKeepsFailureDetails() {
        val first = DeviceSyncIncomingLog.merge(
            entry,
            IncomingHistoryBatch(
                "peer-1",
                HistorySyncCategory.WATCH,
                0,
                8,
                performance = SyncPerformanceSample(500, 720)
            )
        )
        val search = DeviceSyncIncomingLog.merge(
            first,
            IncomingHistoryBatch(
                "peer-1",
                HistorySyncCategory.SEARCH,
                1,
                2,
                performance = SyncPerformanceSample(40, 10)
            )
        )
        val failed = DeviceSyncIncomingLog.merge(
            search,
            IncomingHistoryBatch(
                "peer-1",
                HistorySyncCategory.WATCH,
                0,
                3,
                "Interrupted",
                SyncPerformanceSample(100, 30)
            )
        )
        val unmeasured = DeviceSyncIncomingLog.merge(
            failed,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.WATCH, 0, 2)
        )
        val categories = unmeasured.attempts.single().categories.associateBy { it.category }
        val watch = requireNotNull(categories[DeviceSyncLogCategory.WATCH_HISTORY])
        assertEquals(SyncPerformanceSample(600, 750, 2), watch.incomingPerformance)
        assertEquals(
            "Receiver batches 2: Elapsed 600 ms; app-process CPU 750 ms",
            watch.retryDiagnostic
        )
        assertEquals(13, watch.receivedChanges)
        assertEquals(DeviceSyncLogStatus.FAILED, watch.status)
        assertEquals("Interrupted", watch.error)
        assertEquals(
            SyncPerformanceSample(40, 10),
            categories[DeviceSyncLogCategory.SEARCH_HISTORY]?.incomingPerformance
        )
        assertEquals(
            unmeasured,
            DeviceSyncIncomingLog.merge(
                unmeasured,
                IncomingHistoryBatch(
                    "peer-2",
                    HistorySyncCategory.WATCH,
                    1,
                    1,
                    performance = SyncPerformanceSample(900, 900)
                )
            )
        )
    }

    @Test
    fun oldLogsAndDisabledDiagnosticsKeepNoPerformanceSample() {
        val oldCategory = Json.decodeFromString<DeviceSyncLogCategoryResult>(
            """{"category":"WATCH_HISTORY","status":"SUCCEEDED","receivedChanges":8}"""
        )
        assertNull(oldCategory.incomingPerformance)
        val merged = DeviceSyncIncomingLog.merge(
            entry,
            IncomingHistoryBatch("peer-1", HistorySyncCategory.WATCH, 0, 8)
        )
        val category = merged.attempts.single().categories.single()
        assertNull(category.incomingPerformance)
        assertNull(category.retryDiagnostic)
        assertEquals(oldCategory, category)
    }

    @Test
    fun measuredCategoriesCanBeRestoredAndContinueAccumulating() {
        val measured = DeviceSyncIncomingLog.merge(
            entry,
            IncomingHistoryBatch(
                "peer-1",
                HistorySyncCategory.LEARNING_NOTES,
                1,
                2,
                performance = SyncPerformanceSample(30, 20)
            )
        )
        val restored = Json.decodeFromString<DeviceSyncLogEntry>(Json.encodeToString(measured))
        val next = DeviceSyncIncomingLog.merge(
            restored,
            IncomingHistoryBatch(
                "peer-1",
                HistorySyncCategory.LEARNING_NOTES,
                1,
                0,
                performance = SyncPerformanceSample(10, 5)
            )
        )
        assertEquals(
            SyncPerformanceSample(40, 25, 2),
            next.attempts.single().categories.single().incomingPerformance
        )
    }
}
