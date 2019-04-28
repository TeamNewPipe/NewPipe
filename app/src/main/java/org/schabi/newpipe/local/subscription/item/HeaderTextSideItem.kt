package org.schabi.newpipe.local.subscription.item

import android.view.View.OnClickListener
import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import kotlinx.android.synthetic.main.header_with_text_item.*
import org.schabi.newpipe.R

class HeaderTextSideItem(
        val title: String,
        var infoText: String? = null,
        private val onClickListener: (() -> Unit)? = null
) : Item() {

    companion object {
        const val UPDATE_INFO = 123
    }

    override fun getLayout(): Int = R.layout.header_with_text_item

    override fun bind(viewHolder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(UPDATE_INFO)) {
            viewHolder.header_info.text = infoText
            return
        }

        super.bind(viewHolder, position, payloads)
    }

    override fun bind(viewHolder: ViewHolder, position: Int) {
        viewHolder.header_title.text = title
        viewHolder.header_info.text = infoText

        val listener: OnClickListener? = if (onClickListener != null) OnClickListener { onClickListener.invoke() } else null
        viewHolder.root.setOnClickListener(listener)
    }
}