/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.resolver

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.helper.PlayerHelper

/** Both downloaded-copy and direct-local playback attach the same verified, local caption tracks. */
internal object OfflineSubtitleSources {
    fun merge(context: Context, media: MediaSource, tracks: List<SubtitlesStream>, sourceFactory: DataSource.Factory = DefaultDataSource.Factory(context)): MediaSource {
        val factory = SingleSampleMediaSource.Factory(sourceFactory).setTreatLoadErrorsAsEndOfStream(true)
        val sources = tracks.take(12).mapNotNull { track ->
            val uri = Uri.parse(track.content)
            val mime = track.format?.mimeType ?: return@mapNotNull null
            if (!track.isUrl || uri.scheme !in listOf("file", "content") || mime !in setOf("text/vtt", "application/ttml+xml", "application/x-subrip")) return@mapNotNull null
            val configuration = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime).setLanguage(PlayerHelper.captionLanguageOf(context, track))
                .setRoleFlags(C.ROLE_FLAG_CAPTION).build()
            factory.createMediaSource(configuration, C.TIME_UNSET)
        }
        return if (sources.isEmpty()) media else MergingMediaSource(true, media, *sources.toTypedArray())
    }
}
