package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedGroupAddNewVerticalItemBinding

class FeedGroupAddVerticalItem : BindableItem<FeedGroupAddNewVerticalItemBinding>() {
    override fun getLayout(): Int = R.layout.feed_group_add_new_vertical_item
    override fun bind(viewBinding: FeedGroupAddNewVerticalItemBinding, position: Int) {}
    override fun initializeViewBinding(view: View) = FeedGroupAddNewVerticalItemBinding.bind(view)
}
