package org.schabi.newpipe.fragments.list.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import org.junit.Test;

public class ChannelLandscapeHeaderPolicyTest {
    @Test
    public void phoneLandscapeUsesCompactScrollableHeader() {
        assertTrue(ChannelFragment.shouldUseCompactLandscapeHeader(
                Configuration.ORIENTATION_LANDSCAPE, false));
    }

    @Test
    public void phoneLandscapeUsesShortScrollableMetadataRow() {
        assertEquals(60, ChannelFragment.landscapeHeaderHeightDp(true));
        assertEquals(86, ChannelFragment.landscapeHeaderHeightDp(false));
    }

    @Test
    public void portraitAndLargeScreensKeepNormalHeader() {
        assertFalse(ChannelFragment.shouldUseCompactLandscapeHeader(
                Configuration.ORIENTATION_PORTRAIT, false));
        assertFalse(ChannelFragment.shouldUseCompactLandscapeHeader(
                Configuration.ORIENTATION_LANDSCAPE, true));
    }
}
