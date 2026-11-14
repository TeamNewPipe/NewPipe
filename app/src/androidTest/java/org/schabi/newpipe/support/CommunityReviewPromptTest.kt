package org.schabi.newpipe.support

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class CommunityReviewPromptTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("community_review_prompt", Context.MODE_PRIVATE)

    @Before
    @After
    fun clearState() {
        preferences.edit().clear().commit()
    }

    @Test
    fun promptHasFiveDayReminderAndDoesNotReturnOnActivityRecreation() {
        val shown = CountDownLatch(1)
        ActivityScenario.launch<AboutActivity>(Intent(context, AboutActivity::class.java).setAction(Intent.ACTION_MAIN)).use { scenario ->
            scenario.onActivity { activity ->
                val sessions = CommunityReviewSessions(CommunityReviews.state(context))
                repeat(14) {
                    sessions.onStart(activity)
                    sessions.onStop(activity)
                }
                sessions.onStart(activity)
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentResumed(manager: FragmentManager, fragment: Fragment) {
                            if (fragment is CommunityReviewDialogFragment) shown.countDown()
                        }
                    },
                    false
                )
                CommunityReviewPromptController(activity, sessions)
            }
            assertTrue("Review prompt should appear after enough app openings", shown.await(5, TimeUnit.SECONDS))
            scenario.recreate()
            val before = System.currentTimeMillis()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(CommunityReviewDialogFragment.TAG) as CommunityReviewDialogFragment
                val dialog = fragment.requireDialog() as AlertDialog
                assertEquals(activity.getString(R.string.community_review_prompt_later), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val state = CommunityReviews.state(context)
            assertTrue(state.reminderAt >= before + CommunityReviewPromptState.FIVE_DAYS_MS)
            assertTrue(state.reminderAt <= System.currentTimeMillis() + CommunityReviewPromptState.FIVE_DAYS_MS)
            assertFalse(state.shouldPrompt(state.reminderAt - 1))
            assertTrue(state.shouldPrompt(state.reminderAt))
        }
    }

    @Test
    fun optOutAndCancelHaveDifferentPersistentOutcomes() {
        val state = CommunityReviews.state(context)
        repeat(15) { state.recordOpening() }
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                CommunityReviewDialogFragment().showNow(activity.supportFragmentManager, CommunityReviewDialogFragment.TAG)
                val fragment = activity.supportFragmentManager.findFragmentByTag(CommunityReviewDialogFragment.TAG) as CommunityReviewDialogFragment
                (fragment.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(state.shouldPrompt(Long.MAX_VALUE))
            clearState()
            repeat(15) { state.recordOpening() }
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                CommunityReviewDialogFragment().showNow(activity.supportFragmentManager, CommunityReviewDialogFragment.TAG)
                val fragment = activity.supportFragmentManager.findFragmentByTag(CommunityReviewDialogFragment.TAG) as CommunityReviewDialogFragment
                fragment.requireDialog().cancel()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(state.reminderAt > System.currentTimeMillis())
            assertFalse(state.shouldPrompt(System.currentTimeMillis()))
            assertTrue(state.shouldPrompt(state.reminderAt))
        }
    }

    @Test
    fun aboutScreenShowsConfiguredReviewAction() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            // ViewPager attaches its initial fragment after the activity's first layout pass.
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var found = false
            while (!found && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    val section = activity.findViewById<View>(R.id.about_review_section)
                    found = section?.visibility == View.VISIBLE
                    if (found) assertNotNull(activity.findViewById<View>(R.id.about_review_link))
                }
                if (!found) SystemClock.sleep(25)
            }
            assertTrue("About should expose the community review link", found)
            assertEquals("https://wizestream.org/reviews/", CommunityReviews.destination(context))
        }
    }
}
