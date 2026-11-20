/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediasession

import android.app.Application
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.mediaitem.MediaItemTag

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MediaSessionMetadataPolicyTest {
    private val original = MediaMetadata.Builder()
        .setTitle("Video from Media3")
        .setArtist("Channel from Media3")
        .build()

    @Test
    fun preservesMediaItemInformationWhenActiveTagIsNotYetResolved() {
        val merged = MediaSessionMetadataPolicy.merge(original, null)
        assertEquals("Video from Media3", merged.title)
        assertEquals("Channel from Media3", merged.artist)
    }

    @Test
    fun resolvedTagUpdatesMetadataAndDuration() {
        val tag = mock(MediaItemTag::class.java)
        `when`(tag.title).thenReturn("Resolved video")
        `when`(tag.uploaderName).thenReturn("Resolved channel")
        `when`(tag.streamType).thenReturn(StreamType.VIDEO_STREAM)
        `when`(tag.durationSeconds).thenReturn(84L)

        val merged = MediaSessionMetadataPolicy.merge(original, tag)
        assertEquals("Resolved video", merged.title)
        assertEquals("Resolved channel", merged.artist)
        assertEquals(84_000L, merged.durationMs)
    }

    @Test
    fun blankResolvedFieldsDoNotEraseExistingMedia3Metadata() {
        val tag = mock(MediaItemTag::class.java)
        `when`(tag.title).thenReturn("")
        `when`(tag.uploaderName).thenReturn("")
        `when`(tag.streamType).thenReturn(StreamType.LIVE_STREAM)
        val merged = MediaSessionMetadataPolicy.merge(original, tag)
        assertEquals("Video from Media3", merged.title)
        assertEquals("Channel from Media3", merged.artist)
    }
}
