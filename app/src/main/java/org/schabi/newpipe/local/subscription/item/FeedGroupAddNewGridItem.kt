package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedGroupAddNewGridItemBinding

class FeedGroupAddNewGridItem : BindableItem<FeedGroupAddNewGridItemBinding>() {
    override fun getLayout(): Int = R.layout.feed_group_add_new_grid_item
    override fun bind(viewBinding: FeedGroupAddNewGridItemBinding, position: Int) {}
    override fun initializeViewBinding(view: View) = FeedGroupAddNewGridItemBinding.bind(view)
}
