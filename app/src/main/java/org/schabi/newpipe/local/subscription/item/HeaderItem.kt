package org.schabi.newpipe.local.subscription.item

import android.view.View
import android.view.View.OnClickListener
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.HeaderItemBinding

class HeaderItem(
    val title: String,
    private val onClickListener: (() -> Unit)? = null
) : BindableItem<HeaderItemBinding>() {
    override fun getLayout(): Int = R.layout.header_item

    override fun bind(viewBinding: HeaderItemBinding, position: Int) {
        viewBinding.headerTitle.text = title

        val listener = onClickListener?.let { OnClickListener { onClickListener.invoke() } }
        viewBinding.root.setOnClickListener(listener)
    }

    override fun initializeViewBinding(view: View) = HeaderItemBinding.bind(view)
}
