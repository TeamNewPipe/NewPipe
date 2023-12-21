package org.schabi.newpipe.player.playqueue;


import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class ChannelTabPlayQueue extends AbstractInfoPlayQueue<ChannelTabInfo> {

    final ListLinkHandler linkHandler;

    public ChannelTabPlayQueue(final int serviceId,
                               final ListLinkHandler linkHandler,
                               final Page nextPage,
                               final List<StreamInfoItem> streams,
                               final int index) {
        super(serviceId, linkHandler.getUrl(), nextPage, streams, index);
        this.linkHandler = linkHandler;
    }

    public ChannelTabPlayQueue(final int serviceId,
                               final ListLinkHandler linkHandler) {
        this(serviceId, linkHandler, null, Collections.emptyList(), 0);
    }

    @Override
    protected String getTag() {
        return "ChannelTabPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch() {
        if (isInitial) {
            ExtractorHelper.getChannelTab(this.serviceId, this.linkHandler, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver());
        } else {
            ExtractorHelper.getMoreChannelTabItems(this.serviceId, this.linkHandler, this.nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver());
        }
    }
}
