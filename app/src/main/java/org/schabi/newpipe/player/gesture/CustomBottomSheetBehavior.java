package org.schabi.newpipe.player.gesture;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.schabi.newpipe.R;

import java.util.List;

public class CustomBottomSheetBehavior extends BottomSheetBehavior<FrameLayout> {

    public CustomBottomSheetBehavior(@NonNull final Context context,
                                     @Nullable final AttributeSet attrs) {
        super(context, attrs);
        final DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        final int screenHeight = displayMetrics.heightPixels; //record the screen height
        dragThreshold = (float) (0.05 * screenHeight); // Adjust this value as needed
    }

    Rect globalRect = new Rect();
    private boolean skippingInterception = false;
    private final List<Integer> skipInterceptionOfElements = List.of(
            R.id.detail_content_root_layout, R.id.relatedItemsLayout,
            R.id.itemsListPanel, R.id.view_pager, R.id.tab_layout, R.id.bottomControls,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton);
    private float startY = 0f;
    private float totalDrag = 0f;
    private final float dragThreshold;


    @Override
    public boolean onInterceptTouchEvent(@NonNull final CoordinatorLayout parent,
                                         @NonNull final FrameLayout child,
                                         @NonNull final MotionEvent event) {
        // Drop following when action ends
        if (event.getAction() == MotionEvent.ACTION_CANCEL
                || event.getAction() == MotionEvent.ACTION_UP) {
            skippingInterception = false;
            //reset the variables
            totalDrag = 0f;
            startY = 0f;
        }

        // Found that user still swiping, continue following
        if (skippingInterception || getState() == BottomSheetBehavior.STATE_SETTLING) {
            return false;
        }

        // The interception listens for the child view with the id "fragment_player_holder",
        // so the following two-finger gesture will be triggered only for the player view on
        // portrait and for the top controls (visible) on landscape.
        setSkipCollapsed(event.getPointerCount() == 2);
        if (event.getPointerCount() == 2) {
            return super.onInterceptTouchEvent(parent, child, event);
        }

        // Don't need to do anything if bottomSheet isn't expanded
        if (getState() == BottomSheetBehavior.STATE_EXPANDED
                && event.getAction() == MotionEvent.ACTION_DOWN) {
            // Without overriding scrolling will not work when user touches these elements
            for (final int element : skipInterceptionOfElements) {
                final View view = child.findViewById(element);
                if (view != null) {
                    final boolean visible = view.getGlobalVisibleRect(globalRect);
                    if (visible
                            && globalRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        // Makes bottom part of the player draggable in portrait when
                        // playbackControlRoot is hidden
                        if (element == R.id.bottomControls
                                && child.findViewById(R.id.playbackControlRoot)
                                .getVisibility() != View.VISIBLE) {
                            return super.onInterceptTouchEvent(parent, child, event);
                        }
                        skippingInterception = true;
                        return false;
                    }
                }
            }
        }
        // Implement the distance threshold logic, when the gesture move distance
        // is shorter than the threshold, just do not intercept the touch event.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN://initialize the tracked total distance
                startY = event.getY();
                totalDrag = 0f;
                break;
            case MotionEvent.ACTION_MOVE://accumulate the total distance
                final float currentY = event.getY();
                totalDrag += Math.abs(currentY - startY);
                startY = currentY;
                if (totalDrag < dragThreshold) {
                    return false; // Do not intercept touch events yet if shorter than threshold
                } else {
                    return super.onInterceptTouchEvent(parent, child, event);
                }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL://reset the drag distance and start point
                totalDrag = 0f;
                startY = 0f;
                break;
        }

        return super.onInterceptTouchEvent(parent, child, event);
    }

}
