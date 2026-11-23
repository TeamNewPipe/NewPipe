package org.schabi.newpipe.player.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class FullscreenCommentsReadabilityTest {
    @Test
    fun compactCommentControlsKeepCompleteLabelsWithLargeTextAndRtl() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                listOf(false, true).forEach { rtl ->
                    val configuration = Configuration(activity.resources.configuration).apply {
                        fontScale = 1.6f
                        setLocale(if (rtl) Locale("ar") else Locale.ENGLISH)
                    }
                    val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.DarkTheme)
                    val root = LayoutInflater.from(context).inflate(R.layout.fragment_comments, null)
                    root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    root.findViewById<View>(R.id.comments_show_cached).visibility = View.VISIBLE
                    root.findViewById<View>(R.id.comments_minimize_player).visibility = View.VISIBLE
                    activity.setContentView(root)
                    val density = context.resources.displayMetrics.density
                    val width = (320 * density).toInt()
                    val height = (600 * density).toInt()
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    root.layout(0, 0, width, height)
                    listOf(R.id.comments_top, R.id.comments_newest, R.id.comments_pinned_only, R.id.comments_date_toggle).forEach { id ->
                        val label = root.findViewById<TextView>(id)
                        assertTrue("Control must be laid out", label.width > 0)
                        val available = label.width - label.compoundPaddingLeft - label.compoundPaddingRight
                        assertTrue(
                            "Complete label must fit its scrollable control: ${label.text}",
                            label.paint.measureText(label.text.toString()) <= available + 1f
                        )
                        label.layout?.let { layout ->
                            repeat(layout.lineCount) { assertEquals("No abbreviated filter labels", 0, layout.getEllipsisCount(it)) }
                        }
                    }
                    val list = root.findViewById<View>(R.id.items_list)
                    assertTrue("Comments retain space below their controls", list.height > 0 && list.top < height)
                }
            }
        }
    }
}
