/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.info_list.holder

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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class StreamUploaderAlignmentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun shortChannelNamesStayBesideAvatarsInPhoneAndWideLayouts() {
        instrumentation.runOnMainSync {
            cases().forEach { case ->
                val fixture = createRow(case)
                bindLabels(fixture, "Channel", "12K views")
                assertAlignment(fixture)
                assertContained(fixture)
                assertTextStartAligned(fixture)
                val availableWidth = fixture.uploaderRoot.width - fixture.avatar.width - fixture.gap
                assertTrue("${case.name}: blank row space must remain outside the channel-name target", fixture.name.width < availableWidth)
                if (case.name in listOf("list-phone", "portrait-card-phone", "landscape-card-wide-rtl")) {
                    savePreview(fixture)
                }
            }
        }
    }

    @After
    fun preservePreviews() {
        val folder = File(context.getExternalFilesDir(null), "player-layout-previews")
        val files = folder.listFiles().orEmpty().filter { it.name.startsWith("stream-uploader-alignment-") }
        if (files.isEmpty()) return
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        val commands = listOf("mkdir -p $destination") + files.map { "cp ${it.absolutePath} $destination/" }
        val automation = instrumentation.uiAutomation
        commands.forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test
    fun longLabelsStayBoundedAndRebindingShortLabelsRestoresCompactTargets() {
        instrumentation.runOnMainSync {
            val cases = cases() + Case("portrait-card-large-rtl", R.layout.list_stream_card_item, 360, rtl = true, fontScale = 1.8f)
            cases.forEach { case ->
                val fixture = createRow(case)
                bindLabels(fixture, "Channel", "12K views")
                val shortNameWidth = fixture.name.width

                bindLabels(
                    fixture,
                    "A channel with an unusually long display name ".repeat(8),
                    "999,999 views • Streamed a very long time ago ".repeat(8)
                )
                assertAlignment(fixture)
                assertContained(fixture)

                bindLabels(fixture, "Channel", "12K views")
                assertAlignment(fixture)
                assertContained(fixture)
                assertEquals("${case.name}: a recycled row must release its long-label width", shortNameWidth, fixture.name.width)
                assertTextStartAligned(fixture)
            }
        }
    }

    private fun cases(): List<Case> = listOf(
        Case("list-phone", R.layout.list_stream_item, 360),
        Case("list-wide-rtl", R.layout.list_stream_item, 960, rtl = true),
        Case("grid-phone", R.layout.list_stream_grid_item, 360),
        Case("grid-wide-rtl", R.layout.list_stream_grid_item, 960, rtl = true),
        Case("portrait-card-phone", R.layout.list_stream_card_item, 360),
        Case("portrait-card-wide-rtl", R.layout.list_stream_card_item, 960, rtl = true),
        Case("landscape-card-phone", R.layout.list_stream_card_item, 640, landscape = true),
        Case("landscape-card-wide-rtl", R.layout.list_stream_card_item, 960, landscape = true, rtl = true)
    )

    private fun createRow(case: Case): Fixture {
        val configuration = Configuration(context.resources.configuration).apply {
            val locale = Locale.forLanguageTag(if (case.rtl) "ar" else "en")
            setLocale(locale)
            setLayoutDirection(locale)
            fontScale = case.fontScale
            orientation = if (case.landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = case.widthDp
            screenHeightDp = if (case.landscape) 360 else 1200
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val configuredContext = context.createConfigurationContext(configuration)
        val themed = object : ContextThemeWrapper(configuredContext, R.style.LightTheme) {
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                // Exercise relative constraints without mutating the application's RTL setting.
                flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
            }
        }
        val root = LayoutInflater.from(themed).inflate(case.layout, FrameLayout(themed), false) as ViewGroup
        val uploaderRoot = root.findViewById<ViewGroup>(R.id.itemUploaderRoot)
        root.layoutDirection = configuration.layoutDirection
        uploaderRoot.layoutDirection = configuration.layoutDirection
        root.findViewById<TextView>(R.id.itemVideoTitleView).text = "A sample video"
        val avatar = root.findViewById<View>(R.id.itemUploaderAvatarView).apply { visibility = View.VISIBLE }
        return Fixture(
            case,
            root,
            uploaderRoot,
            avatar,
            root.findViewById(R.id.itemUploaderView),
            root.findViewById(R.id.itemAdditionalDetails),
            (case.widthDp * themed.resources.displayMetrics.density).roundToInt(),
            themed.resources.getDimensionPixelSize(R.dimen.margin_small)
        )
    }

    private fun bindLabels(fixture: Fixture, channel: String, details: String) {
        fixture.name.text = channel
        fixture.details.text = details
        fixture.root.measure(
            View.MeasureSpec.makeMeasureSpec(fixture.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        fixture.root.layout(0, 0, fixture.width, fixture.root.measuredHeight)
    }

    private fun assertAlignment(fixture: Fixture) {
        val case = fixture.case
        val avatar = bounds(fixture, fixture.avatar)
        val name = bounds(fixture, fixture.name)
        val details = bounds(fixture, fixture.details)
        val avatarGap = if (case.rtl) avatar.left - name.right else name.left - avatar.right
        assertEquals("${case.name}: the channel name must start next to the avatar", fixture.gap.toDouble(), avatarGap.toDouble(), 1.0)
        if (case.layout == R.layout.list_stream_card_item && !case.landscape) {
            val metadataGap = if (case.rtl) name.left - details.right else details.left - name.right
            assertEquals("${case.name}: inline metadata must follow the name with a small gap", fixture.gap.toDouble(), metadataGap.toDouble(), 1.0)
        } else {
            val nameStart = if (case.rtl) name.right else name.left
            val detailsStart = if (case.rtl) details.right else details.left
            assertEquals("${case.name}: stacked metadata must align with the channel name", nameStart, detailsStart)
            assertTrue("${case.name}: stacked labels must not overlap", name.bottom <= details.top)
        }
        assertTrue("${case.name}: name and metadata must not overlap", !Rect.intersects(name, details))
    }

    private fun assertContained(fixture: Fixture) {
        val row = Rect(0, 0, fixture.uploaderRoot.width, fixture.uploaderRoot.height)
        listOf(fixture.avatar, fixture.name, fixture.details).forEach { view ->
            val bounds = bounds(fixture, view)
            val label = view.resources.getResourceEntryName(view.id)
            assertTrue("${fixture.case.name}: $label must retain visible space", bounds.width() > 0 && bounds.height() > 0)
            assertTrue("${fixture.case.name}: $label must fit inside the metadata row: $bounds in $row", row.contains(bounds))
        }
    }

    private fun assertTextStartAligned(fixture: Fixture) {
        listOf(fixture.name, fixture.details).forEach { view ->
            val textLayout = requireNotNull(view.layout)
            val actualStart = if (fixture.case.rtl) textLayout.getLineRight(0) else textLayout.getLineLeft(0)
            val expectedStart = if (fixture.case.rtl) view.width - view.compoundPaddingLeft - view.compoundPaddingRight else 0
            assertEquals("${fixture.case.name}: text must align with the start of its view", expectedStart.toDouble(), actualStart.toDouble(), 1.0)
        }
    }

    private fun bounds(fixture: Fixture, view: View): Rect = Rect(0, 0, view.width, view.height).apply {
        fixture.uploaderRoot.offsetDescendantRectToMyCoords(view, this)
    }

    private fun savePreview(fixture: Fixture) {
        val row = fixture.uploaderRoot
        val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorSurface, Color.WHITE))
            row.draw(canvas)
            val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
            File(folder, "stream-uploader-alignment-${fixture.case.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private data class Case(
        val name: String,
        val layout: Int,
        val widthDp: Int,
        val landscape: Boolean = false,
        val rtl: Boolean = false,
        val fontScale: Float = 1f
    )

    private data class Fixture(
        val case: Case,
        val root: ViewGroup,
        val uploaderRoot: ViewGroup,
        val avatar: View,
        val name: TextView,
        val details: TextView,
        val width: Int,
        val gap: Int
    )
}
