package org.schabi.newpipe.local.subscription.item

import android.content.Context
import androidx.annotation.DrawableRes
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.picker_icon_item.icon_view
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.FeedGroupIcon

class PickerIconItem(context: Context, val icon: FeedGroupIcon) : Item() {
    @DrawableRes
    val iconRes: Int = icon.getDrawableRes(context)

    override fun getLayout(): Int = R.layout.picker_icon_item

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.icon_view.setImageResource(iconRes)
    }
}
