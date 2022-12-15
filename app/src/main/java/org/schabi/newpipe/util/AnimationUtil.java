package org.schabi.newpipe.util;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.CallSuper;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

public final class AnimationUtil {
    /* a running list of animators in flight; they will be manually removed onAnimationEnd */
    private static WeakHashMap<View, ValueAnimator> runningAnimators = new WeakHashMap<>();
    /* weak references to old animators post-service for opportunistic reuse before gc'ed */
    private static Set<ValueAnimator> oldAnimators = Collections.newSetFromMap(
            new WeakHashMap<ValueAnimator, Boolean>());

    private AnimationUtil() {
        // no impl pls
    }

    public static void cleanup(final Animator animator) {
        if (animator != null) {
            if (animator instanceof ValueAnimator) {
                ((ValueAnimator) animator).removeAllUpdateListeners();
            }
            animator.removeAllListeners();
            if (animator instanceof NewPipeAnimator) {
                ((NewPipeAnimator) animator).setAnimationParams(null);
            }
        }
    }

    public static void recycle(final ValueAnimator animator) {
        if (animator != null) {
            oldAnimators.add(animator);
        }
    }

    public static ValueAnimator popOldAnimators() {
        final Iterator<ValueAnimator> it = oldAnimators.iterator();
        if (it.hasNext()) {
            final ValueAnimator animator = it.next();
            it.remove();
            return animator;
        }
        return null;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Static functions and interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnAnimateListener {
        void onAnimate(int animatedValue, int initialValue, int targetVslue);
        void onAnimationEnd(View v, boolean reversed, boolean expanded);
    }

    /* AnimationUtil essentially implements a closed system of NewPipeAnimators
     * (subclass of ValueAnimator) which would be kept track of (to allow reverse(),
     * say when the user consecutively taps on it during an animation) and reused
     * as far as possible. These static functions allow callers to essentially
     * 'fire and forget' and do not return the ValueAnimator to prevent clients
     * from accidentally holding a strong reference to it */

    /* shorthands to explicitly specify a direction; note that regardless of direction,
     * the ongoing animation is still reversed if one in flight is detected */
    public static void expand(final View v, final int duration) {
        toggle(v, duration, -1, -1, false);
    }
    public static void collapse(final View v, final int duration) {
        toggle(v, duration, -1, -1, true);
    }
    public static void expand(final TextView v, final int duration, final int lines) {
        toggle(v, duration, -1, lines, false);
    }
    public static void collapse(final TextView v, final int duration, final int lines) {
        toggle(v, duration, lines, -1, true);
    }
    public static void expand(final View v, final int duration, final OnAnimateListener listener) {
        toggle(v, duration, -1, false, listener);
    }
    public static void collapse(final View v, final int duration,
                                final OnAnimateListener listener) {
        toggle(v, duration, -1, true, listener);
    }
    public static void toggle(final View v, final int duration) {
        toggle(v, duration, -1, -1);
    }

    public static void toggle(final View v, final int duration,
                              final int collapsedLines, final int expandedLines) {
        toggle(v, duration, collapsedLines, expandedLines, v instanceof TextView
                ? (((TextView) v).getMaxLines() > collapsedLines)
                : (v.getVisibility() != View.GONE && v.getHeight() > 0));
    }

    public static void toggle(final View v, final int duration,
                              final int collapsedLines, final int expandedLines,
                              final boolean collapse) {
        toggle(v, duration, v instanceof TextView
                ? (collapse ? collapsedLines : expandedLines) : -1, collapse, null);
    }

    /* takes an OnAnimateListener if custom actions are desired */
    public static void toggle(final View v, final int duration, final int lines,
                              final boolean collapse, final OnAnimateListener listener) {
        final ValueAnimator valueAnimator = runningAnimators.get(v);
        if (valueAnimator != null) {
            valueAnimator.reverse();
            return;
        }
        expandCollapse(v, popOldAnimators(), duration, lines, collapse, listener);
    }

    public static ValueAnimator expandCollapse(final View v, final ValueAnimator a,
                                               final int duration, final int lines,
                                               final boolean isCollapsing,
                                               final OnAnimateListener listener) {
        final boolean isTextView = v instanceof TextView;

        final int initialHeight = v.getHeight();
        final int origMaxLines;
        if (isTextView) {
            origMaxLines = ((TextView) v).getMaxLines();
            if (lines != -1) {
                ((TextView) v).setMaxLines(lines);
            }
        } else {
            origMaxLines = -1; // unused
        }
        if (isTextView || !isCollapsing) {
            v.measure(View.MeasureSpec.makeMeasureSpec(v.getMeasuredWidth(),
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED,
                            View.MeasureSpec.UNSPECIFIED));
        }
        final int targetHeight = !isTextView && isCollapsing ? 0 : v.getMeasuredHeight();

        if (!isTextView) {
            // if the animated object is not a TextView, we assume animating to/from being visible
            v.setVisibility(View.VISIBLE);
        }

       final NewPipeAnimator valueAnimator = a instanceof NewPipeAnimator // implies a not null
                ? (NewPipeAnimator) a : new NewPipeAnimator();

        final AnimationParams animationParams = isTextView
                ? (AnimationParams) new TextAnimationParams() : new AnimationParams();
        animationParams.setView(v);
        animationParams.initialValue = initialHeight;
        animationParams.targetValue = targetHeight;
        animationParams.isCollapsing = isCollapsing;
        animationParams.setAnimateListener(listener);
        if (isTextView) {
            ((TextAnimationParams) animationParams).oldLines = origMaxLines;
            ((TextAnimationParams) animationParams).newLines = lines;
        }
        valueAnimator.setAnimationParams(animationParams);
        setupAnimationLogic(valueAnimator);

        valueAnimator.setInterpolator(isCollapsing ? new DecelerateInterpolator()
                : new AccelerateDecelerateInterpolator());
        valueAnimator.setDuration(duration);
        valueAnimator.start();

        return valueAnimator;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Moving parts
    //////////////////////////////////////////////////////////////////////////*/

    /* Holds a WeakReference to an AnimationLogic (we currently only have one type of them)
     * to be shared and strongly referenced by Animators in flight (via setupAnimationLogic()).
     * Idealistically it would be gc'ed after prolonged unuse and recreated as necessary. */
    private static WeakReference<NewPipeAnimationLogic> animationLogic;

    public static NewPipeAnimationLogic getAnimationLogic() {
        final NewPipeAnimationLogic logic;
        if (animationLogic != null && animationLogic.get() != null) {
            logic = animationLogic.get();
        } else {
            logic = new NewPipeAnimationLogic();
            animationLogic = new WeakReference<>(logic);
        }
        return logic;
    }
    /* an AnimationLogic implements AnimatorListener and AnimatorUpdateListener under the hook */
    public static void setupAnimationLogic(final ValueAnimator valueAnimator) {
        final NewPipeAnimationLogic logic = getAnimationLogic();
        if (logic != null) {
            valueAnimator.addUpdateListener(logic);
            valueAnimator.addListener(logic);
        }
    }

    /* base logic for de-/registering animators to/from running list and moving to recycle list */
    public static class BaseRecycleLogic implements Animator.AnimatorListener {
        /* helper functions */
        protected static AnimationParams getAnimationParams(final Animator animation) {
            if (animation instanceof NewPipeAnimator) {
                return ((NewPipeAnimator) animation).animationParams;
            }
            return null;
        }
        protected static View getView(final Animator animation) {
            if (animation instanceof NewPipeAnimator) {
                final AnimationParams p = getAnimationParams(animation);
                if (p != null && p.target != null) {
                    return p.target.get();
                }
            }
            return null;
        }
        /* returns true if the animator is reversed halfway and didn't make its full course */
        protected static boolean abandoned(final Animator animation) {
            if (animation instanceof NewPipeAnimator
                    && ((NewPipeAnimator) animation).animationParams != null) {
                return (int) ((NewPipeAnimator) animation).getAnimatedValue()
                        != ((NewPipeAnimator) animation).animationParams.targetValue;
            }
            return false;
        }

        @CallSuper
        @Override
        public void onAnimationEnd(final Animator animation) {
            if (animation instanceof NewPipeAnimator) {
                final View v = getView(animation);
                if (v != null) {
                    runningAnimators.remove(v);
                }
            }
            // make sure to remove animator from running list first before cleanup()
            // which would remove reference to the animated View
            cleanup(animation);
            if (animation instanceof ValueAnimator) {
                recycle((ValueAnimator) animation);
            }
        }
        @CallSuper
        @Override
        public void onAnimationStart(final Animator animation) {
            final View v = getView(animation);
            if (v != null && animation instanceof ValueAnimator) {
                runningAnimators.put(v, (ValueAnimator) animation);
            }
        }
        @Override
        public void onAnimationCancel(final Animator animation) {
            // no impl pls
        }
        @Override
        public void onAnimationRepeat(final Animator animation) {
            // no impl pls
        }
    }

    /* (currently the only type of) AnimationLogic which animates the View's height
     * for TextView: initial -> new height; everything else: gone -> new height or vice versa */
    // ref: https://medium.com/@yuriyskul/expandable-textview-using-staticlayouts-data-f9bc9cbdf283
    public static class NewPipeAnimationLogic extends BaseRecycleLogic
            implements ValueAnimator.AnimatorUpdateListener {

        /* implements ValueAnimator.AnimatorUpdateListener */
        @Override
        public void onAnimationUpdate(final ValueAnimator animation) {
            final View t = getView(animation);
            final AnimationParams p = getAnimationParams(animation);
            if (t == null || p == null) {
                animation.cancel();
                return;
            }
            t.getLayoutParams().height = (int) animation.getAnimatedValue();
            t.requestLayout();

            if (p.getAnimateListener() != null) {
                p.getAnimateListener().onAnimate((int) animation.getAnimatedValue(),
                        p.initialValue, p.targetValue);
            }
        }

        /* implements Animator.AnimatorListener */
        @Override
        public void onAnimationEnd(final Animator animation) {
            final View t = getView(animation);
            final AnimationParams p = getAnimationParams(animation);
            if (t == null || p == null) {
                super.onAnimationEnd(animation);
                return;
            }
            final boolean reversed = abandoned(animation);
            if (t instanceof TextView) {
                if (reversed) {
                    ((TextView) t).setMaxLines(((TextAnimationParams) p).oldLines);
                } else if (((TextAnimationParams) p).newLines != -1) {
                    ((TextView) t).setMaxLines(((TextAnimationParams) p).newLines);
                }
            } else if ((p.isCollapsing && !reversed) || (!p.isCollapsing && reversed)) {
                t.setVisibility(View.GONE);
            }
            t.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;

            if (p.getAnimateListener() != null) {
                p.getAnimateListener().onAnimationEnd(t, reversed,
                        ((!p.isCollapsing && !reversed) || p.isCollapsing && reversed));
            }
            super.onAnimationEnd(animation);
        }

        @Override
        public void onAnimationStart(final Animator animation) {
            final View t = getView(animation);
            final AnimationParams p = getAnimationParams(animation);
            if (t == null || p == null) {
                animation.cancel();
                return;
            }
            super.onAnimationStart(animation);
            if (t instanceof TextView && p.isCollapsing
                    && ((TextAnimationParams) p).newLines != -1) {
                ((TextView) t).setMaxLines(((TextAnimationParams) p).oldLines);
            }
        }
    }

    public static class AnimationParams {
        public WeakReference<View> target;
        public int initialValue;
        public int targetValue;
        public boolean isCollapsing; // FIXME: determine from targetValue < initialValue ?
        private OnAnimateListener listener;
        private boolean listenerInTarget;

        public void setView(final View v) {
            target = v == null ? null : new WeakReference<>(v);
        }
        public OnAnimateListener setAnimateListener(final OnAnimateListener l) {
            if (l == listener) {
                return listener;
            }
            if (l != null && target != null && target.get() == l) {
                // if the animated View itself implements the OnAnimateListener,
                // flip the switch for WeakReference and don't keep a strong reference to it
                listenerInTarget = true;
                listener = null;
                return (OnAnimateListener) target.get();
            } else {
                listenerInTarget = false;
                listener = l;
            }
            return listener;
        }
        public OnAnimateListener getAnimateListener() {
            if (listener != null) {
                return listener;
            } else if (listenerInTarget
                    && target != null && target.get() instanceof OnAnimateListener) {
                return (OnAnimateListener) target.get();
            }
            return null;
        }
    }
    public static class TextAnimationParams extends AnimationParams {
        public int oldLines;
        public int newLines;
    }

    /* a subclass of ValueAnimator which comprises two components:
     * a set of AnimationLogic (applied by setupAnimationLogic() commonly shared across instances)
     * and an AnimationParams (per instance) which stores a weak reference to the animated View
     * (much like a thinly provisioned ObjectAnimator) and exposes useful functions to listeners */
    public static class NewPipeAnimator extends ValueAnimator {
        private AnimationParams animationParams;

        public void setAnimationParams(final AnimationParams params) {
            if (params != animationParams) {
                if (animationParams != null) {
                    // explicitly clean up old OnAnimateListener just in case
                    animationParams.setAnimateListener(null);
                    animationParams.setView(null);
                }
                animationParams = params;
                if (params != null) {
                    setIntValues(animationParams.initialValue, animationParams.targetValue);
                }
            }
        }
    }
}
