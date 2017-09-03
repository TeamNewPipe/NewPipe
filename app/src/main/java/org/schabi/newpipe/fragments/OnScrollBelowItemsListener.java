package org.schabi.newpipe.fragments;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;

/**
 * Recycler view scroll listener which calls the method {@link #onScrolledDown(RecyclerView)}
 * if the view is scrolled below the last item.
 */
public abstract class OnScrollBelowItemsListener extends RecyclerView.OnScrollListener {

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        if (dy > 0) {
            int pastVisibleItems = 0, visibleItemCount, totalItemCount;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

            visibleItemCount = layoutManager.getChildCount();
            totalItemCount = layoutManager.getItemCount();

            // Already covers the GridLayoutManager case
            if (layoutManager instanceof LinearLayoutManager) {
                pastVisibleItems = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                int[] positions = ((StaggeredGridLayoutManager) layoutManager).findFirstVisibleItemPositions(null);
                if (positions != null && positions.length > 0) pastVisibleItems = positions[0];
            }

            if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                onScrolledDown(recyclerView);
            }
        }
    }

    /**
     * Called when the recycler view is scrolled below the last item.
     *
     * @param recyclerView the recycler view
     */
    public abstract void onScrolledDown(RecyclerView recyclerView);
}
