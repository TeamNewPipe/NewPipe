package org.schabi.newpipe.local.subscription.item

import android.view.View
import androidx.core.view.isVisible
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SubscriptionGroupsHeaderBinding

class GroupsHeader(
    private val title: String,
    private val onSortClicked: () -> Unit,
    private val onSearchClicked: () -> Unit,
    private val onToggleListViewModeClicked: () -> Unit,
    private val onToggleCollapsedClicked: () -> Unit,
    var showSortButton: Boolean = true,
    var listViewMode: Boolean = true,
    var collapsed: Boolean = false
) : BindableItem<SubscriptionGroupsHeaderBinding>() {
    companion object {
        const val PAYLOAD_UPDATE_ICONS = 1
    }

    override fun getLayout(): Int = R.layout.subscription_groups_header

    override fun bind(
        viewBinding: SubscriptionGroupsHeaderBinding,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_UPDATE_ICONS)) {
            updateIcons(viewBinding)
            return
        }

        super.bind(viewBinding, position, payloads)
    }

    override fun bind(viewBinding: SubscriptionGroupsHeaderBinding, position: Int) {
        viewBinding.headerTitle.text = title
        viewBinding.headerSort.setOnClickListener { onSortClicked() }
        viewBinding.headerSearch.setOnClickListener { onSearchClicked() }
        viewBinding.headerToggleViewMode.setOnClickListener { onToggleListViewModeClicked() }
        viewBinding.headerToggleCollapse.setOnClickListener { onToggleCollapsedClicked() }
        updateIcons(viewBinding)
    }

    override fun initializeViewBinding(view: View) = SubscriptionGroupsHeaderBinding.bind(view)

    private fun updateIcons(viewBinding: SubscriptionGroupsHeaderBinding) {
        viewBinding.headerToggleViewMode.setImageResource(
            if (listViewMode) R.drawable.ic_apps else R.drawable.ic_list
        )
        viewBinding.headerSort.isVisible = showSortButton && !collapsed
        viewBinding.headerToggleViewMode.isVisible = !collapsed
        viewBinding.headerToggleCollapse.setImageResource(
            if (collapsed) R.drawable.ic_expand_more else R.drawable.ic_arrow_drop_up
        )
        viewBinding.headerToggleCollapse.contentDescription = viewBinding.root.context.getString(
            if (collapsed) R.string.subscription_groups_expand else R.string.subscription_groups_collapse
        )
    }
}
