package org.schabi.newpipe.local.subscription.item

import android.view.View
import androidx.core.graphics.ColorUtils
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FeedGroupCardGridItemBinding
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.util.ThemeHelper

data class FeedGroupCardGridItem(
    val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    val name: String,
    val icon: FeedGroupIcon,
    val isSelected: Boolean = false
) : BindableItem<FeedGroupCardGridItemBinding>() {
    constructor (feedGroupEntity: FeedGroupEntity) : this(feedGroupEntity.uid, feedGroupEntity.name, feedGroupEntity.icon)

    override fun getId(): Long {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID, FeedGroupEntity.GROUP_UNGROUPED_ID -> super.getId()
            else -> groupId
        }
    }

    override fun getLayout(): Int = R.layout.feed_group_card_grid_item

    override fun bind(viewBinding: FeedGroupCardGridItemBinding, position: Int) {
        viewBinding.title.text = name
        viewBinding.icon.setImageResource(icon.getDrawableRes())
        val context = viewBinding.root.context
        viewBinding.root.setCardBackgroundColor(
            if (isSelected) {
                ColorUtils.blendARGB(
                    ThemeHelper.resolveColorFromAttr(context, R.attr.card_item_background_color),
                    ThemeHelper.resolveColorFromAttr(context, android.R.attr.colorAccent),
                    0.15f
                )
            } else {
                ThemeHelper.resolveColorFromAttr(context, R.attr.card_item_background_color)
            }
        )
    }

    override fun initializeViewBinding(view: View) = FeedGroupCardGridItemBinding.bind(view)
}
