package org.schabi.newpipe.info_list

import android.util.Log
import com.xwray.groupie.GroupieAdapter
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.max

/**
 * Custom RecyclerView.Adapter/GroupieAdapter for [StreamSegmentItem] for handling selection state.
 */
class StreamSegmentAdapter(
    private val listener: StreamSegmentListener
) : GroupieAdapter() {

    var currentIndex: Int = 0
        private set

    /**
     * Returns `true` if the provided [StreamInfo] contains segments, `false` otherwise.
     */
    fun setItems(info: StreamInfo): Boolean {
        if (info.streamSegments.isNotEmpty()) {
            clear()
            addAll(info.streamSegments.map { StreamSegmentItem(it, listener) })
            return true
        }
        return false
    }

    fun selectSegment(segment: StreamSegmentItem) {
        unSelectCurrentSegment()
        currentIndex = max(0, getAdapterPosition(segment))
        segment.isSelected = true
        segment.notifyChanged(StreamSegmentItem.PAYLOAD_SELECT)
    }

    fun selectSegmentAt(position: Int) {
        try {
            selectSegment(getGroupAtAdapterPosition(position) as StreamSegmentItem)
        } catch (e: IndexOutOfBoundsException) {
            // Just to make sure that getGroupAtAdapterPosition doesn't close the app
            // Shouldn't happen since setItems is always called before select-methods but just in case
            currentIndex = 0
            Log.e("StreamSegmentAdapter", "selectSegmentAt: ${e.message}")
        }
    }

    private fun unSelectCurrentSegment() {
        try {
            val segmentItem = getGroupAtAdapterPosition(currentIndex) as StreamSegmentItem
            currentIndex = 0
            segmentItem.isSelected = false
            segmentItem.notifyChanged(StreamSegmentItem.PAYLOAD_SELECT)
        } catch (e: IndexOutOfBoundsException) {
            // Just to make sure that getGroupAtAdapterPosition doesn't close the app
            // Shouldn't happen since setItems is always called before select-methods but just in case
            currentIndex = 0
            Log.e("StreamSegmentAdapter", "unSelectCurrentSegment: ${e.message}")
        }
    }

    interface StreamSegmentListener {
        fun onItemClick(item: StreamSegmentItem, seconds: Int)
    }
}
