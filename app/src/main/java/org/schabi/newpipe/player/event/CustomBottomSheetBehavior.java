package org.schabi.newpipe.player.event;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import org.schabi.newpipe.R;

import java.util.Arrays;
import java.util.List;

public class CustomBottomSheetBehavior extends BottomSheetBehavior<FrameLayout> {

    public CustomBottomSheetBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    boolean visible;
    Rect globalRect = new Rect();
    private boolean skippingInterception = false;
    private final List<Integer> skipInterceptionOfElements = Arrays.asList(
            R.id.detail_content_root_layout, R.id.relatedStreamsLayout,
            R.id.playQueuePanel, R.id.viewpager, R.id.bottomControls);

    @Override
    public boolean onInterceptTouchEvent(@NonNull final CoordinatorLayout parent,
                                         @NonNull final FrameLayout child,
                                         final MotionEvent event) {
        // Drop following when action ends
        if (event.getAction() == MotionEvent.ACTION_CANCEL
                || event.getAction() == MotionEvent.ACTION_UP) {
            skippingInterception = false;
        }

        // Found that user still swiping, continue following
        if (skippingInterception) {
            return false;
        }

        // Don't need to do anything if bottomSheet isn't expanded
        if (getState() == BottomSheetBehavior.STATE_EXPANDED) {
            // Without overriding scrolling will not work when user touches these elements
            for (final Integer element : skipInterceptionOfElements) {
                final ViewGroup viewGroup = child.findViewById(element);
                if (viewGroup != null) {
                    visible = viewGroup.getGlobalVisibleRect(globalRect);
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

        return super.onInterceptTouchEvent(parent, child, event);
    }

}
