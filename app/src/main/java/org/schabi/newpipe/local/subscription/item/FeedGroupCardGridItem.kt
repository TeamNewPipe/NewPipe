package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FeedGroupCardGridItemBinding
import org.schabi.newpipe.local.subscription.FeedGroupIcon

data class FeedGroupCardGridItem(
    val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    val name: String,
    val icon: FeedGroupIcon,
) : BindableItem<FeedGroupCardGridItemBinding>() {
    constructor (feedGroupEntity: FeedGroupEntity) : this(feedGroupEntity.uid, feedGroupEntity.name, feedGroupEntity.icon)

    override fun getId(): Long {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> super.getId()
            else -> groupId
        }
    }

    override fun getLayout(): Int = R.layout.feed_group_card_grid_item

    override fun bind(viewBinding: FeedGroupCardGridItemBinding, position: Int) {
        viewBinding.title.text = name
        viewBinding.icon.setImageResource(icon.getDrawableRes())
    }

    override fun initializeViewBinding(view: View) = FeedGroupCardGridItemBinding.bind(view)
}
