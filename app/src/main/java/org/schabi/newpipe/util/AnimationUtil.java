package org.schabi.newpipe.util;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.core.util.Supplier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

public final class AnimationUtil {
    /* a running list of animators in flight; they will be manually removed onAnimationEnd */
    private static WeakHashMap<View, ValueAnimator> runningAnimators = new WeakHashMap<>();
    /* weak references to old animators post-service for opportunistic reuse before gc'ed */
    private static Set<ValueAnimator> oldAnimators = Collections.newSetFromMap(
            new WeakHashMap<ValueAnimator, Boolean>());
    /* weak references to moving parts like AnimationLogic for opportunistic reuse */
    private static WeakHashMap<Object, Integer> movingParts = new WeakHashMap<>();

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
        void onAnimate(int animatedValue, int initialValue, int targetValue);
        default void onAnimationEnd(View v, boolean reversed, boolean expanded) { }
    }

    private interface SetupAnimationParams {
        void config(AnimationParams params);
    }

    /* AnimationUtil essentially implements a closed system of NewPipeAnimators
     * (subclass of ValueAnimator) which would be kept track of (to allow reverse(),
     * say when the user consecutively taps on it during an animation) and reused
     * as far as possible. These static functions allow callers to essentially
     * 'fire and forget' and do not return the ValueAnimator to prevent clients
     * from accidentally holding a strong reference to it */

    /* shorthands to explicitly specify a direction; note that regardless of direction,
     * the ongoing animation is still reversed if one in flight is detected */
    public static void expand(final TextView v, final int duration, final int lines) {
        toggle(v, duration, () -> params ->
            params.expand().forText().toLines(lines)
        );
    }
    public static void collapse(final TextView v, final int duration, final int lines) {
        toggle(v, duration, () -> params ->
            params.collapse().forText().toLines(lines)
        );
    }
    /* supply an OnAnimateListener if custom actions are desired */
    public static void expand(final View v, final int duration, final OnAnimateListener listener) {
        toggle(v, duration, () -> params ->
            params.expand().setAnimateListener(listener)
        );
    }
    public static void collapse(final View v, final int duration,
                                final OnAnimateListener listener) {
        toggle(v, duration, () -> params ->
            params.collapse().setAnimateListener(listener)
        );
    }

    public static void toggle(final TextView v, final int duration, final int lines,
                              final boolean collapse, final OnAnimateListener listener) {
        toggle(v, duration, () -> params ->
            params.toggle(collapse).forText().toLines(lines).setAnimateListener(listener)
        );
    }
    public static void toggle(final View v, final int duration,
                              final Supplier<SetupAnimationParams> s) {
        animate(v, duration,
                v instanceof TextView ? EXPAND_TEXT_ANIMATION : SLIDE_OPEN_ANIMATION, s);
    }
    public static void animate(final View v, final int duration, @AnimationLogic final int m,
                               final Supplier<SetupAnimationParams> s) {
        final ValueAnimator valueAnimator = runningAnimators.get(v);
        if (valueAnimator != null) {
            valueAnimator.reverse();
            return;
        }
        NewPipeAnimator.get().set(AnimationParams.newInstance(v).apply(s), m).go(duration);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Moving parts
    //////////////////////////////////////////////////////////////////////////*/

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DECELERATE_INTERPOLATOR, ACC_DECELERATE_INTERPOLATOR,
            SLIDE_OPEN_ANIMATION, EXPAND_TEXT_ANIMATION})
    public @interface MovingPart { }

    // magic numbers
    public static final int DECELERATE_INTERPOLATOR = 1;
    public static final int ACC_DECELERATE_INTERPOLATOR = 2;
    public static final int SLIDE_OPEN_ANIMATION = 3;
    public static final int EXPAND_TEXT_ANIMATION = 4;

    private static Object getMovingPart(@MovingPart final int res) {
        if (movingParts.containsValue(res)) {
            final Iterator<Entry<Object, Integer>> it = movingParts.entrySet().iterator();
            while (it.hasNext()) {
                // probably not the most efficient way to get an entry by value
                // fortunately there should be at most 4 in practice
                final Entry<Object, Integer> entry = it.next();
                if (entry.getValue().intValue() == res) {
                    return entry.getKey();
                }
            }
        }
        Object o = null;
        switch (res) {
            case DECELERATE_INTERPOLATOR:
                o = new DecelerateInterpolator();
                break;
            case ACC_DECELERATE_INTERPOLATOR:
                o = new AccelerateDecelerateInterpolator();
                break;
            case SLIDE_OPEN_ANIMATION:
                o = new SlideOpenAnimation();
                break;
            case EXPAND_TEXT_ANIMATION:
                o = new ExpandTextAnimation();
                break;
            default:
                break;
        }
        if (o != null) {
            movingParts.put(o, res);
        }
        return o;
    }

    /* an AnimationLogic implements AnimatorListener and AnimatorUpdateListener under the hook */
    public static NewPipeAnimationLogic setupAnimationLogic(final ValueAnimator valueAnimator,
                                                            @AnimationLogic final int m) {
        final NewPipeAnimationLogic logic = (NewPipeAnimationLogic) getMovingPart(m);
        if (logic != null) {
            valueAnimator.addUpdateListener(logic);
            valueAnimator.addListener(logic);
        }
        return logic;
    }

    public static void preflight(final NewPipeAnimator a, final AnimationParams p,
                                 @AnimationLogic final int m) {
        final NewPipeAnimationLogic logic = setupAnimationLogic(a, m);
        if (logic != null && p.getView() != null) {
            // by now client supplied params should be all set; impute the rest before lift off
            logic.imputeParams(p.getView(), p);
        }
        a.setAnimationParams(p);
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
                if (p != null) {
                    return p.getView();
                }
            }
            return null;
        }
        /* returns true if the animator is reversed halfway and didn't make its full course */
        protected static boolean abandoned(final Animator animation) {
            if (animation instanceof NewPipeAnimator
                    && ((NewPipeAnimator) animation).animationParams != null) {
                return (int) ((NewPipeAnimator) animation).getAnimatedValue()
                        != ((NewPipeAnimator) animation).animationParams.to();
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

    /* base class for AnimationLogic */
    public abstract static class NewPipeAnimationLogic extends BaseRecycleLogic
            implements ValueAnimator.AnimatorUpdateListener {
        /* our own abstracted interface to be implemented by sub-classsz */
        public void animate(final View t, final AnimationParams p,
                            final ValueAnimator animation) { }

        public void animationEnd(final View t, final AnimationParams p, final boolean reversed) { }

        public void animationStart(final View t, final AnimationParams p) { }

        public void imputeParams(final View t, final AnimationParams p) { }

        /* implements ValueAnimator.AnimatorUpdateListener */
        @Override
        public void onAnimationUpdate(final ValueAnimator animation) {
            final View t = getView(animation);
            final AnimationParams p = getAnimationParams(animation);
            if (t == null || p == null) {
                animation.cancel();
                return;
            }
            animate(t, p, animation);

            if (p.getAnimateListener() != null) {
                p.getAnimateListener().onAnimate((int) animation.getAnimatedValue(),
                        p.from(), p.to());
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
            animationEnd(t, p, reversed);

            if (p.getAnimateListener() != null) {
                p.getAnimateListener().onAnimationEnd(t, reversed,
                        ((!p.isCollapsing() && !reversed) || p.isCollapsing() && reversed));
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
            animationStart(t, p);
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SLIDE_OPEN_ANIMATION, EXPAND_TEXT_ANIMATION})
    public @interface AnimationLogic { }

    /* an AnimationLogic which animates the View's height: from gone -> full height or vice versa */
    public static class SlideOpenAnimation extends NewPipeAnimationLogic {
        @Override
        public void imputeParams(final View t, final AnimationParams p) {
            // setup initial and target values
            p.from(t.getHeight());
            if (!p.isCollapsing()) {
                t.measure(View.MeasureSpec.makeMeasureSpec(t.getMeasuredWidth(),
                                View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED,
                                View.MeasureSpec.UNSPECIFIED));
            }
            p.to(p.isCollapsing() ? 0 : t.getMeasuredHeight());

            // as the animated View is not a TextView, we assume animating to/from being visible
            t.setVisibility(View.VISIBLE);
        }

        @Override
        public void animate(final View t, final AnimationParams p,
                            final ValueAnimator animation) {
            t.getLayoutParams().height = (int) animation.getAnimatedValue();
            t.requestLayout();
        }

        @Override
        public void animationEnd(final View t, final AnimationParams p, final boolean reversed) {
            if ((p.isCollapsing() && !reversed) || (!p.isCollapsing() && reversed)) {
                t.setVisibility(View.GONE);
            }
            t.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    /* a variation of SlideOpenAnimation specifically for (expanding) TextView */
    // ref: https://medium.com/@yuriyskul/expandable-textview-using-staticlayouts-data-f9bc9cbdf283
    public static class ExpandTextAnimation extends SlideOpenAnimation {
        @Override
        public void imputeParams(final View t, final AnimationParams p) {
            // setup initial and target values
            p.from(t.getHeight());

            p.forText().fromLines(((TextView) t).getMaxLines());
            final int lines = p.forText().toLines();
            if (lines != -1) {
                ((TextView) t).setMaxLines(lines);
            }
            t.measure(View.MeasureSpec.makeMeasureSpec(t.getMeasuredWidth(),
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED,
                            View.MeasureSpec.UNSPECIFIED));
            p.to(t.getMeasuredHeight());
        }

        @Override
        public void animationEnd(final View t, final AnimationParams p, final boolean reversed) {
            if (reversed) {
                ((TextView) t).setMaxLines(p.forText().fromLines());
            } else if (p.forText().toLines() != -1) {
                ((TextView) t).setMaxLines(p.forText().toLines());
            }
            t.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        @Override
        public void animationStart(final View t, final AnimationParams p) {
            if (p.isCollapsing() && p.forText().toLines() != -1) {
                ((TextView) t).setMaxLines(p.forText().fromLines());
            }
        }
    }

    public static class AnimationParams {
        private WeakReference<View> target;
        private int initialValue;
        private int targetValue;
        private boolean isCollapsing;
        private OnAnimateListener listener;
        private boolean listenerInTarget;

        public static AnimationParams newInstance(final View v) {
            final AnimationParams params;
            if (v instanceof TextView) {
                params = new TextAnimationParams();
            } else {
                params = new AnimationParams();
            }
            return params.setView(v);
        }
        public AnimationParams apply(final Supplier<SetupAnimationParams> s) {
            if (s != null) {
                s.get().config(this);
            }
            return this;
        }
        /* setters */
        public AnimationParams collapse() {
            isCollapsing = true;
            return this;
        }
        public AnimationParams expand() {
            isCollapsing = false;
            return this;
        }
        public AnimationParams toggle(final boolean collapse) {
            isCollapsing = collapse;
            return this;
        }
        public AnimationParams from(final int value) {
            initialValue = value;
            return this;
        }
        public AnimationParams to(final int value) {
            targetValue = value;
            return this;
        }
        public AnimationParams setView(final View v) {
            target = v == null ? null : new WeakReference<>(v);
            return this;
        }
        public OnAnimateListener setAnimateListener(final OnAnimateListener l) {
            if (l == listener) {
                return listener;
            }
            if (l != null && getView() == l) {
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
        /* getters */
        public View getView() {
            return target == null ? null : target.get();
        }
        public int from() {
            return initialValue;
        }
        public int to() {
            return targetValue;
        }
        public boolean isCollapsing() {
            return isCollapsing;
        }
        public OnAnimateListener getAnimateListener() {
            if (listener != null) {
                return listener;
            } else if (listenerInTarget && getView() instanceof OnAnimateListener) {
                return (OnAnimateListener) getView();
            }
            return null;
        }
        /* convenient shorthand for casting to sub-class (when chaining) */
        public TextAnimationParams forText() {
            return (TextAnimationParams) this;
        }
    }
    public static class TextAnimationParams extends AnimationParams {
        private int oldLines;
        private int newLines;
        /* setters */
        public TextAnimationParams fromLines(final int lines) {
            oldLines = lines;
            return this;
        }
        public TextAnimationParams toLines(final int lines) {
            newLines = lines;
            return this;
        }
        /* getters */
        public int fromLines() {
            return oldLines;
        }
        public int toLines() {
            return newLines;
        }
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
                    setIntValues(animationParams.from(), animationParams.to());
                }
            }
        }
        public static NewPipeAnimator get() {
            final ValueAnimator a = popOldAnimators();
            return a instanceof NewPipeAnimator // implies non null
                    ? (NewPipeAnimator) a : new NewPipeAnimator();
        }
        public NewPipeAnimator set(final AnimationParams p, @AnimationLogic final int m) {
            preflight(this, p, m);

            setInterpolator((TimeInterpolator) getMovingPart(
                    p.isCollapsing() ? DECELERATE_INTERPOLATOR : ACC_DECELERATE_INTERPOLATOR));
            return this;
        }
        public NewPipeAnimator go(final int duration) {
            setDuration(duration);
            start();
            return this;
        }
    }
}
