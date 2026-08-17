package org.schabi.newpipe.player.playqueue

import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class PlayQueueTest {

    class SetIndexTests {
        private lateinit var nonEmptyQueue: PlayQueue
        private lateinit var emptyQueue: PlayQueue

        @Before
        fun setup() {
            val streams = ArrayList<PlayQueueItem>(5)
            for (i in 0 until 5) {
                streams.add(makeItemWithUrl("URL_$i"))
            }
            nonEmptyQueue = spy(makePlayQueue(0, streams))
            emptyQueue = spy(makePlayQueue(0, ArrayList()))
        }

        @Test
        fun negative() {
            nonEmptyQueue.index = -5
            assertEquals(0, nonEmptyQueue.index)

            emptyQueue.index = -5
            assertEquals(0, emptyQueue.index)
        }

        @Test
        fun inBounds() {
            nonEmptyQueue.index = 2
            assertEquals(2, nonEmptyQueue.index)

            // emptyQueue not tested because 0 isn't technically inBounds
        }

        @Test
        fun outOfBoundIsComplete() {
            doReturn(true).`when`(nonEmptyQueue).isComplete
            nonEmptyQueue.index = 7
            assertEquals(2, nonEmptyQueue.index)

            doReturn(true).`when`(emptyQueue).isComplete
            emptyQueue.index = 2
            assertEquals(0, emptyQueue.index)
        }

        @Test
        fun outOfBoundsNotComplete() {
            doReturn(false).`when`(nonEmptyQueue).isComplete
            nonEmptyQueue.index = 7
            assertEquals(SIZE - 1, nonEmptyQueue.index)

            doReturn(false).`when`(emptyQueue).isComplete
            emptyQueue.index = 2
            assertEquals(0, emptyQueue.index)
        }

        @Test
        fun indexZero() {
            nonEmptyQueue.index = 0
            assertEquals(0, nonEmptyQueue.index)

            doReturn(true).`when`(emptyQueue).isComplete
            emptyQueue.index = 0
            assertEquals(0, emptyQueue.index)

            doReturn(false).`when`(emptyQueue).isComplete
            emptyQueue.index = 0
            assertEquals(0, emptyQueue.index)
        }

        @Test
        fun addToHistory() {
            nonEmptyQueue.index = 0
            assertFalse(nonEmptyQueue.previous())

            nonEmptyQueue.index = 3
            assertTrue(nonEmptyQueue.previous())
            assertEquals("URL_0", nonEmptyQueue.item!!.url)
        }

        companion object {
            private const val SIZE = 5
        }
    }

    class GetItemTests {
        private lateinit var queue: PlayQueue

        @Before
        fun setup() {
            queue = makePlayQueue(0, streams)
        }

        @Test
        fun inBounds() {
            assertEquals("TARGET_URL", queue.getItem(3)!!.url)
            assertEquals("OTHER_URL", queue.getItem(1)!!.url)
        }

        @Test
        fun outOfBounds() {
            assertNull(queue.getItem(-1))
            assertNull(queue.getItem(5))
        }

        @Test
        fun itemsAreNotCloned() {
            val item = makeItemWithUrl("A url")
            val playQueue = makePlayQueue(0, listOf(item))

            // make sure that items are not cloned when added to the queue
            assertSame(playQueue.item, item)
        }

        companion object {
            private lateinit var streams: List<PlayQueueItem>

            @BeforeClass
            @JvmStatic
            fun init() {
                val list = ArrayList(Collections.nCopies(5, makeItemWithUrl("OTHER_URL")))
                list[3] = makeItemWithUrl("TARGET_URL")
                streams = list
            }
        }
    }

    class EqualsTests {
        private val item1 = makeItemWithUrl("URL_1")
        private val item2 = makeItemWithUrl("URL_2")

        @Test
        fun sameStreams() {
            val streams = Collections.nCopies(5, item1)
            val queue1 = makePlayQueue(0, streams)
            val queue2 = makePlayQueue(0, streams)
            assertTrue(queue1.equalStreams(queue2))
            assertTrue(queue1.equalStreamsAndIndex(queue2))
        }

        @Test
        fun sameStreamsDifferentIndex() {
            val streams = Collections.nCopies(5, item1)
            val queue1 = makePlayQueue(1, streams)
            val queue2 = makePlayQueue(4, streams)
            assertTrue(queue1.equalStreams(queue2))
            assertFalse(queue1.equalStreamsAndIndex(queue2))
        }

        @Test
        fun sameSizeDifferentItems() {
            val streams1 = Collections.nCopies(5, item1)
            val streams2 = Collections.nCopies(5, item2)
            val queue1 = makePlayQueue(0, streams1)
            val queue2 = makePlayQueue(0, streams2)
            assertFalse(queue1.equalStreams(queue2))
        }

        @Test
        fun differentSizeStreams() {
            val streams1 = Collections.nCopies(5, item1)
            val streams2 = Collections.nCopies(6, item2)
            val queue1 = makePlayQueue(0, streams1)
            val queue2 = makePlayQueue(0, streams2)
            assertFalse(queue1.equalStreams(queue2))
        }
    }

    companion object {
        @JvmStatic
        fun makePlayQueue(index: Int, streams: List<PlayQueueItem>): PlayQueue {
            return object : PlayQueue(index, streams) {
                override fun isComplete(): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun fetch() {
                    throw UnsupportedOperationException()
                }
            }
        }

        @JvmStatic
        fun makeItemWithUrl(url: String): PlayQueueItem {
            val infoItem = StreamInfoItem(0, url, "", StreamType.VIDEO_STREAM)
            return PlayQueueItem(infoItem)
        }
    }
}
