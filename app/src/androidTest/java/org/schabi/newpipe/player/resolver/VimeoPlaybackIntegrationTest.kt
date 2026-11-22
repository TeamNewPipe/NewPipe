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
import org.schabi.newpipe.player.helper.PlayerDataSource

class VimeoPlaybackIntegrationTest {
    @Test
    fun adaptiveOnlyVimeoVideoResolvesForVideoAndBackgroundAudioWithoutSeparateAudioStreams() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val dataSource = PlayerDataSource(context, null)
            val manifest = "https://media.example.invalid/master.m3u8?signature=fixture&expires=3600"
            val info = StreamInfo(
                ServiceList.Vimeo.serviceId,
                "https://vimeo.com/1236621193",
                "https://vimeo.com/1236621193",
                StreamType.VIDEO_STREAM,
                "1236621193",
                "Adaptive Vimeo fixture",
                0
            ).apply {
                hlsUrl = manifest
                videoStreams = listOf(
                    VideoStream.Builder().setId("hls").setContent(manifest, true)
                        .setDeliveryMethod(DeliveryMethod.HLS).setMediaFormat(MediaFormat.MPEG_4)
                        .setIsVideoOnly(false).setResolution(VideoStream.RESOLUTION_UNKNOWN).build()
                )
            }
            val quality = object : VideoPlaybackResolver.QualityResolver {
                override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream>): Int = 0
                override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream>, playbackQuality: String): Int = 0
                override fun getAutoFallbackResolutionIndex(sortedVideos: List<VideoStream>): Int = 0
                override fun isDefaultAutoQuality(): Boolean = false
                override fun isAutoQuality(playbackQuality: String): Boolean = false
            }
            val video = VideoPlaybackResolver(context, dataSource, quality).resolve(info)
            val background = AudioPlaybackResolver(context, dataSource).resolve(info)
            assertTrue("Adaptive Vimeo VOD must resolve as HLS", video is HlsMediaSource)
            assertTrue("Background audio must reuse the muxed HLS stream", background is HlsMediaSource)
            assertEquals(Uri.parse(manifest), video!!.mediaItem.localConfiguration!!.uri)
            assertEquals(Uri.parse(manifest), background!!.mediaItem.localConfiguration!!.uri)
            // Sources are deliberately not prepared: this test performs no network requests.
        }
    }
}
