/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.recommendations

import android.app.Application
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RelevanceRankingConcurrencyTest {
    @Test
    fun readingRankingDoesNotWaitForAnUnrelatedTasteUpdate() {
        val lockHeld = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val monitor = Thread {
            synchronized(LocalRecommendations) {
                lockHeld.countDown()
                releaseLock.await(5, TimeUnit.SECONDS)
            }
        }
        val context = RuntimeEnvironment.getApplication()
        monitor.start()
        try {
            assertTrue(lockHeld.await(2, TimeUnit.SECONDS))
            Thread {
                try {
                    LocalRecommendations.relevanceOrder(context, emptyList())
                    finished.countDown()
                } catch (error: Throwable) {
                    // Let the test fail rather than hanging if ranking throws.
                }
            }.start()
            assertTrue(
                "Read-only relevance ranking should not wait for recommendation writes",
                finished.await(2, TimeUnit.SECONDS)
            )
        } finally {
            releaseLock.countDown()
            monitor.join(5_000)
        }
    }
}
