package org.schabi.newpipe.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.shandian.giga.get.FinishedMission

class DownloadCaptionGroupingTest {
    private fun mission(source: String, name: String, kind: Char, id: String): FinishedMission {
        return FinishedMission().apply {
            this.source = source
            this.displayName = name
            this.kind = kind
            this.syncId = id
        }
    }

    @Test
    fun groupsCompanionCaptionsWithoutMixingUnrelatedVideosOrSources() {
        val video = mission("source", "My Video.mp4", 'v', "v1")
        val english = mission("source", "My Video.en.srt", 's', "s1")
        val french = mission("source", "My Video.fr-FR.vtt", 's', "s2")
        val other = mission("different", "My Video.en.srt", 's', "s3")
        val unrelated = mission("source", "Another Video.en.srt", 's', "s4")
        val all = listOf(video, english, french, other, unrelated)
        assertEquals(listOf("en", "fr-FR"), DownloadCaptionGrouping.associated(video, all).map { it.second })
        assertEquals(2, DownloadCaptionGrouping.captionCounts(all)["v1"])
        assertTrue(DownloadCaptionGrouping.associated(english, all).isEmpty())
    }
}
