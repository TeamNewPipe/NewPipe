package org.schabi.newpipe.util;

import android.content.Context;
import android.graphics.PointF;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class LayoutManagerSmoothScroller extends LinearLayoutManager {
    public LayoutManagerSmoothScroller(final Context context) {
        super(context, VERTICAL, false);
    }

    public LayoutManagerSmoothScroller(final Context context, final int orientation,
                                       final boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }

    @Override
    public void smoothScrollToPosition(final RecyclerView recyclerView,
                                       final RecyclerView.State state, final int position) {
        RecyclerView.SmoothScroller smoothScroller
                = new TopSnappedSmoothScroller(recyclerView.getContext());
        smoothScroller.setTargetPosition(position);
        startSmoothScroll(smoothScroller);
    }

    private class TopSnappedSmoothScroller extends LinearSmoothScroller {
        TopSnappedSmoothScroller(final Context context) {
            super(context);

        }

        @Override
        public PointF computeScrollVectorForPosition(final int targetPosition) {
            return LayoutManagerSmoothScroller.this
                    .computeScrollVectorForPosition(targetPosition);
        }

        @Override
        protected int getVerticalSnapPreference() {
            return SNAP_TO_START;
        }
    }
}
