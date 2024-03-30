package org.schabi.newpipe.player.playqueue

import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Collections
import java.util.Objects

object PlayQueueTest {
    fun makePlayQueue(index: Int, streams: List<PlayQueueItem>?): PlayQueue {
        // I tried using Mockito, but it didn't work for some reason
        return object : PlayQueue(index, streams) {
            override val isComplete: Boolean
                get() {
                    throw UnsupportedOperationException()
                }

            override fun fetch() {
                throw UnsupportedOperationException()
            }
        }
    }

    fun makeItemWithUrl(url: String?): PlayQueueItem {
        val infoItem = StreamInfoItem(
                0, url, "", StreamType.VIDEO_STREAM
        )
        return PlayQueueItem(infoItem)
    }

    class SetIndexTests {
        private var nonEmptyQueue: PlayQueue? = null
        private var emptyQueue: PlayQueue? = null
        @Before
        fun setup() {
            val streams: MutableList<PlayQueueItem> = ArrayList(5)
            for (i in 0..4) {
                streams.add(makeItemWithUrl("URL_$i"))
            }
            nonEmptyQueue = Mockito.spy(makePlayQueue(0, streams))
            emptyQueue = Mockito.spy(makePlayQueue(0, ArrayList()))
        }

        @Test
        fun negative() {
            nonEmptyQueue!!.index = -5
            Assert.assertEquals(0, nonEmptyQueue!!.index.toLong())
            emptyQueue!!.index = -5
            Assert.assertEquals(0, nonEmptyQueue!!.index.toLong())
        }

        @Test
        fun inBounds() {
            nonEmptyQueue!!.index = 2
            Assert.assertEquals(2, nonEmptyQueue!!.index.toLong())

            // emptyQueue not tested because 0 isn't technically inBounds
        }

        @Test
        fun outOfBoundIsComplete() {
            Mockito.doReturn(true).`when`(nonEmptyQueue).isComplete
            nonEmptyQueue!!.index = 7
            Assert.assertEquals(2, nonEmptyQueue!!.index.toLong())
            Mockito.doReturn(true).`when`(emptyQueue).isComplete
            emptyQueue!!.index = 2
            Assert.assertEquals(0, emptyQueue!!.index.toLong())
        }

        @Test
        fun outOfBoundsNotComplete() {
            Mockito.doReturn(false).`when`(nonEmptyQueue).isComplete
            nonEmptyQueue!!.index = 7
            Assert.assertEquals((SIZE - 1).toLong(), nonEmptyQueue!!.index.toLong())
            Mockito.doReturn(false).`when`(emptyQueue).isComplete
            emptyQueue!!.index = 2
            Assert.assertEquals(0, emptyQueue!!.index.toLong())
        }

        @Test
        fun indexZero() {
            nonEmptyQueue!!.index = 0
            Assert.assertEquals(0, nonEmptyQueue!!.index.toLong())
            Mockito.doReturn(true).`when`(emptyQueue).isComplete
            emptyQueue!!.index = 0
            Assert.assertEquals(0, emptyQueue!!.index.toLong())
            Mockito.doReturn(false).`when`(emptyQueue).isComplete
            emptyQueue!!.index = 0
            Assert.assertEquals(0, emptyQueue!!.index.toLong())
        }

        @Test
        fun addToHistory() {
            nonEmptyQueue!!.index = 0
            Assert.assertFalse(nonEmptyQueue!!.previous())
            nonEmptyQueue!!.index = 3
            Assert.assertTrue(nonEmptyQueue!!.previous())
            Assert.assertEquals("URL_0", Objects.requireNonNull(nonEmptyQueue!!.item).url)
        }

        companion object {
            private const val SIZE = 5
        }
    }

    class GetItemTests {
        private var queue: PlayQueue? = null
        @Before
        fun setup() {
            queue = makePlayQueue(0, streams)
        }

        @Test
        fun inBounds() {
            Assert.assertEquals("TARGET_URL", Objects.requireNonNull(queue!!.getItem(3)).url)
            Assert.assertEquals("OTHER_URL", Objects.requireNonNull(queue!!.getItem(1)).url)
        }

        @Test
        fun outOfBounds() {
            Assert.assertNull(queue!!.getItem(-1))
            Assert.assertNull(queue!!.getItem(5))
        }

        @Test
        fun itemsAreNotCloned() {
            val item = makeItemWithUrl("A url")
            val playQueue = makePlayQueue(0, java.util.List.of(item))

            // make sure that items are not cloned when added to the queue
            Assert.assertSame(playQueue.item, item)
        }

        companion object {
            private var streams: MutableList<PlayQueueItem>? = null
            @BeforeClass
            fun init() {
                streams = ArrayList(Collections.nCopies(5, makeItemWithUrl("OTHER_URL")))
                streams.set(3, makeItemWithUrl("TARGET_URL"))
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
            Assert.assertTrue(queue1.equalStreams(queue2))
            Assert.assertTrue(queue1.equalStreamsAndIndex(queue2))
        }

        @Test
        fun sameStreamsDifferentIndex() {
            val streams = Collections.nCopies(5, item1)
            val queue1 = makePlayQueue(1, streams)
            val queue2 = makePlayQueue(4, streams)
            Assert.assertTrue(queue1.equalStreams(queue2))
            Assert.assertFalse(queue1.equalStreamsAndIndex(queue2))
        }

        @Test
        fun sameSizeDifferentItems() {
            val streams1 = Collections.nCopies(5, item1)
            val streams2 = Collections.nCopies(5, item2)
            val queue1 = makePlayQueue(0, streams1)
            val queue2 = makePlayQueue(0, streams2)
            Assert.assertFalse(queue1.equalStreams(queue2))
        }

        @Test
        fun differentSizeStreams() {
            val streams1 = Collections.nCopies(5, item1)
            val streams2 = Collections.nCopies(6, item2)
            val queue1 = makePlayQueue(0, streams1)
            val queue2 = makePlayQueue(0, streams2)
            Assert.assertFalse(queue1.equalStreams(queue2))
        }
    }
}
