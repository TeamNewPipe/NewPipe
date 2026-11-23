/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.history

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.learning.LearningPlaylistContext
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.util.ExtractorHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreviousPlaybackHistoryTest {
    @Test
    fun restoredQueueKeepsDuplicateSelectionShuffleBackupAndIndependentSourceMetadata() {
        val first = remote("duplicate", "First occurrence")
        val second = remote("duplicate", "Second occurrence")
        val local = PlayQueueItem.localMedia("Local", "content://media/1", 120, "Artist", "Album", "Folder", "audio/flac", 1, false, "content://art/1")
        val radio = PlayQueueItem.radioStation("Radio", "https://radio.invalid/stream", "Station", true, "https://radio.invalid/logo")
        val television = PlayQueueItem.liveTv("Live TV", "https://tv.invalid/stream", "Group", "video/mp2t", "https://tv.invalid/logo")
        val original = listOf(first, local, radio, second, television)
        val queue = LocalMediaPlayQueue(original, 3)
        queue.shuffleFromStart()
        queue.index = queue.streams.indexOf(second)
        val shuffledNames = queue.streams.map { it.title }
        val selectedIndex = queue.index
        val history = PreviousPlaybackHistory()
        assertTrue(history.push(queue, 78_123L, false, REPEAT_MODE_ALL))

        // Disposing or editing the live queue cannot affect the return point.
        queue.setRecovery(queue.index, 1)
        local.applyLocalMetadata("Edited", "Edited artist", "Edited album", 400)
        queue.remove(0)
        queue.append(listOf(remote("later", "Appended later")))
        queue.dispose()

        val playback = history.pop()!!
        val restored = playback.queue
        assertFalse(playback.playWhenReady)
        assertEquals(REPEAT_MODE_ALL, playback.repeatMode)
        assertEquals(shuffledNames, restored.streams.map { it.title })
        assertEquals(selectedIndex, restored.index)
        assertEquals("Second occurrence", restored.item!!.title)
        assertEquals(78_123L, restored.item!!.recoveryPosition)
        assertTrue(restored.isShuffled)
        assertFalse(restored.isDisposed)
        assertNull(restored.broadcastReceiver)
        restored.init()
        val selected = restored.item
        restored.unshuffle()
        assertEquals(listOf("First occurrence", "Local", "Radio", "Second occurrence", "Live TV"), restored.streams.map { it.title })
        assertEquals(3, restored.index)
        assertSame(selected, restored.item)
        assertNotSame(second, restored.item)
        assertEquals(first.url, restored.streams[0].url)
        assertEquals("https://album.invalid/duplicate", restored.streams[0].albumUrl)
        assertEquals("https://art.invalid/duplicate", restored.streams[0].thumbnails.single().url)
        val restoredLocal = restored.streams[1]
        assertEquals("Artist", restoredLocal.uploader)
        assertEquals("Album", restoredLocal.album)
        assertEquals("audio/flac", restoredLocal.mimeType)
        assertEquals("Folder", restoredLocal.folder)
        assertEquals("content://art/1", restoredLocal.localThumbnailUrl)
        assertEquals(1L, restoredLocal.localMediaId)
        assertTrue(restored.streams[2].isRadio)
        assertEquals("application/x-mpegURL", restored.streams[2].mimeType)
        assertTrue(restored.streams[4].isLiveTv)
        assertEquals("https://tv.invalid/logo", restored.streams[4].localThumbnailUrl)
        restored.dispose()
        assertTrue(history.isEmpty)
    }

    @Test
    fun paginatedQueueActuallyFetchesItsSavedNextPageAfterRestoration() {
        val page = Page("https://playlist.invalid/next", "continuation", listOf("page-id"), mapOf("cookie" to "value"), byteArrayOf(1, 2, 3))
        val item = StreamInfoItem(0, "first", "First", StreamType.VIDEO_STREAM)
        val queue = PlaylistPlayQueue(0, "https://playlist.invalid/list", page, listOf(item), 0)
        val course = LearningPlaylistContext("course", "Course", "profile")
        queue.learningPlaylistContext = course
        val history = PreviousPlaybackHistory()
        assertTrue(history.push(queue, 42_000, true, REPEAT_MODE_OFF))
        val restored = history.pop()!!.queue
        assertEquals(PlaylistPlayQueue::class.java, restored.javaClass)
        assertEquals(course, restored.learningPlaylistContext)
        assertFalse(restored.isComplete)
        var requestedPage: Page? = null
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        try {
            mockStatic(ExtractorHelper::class.java).use { extractor ->
                extractor.`when`<Single<InfoItemsPage<StreamInfoItem>>> {
                    ExtractorHelper.getMorePlaylistItems(eq(0), eq("https://playlist.invalid/list"), any(Page::class.java))
                }.thenAnswer { invocation ->
                    requestedPage = invocation.getArgument(2)
                    Single.just(InfoItemsPage(listOf(StreamInfoItem(0, "second", "Second", StreamType.VIDEO_STREAM)), null, emptyList()))
                }
                restored.fetch()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(listOf("first", "second"), restored.streams.map { it.url })
                assertTrue(restored.isComplete)
                assertEquals(0, restored.index)
                assertEquals(42_000L, restored.item!!.recoveryPosition)
                assertNotSame(page, requestedPage)
                assertEquals(page.url, requestedPage!!.url)
                assertEquals(page.id, requestedPage!!.id)
                assertEquals(page.ids, requestedPage!!.ids)
                assertEquals(page.cookies, requestedPage!!.cookies)
                assertArrayEquals(page.body, requestedPage!!.body)
            }
        } finally {
            restored.dispose()
            RxJavaPlugins.reset()
        }
    }

    @Test
    fun countAndByteLimitsKeepOnlyCompleteRecentSnapshots() {
        val history = PreviousPlaybackHistory()
        repeat(5) { index ->
            assertTrue(history.push(LocalMediaPlayQueue(listOf(remote("$index", "$index")), 0), index * 1_000L, true, REPEAT_MODE_OFF))
        }
        assertEquals(listOf("4", "3", "2"), List(PreviousPlaybackHistory.MAX_ENTRIES) { history.pop()!!.queue.item!!.title })
        assertNull(history.pop())

        repeat(3) { index ->
            assertTrue(history.push(LocalMediaPlayQueue(listOf(remote("$index", "x".repeat(800_000))), 0), index.toLong(), true, REPEAT_MODE_OFF))
        }
        assertEquals(2L, history.pop()!!.queue.item!!.recoveryPosition)
        assertEquals(1L, history.pop()!!.queue.item!!.recoveryPosition)
        assertNull(history.pop())
    }

    @Test
    fun oversizedQueueCannotSilentlyReturnAnOlderVideoOrTruncateTheQueue() {
        val history = PreviousPlaybackHistory()
        val small = LocalMediaPlayQueue(listOf(remote("small", "Small")), 0)
        history.push(small, 12_345, true, REPEAT_MODE_OFF)
        val tooMany = LocalMediaPlayQueue(List(PreviousPlaybackHistory.MAX_ITEMS + 1) { remote("$it", "$it") }, 0)
        assertFalse(history.push(tooMany, 10, true, REPEAT_MODE_OFF))
        assertTrue(history.isEmpty)
        history.push(small, 12_345, true, REPEAT_MODE_OFF)
        val tooLarge = LocalMediaPlayQueue(listOf(remote("large", "x".repeat(PreviousPlaybackHistory.MAX_ENTRY_BYTES + 1))), 0)
        assertFalse(history.push(tooLarge, 10, true, REPEAT_MODE_OFF))
        assertNull(history.pop())
    }

    private fun remote(url: String, title: String) = PlayQueueItem(
        StreamInfoItem(0, url, title, StreamType.VIDEO_STREAM).apply {
            albumUrl = "https://album.invalid/$url"
            thumbnailUrl = "https://art.invalid/$url"
        }
    )
}
