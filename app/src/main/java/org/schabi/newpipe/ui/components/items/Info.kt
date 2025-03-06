package org.schabi.newpipe.ui.components.items

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.util.NO_SERVICE_ID

sealed class Info

class Playlist(
    val serviceId: Int = NO_SERVICE_ID,
    val url: String = "",
    val name: String = "",
    val thumbnails: List<Image> = emptyList(),
    val uploaderName: String = "",
    val streamCount: Long = 10,
) : Info() {

    constructor(item: PlaylistInfoItem) : this(
        item.serviceId, item.url, item.name, item.thumbnails, item.uploaderName.orEmpty(),
        item.streamCount
    )
}
