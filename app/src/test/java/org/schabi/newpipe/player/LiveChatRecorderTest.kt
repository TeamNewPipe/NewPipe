package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChatRecorderTest {
    @Test
    fun offlineReplayShowsOnlyCapturedMessagesUpToChosenTimestamp() {
        val rows = listOf(
            LiveChatRecorder.Entry(0, "A", "first"),
            LiveChatRecorder.Entry(1_000, "B", "second"),
            LiveChatRecorder.Entry(2_000, "A", "third")
        )
        assertEquals(
            listOf("first", "second"),
            LiveChatRecorder.replayRows(rows, 1_500).map(LiveChatRecorder.Entry::text)
        )
        assertEquals(rows, LiveChatRecorder.replayRows(rows, 9_000))
    }

    @Test
    fun replayRetainsOnlyRecentRowsForUiResponsiveness() {
        val rows = (0 until 400).map { LiveChatRecorder.Entry(it.toLong(), "", "$it") }
        assertEquals(250, LiveChatRecorder.replayRows(rows, 400).size)
    }
}
