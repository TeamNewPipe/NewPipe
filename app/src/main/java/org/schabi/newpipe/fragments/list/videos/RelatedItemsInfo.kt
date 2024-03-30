package org.schabi.newpipe.fragments.list.videos

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfo

class RelatedItemsInfo(info: StreamInfo) : ListInfo<InfoItem?>(info.getServiceId(), ListLinkHandler(info.getOriginalUrl(), info.getUrl(),
        info.getId(), emptyList(), null), info.getName()) {
    /**
     * This class is used to wrap the related items of a StreamInfo into a ListInfo object.
     *
     * @param info the stream info from which to get related items
     */
    init {
        setRelatedItems(ArrayList(info.getRelatedItems()))
    }
}
