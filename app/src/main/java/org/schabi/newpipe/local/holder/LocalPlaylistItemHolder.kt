package org.schabi.newpipe.local.holder

import android.view.View
import android.view.ViewGroup
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.PicassoHelper
import java.time.format.DateTimeFormatter

open class LocalPlaylistItemHolder : PlaylistItemHolder {
    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup?) : super(infoItemBuilder, parent)
    internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int,
                         parent: ViewGroup?) : super(infoItemBuilder, layoutId, parent)

    public override fun updateFromItem(localItem: LocalItem?,
                                       historyRecordManager: HistoryRecordManager?,
                                       dateTimeFormatter: DateTimeFormatter) {
        if (!(localItem is PlaylistMetadataEntry)) {
            return
        }
        val item: PlaylistMetadataEntry = localItem
        itemTitleView.setText(item.name)
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.streamCount))
        itemUploaderView.setVisibility(View.INVISIBLE)
        PicassoHelper.loadPlaylistThumbnail(item.thumbnailUrl).into(itemThumbnailView)
        if ((item is PlaylistDuplicatesEntry
                        && item.timesStreamIsContained > 0)) {
            itemView.setAlpha(GRAYED_OUT_ALPHA)
        } else {
            itemView.setAlpha(1.0f)
        }
        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter)
    }

    companion object {
        private val GRAYED_OUT_ALPHA: Float = 0.6f
    }
}
