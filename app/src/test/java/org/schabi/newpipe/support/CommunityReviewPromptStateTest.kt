package org.schabi.newpipe.support

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CommunityReviewPromptStateTest {
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("review-prompt-test", Context.MODE_PRIVATE)
    private val owner = mock(LifecycleOwner::class.java)

    @Before
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun waitsForFifteenDistinctForegroundOpenings() {
        val state = CommunityReviewPromptState(preferences)
        val sessions = CommunityReviewSessions(state)
        repeat(14) {
            sessions.onStart(owner)
            sessions.onStart(owner)
            assertFalse(state.shouldPrompt(1_000))
            sessions.onStop(owner)
        }
        sessions.onStart(owner)
        assertTrue(state.shouldPrompt(1_000))
        assertTrue(sessions.claimAttempt())
        assertFalse(sessions.claimAttempt())
        sessions.onStop(owner)
        sessions.onStart(owner)
        assertTrue(sessions.claimAttempt())
    }

    @Test
    fun reminderWaitsFiveDaysRegardlessOfAdditionalOpeningsAndSurvivesRestart() {
        val state = CommunityReviewPromptState(preferences)
        repeat(15) { state.recordOpening() }
        val now = 1_000_000L
        state.remindLater(now)
        repeat(100) { state.recordOpening() }
        val restored = CommunityReviewPromptState(preferences)
        assertFalse(restored.shouldPrompt(now + CommunityReviewPromptState.FIVE_DAYS_MS - 1))
        assertTrue(restored.shouldPrompt(now + CommunityReviewPromptState.FIVE_DAYS_MS))
        restored.remindLater(now + CommunityReviewPromptState.FIVE_DAYS_MS)
        assertFalse(restored.shouldPrompt(now + 2 * CommunityReviewPromptState.FIVE_DAYS_MS - 1))
        assertTrue(restored.shouldPrompt(now + 2 * CommunityReviewPromptState.FIVE_DAYS_MS))
    }

    @Test
    fun reviewRequestOpensTheConfiguredPageWithoutIdentifiersAndStopsAsking() {
        val context = RuntimeEnvironment.getApplication()
        val local = context.getSharedPreferences("community_review_prompt", Context.MODE_PRIVATE)
        local.edit().clear().commit()
        try {
            val state = CommunityReviews.state(context)
            repeat(15) { state.recordOpening() }
            CommunityReviews.openPage(context)
            val intent = shadowOf(context).nextStartedActivity
            assertEquals("https://wizestream.org/reviews/", intent.data.toString())
            assertEquals(null, intent.data?.query)
            assertEquals(null, intent.data?.fragment)
            assertFalse(state.shouldPrompt(Long.MAX_VALUE))
        } finally {
            local.edit().clear().commit()
        }
    }

    @Test
    fun explicitOptOutOrReviewPageRequestStopsFurtherPrompts() {
        val state = CommunityReviewPromptState(preferences)
        repeat(15) { state.recordOpening() }
        state.stopAsking()
        repeat(100) { state.recordOpening() }
        assertFalse(CommunityReviewPromptState(preferences).shouldPrompt(Long.MAX_VALUE))
    }
}
