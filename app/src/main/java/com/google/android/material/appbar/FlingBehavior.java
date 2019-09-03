package com.google.android.material.appbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

// check this https://github.com/ToDou/appbarlayout-spring-behavior/blob/master/appbarspring/src/main/java/android/support/design/widget/AppBarFlingFixBehavior.java
public final class FlingBehavior extends AppBarLayout.Behavior {

    private ValueAnimator mOffsetAnimator;
    private static final int MAX_OFFSET_ANIMATION_DURATION = 600; // ms

    public FlingBehavior() {
    }

    public FlingBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, AppBarLayout child, View target, int dx, int dy, int[] consumed, int type) {
        if (dy != 0) {
            int val = child.getBottom();
            if (val != 0) {
                int min, max;
                if (dy < 0) {
                    // We're scrolling down
                } else {
                    // We're scrolling up
                    if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
                        mOffsetAnimator.cancel();
                    }
                    min = -child.getUpNestedPreScrollRange();
                    max = 0;
                    consumed[1] = scroll(coordinatorLayout, child, dy, min, max);
                }
            }
        }
    }

    @Override
    public boolean onNestedPreFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull AppBarLayout child, @NonNull View target, float velocityX, float velocityY) {

        if (velocityY != 0) {
            if (velocityY < 0) {
                // We're flinging down
                int val = child.getBottom();
                if (val != 0) {
                    final int targetScroll =
                            +child.getDownNestedPreScrollRange();
                    animateOffsetTo(coordinatorLayout, child, targetScroll, velocityY);
                }

            } else {
                // We're flinging up
                int val = child.getBottom();
                if (val != 0) {
                    final int targetScroll = -child.getUpNestedPreScrollRange();
                    if (getTopBottomOffsetForScrollingSibling() > targetScroll) {
                        animateOffsetTo(coordinatorLayout, child, targetScroll, velocityY);
                    }
                }
            }
        }

        return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY);
    }

    private void animateOffsetTo(final CoordinatorLayout coordinatorLayout,
                                 final AppBarLayout child, final int offset, float velocity) {
        final int distance = Math.abs(getTopBottomOffsetForScrollingSibling() - offset);

        final int duration;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 3 * Math.round(1000 * (distance / velocity));
        } else {
            final float distanceRatio = (float) distance / child.getHeight();
            duration = (int) ((distanceRatio + 1) * 150);
        }

        animateOffsetWithDuration(coordinatorLayout, child, offset, duration);
    }

    private void animateOffsetWithDuration(final CoordinatorLayout coordinatorLayout,
                                           final AppBarLayout child, final int offset, final int duration) {
        final int currentOffset = getTopBottomOffsetForScrollingSibling();
        if (currentOffset == offset) {
            if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
                mOffsetAnimator.cancel();
            }
            return;
        }

        if (mOffsetAnimator == null) {
            mOffsetAnimator = new ValueAnimator();
            mOffsetAnimator.setInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR);
            mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    setHeaderTopBottomOffset(coordinatorLayout, child,
                            (Integer) animator.getAnimatedValue());
                }
            });
        } else {
            mOffsetAnimator.cancel();
        }

        mOffsetAnimator.setDuration(Math.min(duration, MAX_OFFSET_ANIMATION_DURATION));
        mOffsetAnimator.setIntValues(currentOffset, offset);
        mOffsetAnimator.start();
    }
}