package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType

class PlaybackParametersControllerTest {
    @Test
    fun liveStreamRespectsChannelSpeedOrGlobalDefault() {
        assertEquals(
            1.75f,
            PlaybackParametersController.resolvePlaybackSpeed(StreamType.LIVE_STREAM, 1.75f, 2.0f),
            0.001f
        )
        assertEquals(
            1.5f,
            PlaybackParametersController.resolvePlaybackSpeed(StreamType.LIVE_STREAM, null, 1.5f),
            0.001f
        )
    }

    @Test
    fun audioLiveStreamRespectsChannelSpeedOrGlobalDefault() {
        assertEquals(
            1.25f,
            PlaybackParametersController.resolvePlaybackSpeed(
                StreamType.AUDIO_LIVE_STREAM,
                1.25f,
                2.0f
            ),
            0.001f
        )
        assertEquals(
            1.75f,
            PlaybackParametersController.resolvePlaybackSpeed(
                StreamType.AUDIO_LIVE_STREAM,
                null,
                1.75f
            ),
            0.001f
        )
    }

    @Test
    fun nonLiveStreamPrefersChannelProfileSpeed() {
        assertEquals(
            1.75f,
            PlaybackParametersController.resolvePlaybackSpeed(
                StreamType.VIDEO_STREAM,
                1.75f,
                2.0f
            ),
            0.001f
        )
        assertEquals(
            1.25f,
            PlaybackParametersController.resolvePlaybackSpeed(
                StreamType.AUDIO_STREAM,
                1.25f,
                1.5f
            ),
            0.001f
        )
    }

    @Test
    fun nonLiveStreamFallsBackToPreferredSpeed() {
        assertEquals(
            2.0f,
            PlaybackParametersController.resolvePlaybackSpeed(
                StreamType.VIDEO_STREAM,
                null,
                2.0f
            ),
            0.001f
        )
        assertEquals(
            1.5f,
            PlaybackParametersController.resolvePlaybackSpeed(
                StreamType.POST_LIVE_STREAM,
                null,
                1.5f
            ),
            0.001f
        )
        assertEquals(
            1.25f,
            PlaybackParametersController.resolvePlaybackSpeed(null, null, 1.25f),
            0.001f
        )
    }
}
