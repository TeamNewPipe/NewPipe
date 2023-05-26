package org.schabi.newpipe.local.holder

import android.view.View
import android.view.ViewGroup
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper
import java.time.format.DateTimeFormatter

open class LocalPlaylistItemHolder : PlaylistItemHolder {
    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup?) : super(infoItemBuilder, parent) {}
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
        if (item !is PlaylistMetadataEntry) {
            return
        }
        itemTitleView.text = item.name
        itemStreamCountView.text = Localization.localizeStreamCountMini(
            itemStreamCountView.context, item.streamCount
        )
        itemUploaderView.visibility = View.INVISIBLE
        PicassoHelper.loadPlaylistThumbnail(item.thumbnailUrl).into(itemThumbnailView)
        if (item is PlaylistDuplicatesEntry &&
            item.timesStreamIsContained > 0
        ) {
            itemView.alpha = GRAYED_OUT_ALPHA
        } else {
            itemView.alpha = 1.0f
        }
        super.updateFromItem(item, historyRecordManager, dateTimeFormatter)
    }

    companion object {
        private const val GRAYED_OUT_ALPHA = 0.6f
    }
}
