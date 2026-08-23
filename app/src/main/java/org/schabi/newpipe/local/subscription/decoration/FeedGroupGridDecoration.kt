package org.schabi.newpipe.local.subscription.decoration

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

class FeedGroupGridDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val margin: Int
    private val halfMargin: Int

    init {
        margin = context.resources.getDimensionPixelOffset(R.dimen.feed_group_grid_margin)
        halfMargin = margin / 2
    }

    override fun getItemOffsets(outRect: Rect, child: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(child)
        val spanCount = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: 1
        val column = position % spanCount

        // Apply margins
        outRect.left = halfMargin
        outRect.right = halfMargin
        outRect.top = if (position < spanCount) margin else halfMargin
        outRect.bottom = halfMargin
    }
}
