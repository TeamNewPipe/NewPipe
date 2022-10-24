package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedGroupAddNewItemVerticalBinding

class FeedGroupAddItemVertical : BindableItem<FeedGroupAddNewItemVerticalBinding>() {
    override fun getLayout(): Int = R.layout.feed_group_add_new_item_vertical
    override fun bind(viewBinding: FeedGroupAddNewItemVerticalBinding, position: Int) {}
    override fun initializeViewBinding(view: View) = FeedGroupAddNewItemVerticalBinding.bind(view)
}
