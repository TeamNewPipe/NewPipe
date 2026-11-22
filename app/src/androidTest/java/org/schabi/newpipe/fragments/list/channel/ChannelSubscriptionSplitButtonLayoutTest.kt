package org.schabi.newpipe.fragments.list.channel

import android.content.Context
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentChannelBinding

@RunWith(AndroidJUnit4::class)
class ChannelSubscriptionSplitButtonLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun localizedAndLargeTextKeepsBothActionsAlignedAndTheirOutlinesVisible() {
        instrumentation.runOnMainSync {
            val cases = listOf(
                Case("light", "en", 1f, R.style.LightTheme),
                Case("large", "en", 2f, R.style.LightTheme),
                Case("french-large-dark", "fr", 2f, R.style.DarkTheme, labelWidthDp = 112),
                Case("rtl-large-black", "ar", 2f, R.style.BlackTheme),
                Case("landscape", "en", 1f, R.style.LightTheme, landscape = true)
            )
            cases.forEach { case ->
                val fixture = createHeader(case)
                listOf(false, true, false).forEachIndexed { index, subscribed ->
                    setSubscribed(fixture, subscribed)
                    measure(fixture)
                    if (index == 0 && case.name in PREVIEW_CASES) {
                        savePreview(fixture, "${case.name}-after")
                    }
                    assertGeometry(fixture)
                    assertRenderedOutlines(fixture)

                    // ConstraintLayout may remeasure the wrap-content row with its final exact
                    // height. The two actions must also fit that bounded measurement pass.
                    val split = fixture.binding.channelSubscriptionActions
                    split.forceLayout()
                    split.measure(exactly(split.width), exactly(split.height))
                    split.layout(split.left, split.top, split.right, split.bottom)
                    assertGeometry(fixture)
                    assertRenderedOutlines(fixture)

                    if (index == 0 && case.name == "french-large-dark") {
                        assertTrue("The real French label should wrap in the narrow slot", fixture.binding.channelSubscribeButton.lineCount > 1)
                    }
                    if (index == 0 && case.name == "light") {
                        captureLegacyBaselinePreview(fixture)
                    }
                }
            }
        }
    }

    @Test
    fun shorterLabelsAndHiddenGroupActionLetTheSplitButtonShrinkAgain() {
        instrumentation.runOnMainSync {
            val fixture = createHeader(Case("shrink", "fr", 2f, R.style.LightTheme, labelWidthDp = 112))
            val primary = fixture.binding.channelSubscribeButton
            val secondary = fixture.binding.channelGroupButton
            setSubscribed(fixture, false)
            measure(fixture)
            assertGeometry(fixture)
            assertTrue(primary.lineCount > 1)
            val multilineHeight = primary.height

            setSubscribed(fixture, true)
            primary.maxWidth = Int.MAX_VALUE
            measure(fixture)
            assertGeometry(fixture)
            assertRenderedOutlines(fixture)
            assertEquals(1, primary.lineCount)
            assertTrue("A shorter label must release the previous multiline height", primary.height < multilineHeight)
            val shortSplitHeight = primary.height

            primary.maxWidth = dp(primary.context, 112)
            setSubscribed(fixture, false)
            measure(fixture)
            assertGeometry(fixture)
            assertEquals(multilineHeight, primary.height)

            secondary.visibility = View.GONE
            primary.maxWidth = Int.MAX_VALUE
            setSubscribed(fixture, true)
            measure(fixture)
            assertContained(fixture.binding.channelSubscriptionActions, primary)
            assertContained(fixture.binding.channelMetadataRow, primary)
            assertMinimumTarget(primary)
            assertTextFits(primary)
            assertTrue("The remaining action must shrink when the group action disappears", primary.height < multilineHeight)
            assertRenderedOutlines(fixture)
            val singleHeight = primary.height

            secondary.visibility = View.VISIBLE
            measure(fixture)
            assertGeometry(fixture)
            assertRenderedOutlines(fixture)
            assertEquals("Showing the group action must not restore a stale multiline height", shortSplitHeight, primary.height)
            assertTrue(primary.height >= singleHeight)
        }
    }

    @After
    fun preservePreviews() {
        // Connected tests uninstall the app. Copy previews outside app storage, and connect
        // UiAutomation off the main thread for older Android versions.
        val folder = File(context.getExternalFilesDir(null), "player-layout-previews")
        val files = folder.listFiles().orEmpty().filter { it.name.startsWith(PREVIEW_PREFIX) }
        if (files.isEmpty()) return
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        val commands = listOf("mkdir -p $destination") + files.map { "cp ${it.absolutePath} $destination/" }
        val automation = instrumentation.uiAutomation
        commands.forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun createHeader(case: Case): Fixture {
        val configuration = Configuration(context.resources.configuration).apply {
            fontScale = case.fontScale
            val locale = Locale.forLanguageTag(case.language)
            setLocale(locale)
            setLayoutDirection(locale)
            orientation = if (case.landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = if (case.landscape) 720 else 360
            screenHeightDp = if (case.landscape) 360 else 720
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (case.style == R.style.LightTheme) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        }
        val configuredContext = context.createConfigurationContext(configuration)
        val direction = configuration.layoutDirection
        val palette = if (direction == View.LAYOUT_DIRECTION_RTL) {
            // Exercise RTL without changing the application's manifest or shared ApplicationInfo.
            object : ContextThemeWrapper(configuredContext, case.style) {
                override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                    flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
                }
            }
        } else {
            ContextThemeWrapper(configuredContext, case.style)
        }
        val binding = FragmentChannelBinding.inflate(LayoutInflater.from(palette), FrameLayout(palette), false)
        binding.root.layoutDirection = direction
        binding.channelMetadataRow.layoutDirection = direction
        binding.channelSubscriptionActions.layoutDirection = direction
        binding.channelTitleView.text = "A channel with a longer name"
        binding.channelSubscriberView.text = "123,456 subscribers"
        binding.channelGroupButton.visibility = View.VISIBLE
        case.labelWidthDp?.let { binding.channelSubscribeButton.maxWidth = dp(palette, it) }
        return Fixture(binding, dp(palette, configuration.screenWidthDp), dp(palette, configuration.screenHeightDp), direction)
    }

    private fun setSubscribed(fixture: Fixture, subscribed: Boolean) {
        fixture.binding.channelSubscribeButton.apply {
            isActivated = subscribed
            setText(if (subscribed) R.string.subscribed_button_title else R.string.subscribe_button_title)
            jumpDrawablesToCurrentState()
        }
        fixture.binding.channelGroupButton.apply {
            isActivated = subscribed
            jumpDrawablesToCurrentState()
        }
    }

    private fun measure(fixture: Fixture) {
        fixture.binding.root.requestLayout()
        fixture.binding.root.measure(exactly(fixture.width), exactly(fixture.height))
        fixture.binding.root.layout(0, 0, fixture.width, fixture.height)
    }

    private fun assertGeometry(fixture: Fixture) {
        val binding = fixture.binding
        val split = binding.channelSubscriptionActions
        val primary = bounds(split, binding.channelSubscribeButton)
        val secondary = bounds(split, binding.channelGroupButton)
        assertEquals("The split control must resolve the requested layout direction", fixture.layoutDirection, split.layoutDirection)
        if (fixture.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            assertTrue("The group menu must precede the label visually in RTL", secondary.right <= primary.left)
        } else {
            assertTrue("The group menu must follow the label visually in LTR", primary.right <= secondary.left)
        }
        assertEquals("Both halves must start on the same row", primary.top, secondary.top)
        assertEquals("Both bottom outlines must end on the same row", primary.bottom, secondary.bottom)
        listOf(binding.channelSubscribeButton, binding.channelGroupButton).forEach { button ->
            assertContained(split, button)
            assertContained(binding.channelMetadataRow, button)
            assertContained(binding.appBarLayout, button)
            assertMinimumTarget(button)
        }
        assertTextFits(binding.channelSubscribeButton)
    }

    private fun assertContained(container: ViewGroup, child: View) {
        val rectangle = bounds(container, child)
        assertTrue("${child.resources.getResourceEntryName(child.id)} must fit inside ${container.resources.getResourceEntryName(container.id)}: $rectangle in ${container.width}x${container.height}", Rect(0, 0, container.width, container.height).contains(rectangle))
    }

    private fun assertMinimumTarget(button: MaterialButton) {
        val minimum = dp(button.context, 48)
        assertTrue("Action width must retain the 48dp touch target", button.width >= minimum)
        assertTrue("Action height must retain the 48dp touch target", button.height >= minimum)
    }

    private fun assertTextFits(button: MaterialButton) {
        val textHeight = button.layout.getLineBottom(button.lineCount - 1)
        assertTrue("The label must fit above the bottom padding and outline", textHeight <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
    }

    private fun assertRenderedOutlines(fixture: Fixture) {
        val buttons = listOf(fixture.binding.channelSubscribeButton, fixture.binding.channelGroupButton)
            .filter { it.visibility == View.VISIBLE }
        val rendered = renderHeader(fixture)
        try {
            val background = renderWithoutButtons(fixture, buttons)
            try {
                buttons.forEach { button ->
                    val reference = checkNotNull(background.copy(Bitmap.Config.ARGB_8888, true))
                    try {
                        val rectangle = bounds(fixture.binding.root, button)
                        // Keep the original backdrop and global raster coordinates so curved
                        // antialiased strokes are sampled the same way as in the full header.
                        val canvas = Canvas(reference)
                        canvas.translate(rectangle.left.toFloat(), rectangle.top.toFloat())
                        button.draw(canvas)
                        val band = maxOf(2, button.strokeWidth + 1)
                        val edgePixels = IntArray(4)
                        for (y in 0 until button.height) {
                            for (x in 0 until button.width) {
                                if (x >= band && x < button.width - band && y >= band && y < button.height - band) continue
                                val rootX = rectangle.left + x
                                val rootY = rectangle.top + y
                                val expected = reference.getPixel(rootX, rootY)
                                val actual = rendered.getPixel(rootX, rootY)
                                assertTrue("The header must preserve ${button.resources.getResourceEntryName(button.id)} outline at ($x, $y): expected ${Integer.toHexString(expected)}, actual ${Integer.toHexString(actual)}", matchesRenderedColor(expected, actual))
                                if (matchesRenderedColor(expected, background.getPixel(rootX, rootY))) continue
                                if (y < band) edgePixels[0]++
                                if (y >= button.height - band) edgePixels[1]++
                                if (x < band) edgePixels[2]++
                                if (x >= button.width - band) edgePixels[3]++
                            }
                        }
                        assertTrue("The isolated reference must contain all four outline edges: ${edgePixels.contentToString()}", edgePixels.all { it > 0 })
                    } finally {
                        reference.recycle()
                    }
                }
            } finally {
                background.recycle()
            }
        } finally {
            rendered.recycle()
        }
    }

    private fun matchesRenderedColor(expected: Int, actual: Int): Boolean {
        // Separate Canvas composites can round a channel by one 8-bit step. Keep alpha exact
        // and allow only that single RGB step; missing outline pixels still fail the comparison.
        return Color.alpha(expected) == Color.alpha(actual) &&
            abs(Color.red(expected) - Color.red(actual)) <= 1 &&
            abs(Color.green(expected) - Color.green(actual)) <= 1 &&
            abs(Color.blue(expected) - Color.blue(actual)) <= 1
    }

    private fun renderWithoutButtons(fixture: Fixture, buttons: List<MaterialButton>): Bitmap {
        buttons.forEach { it.visibility = View.INVISIBLE }
        try {
            return renderHeader(fixture)
        } finally {
            buttons.forEach { it.visibility = View.VISIBLE }
        }
    }

    private fun captureLegacyBaselinePreview(fixture: Fixture) {
        val split = fixture.binding.channelSubscriptionActions
        split.isBaselineAligned = true
        split.requestLayout()
        try {
            measure(fixture)
            val primary = bounds(split, fixture.binding.channelSubscribeButton)
            val secondary = bounds(split, fixture.binding.channelGroupButton)
            if (primary.top != secondary.top || primary.bottom > split.height || secondary.bottom > split.height) {
                savePreview(fixture, "light-before-baseline-alignment")
            }
        } finally {
            split.isBaselineAligned = false
            split.requestLayout()
            measure(fixture)
        }
    }

    private fun renderHeader(fixture: Fixture): Bitmap {
        val height = minOf(fixture.height, fixture.binding.appBarLayout.bottom + fixture.binding.tabLayout.height)
        return Bitmap.createBitmap(fixture.width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(MaterialColors.getColor(fixture.binding.root, MaterialR.attr.colorSurface))
            fixture.binding.root.draw(canvas)
        }
    }

    private fun savePreview(fixture: Fixture, name: String) {
        val bitmap = renderHeader(fixture)
        try {
            val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
            File(folder, "$PREVIEW_PREFIX$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun bounds(container: ViewGroup, child: View): Rect = Rect(0, 0, child.width, child.height).also {
        container.offsetDescendantRectToMyCoords(child, it)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun dp(palette: Context, value: Int) = (value * palette.resources.displayMetrics.density).roundToInt()

    private data class Case(
        val name: String,
        val language: String,
        val fontScale: Float,
        val style: Int,
        val labelWidthDp: Int? = null,
        val landscape: Boolean = false
    )

    private data class Fixture(val binding: FragmentChannelBinding, val width: Int, val height: Int, val layoutDirection: Int)

    companion object {
        private const val PREVIEW_PREFIX = "channel-subscription-"
        private val PREVIEW_CASES = setOf("light", "french-large-dark", "rtl-large-black")
    }
}
