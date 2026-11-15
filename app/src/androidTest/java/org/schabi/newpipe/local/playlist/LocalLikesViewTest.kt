package org.schabi.newpipe.local.playlist

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class LocalLikesViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val profileId = ProfileManager.getActiveProfileId(context)
    private val database = TestDatabase.createReplacingNewPipeDatabase()

    @After
    fun removeTestPlaylist() {
        val id = (preferences.all[LikedVideosStore.preferenceKey(profileId)] as? Number)?.toLong()
        if (id != null) database.playlistDAO().deletePlaylistForProfile(profileId, id)
        preferences.edit()
            .remove(LikedVideosStore.preferenceKey(profileId))
            .remove(context.getString(R.string.show_local_like_controls_key))
            .commit()
        NewPipeDatabase.close()
    }

    @Test
    fun buttonsStayCompactWhileKeepingAccessibleTouchHeight() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controls = LocalLikesView(activity)
                val like = controls.getChildAt(0) as MaterialButton
                val library = controls.getChildAt(1) as MaterialButton
                val density = activity.resources.displayMetrics.density

                controls.layout(0, 0, (420 * density).toInt(), (64 * density).toInt())

                assertEquals(LinearLayout.HORIZONTAL, controls.orientation)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, like.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, library.layoutParams.width)
                assertTrue(like.minimumHeight >= (48 * density).toInt())
                assertTrue(library.minimumHeight >= (48 * density).toInt())
                assertTrue((like.layoutParams as LinearLayout.LayoutParams).marginEnd > 0)

                controls.layout(0, 0, (320 * density).toInt(), (128 * density).toInt())

                assertEquals(LinearLayout.VERTICAL, controls.orientation)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, like.layoutParams.width)
                assertTrue((like.layoutParams as LinearLayout.LayoutParams).bottomMargin > 0)
            }
        }
    }

    @Test
    fun tapLikesAndUnlikesLocallyAndClearingTheVideoHidesOldState() {
        lateinit var controls: LocalLikesView
        lateinit var like: MaterialButton
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                controls = LocalLikesView(activity)
                like = controls.getChildAt(0) as MaterialButton
                val root = FrameLayout(activity).apply { addView(controls) }
                activity.setContentView(root)
                controls.bind(StreamInfo(0, "test", "https://example.com/local-like-ui", "Video").apply { streamType = StreamType.VIDEO_STREAM })
            }
            await(scenario) { like.isEnabled }
            scenario.onActivity {
                assertFalse(like.isChecked)
                like.performClick()
            }
            await(scenario) { like.isEnabled && like.isChecked }
            scenario.onActivity { like.performClick() }
            await(scenario) { like.isEnabled && !like.isChecked }
            scenario.onActivity {
                controls.bind(null)
                assertEquals(View.GONE, controls.visibility)
            }
            assertFalse(LikedVideosStore(database, preferences, profileId, "Liked videos").observeLiked(0, "https://example.com/local-like-ui").blockingFirst())
            scenario.onActivity { assertEquals(View.GONE, controls.visibility) }
        }
    }

    @Test
    fun libraryShortcutOpensPlaylistAndCollapsesTheDetailSheet() {
        lateinit var host: LikesHostFragment
        lateinit var behavior: BottomSheetBehavior<FrameLayout>
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = CoordinatorLayout(activity)
                val destination = FrameLayout(activity).apply { id = R.id.fragment_holder }
                root.addView(destination, CoordinatorLayout.LayoutParams(-1, -1))
                val player = FrameLayout(activity).apply { id = R.id.fragment_player_holder }
                behavior = BottomSheetBehavior<FrameLayout>().apply { peekHeight = 64 }
                root.addView(player, CoordinatorLayout.LayoutParams(-1, -1).apply { this.behavior = behavior })
                activity.setContentView(root)
                host = LikesHostFragment()
                activity.supportFragmentManager.beginTransaction().replace(player.id, host).commitNow()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            await(scenario) { (host.controls.getChildAt(1) as MaterialButton).isEnabled }
            scenario.onActivity { (host.controls.getChildAt(1) as MaterialButton).performClick() }
            await(scenario) {
                host.requireActivity().supportFragmentManager.findFragmentById(R.id.fragment_holder) is LocalPlaylistFragment &&
                    behavior.state == BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    class LikesHostFragment : Fragment() {
        lateinit var controls: LocalLikesView

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            controls = LocalLikesView(requireContext())
            controls.bind(StreamInfo(0, "test", "https://example.com/liked-library-ui", "Video").apply { streamType = StreamType.VIDEO_STREAM })
            return controls
        }
    }

    @Test
    fun appearancePreferenceHidesAndRestoresControls() {
        lateinit var controls: LocalLikesView
        val settingKey = context.getString(R.string.show_local_like_controls_key)
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                preferences.edit().putBoolean(settingKey, false).commit()
                controls = LocalLikesView(activity)
                activity.setContentView(controls)
                controls.bind(
                    StreamInfo(
                        0,
                        "test",
                        "https://example.com/local-like-visibility",
                        "Video"
                    ).apply {
                        streamType = StreamType.VIDEO_STREAM
                    }
                )
                assertEquals(View.GONE, controls.visibility)

                preferences.edit().putBoolean(settingKey, true).commit()
                assertEquals(View.VISIBLE, controls.visibility)

                preferences.edit().putBoolean(settingKey, false).commit()
                assertEquals(View.GONE, controls.visibility)
            }
        }
    }

    @Test
    fun audioOnlyItemsDoNotExposeVideoLikeControls() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controls = LocalLikesView(activity)
                activity.setContentView(controls)
                controls.bind(StreamInfo(0, "test", "https://example.com/audio", "Audio").apply { streamType = StreamType.AUDIO_STREAM })
                assertEquals(View.GONE, controls.visibility)
            }
        }
    }

    private fun await(scenario: ActivityScenario<AboutActivity>, condition: () -> Boolean) {
        val ready = AtomicBoolean()
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!ready.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { ready.set(condition()) }
            if (!ready.get()) SystemClock.sleep(25)
        }
        assertTrue("Local like controls did not reach the expected state", ready.get())
    }
}
