package org.schabi.newpipe.player.playqueue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayQueueItemTest {

    public static final String URL = "MY_URL";

    @Test
    public void equalsMustWork() {
        final PlayQueueItem a = PlayQueueTest.makeItemWithUrl(URL);
        final PlayQueueItem b = PlayQueueTest.makeItemWithUrl(URL);
        assertEquals(a, b);
    }
}
