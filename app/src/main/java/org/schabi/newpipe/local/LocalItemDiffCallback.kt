package org.schabi.newpipe.local

import androidx.recyclerview.widget.DiffUtil
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

class LocalItemDiffCallback(
    private val oldList: List<LocalItem>,
    private val newList: List<LocalItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        if (oldItem.localItemType != newItem.localItemType) {
            return false
        }

        if (oldItem is PlaylistStreamEntry && newItem is PlaylistStreamEntry) {
            return oldItem.streamEntity.uid == newItem.streamEntity.uid &&
                oldItem.streamId == newItem.streamId &&
                oldItem.joinIndex == newItem.joinIndex
        }

        if (oldItem is PlaylistLocalItem && newItem is PlaylistLocalItem) {
            return oldItem.uid == newItem.uid
        }

        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        if (oldItem is PlaylistStreamEntry && newItem is PlaylistStreamEntry) {
            return oldItem.streamEntity.title == newItem.streamEntity.title &&
                oldItem.streamEntity.duration == newItem.streamEntity.duration &&
                oldItem.streamEntity.uploader == newItem.streamEntity.uploader &&
                oldItem.joinIndex == newItem.joinIndex &&
                oldItem.progressMillis == newItem.progressMillis
        }

        if (oldItem is PlaylistLocalItem && newItem is PlaylistLocalItem) {
            return oldItem.orderingName == newItem.orderingName &&
                oldItem.displayIndex == newItem.displayIndex &&
                oldItem.thumbnailUrl == newItem.thumbnailUrl
        }

        return oldItem == newItem
    }
}
