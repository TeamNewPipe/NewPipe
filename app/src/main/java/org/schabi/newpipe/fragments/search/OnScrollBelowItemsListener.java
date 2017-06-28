package org.schabi.newpipe.fragments.search;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

/**
 * Recycler view scroll listener which calls the method {@link #onScrolledDown(RecyclerView)}
 * if the view is scrolled below the last item.
 */
public abstract class OnScrollBelowItemsListener extends RecyclerView.OnScrollListener {

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        //check for scroll down
        if (dy > 0) {
            int pastVisibleItems, visibleItemCount, totalItemCount;
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            visibleItemCount = recyclerView.getLayoutManager().getChildCount();
            totalItemCount = recyclerView.getLayoutManager().getItemCount();
            pastVisibleItems = layoutManager.findFirstVisibleItemPosition();
            if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                onScrolledDown(recyclerView);
            }
        }
    }

    /**
     * Called when the recycler view is scrolled below the last item.
     * @param recyclerView the recycler view
     */
    public abstract void onScrolledDown(RecyclerView recyclerView);
}
