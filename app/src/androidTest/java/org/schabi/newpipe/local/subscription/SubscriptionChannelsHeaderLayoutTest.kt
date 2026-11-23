package org.schabi.newpipe.local.subscription

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SubscriptionChannelsHeaderBinding
import org.schabi.newpipe.local.subscription.item.SubscriptionChannelsHeader

@RunWith(AndroidJUnit4::class)
class SubscriptionChannelsHeaderLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun compactAndLargeTextHeadersKeepFullLabelsAndSeparateFocusableTouchTargets() {
        // Real Android text metrics cover API 23 as well as newer Android versions.
        instrumentation.runOnMainSync {
            val target = instrumentation.targetContext
            for (scale in listOf(1f, 2f)) {
                for (rtl in listOf(false, true)) {
                    val configuration = Configuration(target.resources.configuration).apply {
                        fontScale = scale
                        val locale = if (rtl) Locale.forLanguageTag("ar") else Locale.ENGLISH
                        setLocale(locale)
                        setLayoutDirection(locale)
                    }
                    val context = object : ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.LightTheme) {
                        override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                            flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
                        }
                    }
                    val host = FrameLayout(context)
                    val binding = SubscriptionChannelsHeaderBinding.inflate(LayoutInflater.from(context), host, false)
                    host.addView(binding.root)
                    var actions = 0
                    SubscriptionChannelsHeader({ actions++ }, { actions++ }, { actions++ }).apply {
                        count = 1234
                        bind(binding, 0)
                    }
                    val root = binding.root
                    val direction = configuration.layoutDirection
                    host.layoutDirection = direction
                    root.layoutDirection = direction
                    val display = context.resources.displayMetrics
                    val width = (320 * display.density).roundToInt()
                    host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    host.layout(0, 0, width, host.measuredHeight)

                    val title = binding.channelsHeaderTitle
                    val count = binding.channelsHeaderCount
                    val buttons = listOf(binding.channelsHeaderEdit, binding.channelsHeaderSearch, binding.channelsHeaderCollapse)
                    val controlsWidth = (binding.channelsHeaderEdit.parent as ViewGroup).width
                    val titleWidth = intrinsicWidth(title)
                    val countWidth = intrinsicWidth(count)
                    val available = root.width - root.paddingLeft - root.paddingRight
                    val metrics = "scale=$scale rtl=$rtl fontPx=${title.textSize} titlePx=$titleWidth countPx=$countWidth controlsPx=$controlsWidth availablePx=$available"
                    Log.i("SubscriptionHeaderTest", metrics)
                    assertEquals(metrics, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, display), title.textSize, 0.01f)
                    assertEquals(metrics, direction, root.layoutDirection)
                    if (scale == 2f) {
                        assertTrue("Large labels must exercise wrapping: $metrics", titleWidth + countWidth + controlsWidth > available)
                        assertEquals(metrics, LinearLayout.VERTICAL, root.orientation)
                    }
                    assertTrue(ViewCompat.isAccessibilityHeading(title))
                    assertEquals(context.resources.getQuantityString(R.plurals.subscription_total_count, 1234, 1234), count.contentDescription)
                    val minimum = (48 * display.density).roundToInt()
                    val labelBounds = listOf(title, count).map { bounds(root, it) }
                    val headerBounds = Rect(0, 0, root.width, root.height)
                    labelBounds.forEach { assertTrue(metrics, headerBounds.contains(it)) }
                    val buttonBounds = buttons.map { button ->
                        assertTrue(button.isFocusable && button.isClickable)
                        assertFalse(button.contentDescription.isNullOrBlank())
                        assertTrue(button.width >= minimum && button.height >= minimum)
                        button.performClick()
                        bounds(root, button).also { rect ->
                            assertTrue(rect.left >= 0 && rect.right <= width)
                            assertTrue(rect.top >= 0 && rect.bottom <= root.height)
                            labelBounds.forEach { assertFalse(Rect.intersects(it, rect)) }
                        }
                    }
                    assertEquals(3, actions)
                    for (first in buttonBounds.indices) {
                        for (second in first + 1 until buttonBounds.size) {
                            assertFalse(Rect.intersects(buttonBounds[first], buttonBounds[second]))
                        }
                    }
                    buttonBounds.zipWithNext().forEach { (first, second) ->
                        assertTrue(metrics, if (rtl) first.left >= second.right else first.right <= second.left)
                    }
                    listOf(title, count).forEach { label ->
                        val layout = requireNotNull(label.layout)
                        val textWidth = label.width - label.compoundPaddingLeft - label.compoundPaddingRight
                        assertTrue(layout.lineCount > 0)
                        assertEquals(label.text.length, layout.getLineEnd(layout.lineCount - 1))
                        repeat(layout.lineCount) { line ->
                            assertEquals(0, layout.getEllipsisCount(line))
                            assertTrue(layout.getLineWidth(line) <= textWidth + 1f)
                        }
                        assertTrue(layout.getLineBottom(layout.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                    }
                }
            }
        }
    }

    private fun intrinsicWidth(label: TextView): Int {
        val margins = label.layoutParams as ViewGroup.MarginLayoutParams
        return ceil(label.paint.measureText(label.text.toString())).toInt() +
            label.compoundPaddingLeft + label.compoundPaddingRight + margins.leftMargin + margins.rightMargin
    }

    private fun bounds(root: SubscriptionHeaderLayout, view: View) = Rect().apply {
        view.getDrawingRect(this)
        root.offsetDescendantRectToMyCoords(view, this)
    }
}
