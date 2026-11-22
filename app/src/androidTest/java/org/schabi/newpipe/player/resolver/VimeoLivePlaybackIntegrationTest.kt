/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.resolver

import android.net.Uri
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.detail.UpcomingPlayback
import org.schabi.newpipe.player.helper.PlayerDataSource

class VimeoLivePlaybackIntegrationTest {
    private val manifest = "https://live-api.vimeocdn.com/fixture/hls.m3u8?signature=unchanged"

    @Test
    fun activeVimeoEventUsesTheNativeLiveHlsSourceForVideoAndBackgroundAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val dataSource = PlayerDataSource(context, null)
            val info = event(StreamType.LIVE_STREAM).apply { hlsUrl = manifest }
            val video = VideoPlaybackResolver(context, dataSource, quality).resolve(info)
            val background = AudioPlaybackResolver(context, dataSource).resolve(info)
            for (source in listOf(video, background)) {
                assertTrue(source is HlsMediaSource)
                val mediaItem = requireNotNull(source).mediaItem
                assertEquals(Uri.parse(manifest), requireNotNull(mediaItem.localConfiguration).uri)
                assertEquals(PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS.toLong(), requireNotNull(mediaItem.liveConfiguration).targetOffsetMs)
            }
            // Creating sources checks the real resolver path without preparing them or using the network.
        }
    }

    @Test
    fun waitingEventIsBlockedByTheExistingUpcomingPlaybackPolicy() {
        val info = event(StreamType.LIVE_STREAM).apply { isUpcoming = true }
        assertTrue(UpcomingPlayback.shouldBlock(info))
        assertTrue(info.videoStreams.isEmpty())
        assertEquals("", info.hlsUrl)
    }

    @Test
    fun recentlyEndedVimeoEventKeepsItsAdvertisedHlsAsANativeFiniteSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val dataSource = PlayerDataSource(context, null)
            val info = event(StreamType.POST_LIVE_STREAM).apply {
                hlsUrl = manifest
                videoStreams = listOf(
                    VideoStream.Builder().setId("session-hls").setContent(manifest, true)
                        .setDeliveryMethod(DeliveryMethod.HLS).setMediaFormat(MediaFormat.MPEG_4)
                        .setIsVideoOnly(false).setResolution(VideoStream.RESOLUTION_UNKNOWN).build()
                )
            }
            assertTrue(VideoPlaybackResolver(context, dataSource, quality).resolve(info) is HlsMediaSource)
            assertTrue(AudioPlaybackResolver(context, dataSource).resolve(info) is HlsMediaSource)
        }
    }

    private fun event(type: StreamType): StreamInfo = StreamInfo(
        ServiceList.Vimeo.serviceId,
        "https://vimeo.com/event/4768062",
        "https://vimeo.com/event/4768062",
        type,
        "event/4768062",
        "Public event fixture",
        0
    )

    private val quality = object : VideoPlaybackResolver.QualityResolver {
        override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream>): Int = 0
        override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream>, playbackQuality: String): Int = 0
        override fun getAutoFallbackResolutionIndex(sortedVideos: List<VideoStream>): Int = 0
        override fun isDefaultAutoQuality(): Boolean = false
        override fun isAutoQuality(playbackQuality: String): Boolean = false
    }
}
