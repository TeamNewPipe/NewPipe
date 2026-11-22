/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.views.WavySeekBar

class ReplayHeatmapUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun preferenceChangesAndMetadataTransitionsKeepTheNativeSeekbarAndHideUnavailableData() {
        withPreferences {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var binding: PlayerBinding
                lateinit var controller: ReplayHeatmapController
                val playback = Playback()
                var defaultHeight = 0
                try {
                    scenario.onActivity { activity ->
                        binding = fixture(activity)
                        activity.setContentView(binding.root)
                        controller = ReplayHeatmapController(binding, playback)
                        measure(binding, 360, 640)
                        defaultHeight = binding.bottomControls.height
                        assertEquals(View.GONE, binding.replayHeatmap.visibility)
                        assertEquals(View.GONE, binding.nextReplayPeak.visibility)
                        controller.onMetadataChanged(info())
                        measure(binding, 360, 640)
                        assertEquals("Default-off metadata must not reserve extra space", defaultHeight, binding.bottomControls.height)
                    }
                    preferences.edit().putBoolean(ReplayHeatmapController.ENABLED_KEY, true).commit()
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        val thumb = binding.playbackSeekBar.thumb
                        val listenerProgress = binding.playbackSeekBar.progress
                        measure(binding, 360, 640)
                        assertEquals(View.VISIBLE, binding.replayHeatmap.visibility)
                        assertEquals(View.VISIBLE, binding.nextReplayPeak.visibility)
                        assertTrue(binding.nextReplayPeak.isFocusable)
                        assertTrue(binding.nextReplayPeak.contentDescription.contains("0:15"))
                        assertFalse(binding.replayHeatmap.isClickable)
                        assertFalse(binding.replayHeatmap.isFocusable)
                        assertTrue(binding.bottomControls.height > defaultHeight)
                        assertSame(thumb, binding.playbackSeekBar.thumb)
                        assertEquals(listenerProgress, binding.playbackSeekBar.progress)
                        binding.nextReplayPeak.performClick()
                        assertEquals(listOf(15_000L), playback.seeks)
                        binding.nextReplayPeak.performClick()
                        assertEquals(listOf(15_000L, 55_000L), playback.seeks)
                        assertFalse("The final peak must not wrap to the beginning", binding.nextReplayPeak.isEnabled)
                        binding.nextReplayPeak.performClick()
                        assertEquals(2, playback.seeks.size)
                        playback.eligible = false
                        controller.update()
                        assertEquals("Live, nonseekable or remote playback must hide the feature", View.GONE, binding.replayHeatmap.visibility)
                        assertEquals(View.GONE, binding.nextReplayPeak.visibility)
                        playback.eligible = true
                        playback.positionMs = 0
                        playback.durationMs = -1
                        controller.update()
                        assertEquals("An unknown engine duration must hide the graph", View.GONE, binding.replayHeatmap.visibility)
                        playback.durationMs = 70_000
                        controller.update()
                        assertEquals(View.VISIBLE, binding.replayHeatmap.visibility)
                        controller.clear()
                        measure(binding, 360, 640)
                        assertEquals(View.GONE, binding.replayHeatmap.visibility)
                        assertEquals(View.GONE, binding.nextReplayPeak.visibility)
                        assertEquals(defaultHeight, binding.bottomControls.height)
                        controller.onMetadataChanged(info().apply { replayHeatmap = emptyList() })
                        assertEquals(View.GONE, binding.replayHeatmap.visibility)
                        controller.onMetadataChanged(info().apply { duration = -1 })
                        assertEquals(View.GONE, binding.replayHeatmap.visibility)
                        controller.onMetadataChanged(info().apply { replayHeatmap = listOf(ReplayHeatmapSample(0, 70_000, 0f)) })
                        assertEquals("An empty replay signal must not reserve a blank graph", View.GONE, binding.replayHeatmap.visibility)
                        controller.onMetadataChanged(info())
                    }
                    preferences.edit().putBoolean(ReplayHeatmapController.ENABLED_KEY, false).commit()
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        measure(binding, 360, 640)
                        assertEquals(View.GONE, binding.replayHeatmap.visibility)
                        assertEquals(View.GONE, binding.nextReplayPeak.visibility)
                        assertEquals(defaultHeight, binding.bottomControls.height)
                    }
                } finally {
                    scenario.onActivity { controller.close() }
                }
            }
        }
    }

    @Test
    fun graphFitsAboveBothSeekbarStylesOnPhoneWideAndRtlLayouts() {
        withPreferences {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().putBoolean(ReplayHeatmapController.ENABLED_KEY, true).commit()
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                for (case in listOf(Case("phone", 360, 640), Case("landscape", 640, 360), Case("rtl-large", 360, 720, true, 1.5f))) {
                    for (style in listOf("standard", "wavy")) {
                        preferences.edit().putString(WavySeekBar.STYLE_KEY, style).commit()
                        scenario.onActivity { activity ->
                            val configuration = Configuration(activity.resources.configuration).apply {
                                val locale = Locale.forLanguageTag(if (case.rtl) "ar" else "en")
                                setLocale(locale)
                                setLayoutDirection(locale)
                                fontScale = case.fontScale
                                screenWidthDp = case.width
                                screenHeightDp = case.height
                                smallestScreenWidthDp = minOf(case.width, case.height)
                                orientation = if (case.width > case.height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                            }
                            val palette = object : ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.DarkTheme) {
                                override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                                    flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
                                }
                            }
                            val binding = fixture(palette)
                            binding.root.layoutDirection = configuration.layoutDirection
                            activity.setContentView(binding.root)
                            ReplayHeatmapController(binding, Playback()).use { controller ->
                                controller.onMetadataChanged(info())
                                binding.sponsorBlockSeekBarMarkers.visibility = View.VISIBLE
                                binding.playbackSeekBar.max = 70_000
                                binding.playbackSeekBar.progress = 12_000
                                binding.playbackSeekBar.secondaryProgress = 45_000
                                val thumb = binding.playbackSeekBar.thumb
                                measure(binding, case.width, case.height)
                                controller.update()
                                val graph = bounds(binding.bottomControls, binding.replayHeatmap)
                                val bar = bounds(binding.bottomControls, binding.playbackSeekBar)
                                assertTrue("Graph must stay above the native seekbar", graph.bottom <= bar.top)
                                assertEquals(bar.left, graph.left)
                                assertEquals(bar.right, graph.right)
                                assertTrue(graph.left >= 0 && graph.right <= binding.bottomControls.width)
                                assertEquals(binding.playbackSeekBar.paddingLeft, binding.replayHeatmap.paddingLeft)
                                assertEquals(binding.playbackSeekBar.paddingRight, binding.replayHeatmap.paddingRight)
                                assertSame(thumb, binding.playbackSeekBar.thumb)
                                assertEquals(12_000, binding.playbackSeekBar.progress)
                                assertEquals(45_000, binding.playbackSeekBar.secondaryProgress)
                                assertEquals(View.VISIBLE, binding.sponsorBlockSeekBarMarkers.visibility)
                                assertTrue(binding.nextReplayPeak.isEnabled)
                                if (style == "standard") savePreview(binding, case.name)
                            }
                        }
                    }
                }
            }
            preservePreviews()
        }
    }

    private fun fixture(palette: Context): PlayerBinding = PlayerBinding.inflate(LayoutInflater.from(ContextThemeWrapper(palette, R.style.DarkTheme))).apply {
        playbackControlRoot.visibility = View.VISIBLE
        loadingPanel.visibility = View.GONE
        playbackCurrentTime.text = "0:12"
        playbackEndTime.text = "1:10"
        PlayerUiTheme.applyColors(playbackSeekBar, palette)
    }

    private fun info(): StreamInfo = StreamInfo(0, "fixture", "https://example.test/video", "Replay fixture").apply {
        duration = 70
        replayHeatmap = listOf(.1f, .8f, .1f, .12f, .1f, .9f, .1f).mapIndexed { index, score ->
            ReplayHeatmapSample(index * 10_000L, (index + 1) * 10_000L, score)
        }
    }

    private inline fun withPreferences(block: () -> Unit) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val oldEnabled = preferences.all[ReplayHeatmapController.ENABLED_KEY] as? Boolean
        val oldStyle = preferences.getString(WavySeekBar.STYLE_KEY, null)
        preferences.edit().remove(ReplayHeatmapController.ENABLED_KEY).putString(WavySeekBar.STYLE_KEY, "standard").commit()
        try {
            block()
        } finally {
            preferences.edit().apply {
                oldEnabled?.let { putBoolean(ReplayHeatmapController.ENABLED_KEY, it) } ?: remove(ReplayHeatmapController.ENABLED_KEY)
                oldStyle?.let { putString(WavySeekBar.STYLE_KEY, it) } ?: remove(WavySeekBar.STYLE_KEY)
            }.commit()
        }
    }

    private fun measure(binding: PlayerBinding, width: Int, height: Int) {
        val density = binding.root.resources.displayMetrics.density
        binding.root.measure(exactly((width * density).toInt()), exactly((height * density).toInt()))
        binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
        binding.root.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun bounds(parent: ViewGroup, child: View): Rect = Rect().also {
        child.getDrawingRect(it)
        parent.offsetDescendantRectToMyCoords(child, it)
    }

    private fun savePreview(binding: PlayerBinding, name: String) {
        val row = binding.bottomControls
        val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLACK)
            row.draw(Canvas(bitmap))
            val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
            File(folder, "replay-heatmap-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun preservePreviews() {
        val files = File(context.getExternalFilesDir(null), "player-layout-previews").listFiles().orEmpty().filter { it.name.startsWith("replay-heatmap-") }
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        val automation = instrumentation.uiAutomation
        (listOf("mkdir -p $destination") + files.map { "cp ${it.absolutePath} $destination/" }).forEach {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { output -> output.readBytes() }
        }
    }

    private fun exactly(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)

    private data class Case(val name: String, val width: Int, val height: Int, val rtl: Boolean = false, val fontScale: Float = 1f)

    private class Playback : ReplayHeatmapController.Playback {
        override var durationMs = 70_000L
        override var positionMs = 0L
        override var eligible = true
        val seeks = mutableListOf<Long>()
        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            this.positionMs = positionMs
        }
    }
}
