package org.schabi.newpipe.local.playlist

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.xmlpull.v1.XmlPullParser

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
    fun compactLocalLikeUsesSmallerIconWithoutLosingButtonWidth() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controls = LocalLikesView(activity)
                assertEquals(1, controls.childCount)
                val like = controls.getChildAt(0) as MaterialButton
                val density = activity.resources.displayMetrics.density
                val touchSize = (48 * density + 0.5f).toInt()

                assertEquals(LinearLayout.HORIZONTAL, controls.orientation)
                assertEquals(0, controls.paddingStart)
                assertEquals(0, controls.paddingEnd)
                assertEquals(touchSize, like.layoutParams.width)
                val visualHeight = (24 * density + 0.5f).toInt()
                val iconSize = (20 * density + 0.5f).toInt()
                assertEquals(visualHeight, like.layoutParams.height)
                assertTrue(like.minimumHeight >= visualHeight)
                assertTrue(like.minimumWidth >= touchSize)
                assertEquals(iconSize, like.iconSize)
                assertTrue(like.text.isEmpty())
                assertTrue(like.icon != null)
                assertTrue(like.contentDescription.toString().contains("locally", ignoreCase = true))

                controls.layout(0, 0, (320 * density).toInt(), (64 * density).toInt())
                assertEquals(LinearLayout.HORIZONTAL, controls.orientation)
                assertEquals(1, controls.childCount)
            }
        }
    }

    @Test
    fun localLikeIsInsideTheExistingPublicLikesRowOnlyOnce() {
        context.resources.getXml(R.layout.fragment_video_detail).use { parser ->
            val ancestors = mutableListOf<Int>()
            var localLikeCount = 0
            var publicLikeFollows = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val id = parser.getAttributeResourceValue(
                            "http://schemas.android.com/apk/res/android",
                            "id",
                            0
                        )
                        if (id == R.id.detail_local_likes) {
                            assertEquals(R.id.detail_likes_row, ancestors.lastOrNull())
                            assertTrue(ancestors.contains(R.id.details_panel))
                            localLikeCount++
                        }
                        if (id == R.id.detail_thumbs_up_img_view) {
                            assertEquals(R.id.detail_likes_row, ancestors.lastOrNull())
                            publicLikeFollows = localLikeCount == 1
                        }
                        ancestors.add(id)
                    }

                    XmlPullParser.END_TAG -> ancestors.removeAt(ancestors.lastIndex)
                }
                parser.next()
            }
            assertEquals(1, localLikeCount)
            assertTrue("Public like count must follow the local heart in one row", publicLikeFollows)
        }
    }

    @Test
    fun compactRowStillRespondsToTapAboveItWithin48DpHitArea() {
        lateinit var panel: RelativeLayout
        lateinit var controls: LocalLikesView
        lateinit var like: MaterialButton
        lateinit var row: LinearLayout
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density + 0.5f).toInt()

                panel = RelativeLayout(activity).apply {
                    id = R.id.details_panel
                    minimumHeight = dp(48)
                }
                val viewCount = View(activity).apply { id = R.id.detail_view_count_view }
                panel.addView(viewCount, RelativeLayout.LayoutParams(dp(160), dp(28)))
                row = LinearLayout(activity).apply {
                    id = R.id.detail_likes_row
                    orientation = LinearLayout.HORIZONTAL
                }
                panel.addView(
                    row,
                    RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { addRule(RelativeLayout.BELOW, viewCount.id) }
                )
                controls = LocalLikesView(activity)
                like = controls.getChildAt(0) as MaterialButton
                row.addView(controls, LinearLayout.LayoutParams(dp(48), dp(24)))
                activity.setContentView(panel)
                controls.bind(
                    StreamInfo(
                        0,
                        "test",
                        "https://example.com/like-expanded-hit",
                        "Video"
                    ).apply { streamType = StreamType.VIDEO_STREAM }
                )
            }

            await(scenario) { like.isEnabled && panel.touchDelegate != null }
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val dp = { n: Int -> (n * density + 0.5f).toInt() }
                assertEquals(dp(24), row.height)
                assertTrue(panel.height >= dp(48))
                val bounds = Rect(0, 0, controls.width, controls.height)
                panel.offsetDescendantRectToMyCoords(controls, bounds)
                val aboveRow = bounds.top - dp(6)
                assertTrue(aboveRow > 0)
                val x = bounds.centerX().toFloat()
                val y = aboveRow.toFloat()
                val eventTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(eventTime, eventTime + 50, MotionEvent.ACTION_UP, x, y, 0)
                try {
                    assertTrue(panel.dispatchTouchEvent(down))
                    panel.dispatchTouchEvent(up)
                } finally {
                    down.recycle()
                    up.recycle()
                }
            }
            await(scenario, timeoutMillis = 12_000) { like.isEnabled && like.isChecked }
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
            await(scenario, timeoutMillis = 12_000) { like.isEnabled && like.isChecked }
            scenario.onActivity { like.performClick() }
            await(scenario, timeoutMillis = 12_000) { like.isEnabled && !like.isChecked }
            scenario.onActivity {
                controls.bind(null)
                assertEquals(View.GONE, controls.visibility)
            }
            assertFalse(LikedVideosStore(database, preferences, profileId, "Liked videos").observeLiked(0, "https://example.com/local-like-ui").blockingFirst())
            scenario.onActivity { assertEquals(View.GONE, controls.visibility) }
        }
    }

    @Test
    fun switchingBetweenVideosDoesNotShowThePreviousVideosLocalLike() {
        lateinit var controls: LocalLikesView
        lateinit var like: MaterialButton
        val firstUrl = "https://example.com/like-navigation-first"
        val secondUrl = "https://example.com/like-navigation-second"
        val first = StreamInfo(0, "test", firstUrl, "First").apply {
            streamType = StreamType.VIDEO_STREAM
        }
        val second = StreamInfo(0, "test", secondUrl, "Second").apply {
            streamType = StreamType.VIDEO_STREAM
        }

        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                controls = LocalLikesView(activity)
                like = controls.getChildAt(0) as MaterialButton
                activity.setContentView(controls)
                controls.bind(first)
            }
            await(scenario) { like.isEnabled }
            scenario.onActivity { like.performClick() }
            await(scenario, timeoutMillis = 12_000) { like.isEnabled && like.isChecked }

            scenario.onActivity { controls.bind(second) }
            await(scenario) { like.isEnabled }
            scenario.onActivity { assertFalse(like.isChecked) }

            // Navigation changes the visible video's like state but must not remove saved likes.
            assertTrue(
                LikedVideosStore(database, preferences, profileId, "Liked videos")
                    .observeLiked(0, firstUrl).blockingFirst()
            )
            scenario.onActivity {
                controls.bind(null)
                assertEquals(View.GONE, controls.visibility)
            }
        }
    }

    @Test
    fun likedVideosPlaylistRemainsInTheLibraryWithoutASecondButton() {
        lateinit var controls: LocalLikesView
        lateinit var like: MaterialButton
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                controls = LocalLikesView(activity)
                like = controls.getChildAt(0) as MaterialButton
                activity.setContentView(FrameLayout(activity).apply { addView(controls) })
                controls.bind(
                    StreamInfo(0, "test", "https://example.com/local-like-library", "Video").apply {
                        streamType = StreamType.VIDEO_STREAM
                    }
                )
                assertEquals(1, controls.childCount)
            }
            await(scenario) { like.isEnabled }
            scenario.onActivity { like.performClick() }
            await(scenario, timeoutMillis = 12_000) { like.isEnabled && like.isChecked }

            val linkedId = (preferences.all[LikedVideosStore.preferenceKey(profileId)] as? Number)
                ?.toLong() ?: error("Liked videos playlist link not created")
            val playlists = database.playlistDAO().getAllDirectForProfile(profileId)
            assertTrue(playlists.any { it.uid == linkedId })
            assertEquals(
                context.getString(R.string.local_liked_videos),
                playlists.first { it.uid == linkedId }.name
            )
            assertEquals(1, database.playlistStreamDAO().getQuickAddEntries(linkedId).size)
            scenario.onActivity {
                assertEquals(1, controls.childCount)
                assertTrue(like.contentDescription.toString().contains("local", ignoreCase = true))
            }
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

    private fun await(
        scenario: ActivityScenario<AboutActivity>,
        timeoutMillis: Long = 5_000,
        condition: () -> Boolean
    ) {
        val ready = AtomicBoolean()
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (!ready.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { ready.set(condition()) }
            if (!ready.get()) SystemClock.sleep(25)
        }
        assertTrue("Local like controls did not reach the expected state", ready.get())
    }
}
