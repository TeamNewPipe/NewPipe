package org.schabi.newpipe.extractor.services.soundcloud.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundcloudStreamExtractorTest {

    @Test
    void sendsTrackAuthorizationWhenResolvingTranscodingUrl() {
        assertEquals(
                "https://api-v2.soundcloud.com/media/stream"
                        + "?client_id=client&track_authorization=authorization",
                SoundcloudStreamExtractor.buildTranscodingApiUrl(
                        "https://api-v2.soundcloud.com/media/stream",
                        "client",
                        "authorization"));
    }

    @Test
    void omitsMissingTrackAuthorization() {
        assertEquals(
                "https://api-v2.soundcloud.com/media/stream?client_id=client",
                SoundcloudStreamExtractor.buildTranscodingApiUrl(
                        "https://api-v2.soundcloud.com/media/stream",
                        "client",
                        ""));
    }

    @Test
    void acceptsUnprotectedAacAndRejectsEncryptedOrUnknownTranscodings() {
        assertTrue(SoundcloudStreamExtractor.isSupportedTranscoding(
                "aac_160k_0_1", "hls"));
        assertTrue(SoundcloudStreamExtractor.isSupportedTranscoding(
                "aac_96k_0_1", "hls"));
        assertTrue(SoundcloudStreamExtractor.isSupportedTranscoding(
                "mp3_0_1", "progressive"));
        assertTrue(SoundcloudStreamExtractor.isSupportedTranscoding(
                "mp3_0_1", "hls"));
        assertTrue(SoundcloudStreamExtractor.isSupportedTranscoding(
                "opus_0_0", "hls"));
        assertFalse(SoundcloudStreamExtractor.isSupportedTranscoding(
                "aac_160k_0_1", "ctr-encrypted-hls"));
        assertFalse(SoundcloudStreamExtractor.isSupportedTranscoding(
                "flac_0_0", "progressive"));
    }
}
