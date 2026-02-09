package org.schabi.newpipe.player.playqueue;


import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class ChannelTabPlayQueue extends AbstractInfoPlayQueue<ChannelTabInfo> {

    /**
     * The channel tab link handler.
     * If null, it indicates that we have yet to fetch the channel info and choose a tab from it.
     */
    @Nullable
    ListLinkHandler tabHandler;

    public ChannelTabPlayQueue(final int serviceId,
                               final ListLinkHandler tabHandler,
                               final Page nextPage,
                               final List<StreamInfoItem> streams,
                               final int index) {
        super(serviceId, tabHandler.getUrl(), nextPage, streams, index);
        this.tabHandler = tabHandler;
    }

    public ChannelTabPlayQueue(final int serviceId,
                               final ListLinkHandler linkHandler) {
        this(serviceId, linkHandler, null, Collections.emptyList(), 0);
    }

    /**
     * Plays the streams in the channel tab where {@link ChannelTabHelper#isStreamsTab} returns
     * true, choosing the first such tab among the ones returned by {@code ChannelInfo.getTabs()}.
     * @param serviceId the service ID of the channel
     * @param channelUrl the channel URL
     */
    public ChannelTabPlayQueue(final int serviceId,
                               final String channelUrl) {
        super(serviceId, channelUrl, null, Collections.emptyList(), 0);
        tabHandler = null;
    }

    @Override
    protected String getTag() {
        return "ChannelTabPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch() {
        if (isInitial) {
            if (tabHandler == null) {
                // we still have not chosen a tab, so we need to fetch the channel
                ExtractorHelper.getChannelInfo(this.serviceId, this.baseUrl, false)
                        .flatMap(channelInfo -> {
                            tabHandler = channelInfo.getTabs()
                                    .stream()
                                    .filter(ChannelTabHelper::isStreamsTab)
                                    .findFirst()
                                    .orElseThrow(() -> new ExtractionException(
                                            "No playable channel tab found"));

                            return ExtractorHelper
                                    .getChannelTab(this.serviceId, this.tabHandler, false);
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(getHeadListObserver());

            } else {
                // fetch the initial page of the channel tab
                ExtractorHelper.getChannelTab(this.serviceId, this.tabHandler, false)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(getHeadListObserver());
            }
        } else {
            // fetch the successive page of the channel tab
            ExtractorHelper.getMoreChannelTabItems(this.serviceId, this.tabHandler, this.nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver());
        }
    }
}
