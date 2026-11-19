package org.schabi.newpipe.recommendations

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.views.NewPipeRecyclerView

class RecommendationsFragmentTest {
    @Test
    fun visibleFeedClearsImmediatelyWhenIncognitoIsEnabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val originalProfile = ProfileManager.getActiveProfileId(context)
        val originalIncognito = IncognitoMode.isEnabled(context)
        val profile = ProfileManager.createProfile(context, "Recommendation test ${System.nanoTime()}")!!
        ProfileManager.setActiveProfile(context, profile.id)
        IncognitoMode.setEnabled(context, false)
        try {
            LocalRecommendations.configure(context, mapOf("enabled" to true), "")
            LocalRecommendations.observe(context, listOf(StreamInfoItem(ServiceHelper.getSelectedServiceId(context), "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Sample video", StreamType.VIDEO_STREAM)))
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = RecommendationsFragment()
                    activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commitNow()
                    val list = fragment.requireView().findViewById<NewPipeRecyclerView>(R.id.items_list)
                    assertTrue(list.adapter != null)
                }
                val loaded = AtomicBoolean()
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (!loaded.get() && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { activity ->
                        val updated = activity.findViewById<NewPipeRecyclerView>(R.id.items_list)
                        loaded.set((updated.adapter?.itemCount ?: 0) > 0)
                    }
                    if (!loaded.get()) SystemClock.sleep(25)
                }
                assertTrue("For You should populate asynchronously", loaded.get())
                scenario.onActivity { activity ->
                    IncognitoMode.setEnabled(activity, true)
                    val updated = activity.findViewById<NewPipeRecyclerView>(R.id.items_list)
                    assertEquals(0, updated.adapter!!.itemCount)
                }
            }
        } finally {
            LocalRecommendations.reset(context)
            ProfileManager.setActiveProfile(context, originalProfile)
            ProfileManager.deleteProfile(context, profile.id)
            IncognitoMode.setEnabled(context, originalIncognito)
        }
    }
}
