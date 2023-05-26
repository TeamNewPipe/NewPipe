package org.schabi.newpipe.local.holder

import android.text.TextUtils
import android.view.ViewGroup
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper
import org.schabi.newpipe.util.ServiceHelper
import java.time.format.DateTimeFormatter

open class RemotePlaylistItemHolder : PlaylistItemHolder {
    constructor(
        infoItemBuilder: LocalItemBuilder,
        parent: ViewGroup?
    ) : super(infoItemBuilder, parent) {
    }

    internal constructor(
        infoItemBuilder: LocalItemBuilder,
        layoutId: Int,
        parent: ViewGroup?
    ) : super(infoItemBuilder, layoutId, parent) {
    }

    override fun updateFromItem(
        item: LocalItem?,
        historyRecordManager: HistoryRecordManager?,
        dateTimeFormatter: DateTimeFormatter
    ) {
        if (item !is PlaylistRemoteEntity) {
            return
        }
        itemTitleView.text = item.name
        itemStreamCountView.text = Localization.localizeStreamCountMini(
            itemStreamCountView.context, item.streamCount
        )
        // Here is where the uploader name is set in the bookmarked playlists library
        if (!TextUtils.isEmpty(item.uploader)) {
            itemUploaderView.text = Localization.concatenateStrings(
                item.uploader,
                ServiceHelper.getNameOfServiceById(item.serviceId)
            )
        } else {
            itemUploaderView.text = ServiceHelper.getNameOfServiceById(item.serviceId)
        }
        PicassoHelper.loadPlaylistThumbnail(item.thumbnailUrl).into(itemThumbnailView)
        super.updateFromItem(item, historyRecordManager, dateTimeFormatter)
    }
}
