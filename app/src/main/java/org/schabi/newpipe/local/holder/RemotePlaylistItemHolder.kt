package org.schabi.newpipe.local.holder

import android.text.TextUtils
import android.view.ViewGroup
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.PicassoHelper
import java.time.format.DateTimeFormatter

open class RemotePlaylistItemHolder : PlaylistItemHolder {
    constructor(infoItemBuilder: LocalItemBuilder,
                parent: ViewGroup?) : super(infoItemBuilder, parent)

    internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int,
                         parent: ViewGroup?) : super(infoItemBuilder, layoutId, parent)

    public override fun updateFromItem(localItem: LocalItem?,
                                       historyRecordManager: HistoryRecordManager?,
                                       dateTimeFormatter: DateTimeFormatter) {
        if (!(localItem is PlaylistRemoteEntity)) {
            return
        }
        val item: PlaylistRemoteEntity = localItem
        itemTitleView.setText(item.getName())
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.getStreamCount()))
        // Here is where the uploader name is set in the bookmarked playlists library
        if (!TextUtils.isEmpty(item.getUploader())) {
            itemUploaderView.setText(Localization.concatenateStrings(item.getUploader(),
                    ServiceHelper.getNameOfServiceById(item.getServiceId())))
        } else {
            itemUploaderView.setText(ServiceHelper.getNameOfServiceById(item.getServiceId()))
        }
        PicassoHelper.loadPlaylistThumbnail(item.getThumbnailUrl()).into(itemThumbnailView)
        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter)
    }
}
