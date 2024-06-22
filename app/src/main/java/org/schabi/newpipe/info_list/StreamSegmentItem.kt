package org.schabi.newpipe.info_list

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ItemStreamSegmentBinding
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.CoilHelper

class StreamSegmentItem(
    private val item: StreamSegment,
    private val onClick: StreamSegmentAdapter.StreamSegmentListener
) : BindableItem<ItemStreamSegmentBinding>() {

    companion object {
        const val PAYLOAD_SELECT = 1
    }

    var isSelected = false

    override fun bind(viewBinding: ItemStreamSegmentBinding, position: Int) {
        CoilHelper.loadThumbnail(viewBinding.previewImage, item.previewUrl)
        viewBinding.textViewTitle.text = item.title
        if (item.channelName == null) {
            viewBinding.textViewChannel.visibility = View.GONE
            // When the channel name is displayed there is less space
            // and thus the segment title needs to be only one line height.
            // But when there is no channel name displayed, the title can be two lines long.
            // The default maxLines value is set to 1 to display all elements in the AS preview,
            viewBinding.textViewTitle.maxLines = 2
        } else {
            viewBinding.textViewChannel.text = item.channelName
            viewBinding.textViewChannel.visibility = View.VISIBLE
        }
        viewBinding.textViewStartSeconds.text =
            Localization.getDurationString(item.startTimeSeconds.toLong())
        viewBinding.root.setOnClickListener { onClick.onItemClick(this, item.startTimeSeconds) }
        viewBinding.root.setOnLongClickListener { onClick.onItemLongClick(this, item.startTimeSeconds); true }
        viewBinding.root.isSelected = isSelected
    }

    override fun bind(
        viewHolder: GroupieViewHolder<ItemStreamSegmentBinding>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECT)) {
            viewHolder.root.isSelected = isSelected
            return
        }
        super.bind(viewHolder, position, payloads)
    }

    override fun getLayout() = R.layout.item_stream_segment

    override fun initializeViewBinding(view: View) = ItemStreamSegmentBinding.bind(view)
}
