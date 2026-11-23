/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.schabi.newpipe.download.DownloadedCopyRepository
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager

internal data class OfflineTimestampTarget(val item: PlayQueueItem, val matchesCurrent: Boolean)

/** Resolves a local timestamp without an extractor or a network fallback. */
internal object OfflineTimestampTargetResolver {
    @WorkerThread
    fun resolve(context: Context, data: TimestampChangeData, current: PlayQueueItem?): OfflineTimestampTarget {
        checkProfile(context, data)
        val uri = data.localUri ?: data.url
        require(Uri.parse(uri).scheme in setOf("file", "content"))
        val local = OfflinePlaybackMetadata.resolve(context, uri)
        check(local.identity.isNotEmpty()) { "Media file is unavailable" }
        if (data.localIdentity != null) check(local.identity == data.localIdentity) { "Media file changed" }
        val source = local.sourceInfo
        if (data.serviceId != PlayQueueItem.LOCAL_SERVICE_ID) {
            check(source != null && source.serviceId == data.serviceId && DownloadedCopyRepository.sameSource(data.serviceId, source.url, data.url)) { "Download source changed" }
        }
        val matches = current != null && if (current.isLocalMedia) {
            OfflinePlaybackMetadata.identity(context, current.url)?.signature == local.identity
        } else {
            current.serviceId == data.serviceId && DownloadedCopyRepository.sameSource(data.serviceId, current.url, data.url)
        }
        val mime = context.contentResolver.getType(Uri.parse(uri))
        val item = PlayQueueItem.localMedia(
            source?.name?.takeIf { it.isNotBlank() } ?: current?.takeIf { matches }?.title ?: Uri.parse(uri).lastPathSegment.orEmpty(),
            uri,
            local.durationMillis / 1000,
            source?.uploaderName,
            null,
            null,
            mime,
            -1,
            source?.streamType != StreamType.AUDIO_STREAM && mime?.startsWith("audio/") != true,
            null
        )
        checkProfile(context, data)
        return OfflineTimestampTarget(item, matches)
    }

    private fun checkProfile(context: Context, data: TimestampChangeData) {
        check(data.profileId == null || data.profileId == ProfileManager.getActiveProfileId(context)) { "Profile changed" }
    }
}
