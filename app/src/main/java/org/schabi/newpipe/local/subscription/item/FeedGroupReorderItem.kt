package org.schabi.newpipe.local.subscription.item

import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.feed_group_reorder_item.group_icon
import kotlinx.android.synthetic.main.feed_group_reorder_item.group_name
import kotlinx.android.synthetic.main.feed_group_reorder_item.handle
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon

data class FeedGroupReorderItem(
    val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    val name: String,
    val icon: FeedGroupIcon,
    val dragCallback: ItemTouchHelper
) : Item() {
    constructor (feedGroupEntity: FeedGroupEntity, dragCallback: ItemTouchHelper) :
            this(feedGroupEntity.uid, feedGroupEntity.name, feedGroupEntity.icon, dragCallback)

    override fun getId(): Long {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> super.getId()
            else -> groupId
        }
    }

    override fun getLayout(): Int = R.layout.feed_group_reorder_item

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.group_name.text = name
        viewHolder.group_icon.setImageResource(icon.getDrawableRes(viewHolder.containerView.context))
        viewHolder.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                dragCallback.startDrag(viewHolder)
                return@setOnTouchListener true
            }

            false
        }
    }

    override fun getDragDirs(): Int {
        return UP or DOWN
    }
}
