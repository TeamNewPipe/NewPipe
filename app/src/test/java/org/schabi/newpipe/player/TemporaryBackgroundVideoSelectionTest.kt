package org.schabi.newpipe.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType

class TemporaryBackgroundVideoSelectionTest {
    @Test
    fun muxedVideoWithoutSeparateAudioRetainsItsSelectionWhileTemporarilyHidden() {
        assertTrue(retain())
    }

    @Test
    fun justEndedLiveMuxedVideoKeepsItsAudioWhenTheScreenTurnsOff() {
        assertTrue(retain(streamType = StreamType.POST_LIVE_STREAM))
        val separated = retain(
            streamType = StreamType.POST_LIVE_STREAM,
            sourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO
        )
        assertFalse(separated)
        assertFalse(retain(streamType = StreamType.POST_LIVE_STREAM, audioStreamsEmpty = false))
        val explicitAudioMode = retain(
            streamType = StreamType.POST_LIVE_STREAM,
            temporaryBackground = false
        )
        assertFalse(explicitAudioMode)
    }

    @Test
    fun explicitAudioPlaybackStillDisablesVideo() {
        assertFalse(retain(temporaryBackground = false))
        assertFalse(retain(mode = PlaybackPresentationMode.AUDIO_BACKGROUND))
        assertFalse(retain(enabled = true))
    }

    @Test
    fun separateAudioAndLiveSourcesKeepTheExistingBackgroundPolicy() {
        assertFalse(retain(sourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO))
        assertFalse(retain(sourceType = SourceType.LIVE_STREAM))
        assertFalse(retain(audioStreamsEmpty = false))
        assertFalse(retain(streamType = StreamType.LIVE_STREAM))
        assertFalse(retain(streamType = StreamType.AUDIO_STREAM))
        assertFalse(retain(sourceType = null))
        assertFalse(retain(streamType = null))
    }

    @Test
    fun unloadedOrAudioOnlyPlayersDoNotRetainAVideoSelection() {
        assertFalse(retain(hasTimeline = false))
        assertFalse(retain(hasEnabledVideoRenderer = false))
    }

    private fun retain(
        enabled: Boolean = false,
        temporaryBackground: Boolean = true,
        mode: PlaybackPresentationMode = PlaybackPresentationMode.VIDEO,
        hasTimeline: Boolean = true,
        hasEnabledVideoRenderer: Boolean = true,
        streamType: StreamType? = StreamType.VIDEO_STREAM,
        sourceType: SourceType? = SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY,
        audioStreamsEmpty: Boolean = true
    ) = PlayerPresentationController.shouldRetainVideoSelection(
        enabled,
        temporaryBackground,
        mode,
        hasTimeline,
        hasEnabledVideoRenderer,
        streamType,
        sourceType,
        audioStreamsEmpty
    )
}
