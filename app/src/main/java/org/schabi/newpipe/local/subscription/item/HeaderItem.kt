package org.schabi.newpipe.local.subscription.item

import android.view.View.OnClickListener
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.header_item.header_title
import org.schabi.newpipe.R

class HeaderItem(val title: String, private val onClickListener: (() -> Unit)? = null) : Item() {

    override fun getLayout(): Int = R.layout.header_item

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.header_title.text = title

        val listener: OnClickListener? = if (onClickListener != null) OnClickListener { onClickListener.invoke() } else null
        viewHolder.root.setOnClickListener(listener)
    }
}
