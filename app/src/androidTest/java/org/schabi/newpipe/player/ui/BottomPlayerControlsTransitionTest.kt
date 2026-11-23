package org.schabi.newpipe.player.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate

class BottomPlayerControlsTransitionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun completedMoreAnimationCannotMoveActionsOverTheSeekTouchTarget() = withPlayer { scenario, fixture ->
        val closed = CountDownLatch(1)
        scenario.onActivity {
            fixture.binding.secondaryControls.animate(
                false,
                0,
                AnimationType.SLIDE_AND_ALPHA,
                0,
                Runnable {
                    fixture.binding.secondaryControls.visibility = View.INVISIBLE
                    closed.countDown()
                }
            )
        }
        assertTrue("More animation must complete", closed.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            assertTrue("Exercise the completed translated state", fixture.binding.secondaryControls.translationY < 0f)
            repeat(3) {
                fixture.enterAndSeek()
                fixture.controller.restore()
                assertEquals(View.INVISIBLE, fixture.binding.secondaryControls.visibility)
                assertEquals(0f, fixture.binding.secondaryControls.translationY, 0f)
                assertEquals(0f, fixture.binding.moreOptionsButton.rotation, 0f)
            }
        }
    }

    @Test
    fun movingControlsDuringAnimationDetachesItsStaleEndCallback() = withPlayer { scenario, fixture ->
        val moved = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        var staleEnds = 0
        scenario.onActivity {
            val row = fixture.binding.secondaryControls
            row.translationY = -row.height / 2f
            row.alpha = 0.5f
            row.animate().translationY(-row.height.toFloat()).alpha(0f)
                .setDuration(200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        try {
                            // Reparent while the Android animator owns the row, not after a
                            // manually invoked end callback or a guessed sleep interval.
                            fixture.enterAndSeek()
                        } catch (error: Throwable) {
                            failure.set(error)
                        } finally {
                            moved.countDown()
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        staleEnds++
                        row.visibility = View.INVISIBLE
                    }
                }).start()
        }
        assertTrue("Animation must reach the layout transition", moved.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
        scenario.onActivity {
            assertEquals("The old layout no longer owns the row", 0, staleEnds)
            assertEquals(View.VISIBLE, fixture.binding.secondaryControls.visibility)
            assertEquals(1f, fixture.binding.secondaryControls.alpha, 0f)
            fixture.controller.restore()
            assertEquals(View.VISIBLE, fixture.binding.secondaryControls.visibility)
            assertEquals(1f, fixture.binding.secondaryControls.alpha, 0f)
            assertEquals(0f, fixture.binding.secondaryControls.translationY, 0f)
            assertEquals(180f, fixture.binding.moreOptionsButton.rotation, 0f)
        }
    }

    private fun withPlayer(block: (ActivityScenario<AboutActivity>, Fixture) -> Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.bottom_player_controls_key)
        val old = prefs.all[key] as? Boolean
        prefs.edit().putBoolean(key, true).commit()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var fixture: Fixture
                scenario.onActivity { activity ->
                    val themed = ContextThemeWrapper(activity, R.style.DarkTheme)
                    val binding = PlayerBinding.inflate(LayoutInflater.from(themed))
                    activity.setContentView(binding.root)
                    binding.loadingPanel.visibility = View.GONE
                    binding.playbackControlRoot.visibility = View.VISIBLE
                    binding.secondaryControls.visibility = View.VISIBLE
                    binding.metadataView.visibility = View.VISIBLE
                    binding.titleTextView.text = "Portrait playback"
                    binding.qualityTextView.text = "1080p"
                    binding.resizeTextView.text = "FIT"
                    binding.playbackCurrentTime.text = "0:00"
                    binding.playbackEndTime.text = "10:00"
                    fixture = Fixture(binding)
                    fixture.measure()
                }
                try {
                    block(scenario, fixture)
                } finally {
                    scenario.onActivity {
                        fixture.binding.secondaryControls.animate().setListener(null).cancel()
                        fixture.controller.restore()
                    }
                }
            }
        } finally {
            prefs.edit().apply { old?.let { putBoolean(key, it) } ?: remove(key) }.commit()
        }
    }

    private class Fixture(val binding: PlayerBinding) {
        val controller = BottomPlayerControls(binding)

        fun measure() {
            val density = binding.root.resources.displayMetrics.density
            val width = (360 * density).toInt()
            val height = (800 * density).toInt()
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            binding.root.layout(0, 0, width, height)
        }

        fun enterAndSeek() {
            assertTrue(controller.update(true, 12, 24, 12, 24))
            measure()
            assertEquals(0f, binding.secondaryControls.translationY, 0f)
            val seek = binding.playbackSeekBar
            seek.max = 1000
            seek.progress = 0
            seek.isEnabled = true
            var userProgress = -1
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) userProgress = progress
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
            val area = android.graphics.Rect(0, 0, seek.width, seek.height)
            (binding.root as ViewGroup).offsetDescendantRectToMyCoords(seek, area)
            val x = area.left + area.width() * 0.75f
            val y = area.exactCenterY()
            val down = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                val event = MotionEvent.obtain(down, down + index * 16L, action, x, y, 0)
                try {
                    assertTrue("The player must handle the seek touch", binding.root.dispatchTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }
            assertTrue("The seekbar must receive the touch, not the secondary actions", userProgress > 500)
        }
    }
}
