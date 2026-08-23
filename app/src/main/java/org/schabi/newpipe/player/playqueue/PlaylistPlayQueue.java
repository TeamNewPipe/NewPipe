package org.schabi.newpipe.player.playqueue;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class PlaylistPlayQueue extends AbstractInfoPlayQueue<PlaylistInfo> {

    public PlaylistPlayQueue(final PlaylistInfo info) {
        super(info);
    }

    public PlaylistPlayQueue(final PlaylistInfo info, final int index) {
        super(info, index);
    }


    public PlaylistPlayQueue(final int serviceId,
                             final String url,
                             final Page nextPage,
                             final List<StreamInfoItem> streams,
                             final int index) {
        super(serviceId, url, nextPage, streams, index);
    }

    @Override
    protected String getTag() {
        return "PlaylistPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch() {
        if (this.isInitial) {
            ExtractorHelper.getPlaylistInfo(this.serviceId, this.baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver());
        } else {
            ExtractorHelper.getMorePlaylistItems(this.serviceId, this.baseUrl, this.nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver());
        }
    }
}
