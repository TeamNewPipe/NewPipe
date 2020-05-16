package org.schabi.newpipe.local.subscription.decoration

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.util.LocalizeLayoutUtils

class FeedGroupCarouselDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val marginStartEnd: Int
    private val marginTopBottom: Int
    private val marginBetweenItems: Int
    private val isRTL: Boolean

    init {
        with(context.resources) {
            marginStartEnd = getDimensionPixelOffset(R.dimen.feed_group_carousel_start_end_margin)
            marginTopBottom = getDimensionPixelOffset(R.dimen.feed_group_carousel_top_bottom_margin)
            marginBetweenItems = getDimensionPixelOffset(R.dimen.feed_group_carousel_between_items_margin)
        }

        isRTL = LocalizeLayoutUtils.isRTL(context)
    }

    override fun getItemOffsets(outRect: Rect, child: View, parent: RecyclerView, state: RecyclerView.State) {
        val childAdapterPosition = parent.getChildAdapterPosition(child)
        val childAdapterCount = parent.adapter?.itemCount ?: 0

        outRect.set(0, marginTopBottom, 0, marginTopBottom)

        if(isRTL) {
            outRect.right = marginBetweenItems

            if (childAdapterPosition == 0) {
                outRect.right = marginStartEnd
            } else if (childAdapterPosition == childAdapterCount - 1) {
                outRect.left = marginStartEnd
            }
        } else {
            outRect.left = marginBetweenItems

            if (childAdapterPosition == 0) {
                outRect.left = marginStartEnd
            } else if (childAdapterPosition == childAdapterCount - 1) {
                outRect.right = marginStartEnd
            }
        }
    }
}