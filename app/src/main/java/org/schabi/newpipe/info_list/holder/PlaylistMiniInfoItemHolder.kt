package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper

open class PlaylistMiniInfoItemHolder(
    infoItemBuilder: InfoItemBuilder?,
    layoutId: Int,
    parent: ViewGroup?
) : InfoItemHolder(infoItemBuilder!!, layoutId, parent) {
    val itemThumbnailView: ImageView
    private val itemStreamCountView: TextView
    val itemTitleView: TextView
    val itemUploaderView: TextView

    init {
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemTitleView = itemView.findViewById(R.id.itemTitleView)
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView)
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView)
    }

    constructor(
        infoItemBuilder: InfoItemBuilder?,
        parent: ViewGroup?
    ) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent) {
    }

    override fun updateFromItem(
        infoItem: InfoItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        if (infoItem !is PlaylistInfoItem) {
            return
        }
        val item = infoItem
        itemTitleView.text = item.name
        itemStreamCountView.text = Localization
            .localizeStreamCountMini(itemStreamCountView.context, item.streamCount)
        itemUploaderView.text = item.uploaderName
        PicassoHelper.loadPlaylistThumbnail(item.thumbnailUrl).into(itemThumbnailView)
        itemView.setOnClickListener {
            if (itemBuilder.onPlaylistSelectedListener != null) {
                itemBuilder.onPlaylistSelectedListener.selected(item)
            }
        }
        itemView.isLongClickable = true
        itemView.setOnLongClickListener {
            if (itemBuilder.onPlaylistSelectedListener != null) {
                itemBuilder.onPlaylistSelectedListener.held(item)
            }
            true
        }
    }
}
