/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.resolver

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifestParser
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import java.io.ByteArrayInputStream
import java.io.IOException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.datasource.NonUriHlsDataSourceFactory
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediaitem.StreamInfoTag

/** Shared playback path for external services, with request-local headers and cache policy. */
object PluginPlaybackSources {
    @JvmStatic
    @Throws(PlaybackResolver.ResolverException::class)
    fun build(
        dataSource: PlayerDataSource,
        stream: Stream,
        cacheKey: String,
        metadata: MediaItemTag
    ): MediaSource {
        if (stream.content.isNullOrEmpty()) {
            throw PlaybackResolver.ResolverException("Plugin returned empty stream content")
        }
        val defaultCache = stream.deliveryMethod == DeliveryMethod.DASH ||
            stream.deliveryMethod == DeliveryMethod.HLS
        val factory = dataSource.getPluginDataSourceFactory(
            stream.requestHeaders.orEmpty(),
            stream.cacheable ?: defaultCache
        )
        val uri = if (stream.isUrl) stream.content else stream.manifestUrl.orEmpty()
        val item = metadata.asMediaItem().buildUpon()
            .setUri(Uri.parse(uri))
            .setCustomCacheKey(cacheKey)
            .build()
        try {
            return when (stream.deliveryMethod) {
                DeliveryMethod.PROGRESSIVE_HTTP -> {
                    if (!stream.isUrl) {
                        throw PlaybackResolver.ResolverException("A progressive plugin stream must have a URL")
                    }
                    ProgressiveMediaSource.Factory(factory).createMediaSource(item)
                }

                DeliveryMethod.DASH -> {
                    val source = DashMediaSource.Factory(factory)
                    if (stream.isUrl) {
                        source.createMediaSource(item)
                    } else {
                        val manifest = ByteArrayInputStream(stream.content.toByteArray()).use {
                            DashManifestParser().parse(Uri.parse(uri), it)
                        }
                        source.createMediaSource(manifest, item)
                    }
                }

                DeliveryMethod.HLS -> {
                    val source = if (stream.isUrl) {
                        HlsMediaSource.Factory(factory)
                    } else {
                        val hlsFactory = NonUriHlsDataSourceFactory.Builder().apply {
                            setDataSourceFactory(factory)
                            setPlaylistString(stream.content)
                        }.build()
                        HlsMediaSource.Factory(hlsFactory)
                    }
                    source.createMediaSource(item)
                }

                DeliveryMethod.SS -> {
                    val source = SsMediaSource.Factory(factory)
                    if (stream.isUrl) {
                        source.createMediaSource(item)
                    } else {
                        val manifest = ByteArrayInputStream(stream.content.toByteArray()).use {
                            SsManifestParser().parse(Uri.parse(uri), it)
                        }
                        source.createMediaSource(manifest, item)
                    }
                }

                else -> throw PlaybackResolver.ResolverException(
                    "Unsupported plugin delivery type: ${stream.deliveryMethod}"
                )
            }
        } catch (error: IOException) {
            throw PlaybackResolver.ResolverException("Could not read the plugin stream manifest", error)
        }
    }

    @JvmStatic
    fun buildLive(dataSource: PlayerDataSource, info: StreamInfo, preferHls: Boolean): MediaSource? {
        val useHls = !info.hlsUrl.isNullOrEmpty() && (preferHls || info.dashMpdUrl.isNullOrEmpty())
        val url = if (useHls) info.hlsUrl else info.dashMpdUrl
        if (url.isNullOrEmpty()) return null
        val factory = dataSource.getPluginDataSourceFactory(info.manifestRequestHeaders.orEmpty(), false)
        val source: MediaSource.Factory = if (useHls) {
            HlsMediaSource.Factory(factory).setAllowChunklessPreparation(true)
        } else {
            DashMediaSource.Factory(factory)
        }
        return source.createMediaSource(
            StreamInfoTag.of(info).asMediaItem().buildUpon()
                .setUri(Uri.parse(url))
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS.toLong())
                        .build()
                ).build()
        )
    }

    @JvmStatic
    fun subtitleFactory(dataSource: PlayerDataSource, subtitle: SubtitlesStream): SingleSampleMediaSource.Factory = SingleSampleMediaSource.Factory(
        dataSource.getPluginDataSourceFactory(subtitle.requestHeaders.orEmpty(), subtitle.cacheable ?: true)
    )
}
