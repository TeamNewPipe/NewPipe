package org.schabi.newpipe.local.subscription.item

import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import org.schabi.newpipe.R

class FeedGroupAddItem : Item() {
    override fun getLayout(): Int = R.layout.feed_group_add_new_item
    override fun bind(viewHolder: ViewHolder, position: Int) {}
}