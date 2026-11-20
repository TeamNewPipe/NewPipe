package org.schabi.newpipe.recommendations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class RecommendationQuickAddTest {
    @Test
    fun permitsRegularStreamsButNotLiveOrMembersOnly() {
        val video = StreamInfoItem(0, "https://example.com/watch", "Video", StreamType.VIDEO_STREAM)
        val audio = StreamInfoItem(0, "https://example.com/audio", "Audio", StreamType.AUDIO_STREAM)
        val live = StreamInfoItem(0, "https://example.com/live", "Live", StreamType.LIVE_STREAM)
        assertTrue(RecommendationQuickAdd.eligible(video))
        assertTrue(RecommendationQuickAdd.eligible(audio))
        assertFalse(RecommendationQuickAdd.eligible(live))
        video.setRequiresMembership(true)
        assertFalse(RecommendationQuickAdd.eligible(video))
    }
}
