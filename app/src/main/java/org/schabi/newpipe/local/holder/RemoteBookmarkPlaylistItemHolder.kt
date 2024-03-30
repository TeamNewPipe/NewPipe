package org.schabi.newpipe.local.holder

import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import java.time.format.DateTimeFormatter

class RemoteBookmarkPlaylistItemHolder internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int,
                                                            parent: ViewGroup?) : RemotePlaylistItemHolder(infoItemBuilder, layoutId, parent) {
    private val itemHandleView: View

    constructor(infoItemBuilder: LocalItemBuilder,
                parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_playlist_bookmark_item, parent)

    init {
        itemHandleView = itemView.findViewById(R.id.itemHandle)
    }

    public override fun updateFromItem(localItem: LocalItem?,
                                       historyRecordManager: HistoryRecordManager?,
                                       dateTimeFormatter: DateTimeFormatter) {
        if (!(localItem is PlaylistRemoteEntity)) {
            return
        }
        itemHandleView.setOnTouchListener(getOnTouchListener(localItem))
        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter)
    }

    private fun getOnTouchListener(item: PlaylistRemoteEntity): OnTouchListener {
        return OnTouchListener({ view: View, motionEvent: MotionEvent ->
            view.performClick()
            if ((itemBuilder != null) && (itemBuilder.getOnItemSelectedListener() != null
                            ) && (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN)) {
                itemBuilder.getOnItemSelectedListener().drag(item,
                        this@RemoteBookmarkPlaylistItemHolder)
            }
            false
        })
    }
}
