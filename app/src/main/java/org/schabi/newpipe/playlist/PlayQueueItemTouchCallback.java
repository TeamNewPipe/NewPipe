package org.schabi.newpipe.playlist;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

public abstract class PlayQueueItemTouchCallback extends ItemTouchHelper.SimpleCallback {
    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 10;
    private static final int MAXIMUM_INITIAL_DRAG_VELOCITY = 25;

    public PlayQueueItemTouchCallback() {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    public abstract void onMove(final int sourceIndex, final int targetIndex);

    @Override
    public int interpolateOutOfBoundsScroll(RecyclerView recyclerView, int viewSize,
                                            int viewSizeOutOfBounds, int totalSize,
                                            long msSinceStartScroll) {
        final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                viewSizeOutOfBounds, totalSize, msSinceStartScroll);
        final int clampedAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY,
                Math.min(Math.abs(standardSpeed), MAXIMUM_INITIAL_DRAG_VELOCITY));
        return clampedAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
                          RecyclerView.ViewHolder target) {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false;
        }

        final int sourceIndex = source.getLayoutPosition();
        final int targetIndex = target.getLayoutPosition();
        onMove(sourceIndex, targetIndex);
        return true;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
}
