package org.schabi.newpipe.ui.components.playlist

import androidx.compose.runtime.Immutable
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Immutable
class PlaylistInfo(
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
    val nextPage: Page?
)
