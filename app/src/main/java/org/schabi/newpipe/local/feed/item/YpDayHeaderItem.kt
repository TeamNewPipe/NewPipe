/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.item

import android.view.View
import androidx.annotation.StringRes
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.YpFeedDayHeaderBinding

/**
 * YewPlay: header that groups the subscriptions feed by day ("Today", "Yesterday", ...).
 */
class YpDayHeaderItem(@StringRes private val title: Int) : BindableItem<YpFeedDayHeaderBinding>(title.toLong()) {
    override fun getLayout(): Int = R.layout.yp_feed_day_header

    override fun bind(viewBinding: YpFeedDayHeaderBinding, position: Int) {
        viewBinding.ypDayHeaderTitle.setText(title)
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount

    override fun initializeViewBinding(view: View) = YpFeedDayHeaderBinding.bind(view)
}
