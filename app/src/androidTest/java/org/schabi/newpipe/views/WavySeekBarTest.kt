package org.schabi.newpipe.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity

class WavySeekBarTest {
    @Test
    fun waveUsesTheSelectedTintAndItsPhaseChangesTheRenderedCurve() {
        val track = WavySeekBar.WaveTrack(1f)
        track.setBounds(0, 0, 120, 14)
        track.setTintList(ColorStateList.valueOf(Color.RED))
        val first = pixels(track)
        assertTrue(first.any { Color.alpha(it) > 200 && Color.red(it) > 200 && Color.green(it) == 0 })
        track.phase = 1.5f
        val shifted = pixels(track)
        assertTrue(first.zip(shifted).count { (a, b) -> a != b } > 100)
        val copy = track.constantState.newDrawable().mutate() as WavySeekBar.WaveTrack
        assertNotSame(track, copy)
        assertTrue("Drawable copies must retain tint and phase", shifted.contentEquals(pixels(copy)))
        copy.setTintList(ColorStateList.valueOf(Color.GREEN))
        copy.phase = 0f
        assertTrue("Mutating a copy must leave the original curve unchanged", shifted.contentEquals(pixels(track)))
    }

    @Test
    fun switchingTintedStylesKeepsNativeProgressBufferThumbAndAnimation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val old = preferences.getString(WavySeekBar.STYLE_KEY, null)
        preferences.edit().putString(WavySeekBar.STYLE_KEY, "standard").commit()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var bar: WavySeekBar
                scenario.onActivity { activity ->
                    bar = WavySeekBar(activity)
                    bar.max = 100
                    bar.progress = 40
                    bar.secondaryProgress = 70
                    // Exercise the native tint mutation that clones custom children on API 23.
                    bar.progressTintList = ColorStateList.valueOf(Color.RED)
                    bar.secondaryProgressTintList = ColorStateList.valueOf(Color.GREEN)
                    bar.progressBackgroundTintList = ColorStateList.valueOf(Color.BLUE)
                    activity.setContentView(bar)
                }
                val original = bar.progressDrawable
                val thumb = bar.thumb
                preferences.edit().putString(WavySeekBar.STYLE_KEY, "wavy").commit()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    assertNotSame(original, bar.progressDrawable)
                    assertSame(thumb, bar.thumb)
                    assertEquals(40, bar.progress)
                    assertEquals(70, bar.secondaryProgress)
                    val before = pixels(bar.progressDrawable)
                    assertTrue(before.any { Color.alpha(it) > 200 && Color.red(it) > 200 && Color.green(it) == 0 })
                    assertTrue(before.any { Color.alpha(it) > 200 && Color.green(it) > 200 && Color.red(it) == 0 })
                    assertTrue(before.any { Color.alpha(it) > 200 && Color.blue(it) > 200 && Color.red(it) == 0 })
                    // Advance the real animator without depending on emulator animation settings.
                    val animator = WavySeekBar::class.java.getDeclaredField("animation").apply { isAccessible = true }.get(bar) as ValueAnimator
                    animator.currentPlayTime = 400
                    assertTrue("Animation must update the displayed (possibly cloned) track", before.zip(pixels(bar.progressDrawable)).count { (a, b) -> a != b } > 20)
                    bar.playbackActive = true
                    bar.visibility = View.GONE
                    bar.playbackActive = false
                }
                preferences.edit().putString(WavySeekBar.STYLE_KEY, "standard").commit()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    assertSame(original, bar.progressDrawable)
                    assertEquals(40, bar.progress)
                }
            }
        } finally {
            preferences.edit().apply {
                if (old == null) remove(WavySeekBar.STYLE_KEY) else putString(WavySeekBar.STYLE_KEY, old)
            }.commit()
        }
    }

    private fun pixels(drawable: Drawable): IntArray {
        val bounds = drawable.copyBounds()
        val height = bounds.height().coerceAtLeast(14)
        val bitmap = Bitmap.createBitmap(120, height, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, 120, height)
            drawable.draw(Canvas(bitmap))
            return IntArray(120 * height).also { bitmap.getPixels(it, 0, 120, 0, 0, 120, height) }
        } finally {
            drawable.bounds = bounds
            bitmap.recycle()
        }
    }
}
