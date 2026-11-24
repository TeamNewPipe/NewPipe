package org.schabi.newpipe.local.subscription

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding

@RunWith(AndroidJUnit4::class)
class SubscriptionFloatingActionsLayoutTest {
    @Test
    fun logicalEndActionsAndLastRowKeepTheirTouchSpaceInCompactWideAndArabicWindows() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val originalLocales = AppCompatDelegate.getApplicationLocales()
            try {
                for (language in listOf("ar", "en")) {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
                    awaitLanguage(scenario, language)
                    scenario.onActivity { activity ->
                        assertTrue(activity.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
                        for (scale in listOf(1f, 2f)) {
                            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
                            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
                            val binding = FragmentSubscriptionBinding.inflate(LayoutInflater.from(context))
                            activity.setContentView(binding.root)
                            val actions = SubscriptionFloatingActions(binding, {}, {}, {})
                            val list = binding.itemsList
                            val originalBottom = list.paddingBottom
                            list.visibility = View.VISIBLE
                            list.layoutManager = LinearLayoutManager(context)
                            list.adapter = object : RecyclerView.Adapter<Row>() {
                                override fun getItemCount() = 30
                                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row = Row(
                                    TextView(parent.context).apply {
                                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 48))
                                        isClickable = true
                                    }
                                )
                                override fun onBindViewHolder(holder: Row, position: Int) {
                                    (holder.itemView as TextView).text = "Channel $position"
                                }
                            }
                            actions.update(true, true, context.getString(R.string.search), R.drawable.ic_search)
                            for ((width, height) in listOf(320 to 420, 640 to 280, 320 to 600)) {
                                measure(binding.root, width, height)
                                list.scrollToPosition(29)
                                measure(binding.root, width, height)
                                val diagnostic = "language=$language scale=$scale viewport=$width×$height"
                                assertEquals(diagnostic, configuration.layoutDirection, binding.root.layoutDirection)
                                assertActions(binding, language == "ar", diagnostic)
                                val last = list.findViewHolderForAdapterPosition(29)!!.itemView
                                val lastBounds = bounds(binding.root, last)
                                val pairBounds = bounds(binding.root, binding.subscriptionFloatingActions)
                                assertTrue("The complete final channel must scroll above the actions: $diagnostic", lastBounds.bottom <= pairBounds.top)
                                assertTrue(diagnostic, Rect(0, 0, binding.root.width, binding.root.height).contains(lastBounds))
                                assertEquals("Reserve only this pair, since navigation and mini-player already bound the viewport", originalBottom + dp(binding.root, 80), list.paddingBottom)
                            }
                            actions.update(false, true, context.getString(R.string.search), R.drawable.ic_search)
                            assertEquals(View.GONE, binding.subscriptionFloatingActions.visibility)
                            assertEquals(originalBottom, list.paddingBottom)
                            assertTrue(list.clipToPadding)
                            actions.dispose()
                        }
                    }
                }
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(originalLocales) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
        }
    }

    private fun assertActions(binding: FragmentSubscriptionBinding, rtl: Boolean, diagnostic: String) {
        val root = binding.root
        val pair = binding.subscriptionFloatingActions
        val pairBounds = bounds(root, pair)
        val rootBounds = Rect(0, 0, root.width, root.height)
        assertTrue(diagnostic, rootBounds.contains(pairBounds))
        assertEquals(diagnostic, if (rtl) dp(root, 16) else root.width - dp(root, 16), if (rtl) pairBounds.left else pairBounds.right)
        assertEquals(diagnostic, root.height - dp(root, 16), pairBounds.bottom)
        val targets = listOf(binding.subscriptionFloatingCreate, binding.subscriptionFloatingSearch).map { button ->
            assertTrue(diagnostic, button.isShown && button.isClickable && button.isFocusable)
            assertFalse(diagnostic, button.contentDescription.isNullOrBlank())
            assertTrue(diagnostic, button.width >= dp(root, 48) && button.height >= dp(root, 48))
            bounds(root, button).also { assertTrue(diagnostic, pairBounds.contains(it)) }
        }
        assertFalse(diagnostic, Rect.intersects(targets[0], targets[1]))
    }

    private fun awaitLanguage(scenario: ActivityScenario<AboutActivity>, language: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = ConfigurationCompat.getLocales(it.resources.configuration)[0]?.language == language }
            if (ready) return
            SystemClock.sleep(20)
        }
        throw AssertionError("The app language did not change to $language")
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dp(view, width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(view, height), View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun bounds(root: ViewGroup, view: View) = Rect().apply {
        view.getDrawingRect(this)
        root.offsetDescendantRectToMyCoords(view, this)
    }

    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).roundToInt()

    private class Row(view: View) : RecyclerView.ViewHolder(view)
}
