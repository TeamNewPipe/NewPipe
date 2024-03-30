package org.schabi.newpipe.player.playqueue

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class PlaylistPlayQueue : AbstractInfoPlayQueue<PlaylistInfo?> {
    constructor(info: PlaylistInfo) : super(info)
    constructor(serviceId: Int,
                url: String?,
                nextPage: Page?,
                streams: List<StreamInfoItem>,
                index: Int) : super(serviceId, url, nextPage, streams, index)

    protected override val tag: String
        protected get() {
            return "PlaylistPlayQueue@" + Integer.toHexString(hashCode())
        }

    public override fun fetch() {
        if (isInitial) {
            ExtractorHelper.getPlaylistInfo(serviceId, baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver())
        } else {
            ExtractorHelper.getMorePlaylistItems(serviceId, baseUrl, nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver())
        }
    }
}
