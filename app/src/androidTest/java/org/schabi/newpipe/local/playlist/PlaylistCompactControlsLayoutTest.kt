package org.schabi.newpipe.local.playlist

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
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
import org.schabi.newpipe.databinding.ListStreamFilterChipsBinding
import org.schabi.newpipe.util.StreamFilterControls
import org.schabi.newpipe.views.CompactPlaylistControlsLayout

@RunWith(AndroidJUnit4::class)
class PlaylistCompactControlsLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun compactRowsKeepTheirLabelsAndTouchTargetsWhenAppLanguageFontOrWidthChanges() = withAppLanguages { activity, rtl ->
        for (scale in listOf(1f, 2f)) {
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
            for (layoutId in listOf(R.layout.fragment_playlist, R.layout.fragment_bookmarks)) {
                val root = LayoutInflater.from(context).inflate(layoutId, null) as ViewGroup
                activity.setContentView(root)
                PlaylistCompactControls.update(root, true)
                val local = layoutId == R.layout.fragment_playlist
                val row = root.findViewById<CompactPlaylistControlsLayout>(
                    if (local) R.id.stream_filter_downloaded_controls else R.id.playlist_category_controls
                )
                val label = root.findViewById<TextView>(if (local) R.id.filter_downloaded else R.id.playlist_category_filter)
                val labelText = label.text.toString()
                val ids = listOf(R.id.playlist_edit_toggle, R.id.collection_select_move) +
                    if (local) emptyList() else listOf(R.id.playlist_category_manage)
                for (width in listOf(320, 600, 320)) {
                    measure(root, width)
                    val metrics = "layout=$layoutId rtl=$rtl scale=$scale width=$width"
                    assertEquals(metrics, configuration.layoutDirection, root.layoutDirection)
                    assertEquals(labelText, label.text.toString())
                    assertTrue(label.isShown && label.width > 0)
                    assertRow(row, ids.map { root.findViewById<View>(it) }, rtl, metrics)
                    assertTrue("Labels must retain their full height: $metrics", label.layout.getLineBottom(label.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                    if (!local) {
                        repeat(label.lineCount) { line -> assertEquals("The selected category must remain readable: $metrics", 0, label.layout.getEllipsisCount(line)) }
                    }
                    if (width == 320 && scale == 2f) {
                        assertEquals("Large labels need the available width: $metrics", LinearLayout.VERTICAL, row.orientation)
                    } else if (width == 600) {
                        assertEquals("Wide windows should reuse one row: $metrics", LinearLayout.HORIZONTAL, row.orientation)
                    }
                    val controls = root.findViewById<View>(if (local) R.id.playlist_controls else R.id.playlist_category_controls)
                    assertTrue(root.findViewById<View>(R.id.items_list).top >= controls.bottom)
                }
            }
        }
    }

    @Test
    fun sharedFiltersHaveNoPlaylistActionSpaceInEitherAppLanguage() = withAppLanguages { activity, rtl ->
        val filters = ListStreamFilterChipsBinding.inflate(activity.layoutInflater)
        activity.setContentView(filters.root)
        StreamFilterControls.bind(filters, "DOWNLOADED", true) { }
        measure(filters.root, 320)
        assertEquals(if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, filters.root.layoutDirection)
        assertEquals(View.GONE, filters.localPlaylistEditControls.visibility)
        assertEquals(LinearLayout.HORIZONTAL, filters.streamFilterDownloadedControls.orientation)
        assertEquals(filters.filterDownloadedScroll.height, filters.streamFilterDownloadedControls.height)
        assertEquals(filters.streamFilterDownloadedControls.width, filters.filterDownloadedScroll.width)
    }

    private fun assertRow(row: CompactPlaylistControlsLayout, buttons: List<View>, rtl: Boolean, metrics: String) {
        val label = row.getChildAt(0)
        val actions = row.getChildAt(1) as ViewGroup
        val labelBounds = bounds(row, label)
        val actionBounds = bounds(row, actions)
        val rowBounds = Rect(0, 0, row.width, row.height)
        assertTrue(metrics, rowBounds.contains(labelBounds))
        assertTrue(metrics, rowBounds.contains(actionBounds))
        assertFalse(metrics, Rect.intersects(labelBounds, actionBounds))
        val endMargin = (actions.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
        assertEquals(
            "Actions must stay at the locale's logical end: $metrics",
            if (rtl) row.paddingLeft + endMargin else row.width - row.paddingRight - endMargin,
            if (rtl) actionBounds.left else actionBounds.right
        )
        if (row.orientation == LinearLayout.HORIZONTAL) {
            assertTrue(metrics, if (rtl) actionBounds.right <= labelBounds.left else labelBounds.right <= actionBounds.left)
        } else {
            assertTrue(metrics, labelBounds.bottom <= actionBounds.top)
        }
        val minimum = (48 * row.resources.displayMetrics.density).roundToInt()
        val targets = buttons.map { button ->
            assertTrue(metrics, button.isShown && button.isFocusable && button.isClickable)
            assertFalse(metrics, button.contentDescription.isNullOrBlank())
            assertTrue(metrics, button.width >= minimum && button.height >= minimum)
            bounds(row, button).also { assertTrue(metrics, rowBounds.contains(it)) }
        }
        targets.forEachIndexed { index, target ->
            targets.drop(index + 1).forEach { assertFalse(metrics, Rect.intersects(target, it)) }
        }
    }

    private fun withAppLanguages(check: (AboutActivity, Boolean) -> Unit) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val original = AppCompatDelegate.getApplicationLocales()
            try {
                for (language in listOf("ar", "en")) {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
                    val deadline = SystemClock.uptimeMillis() + 5000
                    var resolved = false
                    while (!resolved && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { resolved = ConfigurationCompat.getLocales(it.resources.configuration)[0]?.language == language }
                        if (!resolved) SystemClock.sleep(20)
                    }
                    assertTrue("The app language must reach its activity", resolved)
                    scenario.onActivity { activity ->
                        assertTrue("Use the installed app's RTL support", activity.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
                        check(activity, language == "ar")
                    }
                }
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
                instrumentation.waitForIdleSync()
            }
        }
    }

    private fun measure(view: View, widthDp: Int) {
        val density = view.resources.displayMetrics.density
        view.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * density).roundToInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((800 * density).roundToInt(), View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun bounds(root: ViewGroup, view: View) = Rect().apply {
        view.getDrawingRect(this)
        root.offsetDescendantRectToMyCoords(view, this)
    }
}
