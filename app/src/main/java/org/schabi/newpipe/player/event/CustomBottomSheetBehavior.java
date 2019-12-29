package org.schabi.newpipe.player.event;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import org.schabi.newpipe.R;

public class CustomBottomSheetBehavior extends BottomSheetBehavior {

    public CustomBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, View child, MotionEvent event) {
        // Without overriding scrolling will not work in detail_content_root_layout
        ViewGroup controls = child.findViewById(R.id.detail_content_root_layout);
        if (controls != null) {
            Rect rect = new Rect();
            controls.getGlobalVisibleRect(rect);
            if (rect.contains((int) event.getX(), (int) event.getY())) return false;
        }

        // Without overriding scrolling will not work on relatedStreamsLayout
        ViewGroup relatedStreamsLayout = child.findViewById(R.id.relatedStreamsLayout);
        if (relatedStreamsLayout != null) {
            Rect rect = new Rect();
            relatedStreamsLayout.getGlobalVisibleRect(rect);
            if (rect.contains((int) event.getX(), (int) event.getY())) return false;
        }

        ViewGroup playQueue = child.findViewById(R.id.playQueue);
        if (playQueue != null) {
            Rect rect = new Rect();
            playQueue.getGlobalVisibleRect(rect);
            if (rect.contains((int) event.getX(), (int) event.getY())) return false;
        }

        return super.onInterceptTouchEvent(parent, child, event);
    }

}
