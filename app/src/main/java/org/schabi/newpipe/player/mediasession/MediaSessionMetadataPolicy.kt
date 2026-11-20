/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediasession

import androidx.media3.common.MediaMetadata
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.util.StreamTypeUtil

/** Merge resolved metadata without erasing the MediaItem's own title/artist during transitions. */
object MediaSessionMetadataPolicy {
    @JvmStatic
    fun merge(mediaItemMetadata: MediaMetadata, activeTag: MediaItemTag?): MediaMetadata {
        if (activeTag == null) return mediaItemMetadata

        val builder = mediaItemMetadata.buildUpon()
        activeTag.title?.takeIf { it.isNotBlank() }?.let(builder::setTitle)
        activeTag.uploaderName?.takeIf { it.isNotBlank() }?.let(builder::setArtist)

        val seconds = activeTag.durationSeconds
        if (!StreamTypeUtil.isLiveStream(activeTag.streamType) &&
            seconds in 0..Long.MAX_VALUE / 1_000L
        ) {
            builder.setDurationMs(seconds * 1_000L)
        }
        return builder.build()
    }
}
