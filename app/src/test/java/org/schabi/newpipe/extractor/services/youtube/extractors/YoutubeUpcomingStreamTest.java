package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grack.nanojson.JsonObject;

import org.junit.Test;

public class YoutubeUpcomingStreamTest {
    @Test
    public void recognizesUpcomingLiveStreamAndPremiereStatuses() {
        assertTrue(YoutubeStreamExtractor.isUpcomingPlayabilityStatus(
                status("This live event will begin in 2 hours")));
        assertTrue(YoutubeStreamExtractor.isUpcomingPlayabilityStatus(
                status("Premieres in 30 minutes")));
        assertFalse(YoutubeStreamExtractor.isUpcomingPlayabilityStatus(
                status("Video unavailable")));
    }

    private static JsonObject status(final String reason) {
        final JsonObject status = new JsonObject();
        status.put("reason", reason);
        return status;
    }
}
