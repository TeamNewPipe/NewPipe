package org.schabi.newpipe.player.playqueue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@RunWith(Enclosed.class)
public class PlayQueueTest {
    static PlayQueue makePlayQueue(final int index, final List<PlayQueueItem> streams) {
        // I tried using Mockito, but it didn't work for some reason
        return new PlayQueue(index, streams) {
            @Override
            public boolean isComplete() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void fetch() {
                throw new UnsupportedOperationException();
            }
        };
    }

    static PlayQueueItem makeItemWithUrl(final String url) {
        final StreamInfoItem infoItem = new StreamInfoItem(
                0, url, "", StreamType.VIDEO_STREAM
        );
        return new PlayQueueItem(infoItem);
    }

    public static class SetIndexTests {
        private static final int SIZE = 5;
        private PlayQueue nonEmptyQueue;
        private PlayQueue emptyQueue;

        @Before
        public void setup() {
            nonEmptyQueue = spy(makePlayQueue(
                    0, Collections.nCopies(SIZE, mock(PlayQueueItem.class))
            ));
            emptyQueue = spy(makePlayQueue(0, new ArrayList<>()));
        }

        @Test
        public void negative() {
            nonEmptyQueue.setIndex(-5);
            assertEquals(0, nonEmptyQueue.getIndex());

            emptyQueue.setIndex(-5);
            assertEquals(0, nonEmptyQueue.getIndex());
        }

        @Test
        public void inBounds() {
            nonEmptyQueue.setIndex(2);
            assertEquals(2, nonEmptyQueue.getIndex());

            // emptyQueue not tested because 0 isn't technically inBounds
        }

        @Test
        public void outOfBoundIsComplete() {
            doReturn(true).when(nonEmptyQueue).isComplete();
            nonEmptyQueue.setIndex(7);
            assertEquals(2, nonEmptyQueue.getIndex());

            doReturn(true).when(emptyQueue).isComplete();
            emptyQueue.setIndex(2);
            assertEquals(0, emptyQueue.getIndex());
        }

        @Test
        public void outOfBoundsNotComplete() {
            doReturn(false).when(nonEmptyQueue).isComplete();
            nonEmptyQueue.setIndex(7);
            assertEquals(SIZE - 1, nonEmptyQueue.getIndex());

            doReturn(false).when(emptyQueue).isComplete();
            emptyQueue.setIndex(2);
            assertEquals(0, emptyQueue.getIndex());
        }

        @Test
        public void indexZero() {
            nonEmptyQueue.setIndex(0);
            assertEquals(0, nonEmptyQueue.getIndex());

            doReturn(true).when(emptyQueue).isComplete();
            emptyQueue.setIndex(0);
            assertEquals(0, emptyQueue.getIndex());

            doReturn(false).when(emptyQueue).isComplete();
            emptyQueue.setIndex(0);
            assertEquals(0, emptyQueue.getIndex());
        }

        @SuppressWarnings("unchecked")
        @Test
        public void addToHistory() throws NoSuchFieldException, IllegalAccessException {
            final Field field;
            field = PlayQueue.class.getDeclaredField("history");
            field.setAccessible(true);
            List<PlayQueueItem> history;

            /*
            history's size is currently 1. 0 is the also the current index, so history should not
            be affected.
             */
            nonEmptyQueue.setIndex(0);
            history = (List<PlayQueueItem>) Objects.requireNonNull(
                    field.get(nonEmptyQueue)
            );
            assertEquals(1, history.size());

            // Index 3 != 0, so the second history element should be the item at streams[3]
            nonEmptyQueue.setIndex(3);
            history = (List<PlayQueueItem>) Objects.requireNonNull(
                    field.get(nonEmptyQueue)
            );
            assertEquals(nonEmptyQueue.getItem(3), history.get(1));
        }
    }

    public static class GetItemTests {
        private static List<PlayQueueItem> streams;
        private PlayQueue queue;

        @BeforeClass
        public static void init() {
            streams = new ArrayList<>(Collections.nCopies(5, makeItemWithUrl("OTHER_URL")));
            streams.set(3, makeItemWithUrl("TARGET_URL"));
        }

        @Before
        public void setup() {
            queue = makePlayQueue(0, streams);
        }

        @Test
        public void inBounds() {
            assertEquals("TARGET_URL", Objects.requireNonNull(queue.getItem(3)).getUrl());
            assertEquals("OTHER_URL", Objects.requireNonNull(queue.getItem(1)).getUrl());
        }

        @Test
        public void outOfBounds() {
            assertNull(queue.getItem(-1));
            assertNull(queue.getItem(5));
        }
    }

    public static class EqualsTests {
        private final PlayQueueItem item1 = makeItemWithUrl("URL_1");
        private final PlayQueueItem item2 = makeItemWithUrl("URL_2");

        @Test
        public void sameStreams() {
            final List<PlayQueueItem> streams = Collections.nCopies(5, item1);
            final PlayQueue queue1 = makePlayQueue(0, streams);
            final PlayQueue queue2 = makePlayQueue(0, streams);
            assertEquals(queue1, queue2);
        }

        @Test
        public void sameSizeDifferentItems() {
            final List<PlayQueueItem> streams1 = Collections.nCopies(5, item1);
            final List<PlayQueueItem> streams2 = Collections.nCopies(5, item2);
            final PlayQueue queue1 = makePlayQueue(0, streams1);
            final PlayQueue queue2 = makePlayQueue(0, streams2);
            assertNotEquals(queue1, queue2);
        }

        @Test
        public void differentSizeStreams() {
            final List<PlayQueueItem> streams1 = Collections.nCopies(5, item1);
            final List<PlayQueueItem> streams2 = Collections.nCopies(6, item2);
            final PlayQueue queue1 = makePlayQueue(0, streams1);
            final PlayQueue queue2 = makePlayQueue(0, streams2);
            assertNotEquals(queue1, queue2);
        }
    }
}
