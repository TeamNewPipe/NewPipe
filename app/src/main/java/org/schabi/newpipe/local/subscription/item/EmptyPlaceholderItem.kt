package org.schabi.newpipe.local.subscription.item

import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import org.schabi.newpipe.R

class EmptyPlaceholderItem : Item() {
    override fun getLayout(): Int = R.layout.list_empty_view
    override fun bind(viewHolder: ViewHolder, position: Int) {}
}