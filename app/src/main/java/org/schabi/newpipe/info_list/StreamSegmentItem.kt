package org.schabi.newpipe.info_list

import android.widget.ImageView
import android.widget.TextView
import com.nostra13.universalimageloader.core.ImageLoader
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

class StreamSegmentItem(
    private val item: StreamSegment,
    private val onClick: StreamSegmentAdapter.StreamSegmentListener
) : Item<GroupieViewHolder>() {

    companion object {
        const val PAYLOAD_SELECT = 1
    }

    var isSelected = false

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        item.previewUrl?.let {
            ImageLoader.getInstance().displayImage(
                it, viewHolder.root.findViewById<ImageView>(R.id.previewImage),
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS
            )
        }
        viewHolder.root.findViewById<TextView>(R.id.textViewTitle).text = item.title
        viewHolder.root.findViewById<TextView>(R.id.textViewStartSeconds).text =
            Localization.getDurationString(item.startTimeSeconds.toLong())
        viewHolder.root.setOnClickListener { onClick.onItemClick(this, item.startTimeSeconds) }
        viewHolder.root.isSelected = isSelected
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECT)) {
            viewHolder.root.isSelected = isSelected
            return
        }
        super.bind(viewHolder, position, payloads)
    }

    override fun getLayout() = R.layout.item_stream_segment
}
