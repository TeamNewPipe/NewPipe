package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;

public class YoutubeStoryboardFallbackTest {
    @Test
    public void acceptsRegularStoryboardMetadataFromWebResponse() throws Exception {
        final String spec = "https://example.org/$L|48#27#10#5#2#1000#abc#sig";
        final JsonObject web = JsonParser.object().from(
                "{\"storyboards\":{\"playerStoryboardSpecRenderer\":{\"spec\":\""
                        + spec + "\"}}}");
        assertEquals(spec, YoutubeStreamExtractor.storyboardSpec(web));
    }

    @Test
    public void handlesMissingOrEmptyStoryboardsWithoutCrashing() throws Exception {
        assertNull(YoutubeStreamExtractor.storyboardSpec(null));
        assertNull(YoutubeStreamExtractor.storyboardSpec(JsonParser.object().from("{}")));
        assertNull(YoutubeStreamExtractor.storyboardSpec(JsonParser.object().from(
                "{\"storyboards\":{\"playerStoryboardSpecRenderer\":{\"spec\":\"\"}}}")));
    }

    @Test
    public void choosesNonemptyLiveStoryboardWhenBothAvailable() throws Exception {
        final JsonObject metadata = JsonParser.object().from(
                "{\"storyboards\":{\"playerStoryboardSpecRenderer\":{\"spec\":\"regular\"},"
                    + "\"playerLiveStoryboardSpecRenderer\":{\"spec\":\"live\"}}}");
        assertEquals("live", YoutubeStreamExtractor.storyboardSpec(metadata));
    }
}
