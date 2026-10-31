package org.schabi.newpipe.info_list

import androidx.recyclerview.widget.DiffUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class InfoItemDiffCallbackTest {

    private fun createStreamInfoItem(
        serviceId: Int = 0,
        url: String,
        name: String,
        duration: Long = 100L,
        uploaderName: String = "Uploader"
    ): StreamInfoItem {
        val item = StreamInfoItem(serviceId, url, name, StreamType.VIDEO_STREAM)
        item.duration = duration
        item.uploaderName = uploaderName
        return item
    }

    @Test
    fun testAreItemsTheSameForIdenticalStreams() {
        val item1 = createStreamInfoItem(0, "https://youtube.com/watch?v=123", "Video 1")
        val item2 = createStreamInfoItem(0, "https://youtube.com/watch?v=123", "Video 1")

        val diffCallback = InfoItemDiffCallback(listOf(item1), listOf(item2))
        assertTrue(diffCallback.areItemsTheSame(0, 0))
        assertTrue(diffCallback.areContentsTheSame(0, 0))
    }

    @Test
    fun testAreItemsTheSameForDifferentUrls() {
        val item1 = createStreamInfoItem(0, "https://youtube.com/watch?v=123", "Video 1")
        val item2 = createStreamInfoItem(0, "https://youtube.com/watch?v=456", "Video 2")

        val diffCallback = InfoItemDiffCallback(listOf(item1), listOf(item2))
        assertFalse(diffCallback.areItemsTheSame(0, 0))
    }

    @Test
    fun testAreContentsTheSameForModifiedStream() {
        val item1 = createStreamInfoItem(0, "https://youtube.com/watch?v=123", "Video 1", 100L)
        val item2 = createStreamInfoItem(0, "https://youtube.com/watch?v=123", "Video 1 Updated", 200L)

        val diffCallback = InfoItemDiffCallback(listOf(item1), listOf(item2))
        assertTrue(diffCallback.areItemsTheSame(0, 0))
        assertFalse(diffCallback.areContentsTheSame(0, 0))
    }

    @Test
    fun testPaginationAppendedItemsDiff() {
        val page1 = listOf<InfoItem>(
            createStreamInfoItem(0, "https://youtube.com/watch?v=1", "Video 1"),
            createStreamInfoItem(0, "https://youtube.com/watch?v=2", "Video 2")
        )
        val page2 = page1 + listOf(
            createStreamInfoItem(0, "https://youtube.com/watch?v=3", "Video 3"),
            createStreamInfoItem(0, "https://youtube.com/watch?v=4", "Video 4")
        )

        val diffCallback = InfoItemDiffCallback(page1, page2)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        assertEquals(2, diffCallback.oldListSize)
        assertEquals(4, diffCallback.newListSize)
        assertTrue(diffCallback.areItemsTheSame(0, 0))
        assertTrue(diffCallback.areItemsTheSame(1, 1))
        assertNotNull(diffResult)
    }

    @Test
    fun testDuplicateItemsInPlaylist() {
        val stream1 = createStreamInfoItem(0, "https://youtube.com/watch?v=1", "Video 1")
        val stream2 = createStreamInfoItem(0, "https://youtube.com/watch?v=2", "Video 2")
        val stream1Duplicate = createStreamInfoItem(0, "https://youtube.com/watch?v=1", "Video 1 Duplicate")

        val oldList = listOf<InfoItem>(stream1, stream2, stream1Duplicate)
        val newList = listOf<InfoItem>(stream1, stream2, stream1Duplicate)

        val diffCallback = InfoItemDiffCallback(oldList, newList)

        assertTrue(diffCallback.areItemsTheSame(0, 0))
        assertTrue(diffCallback.areItemsTheSame(2, 2))

        assertFalse(diffCallback.areItemsTheSame(0, 2))
        assertFalse(diffCallback.areItemsTheSame(2, 0))
    }
}
