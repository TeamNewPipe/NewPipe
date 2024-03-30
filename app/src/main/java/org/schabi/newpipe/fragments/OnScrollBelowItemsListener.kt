package org.schabi.newpipe.fragments

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * Recycler view scroll listener which calls the method [.onScrolledDown]
 * if the view is scrolled below the last item.
 */
abstract class OnScrollBelowItemsListener() : RecyclerView.OnScrollListener() {
    public override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy > 0) {
            var pastVisibleItems: Int = 0
            val layoutManager: RecyclerView.LayoutManager? = recyclerView.getLayoutManager()
            val visibleItemCount: Int = layoutManager!!.getChildCount()
            val totalItemCount: Int = layoutManager.getItemCount()

            // Already covers the GridLayoutManager case
            if (layoutManager is LinearLayoutManager) {
                pastVisibleItems = layoutManager
                        .findFirstVisibleItemPosition()
            } else if (layoutManager is StaggeredGridLayoutManager) {
                val positions: IntArray? = layoutManager
                        .findFirstVisibleItemPositions(null)
                if (positions != null && positions.size > 0) {
                    pastVisibleItems = positions.get(0)
                }
            }
            if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                onScrolledDown(recyclerView)
            }
        }
    }

    /**
     * Called when the recycler view is scrolled below the last item.
     *
     * @param recyclerView the recycler view
     */
    abstract fun onScrolledDown(recyclerView: RecyclerView?)
}
