/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.settings.preferencesearch.SettingsSearchIndex

@RunWith(AndroidJUnit4::class)
class PlayerActionVisibilityViewsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val hiddenKey = PlayerActionVisibility.preferenceKey(context)
    private val orderKey = context.getString(R.string.portrait_player_actions_key)
    private val bottomKey = context.getString(R.string.bottom_player_controls_key)
    private var oldHidden: Set<String>? = null
    private var oldOrder: String? = null
    private var oldBottom: Boolean? = null

    @Before
    fun setUp() {
        oldHidden = preferences.getStringSet(hiddenKey, null)?.toSet()
        oldOrder = preferences.getString(orderKey, null)
        oldBottom = preferences.all[bottomKey] as? Boolean
        PlayerActionVisibility.save(context, emptySet())
    }

    @After
    fun restorePreferences() {
        preferences.edit().apply {
            oldHidden?.let { putStringSet(hiddenKey, it) } ?: remove(hiddenKey)
            oldOrder?.let { putString(orderKey, it) } ?: remove(orderKey)
            oldBottom?.let { putBoolean(bottomKey, it) } ?: remove(bottomKey)
        }.commit()
    }

    @Test
    fun metadataUpdatesCannotRevealHiddenToolsAndUnhidingRestoresAvailability() = withPlayer { binding ->
        binding.captionTextView.visibility = View.GONE
        binding.projectionButton.visibility = View.INVISIBLE
        binding.castToPairedDevice.visibility = View.VISIBLE
        PlayerActionVisibility.save(context, setOf("captions", "projection", "cast"))
        binding.captionTextView.visibility = View.VISIBLE
        binding.castToPairedDevice.visibility = View.VISIBLE
        assertEquals(View.GONE, binding.captionTextView.visibility)
        assertEquals(View.GONE, binding.projectionButton.visibility)
        assertEquals(View.GONE, binding.castToPairedDevice.visibility)

        // The provider removed captions before the user showed the tools again.
        binding.captionTextView.visibility = View.GONE
        PlayerActionVisibility.save(context, emptySet())
        assertEquals(View.GONE, binding.captionTextView.visibility)
        assertEquals(View.INVISIBLE, binding.projectionButton.visibility)
        assertEquals(View.VISIBLE, binding.castToPairedDevice.visibility)
        binding.captionTextView.visibility = View.VISIBLE
        assertEquals(View.VISIBLE, binding.captionTextView.visibility)
    }

    @Test
    fun hiddenCaptionsCollapseTheirContainerAndHiddenToolsLoseFocus() = withPlayer { binding ->
        binding.captionTextView.visibility = View.VISIBLE
        binding.castToPairedDevice.visibility = View.VISIBLE
        measure(binding.root, 360, 640)
        val row = binding.secondaryControls.getChildAt(0)
        val captionContainer = binding.captionTextView.parent as View
        val originalWidth = row.measuredWidth
        assertTrue(captionContainer.measuredWidth > 0)
        assertTrue(binding.castToPairedDevice.requestFocus())

        PlayerActionVisibility.save(context, setOf("captions", "cast"))
        measure(binding.root, 360, 640)
        assertEquals(0, captionContainer.measuredWidth)
        assertTrue(row.measuredWidth < originalWidth)
        assertFalse(binding.castToPairedDevice.hasFocus())
        assertTrue(binding.playPauseButton.hasFocus())
        val focusable = arrayListOf<View>()
        binding.root.addFocusables(focusable, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        assertFalse(binding.captionTextView in focusable)
        assertFalse(binding.castToPairedDevice in focusable)
        assertTrue(binding.playPauseButton in focusable)
    }

    @Test
    fun detachedControlsRefreshOnReattachWithoutLosingTheirRequestedState() = withPlayer { binding ->
        val button = binding.castToPairedDevice
        val row = button.parent as LinearLayout
        val index = row.indexOfChild(button)
        val params = button.layoutParams
        button.visibility = View.VISIBLE
        row.removeView(button)
        assertFalse(button.isAttachedToWindow)
        PlayerActionVisibility.save(context, setOf("cast"))
        assertEquals("Detached controls must not retain a preference listener", View.VISIBLE, button.visibility)
        row.addView(button, index, params)
        assertEquals(View.GONE, button.visibility)
        button.visibility = View.GONE
        row.removeView(button)
        PlayerActionVisibility.save(context, emptySet())
        row.addView(button, index, params)
        assertEquals("An unavailable control must stay unavailable after reattachment", View.GONE, button.visibility)
        button.visibility = View.VISIBLE
        assertEquals(View.VISIBLE, button.visibility)
    }

    @Test
    fun portraitOrderFullscreenReparentingAndPopupAvailabilityKeepTheMask() {
        for (orientation in listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
            withPlayer(orientation) { binding ->
                PortraitPlayerActions.save(context, listOf("cast", "share", "captions"))
                val order = preferences.getString(orderKey, null)
                PlayerActionVisibility.save(context, setOf("cast", "share"))
                PortraitPlayerActions.apply(binding.secondaryControls)
                val row = binding.secondaryControls.getChildAt(0) as LinearLayout
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    assertEquals(R.id.castToPairedDevice, row.getChildAt(0).id)
                } else {
                    assertEquals(R.id.resizeTextView, row.getChildAt(0).id)
                }
                val bottom = BottomPlayerControls(binding)
                preferences.edit().putBoolean(bottomKey, true).commit()
                repeat(2) {
                    assertTrue(bottom.update(true, 0, 0, 0, 0))
                    assertEquals(View.GONE, binding.castToPairedDevice.visibility)
                    assertEquals(View.GONE, binding.share.visibility)
                    bottom.restore()
                }

                // Popup hides some tools. Showing them in settings must not defeat that decision.
                binding.share.visibility = View.GONE
                binding.fullScreenButton.visibility = View.VISIBLE
                PlayerActionVisibility.save(context, emptySet())
                assertEquals(View.GONE, binding.share.visibility)
                assertEquals(View.VISIBLE, binding.castToPairedDevice.visibility)
                assertEquals(View.VISIBLE, binding.fullScreenButton.visibility)
                binding.share.visibility = View.VISIBLE
                assertEquals(View.VISIBLE, binding.share.visibility)
                assertEquals(order, preferences.getString(orderKey, null))
            }
        }
    }

    @Test
    fun tvCastAndKodiAreIndependentAndInflationRespectsSavedVisibility() {
        PlayerActionVisibility.save(context, setOf("cast_tv", "kodi"))
        withPlayer { player ->
            val detail = FragmentVideoDetailBinding.inflate(LayoutInflater.from(player.root.context))
            assertEquals(View.GONE, player.nextReplayPeak.visibility)
            assertEquals(View.VISIBLE, player.castToPairedDevice.visibility)
            assertEquals(View.GONE, player.playWithKodi.visibility)
            assertEquals(View.GONE, detail.detailControlsCast.visibility)
            assertEquals(View.GONE, detail.detailControlsPlayWithKodi.visibility)
            detail.detailControlsCast.visibility = View.VISIBLE
            detail.detailControlsPlayWithKodi.visibility = View.VISIBLE
            assertEquals(View.GONE, detail.detailControlsCast.visibility)
            assertEquals(View.GONE, detail.detailControlsPlayWithKodi.visibility)
        }
    }

    @Test
    fun wideLandscapeDetailsRespectHiddenActionsAndProviderAvailability() {
        PlayerActionVisibility.save(context, setOf("cast_tv", "kodi", "share", "browser"))
        withPlayer(Configuration.ORIENTATION_LANDSCAPE, screenWidthDp = 960) { player ->
            val detail = FragmentVideoDetailBinding.inflate(LayoutInflater.from(player.root.context))
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, detail.root.resources.configuration.orientation)
            assertEquals(960, detail.root.resources.configuration.screenWidthDp)
            val actions = listOf(
                detail.detailControlsCast,
                detail.detailControlsPlayWithKodi,
                detail.detailControlsShare,
                detail.detailControlsOpenInBrowser
            )
            actions.forEach {
                assertEquals(View.GONE, it.visibility)
                it.visibility = View.VISIBLE
                assertEquals(View.GONE, it.visibility)
            }
            detail.detailControlsCast.visibility = View.GONE
            PlayerActionVisibility.save(context, emptySet())
            // Detached details refresh when the actual fragment content is attached.
            player.root.addView(detail.root)
            assertEquals(View.GONE, detail.detailControlsCast.visibility)
            assertEquals(View.VISIBLE, detail.detailControlsPlayWithKodi.visibility)
            assertEquals(View.VISIBLE, detail.detailControlsShare.visibility)
            assertEquals(View.VISIBLE, detail.detailControlsOpenInBrowser.visibility)
            player.root.removeView(detail.root)
        }
    }

    @Test
    fun chooserIsSearchableAndSaveCancelAndResetLeavePortraitOrderAlone() {
        val key = context.getString(R.string.visible_player_actions_key)
        assertTrue(SettingsSearchIndex.build(context).searchFor("Cast").any { it.key == key })
        PortraitPlayerActions.save(context, listOf("cast", "share"))
        val order = preferences.getString(orderKey, null)
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            showNarrowChooser(scenario)
            onData(equalTo(context.getString(R.string.device_cast_title))).inRoot(isDialog()).perform(click())
            assertEquals(emptySet<String>(), PlayerActionVisibility.hidden(context))
            onView(withId(android.R.id.button2)).inRoot(isDialog())
                .check(matches(isCompletelyDisplayed())).perform(click())
            assertEquals(emptySet<String>(), PlayerActionVisibility.hidden(context))

            showNarrowChooser(scenario)
            onData(equalTo(context.getString(R.string.device_cast_title))).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog())
                .check(matches(isCompletelyDisplayed())).perform(click())
            assertEquals(setOf("cast"), PlayerActionVisibility.hidden(context))
            assertEquals(order, preferences.getString(orderKey, null))

            showNarrowChooser(scenario)
            onView(withId(R.id.visible_player_actions_reset)).inRoot(isDialog())
                .check(matches(isCompletelyDisplayed())).perform(click())
            assertEquals(emptySet<String>(), PlayerActionVisibility.hidden(context))
            assertEquals(order, preferences.getString(orderKey, null))
        }
    }

    private fun showNarrowChooser(scenario: ActivityScenario<AboutActivity>) {
        scenario.onActivity { activity ->
            val dialog = PlayerActionVisibilityDialog.show(activity)
            val width = (320 * activity.resources.displayMetrics.density).toInt()
            dialog.window!!.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun withPlayer(
        orientation: Int = Configuration.ORIENTATION_PORTRAIT,
        screenWidthDp: Int = 360,
        block: (PlayerBinding) -> Unit
    ) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var wasInTouchMode = false
            scenario.onActivity { wasInTouchMode = it.window.decorView.isInTouchMode }
            try {
                lateinit var binding: PlayerBinding
                scenario.onActivity { activity ->
                    val configuration = Configuration(activity.resources.configuration).apply {
                        this.orientation = orientation
                        this.screenWidthDp = screenWidthDp
                    }
                    val themed = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.DarkTheme)
                    binding = PlayerBinding.inflate(LayoutInflater.from(themed))
                    binding.loadingPanel.visibility = View.GONE
                    binding.playbackControlRoot.visibility = View.VISIBLE
                    binding.secondaryControls.visibility = View.VISIBLE
                    activity.setContentView(binding.root)
                }
                // A real key leaves touch mode after earlier Espresso taps, including on API 35.
                onView(withId(R.id.playbackControlRoot)).check(matches(isDisplayed()))
                    .perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN))
                scenario.onActivity {
                    assertTrue(binding.root.isAttachedToWindow)
                    assertTrue(binding.root.isLaidOut)
                    assertTrue(binding.root.hasWindowFocus())
                    assertFalse(binding.root.isInTouchMode)
                    block(binding)
                }
            } finally {
                instrumentation.setInTouchMode(wasInTouchMode)
            }
        }
    }

    private fun measure(root: View, widthDp: Int, heightDp: Int) {
        val density = root.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }
}
