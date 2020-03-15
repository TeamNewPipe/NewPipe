package org.schabi.newpipe.local.subscription.item

import android.view.View.*
import androidx.annotation.DrawableRes
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.header_with_menu_item.*
import org.schabi.newpipe.R

class HeaderWithMenuItem(
        val title: String,
        @DrawableRes val itemIcon: Int = 0,
        private val onClickListener: (() -> Unit)? = null,
        private val menuItemOnClickListener: (() -> Unit)? = null
) : Item() {
    companion object {
        const val PAYLOAD_SHOW_MENU_ITEM = 1
        const val PAYLOAD_HIDE_MENU_ITEM = 2
    }

    override fun getLayout(): Int = R.layout.header_with_menu_item


    override fun bind(viewHolder: GroupieViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SHOW_MENU_ITEM)) {
            viewHolder.header_menu_item.visibility = VISIBLE
            return
        } else if (payloads.contains(PAYLOAD_HIDE_MENU_ITEM)) {
            viewHolder.header_menu_item.visibility = GONE
            return
        }

        super.bind(viewHolder, position, payloads)
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.header_title.text = title
        viewHolder.header_menu_item.setImageResource(itemIcon)

        val listener: OnClickListener? =
                onClickListener?.let { OnClickListener { onClickListener.invoke() } }
        viewHolder.root.setOnClickListener(listener)

        val menuItemListener: OnClickListener? =
                menuItemOnClickListener?.let { OnClickListener { menuItemOnClickListener.invoke() } }
        viewHolder.header_menu_item.setOnClickListener(menuItemListener)
    }
}