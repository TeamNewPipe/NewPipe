/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSelectionSaveGateTest {
    @Test
    fun queuedWritesFinishInOrderAndOnlyTheLatestMatchingSaveCanClearDirtyState() {
        PlaylistSelectionSaveGate().use { gate ->
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val releaseSecond = CountDownLatch(1)
            val firstIds = mutableListOf(1L, 2L, 1L)
            val secondIds = listOf(2L, 1L, 1L)
            try {
                val first = gate.submit(firstIds) {
                    firstStarted.countDown()
                    await(releaseFirst)
                }.test()
                await(firstStarted)
                firstIds.clear()
                val second = gate.submit(secondIds) {
                    secondStarted.countDown()
                    await(releaseSecond)
                }.test()
                assertTrue(gate.hasPendingWrites)
                assertEquals("The second write must wait for the first", 1L, secondStarted.count)
                releaseFirst.countDown()
                first.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
                val older = first.values().single()
                assertEquals(listOf(1L, 2L, 1L), older.streamIds)
                await(secondStarted)
                assertFalse("A finished save cannot enable selection while a newer write runs", gate.canClearDirty(older, older.streamIds))

                releaseSecond.countDown()
                second.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
                val latest = second.values().single()
                assertFalse(gate.hasPendingWrites)
                assertFalse(gate.canClearDirty(older, older.streamIds))
                assertTrue(gate.canClearDirty(latest, secondIds))
                assertFalse("An unsaved edit made after the latest submission must remain dirty", gate.canClearDirty(latest, listOf(1L, 1L, 2L)))
            } finally {
                releaseFirst.countDown()
                releaseSecond.countDown()
            }
        }
    }

    @Test
    fun aFailedWriteCannotMakeAnOlderSuccessfulOrderCurrentAgain() {
        PlaylistSelectionSaveGate().use { gate ->
            val ids = listOf(1L, 2L)
            val older = gate.submit(ids) {}.blockingGet()
            assertTrue(gate.canClearDirty(older, ids))

            gate.submit(ids) { throw IOException("Database write failed") }.test()
                .awaitDone(5, TimeUnit.SECONDS).assertError(IOException::class.java).assertNoValues()

            assertFalse(gate.hasPendingWrites)
            assertFalse(gate.canClearDirty(older, ids))
            val retry = gate.submit(ids) {}.blockingGet()
            assertTrue(gate.canClearDirty(retry, ids))
        }
    }

    @Test
    fun closingTheViewKeepsAlreadyQueuedWritesButCannotEnableSelectionAfterwards() {
        val gate = PlaylistSelectionSaveGate()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completedWrites = AtomicInteger()
        try {
            val first = gate.submit(listOf(1L)) {
                started.countDown()
                await(release)
                completedWrites.incrementAndGet()
            }.test()
            await(started)
            val second = gate.submit(listOf(2L)) { completedWrites.incrementAndGet() }.test()
            gate.close()
            assertTrue(gate.hasPendingWrites)
            gate.submit(listOf(3L)) { completedWrites.incrementAndGet() }.test()
                .assertError(IllegalStateException::class.java).assertNoValues()

            release.countDown()
            first.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
            second.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()

            assertEquals(2, completedWrites.get())
            assertFalse(gate.hasPendingWrites)
            val saved = second.values().single()
            assertFalse(gate.canClearDirty(saved, saved.streamIds))
        } finally {
            release.countDown()
            gate.close()
        }
    }

    @Test
    fun disposingTheUiObserverDoesNotCancelAnAcceptedWriteOrClearItsPendingStateEarly() {
        PlaylistSelectionSaveGate().use { gate ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finalStarted = CountDownLatch(1)
            val releaseFinal = CountDownLatch(1)
            val completedWrites = AtomicInteger()
            val finalCallback = AtomicBoolean()
            try {
                val disposed = gate.submit(listOf(1L)) {
                    started.countDown()
                    await(release)
                    completedWrites.incrementAndGet()
                }.test()
                await(started)
                disposed.dispose()
                assertTrue(gate.hasPendingWrites)
                val idle = gate.awaitIdle().doOnComplete {
                    assertTrue("Save completion must be delivered before idle wakes a waiting selection", finalCallback.get())
                }.test()
                idle.assertNotComplete()
                val after = gate.submit(listOf(2L)) {
                    assertEquals("Disposed observers must not interrupt persisted writes", 1, completedWrites.get())
                    finalStarted.countDown()
                    await(releaseFinal)
                }.doOnSuccess { finalCallback.set(true) }.test()
                release.countDown()
                await(finalStarted)
                assertTrue(gate.hasPendingWrites)
                idle.assertNotComplete()
                releaseFinal.countDown()
                after.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
                idle.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()

                disposed.assertNoValues().assertNotComplete()
                assertEquals(1, completedWrites.get())
                assertFalse(gate.hasPendingWrites)
                val saved = after.values().single()
                assertTrue(gate.canClearDirty(saved, saved.streamIds))
            } finally {
                release.countDown()
                releaseFinal.countDown()
            }
        }
    }

    private fun await(latch: CountDownLatch) {
        assertTrue("The controlled save must reach its expected stage", latch.await(5, TimeUnit.SECONDS))
    }
}
