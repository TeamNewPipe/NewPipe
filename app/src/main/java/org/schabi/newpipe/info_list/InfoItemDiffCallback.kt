package org.schabi.newpipe.info_list

import androidx.recyclerview.widget.DiffUtil
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class InfoItemDiffCallback(
    private val oldList: List<InfoItem>,
    private val newList: List<InfoItem>
) : DiffUtil.Callback() {

    private val oldOccurrenceIndices: IntArray = computeOccurrenceIndices(oldList)
    private val newOccurrenceIndices: IntArray = computeOccurrenceIndices(newList)

    private fun getItemKey(item: InfoItem): String {
        return when {
            !item.url.isNullOrEmpty() ->
                "${item.infoType}_${item.serviceId}_${item.url}"

            !item.name.isNullOrEmpty() ->
                "${item.infoType}_${item.serviceId}_${item.name}"

            else -> item.toString()
        }
    }

    private fun computeOccurrenceIndices(list: List<InfoItem>): IntArray {
        val indices = IntArray(list.size)
        val counts = HashMap<String, Int>()
        for (i in list.indices) {
            val key = getItemKey(list[i])
            val count = (counts[key] ?: 0) + 1
            counts[key] = count
            indices[i] = count
        }
        return indices
    }

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        if (oldItem.infoType != newItem.infoType) {
            return false
        }
        if (oldItem.serviceId != newItem.serviceId) {
            return false
        }

        val sameKey = when {
            !oldItem.url.isNullOrEmpty() && !newItem.url.isNullOrEmpty() ->
                oldItem.url == newItem.url

            !oldItem.name.isNullOrEmpty() && !newItem.name.isNullOrEmpty() ->
                oldItem.name == newItem.name

            else -> oldItem == newItem
        }

        if (!sameKey) {
            return false
        }

        return oldOccurrenceIndices[oldItemPosition] == newOccurrenceIndices[newItemPosition]
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        if (oldItem.name != newItem.name || oldItem.url != newItem.url) {
            return false
        }

        if (oldItem is StreamInfoItem && newItem is StreamInfoItem) {
            return oldItem.duration == newItem.duration &&
                oldItem.uploaderName == newItem.uploaderName &&
                oldItem.viewCount == newItem.viewCount &&
                oldItem.streamType == newItem.streamType
        }

        if (oldItem is PlaylistInfoItem && newItem is PlaylistInfoItem) {
            return oldItem.streamCount == newItem.streamCount &&
                oldItem.uploaderName == newItem.uploaderName
        }

        if (oldItem is ChannelInfoItem && newItem is ChannelInfoItem) {
            return oldItem.subscriberCount == newItem.subscriberCount
        }

        return true
    }
}
