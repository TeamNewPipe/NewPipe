package org.schabi.newpipe.local.subscription

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.ActivityMainBinding
import org.schabi.newpipe.databinding.ActivityPlayerQueueControlBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.databinding.PlaylistHeaderBinding
import org.schabi.newpipe.databinding.SubscriptionChannelsHeaderBinding
import org.schabi.newpipe.databinding.SubscriptionGroupsHeaderBinding
import org.schabi.newpipe.local.subscription.item.GroupsHeader
import org.schabi.newpipe.local.subscription.item.SubscriptionChannelsHeader

@RunWith(AndroidJUnit4::class)
class SubscriptionChannelsHeaderLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun compactAndLargeTextHeadersKeepFullLabelsAndSeparateFocusableTouchTargets() = withAppLanguages { activity, rtl ->
        for (scale in listOf(1f, 2f)) {
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
            val host = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val inflater = LayoutInflater.from(context)
            val channels = SubscriptionChannelsHeaderBinding.inflate(inflater, host, true)
            val groups = SubscriptionGroupsHeaderBinding.inflate(inflater, host, true)
            activity.setContentView(host)
            var channelActions = 0
            SubscriptionChannelsHeader({ channelActions++ }, { channelActions++ }, { channelActions++ }).apply {
                count = 1234
                bind(channels, 0)
            }
            var groupActions = 0
            GroupsHeader(
                context.getString(R.string.feed_groups_header_title),
                { groupActions++ },
                { groupActions++ },
                { groupActions++ },
                { groupActions++ }
            ).bind(groups, 0)

            val locale = requireNotNull(ConfigurationCompat.getLocales(configuration)[0])
            assertEquals(if (rtl) "ar" else "en", locale.language)
            assertEquals(context.getString(R.string.tab_subscriptions), channels.channelsHeaderTitle.text.toString())
            assertEquals(NumberFormat.getIntegerInstance(locale).format(1234), channels.channelsHeaderCount.text.toString())
            assertEquals(context.resources.getQuantityString(R.plurals.subscription_total_count, 1234, 1234), channels.channelsHeaderCount.contentDescription)
            assertTrue(ViewCompat.isAccessibilityHeading(channels.channelsHeaderTitle))

            // Reuse the same bound views across widths to exercise wrapping and unwrapping.
            for (widthDp in listOf(320, 600, 320)) {
                measure(host, widthDp)
                val metrics = "width=$widthDp scale=$scale rtl=$rtl"
                assertEquals(metrics, configuration.layoutDirection, host.layoutDirection)
                assertEquals(metrics, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics), channels.channelsHeaderTitle.textSize, 0.01f)
                assertHeader(
                    channels.root,
                    listOf(channels.channelsHeaderTitle, channels.channelsHeaderCount),
                    listOf(channels.channelsHeaderEdit, channels.channelsHeaderSearch, channels.channelsHeaderCollapse),
                    rtl,
                    metrics
                )
                assertHeader(
                    groups.root,
                    listOf(groups.headerTitle),
                    listOf(groups.headerSearch, groups.headerToggleCollapse, groups.headerToggleViewMode, groups.headerSort),
                    rtl,
                    metrics
                )
                if (widthDp == 320 && scale == 2f) {
                    assertEquals("Large channel labels must exercise wrapping: $metrics", LinearLayout.VERTICAL, channels.root.orientation)
                    assertEquals("Large group labels must exercise wrapping: $metrics", LinearLayout.VERTICAL, groups.root.orientation)
                }
                val title = bounds(channels.root, channels.channelsHeaderTitle)
                val count = bounds(channels.root, channels.channelsHeaderCount)
                assertTrue(metrics, if (rtl) title.left >= count.right else title.right <= count.left)
            }
            assertEquals(9, channelActions)
            assertEquals(12, groupActions)
        }
    }

    @Test
    fun appLanguageMirrorsNavigationAndQueueWhilePlaybackTimeKeepsItsPhysicalDirection() = withAppLanguages { activity, rtl ->
        val direction = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val main = ActivityMainBinding.inflate(activity.layoutInflater)
        activity.setContentView(main.root)
        main.themedSplashOverlay.visibility = View.GONE
        measure(main.root, 400, 800)
        assertEquals(direction, main.root.layoutDirection)
        main.root.openDrawer(GravityCompat.START, false)
        val drawer = main.drawerLayout.navigation
        assertTrue(main.root.isDrawerOpen(GravityCompat.START))
        assertEquals("The drawer must open on the locale's start edge", if (rtl) main.root.width else 0, if (rtl) drawer.right else drawer.left)
        main.root.closeDrawer(GravityCompat.START, false)
        main.mainNavigationRail.visibility = View.VISIBLE
        measure(main.root, 720, 420)
        assertEquals("The navigation rail must use the same start edge", if (rtl) main.mainContent.width else 0, if (rtl) main.mainNavigationRail.right else main.mainNavigationRail.left)

        val binding = PlayerBinding.inflate(activity.layoutInflater)
        activity.setContentView(binding.root)
        binding.loadingPanel.visibility = View.GONE
        binding.playbackControlRoot.visibility = View.VISIBLE
        binding.replayHeatmap.visibility = View.VISIBLE
        binding.sponsorBlockSeekBarMarkers.visibility = View.VISIBLE
        binding.playbackCurrentTime.text = "01:00"
        binding.playbackEndTime.text = "10:00"
        binding.titleTextView.text = activity.getString(R.string.tab_subscriptions)
        val primaryActions = listOf(binding.queueButton, binding.commentsButton, binding.learningNoteButton, binding.segmentsButton, binding.moreOptionsButton)
        primaryActions.forEach { it.visibility = View.VISIBLE }
        measure(binding.root, 640, 360)
        assertEquals(direction, binding.root.layoutDirection)
        assertEquals(direction, binding.metadataView.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, binding.bottomControls.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, binding.playbackSeekBar.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, binding.sponsorBlockSeekBarMarkers.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, binding.replayHeatmap.layoutDirection)
        assertTrue(bounds(binding.root, binding.playPreviousButton).right <= bounds(binding.root, binding.playPauseButton).left)
        assertTrue(bounds(binding.root, binding.playPauseButton).right <= bounds(binding.root, binding.playNextButton).left)
        assertTrue(bounds(binding.root, binding.playbackCurrentTime).right <= bounds(binding.root, binding.playbackSeekBar).left)
        assertTrue(bounds(binding.root, binding.playbackSeekBar).right <= bounds(binding.root, binding.playbackEndTime).left)

        // Physical D-pad navigation follows the mirrored metadata row, not stale LTR IDs.
        assertSame(binding.queueButton, binding.commentsButton.focusSearch(if (rtl) View.FOCUS_RIGHT else View.FOCUS_LEFT))
        assertSame(binding.learningNoteButton, binding.commentsButton.focusSearch(if (rtl) View.FOCUS_LEFT else View.FOCUS_RIGHT))
        assertForwardSeek(binding.playbackSeekBar, "Main player, rtl=$rtl")

        for ((hud, progress, icon) in listOf(
            Triple(binding.volumeRelativeLayout, binding.volumeProgressBar, binding.volumeImageView),
            Triple(binding.brightnessRelativeLayout, binding.brightnessProgressBar, binding.brightnessImageView)
        )) {
            hud.visibility = View.VISIBLE
            measure(binding.root, 640, 360)
            val hudBounds = Rect(0, 0, hud.width, hud.height)
            val metrics = "Player HUD=${hud.resources.getResourceEntryName(hud.id)}, rtl=$rtl, bounds=$hudBounds"
            assertEquals(metrics, direction, hud.layoutDirection)
            for (child in listOf(progress, icon)) {
                val childBounds = bounds(hud, child)
                val childMetrics = "$metrics, child=${child.resources.getResourceEntryName(child.id)}, bounds=$childBounds"
                assertTrue("Player HUD contents need positive space: $childMetrics", child.width > 0 && child.height > 0)
                assertTrue("Player HUD contents must stay contained: $childMetrics", hudBounds.contains(childBounds))
            }
            val iconBounds = bounds(hud, icon)
            assertTrue("Player HUD icon must be horizontally centered: $metrics, icon=$iconBounds", iconBounds.left + iconBounds.right - hud.width in -1..1)
            assertTrue("Player HUD icon must be vertically centered: $metrics, icon=$iconBounds", iconBounds.top + iconBounds.bottom - hud.height in -1..1)
            hud.visibility = View.GONE
        }

        binding.itemsListPanel.visibility = View.VISIBLE
        measure(binding.root, 360, 640)
        assertEquals(direction, binding.itemsListPanel.layoutDirection)
        val queueActions = listOf(binding.repeatButton, binding.shuffleButton, binding.addToPlaylistButton, binding.itemsListClose)
        val queueBounds = queueActions.map { bounds(binding.itemsListControl, it) }
        queueBounds.forEach { assertTrue(Rect(0, 0, binding.itemsListControl.width, binding.itemsListControl.height).contains(it)) }
        queueBounds.zipWithNext().forEach { (first, second) ->
            assertTrue("Queue controls must remain separate and follow app direction", if (rtl) first.left >= second.right else first.right <= second.left)
        }

        for ((widthDp, heightDp) in listOf(400 to 800, 720 to 420)) {
            val landscape = widthDp > heightDp
            val configuration = Configuration(activity.resources.configuration).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = widthDp
                screenHeightDp = heightDp
                smallestScreenWidthDp = minOf(widthDp, heightDp)
            }
            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
            val queuePlayer = ActivityPlayerQueueControlBinding.inflate(LayoutInflater.from(context))
            activity.setContentView(queuePlayer.root)
            queuePlayer.songName.text = activity.getString(R.string.tab_subscriptions)
            queuePlayer.currentTime.text = "01:00"
            queuePlayer.endTime.text = "10:00"
            measure(queuePlayer.root, widthDp, heightDp)
            val metrics = "Queue player, landscape=$landscape rtl=$rtl"
            assertEquals("The orientation-specific layout must be inflated: $metrics", landscape, queuePlayer.root.findViewById<View>(R.id.control_pane) != null)
            assertEquals(metrics, direction, queuePlayer.root.layoutDirection)
            assertEquals(metrics, direction, queuePlayer.metadata.layoutDirection)
            assertEquals(metrics, View.LAYOUT_DIRECTION_LTR, queuePlayer.progressBar.layoutDirection)
            assertEquals(metrics, View.LAYOUT_DIRECTION_LTR, queuePlayer.seekBar.layoutDirection)
            assertTrue(metrics, bounds(queuePlayer.root, queuePlayer.currentTime).right <= bounds(queuePlayer.root, queuePlayer.seekBar).left)
            assertTrue(metrics, bounds(queuePlayer.root, queuePlayer.seekBar).right <= bounds(queuePlayer.root, queuePlayer.endTime).left)
            assertForwardSeek(queuePlayer.seekBar, metrics)
        }
    }

    @Test
    fun playlistTextKeepsSpaceBesideItsThumbnailAndCountInBothAppLanguages() = withAppLanguages { activity, rtl ->
        for (scale in listOf(1f, 2f)) {
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
            val host = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            activity.setContentView(host)
            val inflater = LayoutInflater.from(context)
            val titleText = if (rtl) "دروس اللغة والمستويات" else "Language lessons and levels"
            val uploaderText = if (rtl) "قناة الدروس التعليمية" else "Learning channel"
            for (layout in listOf(R.layout.list_playlist_item, R.layout.list_playlist_mini_item)) {
                val row = inflater.inflate(layout, host, false) as ViewGroup
                host.addView(row)
                row.findViewById<TextView>(R.id.itemTitleView).text = titleText
                row.findViewById<TextView>(R.id.itemUploaderView).text = uploaderText
                measure(host, 360)
                val thumbnail = bounds(row, row.findViewById(R.id.itemThumbnailView))
                for (id in listOf(R.id.itemTitleView, R.id.itemUploaderView)) {
                    val label = row.findViewById<TextView>(id)
                    val rectangle = bounds(row, label)
                    assertTrue("Playlist labels need positive space: rtl=$rtl scale=$scale", label.width > 0)
                    assertTrue(rectangle.left >= 0 && rectangle.right <= row.width)
                    assertTrue("Playlist text belongs beyond the thumbnail at logical end", if (rtl) rectangle.right <= thumbnail.left else rectangle.left >= thumbnail.right)
                }
            }
            val header = PlaylistHeaderBinding.inflate(inflater, host, true)
            header.playlistTitleView.text = titleText
            header.uploaderLayout.visibility = View.VISIBLE
            header.uploaderName.text = uploaderText
            header.playlistStreamCount.text = "123"
            measure(host, 360)
            val uploader = bounds(header.root, header.uploaderLayout)
            val count = bounds(header.root, header.playlistStreamCount)
            assertTrue(header.uploaderName.width > 0 && header.playlistStreamCount.width > 0)
            assertFalse("Remote playlist uploader and count must not overlap", Rect.intersects(uploader, count))
            assertTrue(if (rtl) uploader.left >= count.right else uploader.right <= count.left)
        }
    }

    @Test
    fun downloadRowsAndVideoStatisticsKeepTheirDirectionalChainsInsideTheirBounds() = withAppLanguages { activity, rtl ->
        val direction = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val download = activity.layoutInflater.inflate(R.layout.mission_item_linear, null) as ViewGroup
        val name = download.findViewById<TextView>(R.id.item_name).apply {
            text = if (rtl) "دروس اللغة للمستوى الأول" else "Language lessons for the first level"
        }
        val size = download.findViewById<TextView>(R.id.item_size).apply { text = "100 MB" }
        val status = download.findViewById<TextView>(R.id.item_status).apply { text = "45%" }
        activity.setContentView(download)
        measure(download, 360)
        assertEquals(direction, download.layoutDirection)
        val downloadBounds = Rect(0, 0, download.width, download.height)
        for (label in listOf(name, size, status)) {
            assertTrue("Download text needs positive space: rtl=$rtl", label.width > 0 && label.height > 0)
            assertTrue("Download text must stay inside its row: rtl=$rtl", downloadBounds.contains(bounds(download, label)))
        }
        val icon = bounds(download, download.findViewById(R.id.item_icon))
        val more = bounds(download, download.findViewById(R.id.item_more))
        for (row in listOf(listOf(icon, bounds(download, name), more), listOf(icon, bounds(download, size), bounds(download, status), more))) {
            row.zipWithNext().forEach { (first, second) ->
                assertTrue("Download text must stay between icon and menu: rtl=$rtl", if (rtl) first.left >= second.right else first.right <= second.left)
            }
        }

        for (wide in listOf(false, true)) {
            val configuration = Configuration(activity.resources.configuration).apply {
                orientation = if (wide) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = if (wide) 900 else 360
                screenHeightDp = if (wide) 600 else 800
                smallestScreenWidthDp = if (wide) 600 else 360
            }
            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
            val selectedLayout = TypedValue()
            context.resources.getValue(R.layout.fragment_video_detail, selectedLayout, true)
            assertEquals("Exercise the actual normal and wide landscape resources", wide, selectedLayout.string.toString().contains("layout-w840dp-land"))
            val details = LayoutInflater.from(context).inflate(R.layout.fragment_video_detail, null) as ViewGroup
            val statistics = details.findViewById<ViewGroup>(R.id.details_panel)
            val localLike = details.findViewById<View>(R.id.detail_local_likes)
            val publicLikes = listOf(R.id.detail_thumbs_up_img_view, R.id.detail_thumbs_up_count_view, R.id.detail_thumbs_down_img_view, R.id.detail_thumbs_down_count_view)
                .map { statistics.findViewById<View>(it) }
            val likesRow = publicLikes.first().parent as ViewGroup
            assertEquals("The local heart must remain inline only in the normal resource", !wide, localLike.parent === likesRow)
            assertSame("The existing touch-delegate host must be retained", statistics, likesRow.parent)
            (statistics.parent as ViewGroup).removeView(statistics)
            val host = FrameLayout(context).apply {
                addView(statistics, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            statistics.findViewById<TextView>(R.id.detail_thumbs_up_count_view).text = "1,234"
            statistics.findViewById<TextView>(R.id.detail_thumbs_down_count_view).text = "56"
            activity.setContentView(host)
            // Include the real inline heart's width without starting its database-backed binding.
            if (!wide) localLike.visibility = View.VISIBLE
            val likes = if (wide) publicLikes else listOf(localLike) + publicLikes
            val viewCount = statistics.findViewById<TextView>(R.id.detail_view_count_view)
            for (wideCount in listOf(false, true)) {
                viewCount.text = if (wideCount) {
                    if (rtl) "١٢٣٬٤٥٦٬٧٨٩٬٠١٢٬٣٤٥٬٦٧٨٬٩٠١ مشاهدة" else "123,456,789,012,345,678,901 views"
                } else {
                    "1"
                }
                measure(host, 360)
                assertEquals(direction, statistics.layoutDirection)
                val statisticsBounds = Rect(0, 0, statistics.width, statistics.height)
                val metrics = "rtl=$rtl, wide=$wide, wideCount=$wideCount, panel=$statisticsBounds, host=${host.width}x${host.height}"
                assertEquals("Exercise counts wider and narrower than the likes row: $metrics", wideCount, viewCount.width > likesRow.width)
                for (view in likes + viewCount) {
                    val childBounds = bounds(statistics, view)
                    val childMetrics = "$metrics, child=${view.resources.getResourceEntryName(view.id)}, bounds=$childBounds"
                    assertTrue("Video statistics need positive space: $childMetrics", view.width > 0 && view.height > 0)
                    assertTrue("Video statistics must stay contained: $childMetrics", statisticsBounds.contains(childBounds))
                }
                val likesBounds = likes.map { bounds(statistics, it) }
                likesBounds.zipWithNext().forEachIndexed { index, (first, second) ->
                    val childIds = likes.subList(index, index + 2).joinToString { it.resources.getResourceEntryName(it.id) }
                    assertTrue("Likes icons and counts must remain separate: $metrics, children=$childIds, bounds=$first/$second", if (rtl) first.left >= second.right else first.right <= second.left)
                }
                val rowBounds = bounds(statistics, likesRow)
                val countBounds = bounds(statistics, viewCount)
                val rowMetrics = "$metrics, row=$rowBounds, count=$countBounds"
                assertTrue("The complete likes row must stay contained: $rowMetrics", statisticsBounds.contains(rowBounds))
                assertTrue("The likes row must be centered as a unit: $rowMetrics", rowBounds.left + rowBounds.right - statistics.width in -1..1)
                assertTrue("The count must be centered above the likes: $rowMetrics", countBounds.left + countBounds.right - statistics.width in -1..1)
                assertTrue("The count and likes row must remain vertically separate: $rowMetrics", countBounds.bottom <= rowBounds.top)
                assertEquals(
                    "The likes chain must begin at the row's logical start: $rowMetrics, first=${likesBounds.first()}",
                    if (rtl) rowBounds.right - likesRow.paddingRight else rowBounds.left + likesRow.paddingLeft,
                    if (rtl) likesBounds.first().right else likesBounds.first().left
                )
            }
        }
    }

    private fun assertHeader(root: SubscriptionHeaderLayout, labels: List<TextView>, buttons: List<View>, rtl: Boolean, metrics: String) {
        val direction = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        assertEquals(metrics, direction, root.layoutDirection)
        val labelBounds = labels.map { bounds(root, it) }
        val headerBounds = Rect(0, 0, root.width, root.height)
        labelBounds.forEach { assertTrue(metrics, headerBounds.contains(it)) }
        val title = labelBounds.first()
        assertEquals("Labels belong at the locale's start edge: $metrics", if (rtl) root.width - root.paddingRight else root.paddingLeft, if (rtl) title.right else title.left)
        val actions = buttons.first().parent as ViewGroup
        val actionBounds = bounds(root, actions)
        assertEquals("Actions belong at the locale's end edge: $metrics", if (rtl) root.paddingLeft else root.width - root.paddingRight, if (rtl) actionBounds.left else actionBounds.right)
        if (root.orientation == LinearLayout.HORIZONTAL) {
            labelBounds.forEach { assertTrue(metrics, if (rtl) it.left >= actionBounds.right else it.right <= actionBounds.left) }
        } else {
            labelBounds.forEach { assertTrue(metrics, it.bottom <= actionBounds.top) }
        }
        val minimum = (48 * root.resources.displayMetrics.density).roundToInt()
        val buttonBounds = buttons.map { button ->
            assertEquals(metrics, direction, button.layoutDirection)
            assertTrue(button.isFocusable && button.isClickable)
            assertFalse(button.contentDescription.isNullOrBlank())
            assertTrue(button.width >= minimum && button.height >= minimum)
            button.performClick()
            bounds(root, button).also { rect ->
                assertTrue(metrics, headerBounds.contains(rect))
                labelBounds.forEach { assertFalse(Rect.intersects(it, rect)) }
            }
        }
        buttonBounds.zipWithNext().forEach { (first, second) ->
            assertTrue(metrics, if (rtl) first.left >= second.right else first.right <= second.left)
        }
        labels.forEach { label ->
            val layout = requireNotNull(label.layout)
            val textWidth = label.width - label.compoundPaddingLeft - label.compoundPaddingRight
            assertTrue(layout.lineCount > 0)
            assertEquals(label.text.length, layout.getLineEnd(layout.lineCount - 1))
            repeat(layout.lineCount) { line ->
                assertEquals(0, layout.getEllipsisCount(line))
                assertTrue("Full label must fit: $metrics, ${label.text}", layout.getLineWidth(line) <= textWidth + 1f)
            }
            assertTrue(layout.getLineBottom(layout.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
        }
        if (root.orientation == LinearLayout.VERTICAL) {
            val labelWidth = labels.sumOf(::intrinsicWidth)
            assertTrue("Wrapped labels must need their own row: $metrics", labelWidth + actions.width > root.width - root.paddingLeft - root.paddingRight)
        }
    }

    private fun withAppLanguages(action: (AboutActivity, Boolean) -> Unit) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val original = AppCompatDelegate.getApplicationLocales()
            var wasInTouchMode = false
            scenario.onActivity { wasInTouchMode = it.window.decorView.isInTouchMode }
            try {
                instrumentation.setInTouchMode(false)
                // Use the same application-language API as Settings, including the OS path on 33+.
                for (language in listOf("ar", "en")) {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
                    val deadline = SystemClock.uptimeMillis() + 5000
                    var resolved = false
                    while (!resolved && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { resolved = ConfigurationCompat.getLocales(it.resources.configuration)[0]?.language == language }
                        if (!resolved) SystemClock.sleep(20)
                    }
                    assertTrue("The app language change must reach its activity", resolved)
                    scenario.onActivity { activity ->
                        assertEquals(language, ConfigurationCompat.getLocales(activity.resources.configuration)[0]?.language)
                        assertTrue("The installed app must support RTL without a test-only ApplicationInfo override", activity.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
                        action(activity, language == "ar")
                    }
                }
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
                instrumentation.waitForIdleSync()
                instrumentation.setInTouchMode(wasInTouchMode)
            }
        }
    }

    private fun intrinsicWidth(label: TextView): Int {
        val margins = label.layoutParams as ViewGroup.MarginLayoutParams
        return ceil(label.paint.measureText(label.text.toString())).toInt() + label.compoundPaddingLeft + label.compoundPaddingRight + margins.leftMargin + margins.rightMargin
    }

    private fun measure(root: View, widthDp: Int, heightDp: Int? = null) {
        val density = root.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = heightDp?.let { View.MeasureSpec.makeMeasureSpec((it * density).roundToInt(), View.MeasureSpec.EXACTLY) } ?: View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), height)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun bounds(root: ViewGroup, view: View) = Rect().apply {
        view.getDrawingRect(this)
        root.offsetDescendantRectToMyCoords(view, this)
    }

    private fun assertForwardSeek(seek: SeekBar, metrics: String) {
        seek.isEnabled = true
        seek.max = 1000
        val trackWidth = seek.width - seek.paddingLeft - seek.paddingRight
        assertTrue("The timeline needs a usable touch track: $metrics", trackWidth > 0 && seek.height > 0)
        tap(seek, seek.paddingLeft + trackWidth / 4f, seek.height / 2f)
        val earlier = seek.progress
        tap(seek, seek.paddingLeft + trackWidth * 3 / 4f, seek.height / 2f)
        assertTrue("Moving right must advance playback: $metrics", seek.progress > earlier)
    }

    private fun tap(view: View, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).withIndex()) {
            val event = MotionEvent.obtain(now, now + index * 16, action, x, y, 0)
            try {
                assertTrue(view.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
    }
}
