package org.schabi.newpipe.info_list.holder

import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.PicassoHelper

open class PlaylistMiniInfoItemHolder(infoItemBuilder: InfoItemBuilder, layoutId: Int,
                                      parent: ViewGroup?) : InfoItemHolder(infoItemBuilder, layoutId, parent) {
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

    constructor(infoItemBuilder: InfoItemBuilder,
                parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent)

    public override fun updateFromItem(infoItem: InfoItem?,
                                       historyRecordManager: HistoryRecordManager) {
        if (!(infoItem is PlaylistInfoItem)) {
            return
        }
        val item: PlaylistInfoItem = infoItem
        itemTitleView.setText(item.getName())
        itemStreamCountView.setText(Localization.localizeStreamCountMini(itemStreamCountView.getContext(), item.getStreamCount()))
        itemUploaderView.setText(item.getUploaderName())
        PicassoHelper.loadPlaylistThumbnail(item.getThumbnails()).into(itemThumbnailView)
        itemView.setOnClickListener(View.OnClickListener({ view: View? ->
            if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                itemBuilder.getOnPlaylistSelectedListener()!!.selected(item)
            }
        }))
        itemView.setLongClickable(true)
        itemView.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                itemBuilder.getOnPlaylistSelectedListener()!!.held(item)
            }
            true
        }))
    }
}
