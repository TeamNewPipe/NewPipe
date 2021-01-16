package org.schabi.newpipe.local.subscription.item

import android.view.View
import android.view.View.OnClickListener
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.HeaderWithMenuItemBinding

class HeaderWithMenuItem(
    val title: String,
    @DrawableRes val itemIcon: Int = 0,
    var showMenuItem: Boolean = true,
    private val onClickListener: (() -> Unit)? = null,
    private val menuItemOnClickListener: (() -> Unit)? = null
) : BindableItem<HeaderWithMenuItemBinding>() {
    companion object {
        const val PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM = 1
    }

    override fun getLayout(): Int = R.layout.header_with_menu_item

    override fun bind(viewBinding: HeaderWithMenuItemBinding, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM)) {
            updateMenuItemVisibility(viewBinding)
            return
        }

        super.bind(viewBinding, position, payloads)
    }

    override fun bind(viewBinding: HeaderWithMenuItemBinding, position: Int) {
        viewBinding.headerTitle.text = title
        viewBinding.headerMenuItem.setImageResource(itemIcon)

        val listener = onClickListener?.let { OnClickListener { onClickListener.invoke() } }
        viewBinding.root.setOnClickListener(listener)

        val menuItemListener = menuItemOnClickListener?.let { OnClickListener { menuItemOnClickListener.invoke() } }
        viewBinding.headerMenuItem.setOnClickListener(menuItemListener)
        updateMenuItemVisibility(viewBinding)
    }

    override fun initializeViewBinding(view: View) = HeaderWithMenuItemBinding.bind(view)

    private fun updateMenuItemVisibility(viewBinding: HeaderWithMenuItemBinding) {
        viewBinding.headerMenuItem.isVisible = showMenuItem
    }
}
