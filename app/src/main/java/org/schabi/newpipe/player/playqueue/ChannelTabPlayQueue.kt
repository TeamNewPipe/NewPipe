package org.schabi.newpipe.player.playqueue

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class ChannelTabPlayQueue @JvmOverloads constructor(serviceId: Int,
                                                    val linkHandler: ListLinkHandler?,
                                                    nextPage: Page? = null,
                                                    streams: List<StreamInfoItem> = emptyList(),
                                                    index: Int = 0) : AbstractInfoPlayQueue<ChannelTabInfo?>(serviceId, linkHandler!!.getUrl(), nextPage, streams, index) {
    protected override val tag: String
        protected get() {
            return "ChannelTabPlayQueue@" + Integer.toHexString(hashCode())
        }

    public override fun fetch() {
        if (isInitial) {
            ExtractorHelper.getChannelTab(serviceId, linkHandler, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver())
        } else {
            ExtractorHelper.getMoreChannelTabItems(serviceId, linkHandler, nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver())
        }
    }
}
