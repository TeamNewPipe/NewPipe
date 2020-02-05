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

    boolean skippingInterception = false;

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, View child, MotionEvent event) {
        // Behavior of globalVisibleRect is different on different APIs.
        // For example, on API 19 getGlobalVisibleRect returns a filled rect of a collapsed view while on the latest API
        // it returns empty rect in that case. So check visibility with return value too
        boolean visible;
        Rect rect = new Rect();

        // Drop folowing when actions ends
        if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP)
            skippingInterception = false;

        // Found that user still swipping, continue folowing
        if (skippingInterception) return false;

        // Without overriding scrolling will not work in detail_content_root_layout
        ViewGroup controls = child.findViewById(R.id.detail_content_root_layout);
        if (controls != null) {
            visible = controls.getGlobalVisibleRect(rect);
            if (rect.contains((int) event.getRawX(), (int) event.getRawY()) && visible) {
                skippingInterception = true;
                return false;
            }
        }

        // Without overriding scrolling will not work on relatedStreamsLayout
        ViewGroup relatedStreamsLayout = child.findViewById(R.id.relatedStreamsLayout);
        if (relatedStreamsLayout != null) {
            visible = relatedStreamsLayout.getGlobalVisibleRect(rect);
            if (rect.contains((int) event.getRawX(), (int) event.getRawY()) && visible) {
                skippingInterception = true;
                return false;
            }
        }

        ViewGroup playQueue = child.findViewById(R.id.playQueue);
        if (playQueue != null) {
            visible = playQueue.getGlobalVisibleRect(rect);
            if (rect.contains((int) event.getRawX(), (int) event.getRawY()) && visible) {
                skippingInterception = true;
                return false;
            }
        }

        return super.onInterceptTouchEvent(parent, child, event);
    }

}
