package org.schabi.newpipe.views.glass;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

/**
 * Standard motion choreography for the Frost Glass Design System.
 * - Press: scale to 0.96 with a spring (150ms).
 * - Active capsule slides between items with an overshoot interpolator (250ms).
 * - Pill and mini player fade and slide in and out (150ms).
 */
public final class GlassMotion {

    public static final long DURATION_PRESS = 150L;
    public static final long DURATION_CAPSULE_SLIDE = 250L;
    public static final long DURATION_TRANSITION = 150L;

    public static final float PRESS_SCALE = 0.96f;

    private static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(1.25f);
    private static final DecelerateInterpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();
    private static final AccelerateInterpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();

    private GlassMotion() {
    }

    /**
     * Binds subtle press scale (0.96, 150ms) to a view without interfering with click handlers.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void applyPressScale(@NonNull final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(PRESS_SCALE)
                            .scaleY(PRESS_SCALE)
                            .setDuration(DURATION_PRESS)
                            .setInterpolator(DECELERATE_INTERPOLATOR)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(DURATION_PRESS)
                            .setInterpolator(OVERSHOOT_INTERPOLATOR)
                            .start();
                    break;
            }
            return false; // allow onClick to fire
        });
    }

    /**
     * Animates the active red capsule indicator sliding to a target position.
     * Uses an overshoot interpolator over 250ms.
     */
    public static void slideIndicator(@NonNull final View indicator, final float targetY) {
        indicator.animate()
                .translationY(targetY)
                .setDuration(DURATION_CAPSULE_SLIDE)
                .setInterpolator(OVERSHOOT_INTERPOLATOR)
                .start();
    }

    /**
     * Smoothly fades and slides a floating glass view in or out.
     */
    public static void setVisibilityAnimated(@NonNull final View view, final boolean visible) {
        final float translationDistance = 40f * view.getResources().getDisplayMetrics().density;
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationX(translationDistance);
            view.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(DURATION_TRANSITION)
                    .setInterpolator(DECELERATE_INTERPOLATOR)
                    .setListener(null)
                    .start();
        } else {
            view.animate()
                    .alpha(0f)
                    .translationX(translationDistance)
                    .setDuration(DURATION_TRANSITION)
                    .setInterpolator(ACCELERATE_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(final Animator animation) {
                            view.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }
}
