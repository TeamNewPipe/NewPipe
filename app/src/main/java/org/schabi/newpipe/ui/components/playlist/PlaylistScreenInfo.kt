package org.schabi.newpipe.ui.components.playlist

import androidx.compose.runtime.Immutable
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Immutable
class PlaylistScreenInfo(
    val id: String,
    val serviceId: Int,
    val url: String,
    val name: String,
    val description: Description,
    val relatedItems: List<StreamInfoItem>,
    val streamCount: Long,
    val uploaderUrl: String?,
    val uploaderName: String?,
    val uploaderAvatars: List<Image>,
    val thumbnails: List<Image>,
    val nextPage: Page?
) {
    constructor(playlistInfo: PlaylistInfo) : this(
        playlistInfo.id,
        playlistInfo.serviceId,
        playlistInfo.url,
        playlistInfo.name,
        playlistInfo.description ?: Description.EMPTY_DESCRIPTION,
        playlistInfo.relatedItems,
        playlistInfo.streamCount,
        playlistInfo.uploaderUrl,
        playlistInfo.uploaderName,
        playlistInfo.uploaderAvatars,
        playlistInfo.thumbnails,
        playlistInfo.nextPage,
    )
}
