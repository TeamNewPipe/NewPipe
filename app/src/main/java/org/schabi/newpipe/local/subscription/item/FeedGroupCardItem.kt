package org.schabi.newpipe.local.subscription.item

import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import kotlinx.android.synthetic.main.feed_group_card_item.*
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon

data class FeedGroupCardItem(
        val groupId: Long = -1,
        val name: String,
        val icon: FeedGroupIcon
) : Item() {
    constructor (feedGroupEntity: FeedGroupEntity) : this(feedGroupEntity.uid, feedGroupEntity.name, feedGroupEntity.icon)

    override fun getId(): Long {
        return if (groupId == -1L) super.getId() else groupId
    }

    override fun getLayout(): Int = R.layout.feed_group_card_item

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.title.text = name
        viewHolder.icon.setImageResource(icon.getDrawableRes(viewHolder.containerView.context))
    }
}