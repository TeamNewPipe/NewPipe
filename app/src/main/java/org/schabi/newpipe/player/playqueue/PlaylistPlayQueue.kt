package org.schabi.newpipe.player.playqueue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class PlaylistPlayQueue : AbstractInfoPlayQueue<PlaylistInfo> {

    constructor(info: PlaylistInfo) : super(info)
    constructor(info: PlaylistInfo, index: Int) : super(info, index)
    constructor(
        serviceId: Int,
        url: String,
        nextPage: Page?,
        streams: List<StreamInfoItem>,
        index: Int
    ) : super(serviceId, url, nextPage, streams, index)

    override fun getTag(): String = "PlaylistPlayQueue@${Integer.toHexString(hashCode())}"

    override fun fetch() {
        startFetch {
            try {
                if (isInitial) {
                    val result = withContext(Dispatchers.IO) {
                        ExtractorHelper.getPlaylistInfo(serviceId, baseUrl, false)
                    }
                    handleResult(result)
                } else {
                    val result = withContext(Dispatchers.IO) {
                        ExtractorHelper.getMorePlaylistItems(serviceId, baseUrl, nextPage!!)
                    }
                    handleNextPageResult(result)
                }
            } catch (e: Throwable) {
                handleError(e)
            }
        }
    }
}
