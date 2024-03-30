package org.schabi.newpipe.local.holder

import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import java.time.format.DateTimeFormatter

abstract class PlaylistItemHolder(infoItemBuilder: LocalItemBuilder, layoutId: Int,
                                  parent: ViewGroup?) : LocalItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView
    val itemStreamCountView: TextView
    val itemTitleView: TextView
    val itemUploaderView: TextView

    init {
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemTitleView = itemView.findViewById(R.id.itemTitleView)
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView)
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView)
    }

    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent)

    public override fun updateFromItem(localItem: LocalItem?,
                                       historyRecordManager: HistoryRecordManager?,
                                       dateTimeFormatter: DateTimeFormatter) {
        itemView.setOnClickListener(View.OnClickListener({ view: View? ->
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().selected(localItem)
            }
        }))
        itemView.setLongClickable(true)
        itemView.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().held(localItem)
            }
            true
        }))
    }
}
