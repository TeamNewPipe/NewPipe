package org.schabi.newpipe.info_list

import android.view.View
import com.nostra13.universalimageloader.core.ImageLoader
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ItemStreamSegmentBinding
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

class StreamSegmentItem(
    private val item: StreamSegment,
    private val onClick: StreamSegmentAdapter.StreamSegmentListener
) : BindableItem<ItemStreamSegmentBinding>() {
    companion object {
        const val PAYLOAD_SELECT = 1
    }

    var isSelected = false

    override fun initializeViewBinding(view: View) = ItemStreamSegmentBinding.bind(view)

    override fun bind(viewBinding: ItemStreamSegmentBinding, position: Int) {
        item.previewUrl?.let {
            ImageLoader.getInstance().displayImage(
                it, viewBinding.previewImage,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS
            )
        }
        viewBinding.textViewTitle.text = item.title
        viewBinding.textViewStartSeconds.text = Localization.getDurationString(item.startTimeSeconds.toLong())
        viewBinding.root.setOnClickListener { onClick.onItemClick(this, item.startTimeSeconds) }
        viewBinding.root.isSelected = isSelected
    }

    override fun bind(viewBinding: ItemStreamSegmentBinding, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECT)) {
            viewBinding.root.isSelected = isSelected
            return
        }
        super.bind(viewBinding, position, payloads)
    }

    override fun getLayout() = R.layout.item_stream_segment
}
