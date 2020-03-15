package org.schabi.newpipe.player.playqueue;

import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public final class ChannelPlayQueue extends AbstractInfoPlayQueue<ChannelTabInfo, ChannelInfoItem> {
    private ChannelTabInfo channelTabInfo;

    public ChannelPlayQueue(final ChannelTabInfo channelTabInfo, final List<StreamInfoItem> streams, final int index) {
        super(channelTabInfo.getServiceId(), channelTabInfo.getUrl(), channelTabInfo.getNextPageUrl(), streams, index);

        this.channelTabInfo = channelTabInfo;
    }

    @Override
    protected String getTag() {
        return "ChannelPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch() {
        if (this.isInitial) {
            ExtractorHelper.getChannelInfo(this.serviceId, this.baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((Consumer<? super ChannelInfo>) getHeadListObserver());
        } else {
            ExtractorHelper.getMoreChannelTabItems(this.channelTabInfo, this.nextUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver());
        }
    }
}
