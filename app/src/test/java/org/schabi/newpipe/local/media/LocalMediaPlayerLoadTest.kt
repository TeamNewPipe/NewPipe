/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.local.media

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaPlayerLoadTest {
    @Test fun refreshDuringReadOrBeforeDeliveryRetriesAndDeliversCurrentMetadata() {
        for (refreshDuringRead in listOf(true, false)) {
            val work = ArrayDeque<Runnable>()
            val delivery = ArrayDeque<Runnable>()
            var generation = 0L
            val results = mutableListOf<String>()
            val request = loadLocalMediaWithRefresh(
                Executor { work.add(it) },
                { delivery.add(it) },
                { generation },
                { current ->
                    if (current == 0L && refreshDuringRead) generation++
                    "metadata-$current"
                },
                { results.add(it) }
            )

            work.remove().run()
            if (!refreshDuringRead) generation++
            delivery.remove().run()
            assertTrue(results.isEmpty())
            assertFalse(request.isDone)
            work.remove().run()
            delivery.remove().run()
            assertEquals(listOf("metadata-1"), results)
            assertTrue(request.isDone)
        }
    }

    @Test fun refreshBeforeMainThreadDeliveryRetriesAndCancellationStillApplies() {
        val work = ArrayDeque<Runnable>()
        val delivery = ArrayDeque<Runnable>()
        var generation = 0L
        var reads = 0
        val results = mutableListOf<Long>()
        val request = loadLocalMediaWithRefresh(
            Executor { work.add(it) },
            { delivery.add(it) },
            { generation },
            {
                reads++
                it
            },
            { results.add(it) }
        )

        work.remove().run()
        generation++
        delivery.remove().run()
        assertTrue(results.isEmpty())
        assertTrue(request.cancel(true))
        work.remove().run()
        assertEquals(1, reads)
        assertTrue(results.isEmpty())
        assertTrue(delivery.isEmpty())
        assertTrue(request.isCancelled)
    }

    @Test fun completedWorkerRemainsCancellableUntilItsCallbackRuns() {
        val delivery = ArrayDeque<Runnable>()
        val results = mutableListOf<String>()
        val request = loadLocalMediaWithRefresh(
            Executor { it.run() },
            { delivery.add(it) },
            { 0L },
            { "artwork" },
            { results.add(it) }
        )

        assertFalse(request.isDone)
        assertTrue(request.cancel(true))
        delivery.remove().run()
        assertTrue(results.isEmpty())
    }

    @Test fun repeatedProviderChangesDoNotStarvePlayerPresentationOrRetryIndefinitely() {
        val work = ArrayDeque<Runnable>()
        val delivery = ArrayDeque<Runnable>()
        var generation = 0L
        var reads = 0
        val results = mutableListOf<Long>()
        val request = loadLocalMediaWithRefresh(
            Executor { work.add(it) },
            { delivery.add(it) },
            { generation },
            {
                reads++
                ++generation
            },
            { results.add(it) }
        )

        repeat(2) {
            work.remove().run()
            delivery.remove().run()
        }
        assertEquals(2, reads)
        assertEquals(listOf(2L), results)
        assertTrue(request.isDone)
        assertTrue(work.isEmpty())
        assertTrue(delivery.isEmpty())
    }

    @Test fun cancellingAnActiveRequestInterruptsItsWorker() {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        try {
            val request = loadLocalMediaWithRefresh(
                executor,
                { error("A cancelled read must not reach delivery") },
                { 0L },
                {
                    started.countDown()
                    try {
                        CountDownLatch(1).await()
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                },
                { error("A cancelled request must not publish metadata") }
            )
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(request.cancel(true))
            assertTrue(interrupted.await(5, TimeUnit.SECONDS))
            assertTrue(request.isCancelled)
        } finally {
            executor.shutdownNow()
        }
    }
}
