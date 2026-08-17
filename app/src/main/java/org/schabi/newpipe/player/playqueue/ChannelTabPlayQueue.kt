package org.schabi.newpipe.player.playqueue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class ChannelTabPlayQueue : AbstractInfoPlayQueue<ChannelTabInfo> {

    val linkHandler: ListLinkHandler

    constructor(
        serviceId: Int,
        linkHandler: ListLinkHandler,
        nextPage: Page?,
        streams: List<StreamInfoItem>,
        index: Int
    ) : super(serviceId, linkHandler.url, nextPage, streams, index) {
        this.linkHandler = linkHandler
    }

    constructor(serviceId: Int, linkHandler: ListLinkHandler) : this(
        serviceId,
        linkHandler,
        null,
        emptyList(),
        0
    )

    override fun getTag(): String = "ChannelTabPlayQueue@${Integer.toHexString(hashCode())}"

    override fun fetch() {
        startFetch {
            try {
                if (isInitial) {
                    val result = withContext(Dispatchers.IO) {
                        ExtractorHelper.getChannelTab(serviceId, linkHandler, false)
                    }
                    handleResult(result)
                } else {
                    val result = withContext(Dispatchers.IO) {
                        ExtractorHelper.getMoreChannelTabItems(serviceId, linkHandler, nextPage!!)
                    }
                    handleNextPageResult(result)
                }
            } catch (e: Throwable) {
                handleError(e)
            }
        }
    }
}
