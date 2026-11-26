package us.shandian.giga.ui.adapter

import android.app.Application
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MissionMetadataLayoutTest {
    @Test
    fun frenchCompletedDownloadKeepsProgressBeforeTheDate() {
        val row = row(R.layout.mission_item_linear, Locale.FRANCE, 1f)
        bind(row, "DEEPIN LINUX · 314,96 kB", "100%", "26 nov. 2026")
        measure(row, 360)

        assertMetadataFits(row, "French completion")
        val status = bounds(row, R.id.item_status)
        val date = bounds(row, R.id.item_date)
        assertEquals(status.top, date.top)
        assertTrue(status.right <= date.left)
        assertTrue(bounds(row, R.id.item_size).bottom <= status.top)
    }

    @Test
    fun listAndGridKeepLongMetadataReadableAtLargeFontSizesAndInRtl() {
        for (layout in intArrayOf(R.layout.mission_item_linear, R.layout.mission_item)) {
            val widths = if (layout == R.layout.mission_item_linear) intArrayOf(320, 360) else intArrayOf(160, 200)
            for (width in widths) {
                for (scale in floatArrayOf(1f, 2f)) {
                    for (locale in listOf(Locale.FRANCE, Locale.forLanguageTag("ar"))) {
                        val row = row(layout, locale, scale)
                        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(1_795_694_400_000L))
                        val summary = "A long channel name with additional metadata · 314,96 kB"
                        bind(row, summary, "100%", date)
                        measure(row, width)

                        assertMetadataFits(row, "layout=$layout locale=$locale widthDp=$width fontScale=$scale")
                        assertEquals(summary, row.findViewById<TextView>(R.id.item_size).text.toString())
                        assertEquals(date, row.findViewById<TextView>(R.id.item_date).text.toString())
                        assertEquals(if (locale.language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, row.layoutDirection)
                        if (layout == R.layout.mission_item_linear) assertStatusPrecedesDate(row)
                    }
                }
            }
        }
    }

    @Test
    fun metadataReflowsAfterWidthAndMissionStatusChange() {
        val row = row(R.layout.mission_item_linear, Locale.FRANCE, 2f)
        bind(row, "DEEPIN LINUX · 314,96 kB", row.context.getString(R.string.download_remote_status), "26 nov. 2026")
        measure(row, 640)
        assertMetadataFits(row, "wide remote")
        assertEquals(bounds(row, R.id.item_status).top, bounds(row, R.id.item_date).top)

        measure(row, 320)
        assertMetadataFits(row, "320dp remote")
        assertTrue(bounds(row, R.id.item_status).bottom <= bounds(row, R.id.item_date).top)

        bind(row, "DEEPIN LINUX · 314,96 kB", "100%", "26 nov. 2026")
        measure(row, 640)
        assertMetadataFits(row, "wide complete")
        assertEquals(bounds(row, R.id.item_status).top, bounds(row, R.id.item_date).top)

        bind(row, "314,96 kB · 20 kB/s", "32.10%", "")
        measure(row, 320)
        assertMetadataFits(row, "320dp active")
        assertEquals("", row.findViewById<TextView>(R.id.item_date).text.toString())
        assertEquals(bounds(row, R.id.item_status).top, bounds(row, R.id.item_date).top)
    }

    private fun row(layout: Int, locale: Locale, fontScale: Float): ViewGroup {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            this.fontScale = fontScale
        }
        val context = ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.DarkTheme)
        return (LayoutInflater.from(context).inflate(layout, FrameLayout(context), false) as ViewGroup).apply {
            layoutDirection = configuration.layoutDirection
            findViewById<TextView>(R.id.item_name).text = "deepin 25.3.0 Released!"
        }
    }

    private fun bind(row: View, summary: String, status: String, date: String) {
        row.findViewById<TextView>(R.id.item_size).text = summary
        row.findViewById<TextView>(R.id.item_status).text = status
        row.findViewById<TextView>(R.id.item_date).text = date
    }

    private fun measure(row: View, widthDp: Int) {
        val width = (widthDp * row.resources.displayMetrics.density).roundToInt()
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    }

    private fun assertStatusPrecedesDate(row: ViewGroup) {
        val status = bounds(row, R.id.item_status)
        val date = bounds(row, R.id.item_date)
        assertTrue(
            "Status must precede the date, including when stacked",
            status.bottom <= date.top || if (row.layoutDirection == View.LAYOUT_DIRECTION_RTL) status.left >= date.right else status.right <= date.left
        )
    }

    private fun assertMetadataFits(row: ViewGroup, stage: String) {
        val ids = intArrayOf(R.id.item_size, R.id.item_status, R.id.item_date)
        for (id in ids) {
            val label = row.findViewById<TextView>(id)
            val rect = bounds(row, id)
            val metrics = "stage=$stage id=${row.resources.getResourceEntryName(id)} row=${row.width}x${row.height} " +
                "label=$rect padding=${label.compoundPaddingLeft},${label.compoundPaddingRight} textSize=${label.textSize} text=${label.text}"
            assertTrue("Metadata extends outside the row: $metrics", rect.left >= 0 && rect.right <= row.width && rect.top >= 0 && rect.bottom <= row.height)
            assertFalse("Metadata overlaps the overflow button: $metrics", Rect.intersects(rect, bounds(row, R.id.item_more)))
            assertFalse("Metadata overlaps the thumbnail: $metrics", Rect.intersects(rect, bounds(row, R.id.item_icon)))
            val textLayout = requireNotNull(label.layout)
            assertEquals("Metadata is truncated: $metrics", label.text.length, textLayout.getLineEnd(textLayout.lineCount - 1))
            assertTrue("Metadata is clipped vertically: $metrics", textLayout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
            for (line in 0 until textLayout.lineCount) {
                assertEquals(0, textLayout.getEllipsisCount(line))
                assertTrue(
                    "Metadata is clipped horizontally: $metrics line=$line lineMax=${textLayout.getLineMax(line)}",
                    textLayout.getLineMax(line) <= label.width - label.compoundPaddingLeft - label.compoundPaddingRight + 1f
                )
            }
        }
        for (first in ids.indices) {
            for (second in first + 1 until ids.size) {
                assertFalse("Metadata labels overlap", Rect.intersects(bounds(row, ids[first]), bounds(row, ids[second])))
            }
        }
    }

    private fun bounds(row: ViewGroup, id: Int): Rect {
        val child = row.findViewById<View>(id)
        return Rect().also {
            child.getDrawingRect(it)
            row.offsetDescendantRectToMyCoords(child, it)
        }
    }
}
