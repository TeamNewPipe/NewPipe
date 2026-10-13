package org.schabi.newpipe.player.playqueue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PlayQueueItemTest {

    public static final String URL = "MY_URL";

    @Test
    public void equalsMustNotBeOverloaded() {
        final PlayQueueItem a = PlayQueueTest.makeItemWithUrl(URL);
        final PlayQueueItem b = PlayQueueTest.makeItemWithUrl(URL);
        assertEquals(a, a);
        assertNotEquals(a, b); // they should compare different even if they have the same data
    }

    @Test
    public void forceReloadFlagManagement() {
        final PlayQueueItem item = PlayQueueTest.makeItemWithUrl(URL);
        assertEquals(false, item.isForceReload());

        item.setForceReload(true);
        assertEquals(true, item.isForceReload());

        item.setForceReload(false);
        assertEquals(false, item.isForceReload());
    }
}
