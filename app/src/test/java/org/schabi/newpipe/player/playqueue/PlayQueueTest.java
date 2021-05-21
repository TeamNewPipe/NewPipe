package org.schabi.newpipe.player.playqueue;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@RunWith(Enclosed.class)
public class PlayQueueTest {
    public static PlayQueue mockPlayQueue(final int index, final List<PlayQueueItem> streams) {
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

    public static class EqualsTests {
        private PlayQueueItem item1;
        private PlayQueueItem item2;

        @Before
        public void setup() {
            final String url1 = "www.website1.com";
            final String url2 = "www.website2.com";
            final StreamInfoItem info1 = new StreamInfoItem(
                    0, url1, "", StreamType.VIDEO_STREAM
            );
            final StreamInfoItem info2 = new StreamInfoItem(
                    0, url2, "", StreamType.VIDEO_STREAM
            );
            item1 = new PlayQueueItem(info1);
            item2 = new PlayQueueItem(info2);
        }

        @Test
        public void sameStreams() {
            final List<PlayQueueItem> streams = Collections.nCopies(5, item1);
            final PlayQueue queue1 = mockPlayQueue(0, streams);
            final PlayQueue queue2 = mockPlayQueue(0, streams);
            assertEquals(queue1, queue2);
        }

        @Test
        public void sameSizeDifferentItems() {
            final List<PlayQueueItem> streams1 = Collections.nCopies(5, item1);
            final List<PlayQueueItem> streams2 = Collections.nCopies(5, item2);
            final PlayQueue queue1 = mockPlayQueue(0, streams1);
            final PlayQueue queue2 = mockPlayQueue(0, streams2);
            assertNotEquals(queue1, queue2);
        }

        @Test
        public void differentSizeStreams() {
            final List<PlayQueueItem> streams1 = Collections.nCopies(5, item1);
            final List<PlayQueueItem> streams2 = Collections.nCopies(6, item2);
            final PlayQueue queue1 = mockPlayQueue(0, streams1);
            final PlayQueue queue2 = mockPlayQueue(0, streams2);
            assertNotEquals(queue1, queue2);
        }
    }
}
