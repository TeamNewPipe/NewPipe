/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.recommendations

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecommendationWorkDispatcherObservationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun relatedVideoObservationEventuallyAppearsInLocalRecommendations() {
        LocalRecommendations.configure(context, mapOf("enabled" to true), "")
        val related = StreamInfoItem(
            0,
            "https://example.org/async-related",
            "Async related video",
            StreamType.VIDEO_STREAM
        ).apply {
            uploaderName = "Test channel"
            duration = 120
        }

        RecommendationWorkDispatcher.observe(context, listOf(related))

        // A scheduled worker batches observations for 100 ms; wait for the actual model update,
        // rather than assuming a synchronous SharedPreferences write on the caller thread.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var observed = false
        while (System.nanoTime() < deadline) {
            observed = LocalRecommendations.feed(context, 0).any { it.url == related.url }
            if (observed) break
            Thread.sleep(20)
        }
        assertTrue("Related item should be persisted by the background worker", observed)
    }
}
