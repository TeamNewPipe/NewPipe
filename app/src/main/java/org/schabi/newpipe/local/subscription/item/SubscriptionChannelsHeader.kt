package org.schabi.newpipe.local.subscription.item

import android.view.View
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.xwray.groupie.viewbinding.BindableItem
import java.text.NumberFormat
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SubscriptionChannelsHeaderBinding

/** The channel section and its inline editor share the same labeled, keyboard-accessible actions. */
class SubscriptionChannelsHeader(
    private val onEditClicked: () -> Unit,
    private val onSearchClicked: () -> Unit,
    private val onToggleCollapsedClicked: () -> Unit
) : BindableItem<SubscriptionChannelsHeaderBinding>() {
    var count = 0
    var collapsed = false

    override fun getLayout() = R.layout.subscription_channels_header

    override fun initializeViewBinding(view: View) = SubscriptionChannelsHeaderBinding.bind(view)

    override fun bind(viewBinding: SubscriptionChannelsHeaderBinding, position: Int) = bindControls(viewBinding)

    fun bindControls(binding: SubscriptionChannelsHeaderBinding, editing: Boolean = false) {
        val context = binding.root.context
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
            ?: java.util.Locale.getDefault()
        binding.channelsHeaderCount.text = NumberFormat.getIntegerInstance(locale).format(count)
        binding.channelsHeaderCount.contentDescription = context.resources.getQuantityString(
            R.plurals.subscription_total_count,
            count,
            count
        )
        ViewCompat.setAccessibilityHeading(binding.channelsHeaderTitle, true)
        binding.channelsHeaderEdit.setImageResource(if (editing) R.drawable.ic_done else R.drawable.ic_edit)
        binding.channelsHeaderEdit.contentDescription = context.getString(
            if (editing) R.string.playlist_edit_done else R.string.subscription_channels_edit
        )
        binding.channelsHeaderEdit.setOnClickListener { onEditClicked() }
        binding.channelsHeaderSearch.setOnClickListener { onSearchClicked() }
        binding.channelsHeaderCollapse.isVisible = !editing
        binding.channelsHeaderCollapse.setImageResource(if (collapsed) R.drawable.ic_expand_more else R.drawable.ic_arrow_drop_up)
        binding.channelsHeaderCollapse.contentDescription = context.getString(
            if (collapsed) R.string.subscription_channels_expand else R.string.subscription_channels_collapse
        )
        binding.channelsHeaderCollapse.setOnClickListener { onToggleCollapsedClicked() }
    }
}
