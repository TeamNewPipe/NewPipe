package org.schabi.newpipe.util

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.databinding.ListStreamFilterChipsBinding
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StreamFilterSelectionTest {
    @Test
    fun contentAndWatchStatusAreIntersectedWhileContentChoicesAreCombined() {
        val selection = StreamFilterSelection.parse("VIDEOS,SHORTS,UNWATCHED")
        assertTrue(selection.matches(stream(), null))
        assertTrue(selection.matches(stream(short = true), null))
        assertFalse(selection.matches(stream(type = StreamType.LIVE_STREAM), null))
        assertFalse(selection.matches(stream(), StreamStateEntity(1, 120_000)))
        assertEquals(selection.encode(), StreamFilterSelection.parse(selection.encode()).encode())
    }

    @Test
    fun finishedAndPartialFiltersRemainSeparateAndLegacyValuesRestore() {
        val stream = stream()
        val partial = StreamStateEntity(1, 120_000)
        val finished = StreamStateEntity(1, 590_000)
        assertTrue(StreamFilterSelection.parse("WATCHED").matches(stream, finished))
        assertFalse(StreamFilterSelection.parse("WATCHED").matches(stream, partial))
        assertTrue(StreamFilterSelection.parse("PARTIALLY_WATCHED").matches(stream, partial))
        assertFalse(StreamFilterSelection.parse("PARTIALLY_WATCHED").matches(stream, finished))
        assertEquals("LIVE", StreamFilterSelection.parse("LIVE").encode())
        assertFalse(StreamFilterSelection.parse("NONE,REMOVED_FILTER").isActive)
    }

    @Test
    fun selectingAllClearsOnlyItsOwnRowAndLastDeselectionRestoresAll() {
        val binding = binding()
        var value = ""
        StreamFilterControls.bind(binding, "VIDEOS,UNWATCHED", true) { value = it }
        binding.filterShorts.performClick()
        assertEquals("VIDEOS,SHORTS,UNWATCHED".split(",").toSet(), value.split(",").toSet())
        binding.filterWatchAll.performClick()
        assertEquals(setOf("VIDEOS", "SHORTS"), value.split(",").toSet())
        binding.filterAll.performClick()
        assertEquals("", value)
        binding.filterUnwatched.performClick()
        assertEquals("UNWATCHED", value)
        binding.filterUnwatched.performClick()
        assertEquals("", value)
        assertTrue(binding.filterWatchAll.isChecked)
    }

    @Test
    fun channelControlsOnlyShowWatchStatusAndRebindingDoesNotNotifyOldListener() {
        val binding = binding()
        var oldCalls = 0
        StreamFilterControls.bind(binding, "SHORTS,UNWATCHED", true) { oldCalls++ }
        var value = ""
        StreamFilterControls.bind(binding, "PARTIALLY_WATCHED", false) { value = it }
        assertEquals(0, oldCalls)
        assertEquals(View.GONE, (binding.streamFilterChipGroup.parent as View).visibility)
        binding.filterWatched.performClick()
        assertEquals(setOf("PARTIALLY_WATCHED", "WATCHED"), value.split(",").toSet())
    }

    @Test
    fun explicitSelectionsCanRevealItemsHiddenByVisibilityDefaults() {
        val stream = stream()
        val progress = StreamStateEntity(1, 590_000)
        val hiddenVideos = setOf(StreamListFilter.SHORTS)
        assertFalse(StreamFilterSelection.parse("").matchesVisible(stream, progress, hiddenVideos, false, false))
        assertTrue(StreamFilterSelection.parse("VIDEOS,WATCHED").matchesVisible(stream, progress, hiddenVideos, false, false))
        assertFalse(StreamFilterSelection.parse("VIDEOS,UNWATCHED").matchesVisible(stream, progress, hiddenVideos, true, true))
    }

    @Test
    fun downloadedIsIndependentOfWatchStatusAndContentTypes() {
        val selection = StreamFilterSelection.parse("VIDEOS,UNWATCHED,DOWNLOADED")
        assertTrue(selection.downloadedOnly)
        assertEquals(setOf(StreamListFilter.UNWATCHED), selection.watchFilters)
        assertFalse(selection.matchesDownloaded(false))
        assertTrue(selection.matchesDownloaded(true))
        assertEquals(
            setOf("VIDEOS", "UNWATCHED", "DOWNLOADED"),
            StreamFilterSelection.parse(selection.encode()).encode().split(",").toSet()
        )
        val binding = binding()
        var value = ""
        StreamFilterControls.bind(binding, "VIDEOS,UNWATCHED,DOWNLOADED", true) { value = it }
        assertTrue(binding.filterDownloaded.isChecked)
        binding.filterDownloaded.performClick()
        assertEquals(setOf("VIDEOS", "UNWATCHED"), value.split(",").toSet())
        binding.filterDownloaded.performClick()
        assertEquals(setOf("VIDEOS", "UNWATCHED", "DOWNLOADED"), value.split(",").toSet())
        binding.filterWatchAll.performClick()
        assertEquals(setOf("VIDEOS", "DOWNLOADED"), value.split(",").toSet())
    }

    private fun binding(): ListStreamFilterChipsBinding {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        return ListStreamFilterChipsBinding.inflate(LayoutInflater.from(context))
    }

    private fun stream(type: StreamType = StreamType.VIDEO_STREAM, short: Boolean = false) = StreamInfoItem(0, "https://example.com/video", "Video", type).apply {
        duration = 600
        setShortFormContent(short)
    }
}
