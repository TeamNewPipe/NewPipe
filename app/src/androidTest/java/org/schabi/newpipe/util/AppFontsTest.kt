/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.hamcrest.Matchers.anything
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.ListStreamItemBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.player.ui.PlayerUiTheme
import org.schabi.newpipe.settings.AppearanceSettingsFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.appearance.AppFontSettings

@RunWith(AndroidJUnit4::class)
class AppFontsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val boldKey = context.getString(R.string.bold_media_text_key)
    private lateinit var previous: Map<String, Any?>

    @Before
    fun savePreferences() {
        previous = listOf(AppFonts.KEY, boldKey).associateWith { preferences.all[it] }
        preferences.edit().remove(AppFonts.KEY).putBoolean(boldKey, false).commit()
        instrumentation.waitForIdleSync()
    }

    @After
    fun restorePreferencesAndPreservePreviews() {
        preferences.edit().apply {
            previous.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> remove(key)
                }
            }
        }.commit()
        instrumentation.waitForIdleSync()
        val files = File(context.getExternalFilesDir(null), "player-layout-previews").listFiles().orEmpty()
            .filter { it.name.startsWith("app-font-") }
        if (files.isEmpty()) return
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        (listOf("mkdir -p $destination") + files.map { "cp ${it.absolutePath} $destination/" }).forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test
    fun appearanceDialogPreviewsSurviveRotationAndOnlyApplyAfterConfirmation() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = AppearanceSettingsFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, fragment, APPEARANCE).commitNow()
                fragment.findPreference<Preference>(AppFonts.KEY)!!.performClick()
            }
            selectPreset(AppFontPreset.SERIF)
            assertEquals(AppFontPreset.SYSTEM, AppFonts.selected(context))
            previewLabel().check(matches(isDisplayed())).check { view, error ->
                if (error != null) throw error
                assertFont(view as TextView, Typeface.SERIF)
            }
            scenario.recreate()
            assertEquals(AppFontPreset.SYSTEM, AppFonts.selected(context))
            previewLabel().check { view, error ->
                if (error != null) throw error
                assertFont(view as TextView, Typeface.SERIF)
            }
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            assertEquals(AppFontPreset.SYSTEM, AppFonts.selected(context))

            openFontDialog(scenario)
            selectPreset(AppFontPreset.MONOSPACE)
            onView(withText(R.string.app_font_apply)).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            assertEquals(AppFontPreset.MONOSPACE, AppFonts.selected(context))
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(APPEARANCE) as AppearanceSettingsFragment
                assertEquals(activity.getString(R.string.app_font_monospace), fragment.findPreference<Preference>(AppFonts.KEY)!!.summary.toString())
                val labels = textViews(activity.window.decorView).filter { it.text.toString() == activity.getString(R.string.app_font_title) }
                assertTrue("The real Appearance row must remain visible", labels.isNotEmpty())
                labels.forEach { assertMonospace(it) }
            }
            openFontDialog(scenario)
            selectPreset(AppFontPreset.SYSTEM)
            onView(withText(R.string.app_font_apply)).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            assertEquals(AppFontPreset.SYSTEM, AppFonts.selected(context))
        }
    }

    @Test
    fun recycledFeedTitlesAndBoldItalicTextKeepFontSelectionAndRestoreOriginalFaces() {
        instrumentation.runOnMainSync {
            val palette = ContextThemeWrapper(context, R.style.LightTheme)
            val binding = ListStreamItemBinding.inflate(LayoutInflater.from(palette), FrameLayout(palette), false)
            val title = binding.itemVideoTitleView
            val titleSize = title.textSize
            val italic = TextView(palette).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC) }
            val originalItalic = italic.typeface
            MediaTextWeight.apply(italic)
            val stream = StreamEntity(
                serviceId = 0,
                url = "https://www.youtube.com/watch?v=font-preview",
                title = "A recycled video title",
                streamType = StreamType.VIDEO_STREAM,
                duration = 120,
                uploader = "Example channel"
            )
            val state = StreamWithState(stream, null)
            StreamItem(state).bind(binding, 0)
            val originalTitle = title.typeface
            preferences.edit().putString(AppFonts.KEY, AppFontPreset.SERIF.value).commit()
            StreamItem(state, isHighlighted = true).bind(binding, 0)
            assertTrue(title.typeface.isBold)
            StreamItem(state).bind(binding, 1)
            assertFalse(title.typeface.isBold)
            assertFont(title, Typeface.SERIF, originalTitle)
            preferences.edit().putBoolean(boldKey, true).commit()
            MediaTextWeight.apply(italic)
            assertTrue(italic.typeface.isBold)
            assertTrue(italic.typeface.isItalic)

            preferences.edit().putString(AppFonts.KEY, AppFontPreset.MONOSPACE.value).commit()
            StreamItem(state).bind(binding, 2)
            assertMonospace(title)
            MediaTextWeight.apply(italic)
            assertTrue(italic.typeface.isBold)
            assertTrue(italic.typeface.isItalic)
            preferences.edit().putString(AppFonts.KEY, AppFontPreset.SYSTEM.value).putBoolean(boldKey, false).commit()
            AppFonts.applyTree(binding.root)
            MediaTextWeight.apply(italic)
            assertSame(originalTitle, title.typeface)
            assertSame(originalItalic, italic.typeface)
            assertEquals(titleSize, title.textSize, 0f)
            preferences.edit().putString(AppFonts.KEY, "missing-restored-font").commit()
            assertEquals(AppFontPreset.SYSTEM, AppFonts.selected(context))
        }
    }

    @Test
    fun servicePlayerRefreshesInPlaceAndLeavesCaptionSubtreeUnchanged() {
        lateinit var binding: PlayerBinding
        lateinit var originalTitle: Typeface
        lateinit var captionLabel: TextView
        val captionFace = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        var titleSize = 0f
        instrumentation.runOnMainSync {
            val service = ContextThemeWrapper(context, R.style.DarkTheme)
            val palette = PlayerUiTheme.createContext(service)
            binding = PlayerBinding.inflate(LayoutInflater.from(palette))
            originalTitle = binding.titleTextView.typeface
            titleSize = binding.titleTextView.textSize
            captionLabel = TextView(palette).apply { typeface = captionFace }
            binding.subtitleView.addView(captionLabel)
            AppFonts.observe(binding.root)
        }
        preferences.edit().putString(AppFonts.KEY, AppFontPreset.MONOSPACE.value).commit()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertMonospace(binding.titleTextView)
            assertSame(captionFace, captionLabel.typeface)
            assertEquals(titleSize, binding.titleTextView.textSize, 0f)
        }
        preferences.edit().putString(AppFonts.KEY, AppFontPreset.SERIF.value).commit()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertSame(captionFace, captionLabel.typeface)
        }
        preferences.edit().putString(AppFonts.KEY, AppFontPreset.SYSTEM.value).commit()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertSame(originalTitle, binding.titleTextView.typeface)
            assertSame(captionFace, captionLabel.typeface)
        }
    }

    @Test
    fun fontPreviewsWrapAtLargeTextSizesAndRenderRtlScripts() {
        instrumentation.runOnMainSync {
            listOf(
                Preview("serif-light", AppFontPreset.SERIF),
                Preview("monospace-large", AppFontPreset.MONOSPACE, 1.8f),
                Preview("serif-rtl", AppFontPreset.SERIF, 1.4f, "ar")
            ).forEach { sample ->
                val config = Configuration(context.resources.configuration).apply {
                    val locale = Locale.forLanguageTag(sample.language)
                    setLocale(locale)
                    setLayoutDirection(locale)
                    fontScale = sample.scale
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                }
                val palette = object : ContextThemeWrapper(context.createConfigurationContext(config), R.style.LightTheme) {
                    override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                        flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
                    }
                }
                val preview = AppFontSettings.preview(palette).apply {
                    layoutDirection = config.layoutDirection
                    setBackgroundColor(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorSurface))
                }
                AppFontSettings.updatePreview(preview, sample.preset)
                val width = (360 * palette.resources.displayMetrics.density).roundToInt()
                preview.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                preview.layout(0, 0, width, preview.measuredHeight)
                dispatchPreDraw(preview)
                textViews(preview).forEach { text ->
                    assertTrue(text.height > 0 && text.lineCount > 0)
                    assertTrue(text.layout.getLineBottom(text.lineCount - 1) <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                }
                val longTitle = preview.findViewById<TextView>(R.id.app_font_preview_long)
                assertTrue("Long titles must wrap without shrinking user text", longTitle.lineCount > 1)
                assertTrue(preview.findViewById<TextView>(R.id.app_font_preview_bold).typeface.isBold)
                val bitmap = Bitmap.createBitmap(width, preview.height, Bitmap.Config.ARGB_8888)
                try {
                    preview.draw(Canvas(bitmap))
                    val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
                    File(folder, "app-font-${sample.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun selectPreset(preset: AppFontPreset) {
        onData(anything()).inRoot(isDialog()).inAdapterView(isAssignableFrom(ListView::class.java))
            .atPosition(preset.ordinal).perform(click())
    }

    private fun previewLabel(): ViewInteraction {
        onView(isAssignableFrom(ListView::class.java)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints() = isAssignableFrom(ListView::class.java)
            override fun getDescription() = "show the real font preview label"
            override fun perform(uiController: UiController, view: View) {
                val list = view as ListView
                // Espresso's onData waits for 90% of the whole footer to be visible. A
                // preview taller than the dialog cannot meet that condition. Position
                // the last selectable row first, then reveal only the label under test.
                list.setSelectionFromTop(AppFontPreset.entries.lastIndex, 0)
                uiController.loopMainThreadUntilIdle()
                val label = checkNotNull(list.findViewById<TextView>(R.id.app_font_preview_normal)) {
                    "The real font-preview footer must be attached after positioning the list"
                }
                val bounds = Rect()
                label.getDrawingRect(bounds)
                label.requestRectangleOnScreen(bounds, true)
                uiController.loopMainThreadUntilIdle()
            }
        })
        return onView(withId(R.id.app_font_preview_normal)).inRoot(isDialog())
    }

    private fun openFontDialog(scenario: ActivityScenario<SettingsActivity>) {
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag(APPEARANCE) as AppearanceSettingsFragment
            fragment.findPreference<Preference>(AppFonts.KEY)!!.performClick()
        }
        onView(withText(R.string.app_font_title)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    private fun assertFont(view: TextView, expected: Typeface, original: Typeface = Typeface.DEFAULT) {
        val sample = "iiiiMMMM1234"
        val paint = Paint(view.paint).apply {
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(expected, original.weight, original.isItalic) else Typeface.create(expected, original.style)
        }
        assertEquals("The label must use the selected family", paint.measureText(sample), view.paint.measureText(sample), 0.01f)
    }

    private fun assertMonospace(view: TextView) {
        assertEquals("Narrow and wide Latin letters must use equal advances", view.paint.measureText("iiii"), view.paint.measureText("WWWW"), 0.01f)
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun dispatchPreDraw(view: View) {
        if (view is ViewGroup) for (index in 0 until view.childCount) dispatchPreDraw(view.getChildAt(index))
        // Detached views have separate floating observers; Arabic text needs its pre-draw scroll.
        view.viewTreeObserver.dispatchOnPreDraw()
    }

    private data class Preview(val name: String, val preset: AppFontPreset, val scale: Float = 1f, val language: String = "en")

    companion object {
        private const val APPEARANCE = "app-font-appearance-test"
    }
}
