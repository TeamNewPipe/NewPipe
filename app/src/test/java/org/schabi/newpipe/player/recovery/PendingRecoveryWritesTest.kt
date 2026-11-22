package org.schabi.newpipe.player.recovery

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRecoveryWritesTest {
    private fun snapshot(session: String, position: Long, time: Long): QueueSnapshot {
        return QueueSnapshot(session, time, emptyList(), 0, position)
    }

    @Test
    fun keepsOnlyNewestPositionForSameSession() {
        val pending = PendingRecoveryWrites()
        val outcomes = mutableListOf<Boolean>()
        pending.offer(
            PendingRecoveryWrites.Write("profile", snapshot("one", 10, 1), AtomicBoolean(true), 1) {
                outcomes.add(it)
            }
        )
        pending.offer(
            PendingRecoveryWrites.Write("profile", snapshot("one", 20, 2), AtomicBoolean(true), 1) {
                outcomes.add(it)
            }
        )
        assertEquals(listOf(false), outcomes)
        assertEquals(20L, pending.poll()?.snapshot?.position)
        assertEquals(null, pending.poll())
    }

    @Test
    fun neverOverwritesNewerPendingPositionWithStaleSnapshot() {
        val pending = PendingRecoveryWrites()
        pending.offer(
            PendingRecoveryWrites.Write("profile", snapshot("one", 50, 5), AtomicBoolean(true), 1) {}
        )
        var rejected = false
        pending.offer(
            PendingRecoveryWrites.Write("profile", snapshot("one", 10, 1), AtomicBoolean(true), 1) {
                rejected = !it
            }
        )
        assertTrue(rejected)
        assertEquals(50L, pending.poll()?.snapshot?.position)
    }

    @Test
    fun preservesSeparatePlaybackSessions() {
        val pending = PendingRecoveryWrites()
        pending.offer(
            PendingRecoveryWrites.Write("profile", snapshot("one", 30, 1), AtomicBoolean(true), 1) {}
        )
        pending.offer(
            PendingRecoveryWrites.Write("profile", snapshot("two", 5, 2), AtomicBoolean(true), 1) {}
        )
        assertNotNull(pending.poll())
        assertNotNull(pending.poll())
        assertEquals(null, pending.poll())
    }

    @Test
    fun boundsPendingWorkDuringWriterStall() {
        val pending = PendingRecoveryWrites()
        var discarded = 0
        repeat(20) { index ->
            pending.offer(
                PendingRecoveryWrites.Write(
                    "profile",
                    snapshot("session-$index", index.toLong(), index.toLong()),
                    AtomicBoolean(true),
                    1
                ) { committed ->
                    if (!committed) discarded++
                }
            )
        }
        var retained = 0
        while (pending.poll() != null) retained++
        assertEquals(8, retained)
        assertEquals(12, discarded)
    }
}
