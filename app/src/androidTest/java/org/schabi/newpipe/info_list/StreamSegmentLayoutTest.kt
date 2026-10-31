package org.schabi.newpipe.info_list

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ItemStreamSegmentBinding

@MediumTest
@RunWith(AndroidJUnit4::class)
class StreamSegmentLayoutTest {
    @Test
    fun timestampFitsAcrossPhoneConfigurations() {
        val failures = mutableListOf<String>()
        UiThreadStatement.runOnUiThread {
            for (orientation in listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
                for (fontScale in listOf(1.0f, 1.3f, 2.0f)) {
                    for (widthDp in listOf(320, 360)) {
                        for (longTitle in listOf(false, true)) {
                            for (hasChannel in listOf(false, true)) {
                                val case = Case(orientation, fontScale, widthDp, longTitle, hasChannel)
                                try {
                                    checkRow(case)
                                } catch (error: AssertionError) {
                                    failures += "$case: ${error.message}"
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkRow(case: Case) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply {
            orientation = case.orientation
            fontScale = case.fontScale
            smallestScreenWidthDp = 320
            screenWidthDp = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 640 else 360
            screenHeightDp = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 360 else 640
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK.inv()) or
                Configuration.SCREENLAYOUT_SIZE_NORMAL
        }
        val context = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.LightTheme)
        val binding = ItemStreamSegmentBinding.inflate(LayoutInflater.from(context))

        // Use sample chapter text to check the layout without loading a video or images.
        // A new row allows one title line with a channel name, or two lines without one.
        binding.textViewTitle.text = if (case.longTitle) {
            "A long chapter title describing several topics in enough detail to wrap onto another line"
        } else {
            "Intro"
        }
        binding.textViewTitle.maxLines = if (case.hasChannel) 1 else 2
        binding.textViewChannel.text = "Example artist"
        binding.textViewChannel.visibility = if (case.hasChannel) View.VISIBLE else View.GONE
        binding.textViewStartSeconds.text = "1:23:45"

        val root = binding.root
        val widthPx = (case.widthDp * context.resources.displayMetrics.density).roundToInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        // Save one example row as a PNG when layoutCaptureName is supplied.
        val captureName = InstrumentationRegistry.getArguments().getString("layoutCaptureName")
        if (captureName != null && case.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            case.fontScale == 1.3f && case.widthDp == 360 && case.longTitle && !case.hasChannel
        ) {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            root.draw(canvas)
            File(base.getExternalFilesDir(null), "$captureName.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }

        // The full timestamp must fit inside its view and every parent view.
        val timestamp = binding.textViewStartSeconds
        assertTrue("timestamp has no text layout", timestamp.layout != null)
        assertTrue(
            "timestamp text needs ${textHeight(timestamp)}px but its view is ${timestamp.height}px high",
            textHeight(timestamp) <= timestamp.height
        )
        var ancestor = timestamp.parent as ViewGroup
        while (true) {
            val bounds = Rect(0, 0, timestamp.width, timestamp.height)
            ancestor.offsetDescendantRectToMyCoords(timestamp, bounds)
            assertTrue(
                "timestamp bounds $bounds exceed ${ancestor.javaClass.simpleName} height ${ancestor.height}px",
                bounds.top >= ancestor.paddingTop && bounds.bottom <= ancestor.height - ancestor.paddingBottom
            )
            if (ancestor === root) break
            ancestor = ancestor.parent as ViewGroup
        }

        val column = timestamp.parent as ViewGroup
        val contentHeight = column.paddingTop + column.paddingBottom +
            listOf(binding.textViewTitle, binding.textViewChannel, timestamp)
                .filter { it.visibility != View.GONE }
                .sumOf {
                    val margins = it.layoutParams as ViewGroup.MarginLayoutParams
                    textHeight(it) + margins.topMargin + margins.bottomMargin
                }
        // Keep short rows at their usual height when the text fits beside the thumbnail.
        if (!case.longTitle && contentHeight <= binding.previewImage.height) {
            assertEquals(
                "short content should retain the compact thumbnail-height row",
                binding.previewImage.height + root.paddingTop + root.paddingBottom,
                root.height
            )
        }
    }

    private fun textHeight(view: TextView): Int = view.layout.height + view.compoundPaddingTop + view.compoundPaddingBottom

    private data class Case(
        val orientation: Int,
        val fontScale: Float,
        val widthDp: Int,
        val longTitle: Boolean,
        val hasChannel: Boolean
    )
}
