package org.schabi.newpipe.local.subscription.item

import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import androidx.annotation.DrawableRes
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.header_with_menu_item.header_menu_item
import kotlinx.android.synthetic.main.header_with_menu_item.header_title
import org.schabi.newpipe.R

class HeaderWithMenuItem(
    val title: String,
    @DrawableRes val itemIcon: Int = 0,
    var showMenuItem: Boolean = true,
    private val onClickListener: (() -> Unit)? = null,
    private val menuItemOnClickListener: (() -> Unit)? = null
) : Item() {
    companion object {
        const val PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM = 1
    }

    override fun getLayout(): Int = R.layout.header_with_menu_item

    override fun bind(viewHolder: GroupieViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM)) {
            updateMenuItemVisibility(viewHolder)
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
        updateMenuItemVisibility(viewHolder)
    }

    private fun updateMenuItemVisibility(viewHolder: GroupieViewHolder) {
        viewHolder.header_menu_item.visibility = if (showMenuItem) VISIBLE else GONE
    }
}
