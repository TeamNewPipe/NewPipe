package org.schabi.newpipe.player.resolver

import android.net.Uri
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.StreamInfoTag

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PluginPlaybackSourcesTest {
    @Test
    fun cacheIdentityContainsProviderIdAndSupportsSparsePluginIds() {
        val stream = stream(DeliveryMethod.PROGRESSIVE_HTTP)
        val first = PlaybackResolver.cacheKeyOf(info(1001), stream)
        val second = PlaybackResolver.cacheKeyOf(info(1002), stream)
        assertTrue(first.startsWith("1001 "))
        assertNotEquals(first, second)
        // A service ID is part of the key, never a StringBuilder allocation capacity.
        assertTrue(PlaybackResolver.cacheKeyOf(info(1_500_000_000), stream).startsWith("1500000000 "))
    }

    @Test
    fun progressivePluginSourcesKeepTheirHeadersAndCachePolicyIndependent() {
        val data = mock(PlayerDataSource::class.java)
        val firstHeaders = mapOf("Authorization" to "Bearer first")
        val secondHeaders = mapOf("Referer" to "https://second.example/")
        `when`(data.getPluginDataSourceFactory(firstHeaders, false)).thenReturn(DefaultHttpDataSource.Factory())
        `when`(data.getPluginDataSourceFactory(secondHeaders, true)).thenReturn(DefaultHttpDataSource.Factory())
        val metadata = StreamInfoTag.of(info())
        val first = PluginPlaybackSources.build(
            data,
            stream(DeliveryMethod.PROGRESSIVE_HTTP, firstHeaders, false),
            "first",
            metadata
        )
        val second = PluginPlaybackSources.build(
            data,
            stream(DeliveryMethod.PROGRESSIVE_HTTP, secondHeaders, true),
            "second",
            metadata
        )
        assertTrue(first is ProgressiveMediaSource)
        assertEquals(Uri.parse("https://media.example/video"), first.mediaItem.localConfiguration!!.uri)
        assertEquals("first", first.mediaItem.localConfiguration!!.customCacheKey)
        assertEquals("second", second.mediaItem.localConfiguration!!.customCacheKey)
        verify(data).getPluginDataSourceFactory(firstHeaders, false)
        verify(data).getPluginDataSourceFactory(secondHeaders, true)
    }

    @Test
    fun livePluginManifestsUseTheirDeclaredHeadersAndSelectedFormatWithoutCaching() {
        val data = mock(PlayerDataSource::class.java)
        val headers = mapOf("X-Playback-Token" to "fixture")
        `when`(data.getPluginDataSourceFactory(headers, false)).thenReturn(DefaultHttpDataSource.Factory())
        val live = info().apply {
            streamType = StreamType.LIVE_STREAM
            dashMpdUrl = "https://media.example/live.mpd"
            hlsUrl = "https://media.example/live.m3u8"
            manifestRequestHeaders = headers
        }
        val dash = requireNotNull(PlaybackResolver.maybeBuildLiveMediaSource(data, live))
        val hls = requireNotNull(PlaybackResolver.maybeBuildLiveMediaSource(data, live, true))
        assertTrue(dash is DashMediaSource)
        assertTrue(hls is HlsMediaSource)
        assertEquals(live.dashMpdUrl, dash.mediaItem.localConfiguration!!.uri.toString())
        assertEquals(live.hlsUrl, hls.mediaItem.localConfiguration!!.uri.toString())
    }

    @Test
    fun unsupportedSharedDeliveryMethodsFailWithAResolverError() {
        val data = mock(PlayerDataSource::class.java)
        val error = assertThrows(PlaybackResolver.ResolverException::class.java) {
            PluginPlaybackSources.build(data, stream(DeliveryMethod.SABR), "sabr", StreamInfoTag.of(info()))
        }
        assertEquals("Unsupported plugin delivery type: SABR", error.message)
    }

    private fun info(serviceId: Int = 1001) = StreamInfo(
        serviceId,
        "video",
        "https://provider.example/watch/video",
        "Plugin video"
    ).apply {
        streamType = StreamType.VIDEO_STREAM
        uploaderName = "Fixture uploader"
    }

    private fun stream(
        delivery: DeliveryMethod,
        headers: Map<String, String> = emptyMap(),
        cacheable: Boolean? = null
    ): VideoStream = VideoStream.Builder()
        .setId("format")
        .setContent("https://media.example/video", true)
        .setMediaFormat(MediaFormat.MPEG_4)
        .setDeliveryMethod(delivery)
        .setResolution("720p")
        .setIsVideoOnly(false)
        .setRequestHeaders(headers)
        .setCacheable(cacheable)
        .build()
}
