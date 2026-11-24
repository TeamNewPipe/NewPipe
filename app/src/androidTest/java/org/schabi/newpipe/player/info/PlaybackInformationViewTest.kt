package org.schabi.newpipe.player.info

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior

@RunWith(AndroidJUnit4::class)
class PlaybackInformationViewTest {
    @Test
    fun portraitAndRotationKeepThePanelBoundedAndEveryRowReachable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val application = ApplicationProvider.getApplicationContext<Context>()
            for (fontScale in listOf(1f, 2f)) {
                val configuration = Configuration(application.resources.configuration).apply { this.fontScale = fontScale }
                val context = object : ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.DarkTheme) {
                    override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                        flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
                    }
                }
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    val binding = PlayerBinding.inflate(LayoutInflater.from(context))
                    val panel = binding.playbackInformationPanel
                    val header = panel.getChildAt(0) as LinearLayout
                    val close = header.getChildAt(1) as ImageButton
                    val scroll = panel.getChildAt(1) as ScrollView
                    val details = scroll.getChildAt(0) as TextView
                    binding.root.layoutDirection = direction
                    panel.visibility = View.VISIBLE
                    scroll.isSmoothScrollingEnabled = false
                    details.text = longDetails()

                    // Reuse the same parent: its laid-out height still describes the previous size
                    // while the next measurement runs, just as during a fullscreen/portrait change.
                    for ((widthDp, heightDp) in listOf(640 to 360, 320 to 180, 412 to 232, 640 to 360, 320 to 180)) {
                        val label = "$widthDp x $heightDp dp, fontScale=$fontScale, direction=$direction"
                        measureAndLayout(binding.root, dp(context, widthDp), dp(context, heightDp))
                        assertEquals("The player must use the requested direction: $label", direction, binding.root.layoutDirection)
                        assertEquals("The panel must use the requested direction: $label", direction, panel.layoutDirection)
                        assertEquals("The header must use the requested direction: $label", direction, header.layoutDirection)
                        assertTrue("Panel must fit the player: $label", panel.top >= 0 && panel.bottom <= binding.root.height)
                        assertTrue("Panel must fit horizontally: $label", panel.left >= 0 && panel.right <= binding.root.width)
                        assertTrue("The close button must remain visible: $label", close.height > 0 && close.top >= 0 && close.bottom <= header.height && close.left >= 0 && close.right <= header.width)
                        assertTrue("The header must remain above the details: $label", header.bottom <= scroll.top)
                        assertTrue("The details need a visible viewport: $label", scroll.height > 0 && scroll.bottom <= panel.height - panel.paddingBottom)

                        scroll.scrollTo(0, 0)
                        val overflows = details.bottom > scroll.height - scroll.paddingBottom
                        if (widthDp == 320 || widthDp == 412) {
                            assertTrue("Portrait details must exercise scrolling: $label", overflows)
                        }
                        if (overflows) {
                            assertTrue("Overflowing details should scroll: $label", scroll.canScrollVertically(1))
                            scroll.fullScroll(View.FOCUS_DOWN)
                            assertTrue("The final row must be reachable: $label", scroll.scrollY > 0)
                        }
                        assertFalse("Scrolling must reach the bottom: $label", scroll.canScrollVertically(1))
                        assertTrue("The last row must fit inside the viewport: $label", details.bottom - scroll.scrollY <= scroll.height - scroll.paddingBottom)
                        assertEquals("Scrolling must not move the header: $label", panel.paddingTop, header.top)
                    }
                }
            }
        }
    }

    @Test
    fun panelHonorsExactAndZeroHeightConstraints() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.DarkTheme)
            val binding = PlayerBinding.inflate(LayoutInflater.from(context))
            val panel = binding.playbackInformationPanel
            panel.visibility = View.VISIBLE
            ((panel.getChildAt(1) as ScrollView).getChildAt(0) as TextView).text = longDetails()
            measureAndLayout(binding.root, dp(context, 640), dp(context, 360))
            val width = View.MeasureSpec.makeMeasureSpec(dp(context, 320), View.MeasureSpec.EXACTLY)

            panel.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.AT_MOST))
            assertEquals(0, panel.measuredHeight)
            val exactHeight = dp(context, 96)
            panel.measure(width, View.MeasureSpec.makeMeasureSpec(exactHeight, View.MeasureSpec.EXACTLY))
            assertEquals(exactHeight, panel.measuredHeight)
            panel.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            assertTrue(panel.measuredHeight in 1..dp(context, 320))
        }
    }

    @Test
    fun informationScrollKeepsThePlayerSheetAndAppBarStillAndReleasesTouches() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var fixture: GestureFixture
            scenario.onActivity { activity ->
                fixture = GestureFixture(activity)
                val originalSheetTop = fixture.sheet.top
                val originalAppBarOffset = fixture.appBarBehavior.topAndBottomOffset
                val scroll = fixture.scroll
                val x = scroll.width * 0.5f
                val startY = scroll.height * 0.8f
                val endY = scroll.height * 0.2f
                fixture.send(scroll, MotionEvent.ACTION_DOWN, x, startY)
                for (step in 1..8) {
                    fixture.send(scroll, MotionEvent.ACTION_MOVE, x, startY + (endY - startY) * step / 8)
                }
                fixture.send(scroll, MotionEvent.ACTION_UP, x, endY, 300)

                assertTrue("The swipe must scroll the information", scroll.scrollY > 0)
                assertEquals("The swipe must not move the player sheet", originalSheetTop, fixture.sheet.top)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, fixture.behavior.state)
                assertEquals("The swipe must not collapse the app bar", originalAppBarOffset, fixture.appBarBehavior.topAndBottomOffset)
                assertFalse("UP must release ancestor interception", fixture.parent.interceptionDisallowed)

                fixture.send(scroll, MotionEvent.ACTION_DOWN, x, startY)
                fixture.send(scroll, MotionEvent.ACTION_CANCEL, x, startY)
                assertFalse("CANCEL must release ancestor interception", fixture.parent.interceptionDisallowed)

                // This padded header area has no click handler or scrolling child.
                fixture.send(fixture.panel, MotionEvent.ACTION_DOWN, 1f, fixture.close.height * 0.5f)
                assertFalse("An unhandled panel touch must not retain the gesture", fixture.parent.interceptionDisallowed)
                fixture.send(fixture.panel, MotionEvent.ACTION_CANCEL, 1f, fixture.close.height * 0.5f)

                fixture.send(fixture.close, MotionEvent.ACTION_DOWN, fixture.close.width * 0.5f, fixture.close.height * 0.5f)
                fixture.send(fixture.close, MotionEvent.ACTION_UP, fixture.close.width * 0.5f, fixture.close.height * 0.5f)
                assertFalse("Closing the panel must release ancestor interception", fixture.parent.interceptionDisallowed)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertEquals(View.GONE, fixture.panel.visibility) }
        }
    }

    @Test
    fun panelRefreshesOnlyWhileAttachedAndVisible() {
        val ticks = AtomicInteger()
        val nextTicks = AtomicReference(CountDownLatch(2))
        lateinit var panel: PlaybackInformationView
        lateinit var root: FrameLayout
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                root = FrameLayout(activity)
                panel = PlaybackInformationView(activity).apply { visibility = View.GONE }
                root.addView(panel)
                activity.setContentView(root)
                val details = (panel.getChildAt(1) as ScrollView).getChildAt(0) as TextView
                details.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        ticks.incrementAndGet()
                        nextTicks.get().countDown()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                panel.bind(PlaybackInformation())
            }
            SystemClock.sleep(1200)
            assertEquals(0, ticks.get())
            scenario.onActivity { panel.toggle() }
            assertTrue("Visible panel should refresh repeatedly", nextTicks.get().await(5, TimeUnit.SECONDS))

            scenario.onActivity {
                val header = panel.getChildAt(0) as LinearLayout
                (header.getChildAt(1) as ImageButton).performClick()
                assertEquals(View.GONE, panel.visibility)
            }
            val closedTicks = ticks.get()
            SystemClock.sleep(1200)
            assertEquals(closedTicks, ticks.get())

            nextTicks.set(CountDownLatch(1))
            scenario.onActivity { panel.toggle() }
            assertTrue(nextTicks.get().await(5, TimeUnit.SECONDS))
            scenario.moveToState(Lifecycle.State.CREATED)
            val backgroundTicks = ticks.get()
            SystemClock.sleep(1200)
            assertEquals(backgroundTicks, ticks.get())
            nextTicks.set(CountDownLatch(1))
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("Returning to the window should resume refreshes", nextTicks.get().await(5, TimeUnit.SECONDS))

            scenario.onActivity { root.removeView(panel) }
            val detachedTicks = ticks.get()
            SystemClock.sleep(1200)
            assertEquals(detachedTicks, ticks.get())
            scenario.onActivity { panel.clear() }
        }
    }

    private class GestureFixture(activity: AboutActivity) {
        val parent = GestureCoordinator(activity)
        val sheet = FrameLayout(activity)
        val details = FragmentVideoDetailBinding.inflate(activity.layoutInflater, sheet, true)
        val player = PlayerBinding.inflate(activity.layoutInflater, details.playerPlaceholder, true)
        val panel = player.playbackInformationPanel
        val scroll = panel.getChildAt(1) as ScrollView
        val close = (panel.getChildAt(0) as LinearLayout).getChildAt(1) as ImageButton
        val behavior = CustomBottomSheetBehavior(activity, null)
        val appBarBehavior: AppBarLayout.Behavior
        private var eventTime = SystemClock.uptimeMillis()
        private var downTime = eventTime

        init {
            val playerHeight = dp(activity, 180)
            details.overlayLayout.visibility = View.GONE
            details.viewPager.visibility = View.GONE
            details.relatedItemsLayout?.visibility = View.GONE
            details.detailContentRootHiding.visibility = View.GONE
            details.loadingProgressBar.visibility = View.GONE
            details.root.findViewById<View>(R.id.error_panel).visibility = View.GONE
            details.detailThumbnailRootLayout.layoutParams.height = playerHeight
            details.detailThumbnailImageView.layoutParams.height = playerHeight
            details.detailThumbnailImageView.minimumHeight = playerHeight
            (details.detailContentRootLayout.layoutParams as ViewGroup.MarginLayoutParams).topMargin = playerHeight
            details.detailVideoTitleView.text = "Selected video"
            player.playbackControlRoot.visibility = View.GONE
            player.root.findViewById<View>(R.id.loading_panel).visibility = View.GONE
            panel.visibility = View.VISIBLE
            panel.bind(PlaybackInformation())
            behavior.peekHeight = dp(activity, 56)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            parent.addView(sheet, CoordinatorLayout.LayoutParams(-1, -1).apply { this.behavior = this@GestureFixture.behavior })
            activity.setContentView(parent)
            (scroll.getChildAt(0) as TextView).text = longDetails()
            measureAndLayout(parent, dp(activity, 320), dp(activity, 640))
            appBarBehavior = (details.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams).behavior as AppBarLayout.Behavior
            assertTrue("The portrait information must have a scrollable viewport", scroll.height > 0 && scroll.canScrollVertically(1))
        }

        fun send(target: View, action: Int, x: Float, y: Float, delay: Long = 32) {
            eventTime += delay
            if (action == MotionEvent.ACTION_DOWN) downTime = eventTime
            val targetLocation = IntArray(2)
            val origin = IntArray(2)
            target.getLocationOnScreen(targetLocation)
            parent.getLocationOnScreen(origin)
            val event = MotionEvent.obtain(downTime, eventTime, action, targetLocation[0] + x, targetLocation[1] + y, 0)
            // Preserve raw screen coordinates for the real behaviors' hit tests while dispatching
            // parent-local coordinates through the normal child hierarchy.
            event.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
            parent.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private class GestureCoordinator(context: Context) : CoordinatorLayout(context) {
        var interceptionDisallowed = false
            private set

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
            interceptionDisallowed = disallowIntercept
        }
    }

    companion object {
        private fun longDetails(): String = (1..10).joinToString("\n") { "Playback detail $it: long decoder and codec information for the selected stream" }

        private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

        private fun measureAndLayout(view: View, width: Int, height: Int) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }
}
