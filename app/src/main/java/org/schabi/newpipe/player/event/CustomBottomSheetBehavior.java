package org.schabi.newpipe.player.event;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import org.schabi.newpipe.R;

import java.util.Arrays;
import java.util.List;

public class CustomBottomSheetBehavior extends BottomSheetBehavior<FrameLayout> {

    public CustomBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    boolean visible;
    Rect globalRect = new Rect();
    private boolean skippingInterception = false;
    private List<Integer> skipInterceptionOfElements = Arrays.asList(
            R.id.detail_content_root_layout, R.id.relatedStreamsLayout, R.id.playQueuePanel, R.id.viewpager);

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull FrameLayout child, MotionEvent event) {
        // Drop following when action ends
        if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP)
            skippingInterception = false;

        // Found that user still swiping, continue following
        if (skippingInterception) return false;

        // Without overriding scrolling will not work when user touches these elements
        for (Integer element : skipInterceptionOfElements) {
            ViewGroup viewGroup = child.findViewById(element);
            if (viewGroup != null) {
                visible = viewGroup.getGlobalVisibleRect(globalRect);
                if (visible && globalRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    skippingInterception = true;
                    return false;
                }
            }
        }

        return super.onInterceptTouchEvent(parent, child, event);
    }

}
