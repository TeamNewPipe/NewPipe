package org.schabi.newpipe.util;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.core.util.Consumer;

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
    private static WeakHashMap<View, Animator> runningAnimators = new WeakHashMap<>();
    /* weak references to old animators post-service for opportunistic reuse before gc'ed */
    private static Set<Animator> oldAnimators = Collections.newSetFromMap(new WeakHashMap<>());
    /* weak references to moving parts like AnimationLogic to be shared across running animators */
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
                final AnimationParams params = ((NewPipeAnimator) animator).getAnimationParams();
                if (params != null) {
                    params.reset();
                }
            }
        }
    }

    public static void recycle(final Animator animator) {
        if (animator != null) {
            oldAnimators.add(animator);
        }
    }

    public static <T> T popOldAnimators(final Class<T> type) {
        final Iterator<Animator> it = oldAnimators.iterator();
        while (it.hasNext()) {
            final Animator item = it.next();
            if (item.getClass() == type) {
                it.remove();
                return type.cast(item);
            }
        }
        return null;
    }

    // ref: https://stackoverflow.com/a/450874, https://stackoverflow.com/a/4294919
    private static <T> T toType(final Object obj, final Class<T> type) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
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

    /* AnimationUtil essentially implements a closed system of NewPipeAnimators
     * (subclass of ValueAnimator) which would be kept track of (to allow reverse(),
     * say when the user consecutively taps on it during an animation) and reused
     * as far as possible. These static functions allow callers to essentially
     * 'fire and forget' and do not return the ValueAnimator to prevent clients
     * from accidentally holding a strong reference to it */

    /* shorthands to explicitly specify a direction; note that regardless of direction,
     * the ongoing animation is still reversed if one in flight is detected */
    public static void expand(final TextView v, final int duration, final int lines) {
        toggle(v, duration, params -> params.expand().forText().toLines(lines));
    }
    public static void collapse(final TextView v, final int duration, final int lines) {
        toggle(v, duration, params -> params.collapse().forText().toLines(lines));
    }
    /* supply an OnAnimateListener if custom actions are desired */
    public static void expand(final View v, final int duration, final OnAnimateListener listener) {
        toggle(v, duration, params -> params.expand().setAnimateListener(listener));
    }
    public static void collapse(final View v, final int duration,
                                final OnAnimateListener listener) {
        toggle(v, duration, params -> params.collapse().setAnimateListener(listener));
    }

    public static void toggle(final TextView v, final int duration, final int lines,
                              final boolean collapse, final OnAnimateListener listener) {
        toggle(v, duration, params ->
                params.toggle(collapse).forText().toLines(lines).setAnimateListener(listener));
    }
    public static void toggle(final View v, final int duration,
                              final Consumer<AnimationParams> c) {
        animate(v, duration,
                v instanceof TextView ? EXPAND_TEXT_ANIMATION : SLIDE_OPEN_ANIMATION, c);
    }
    public static void animate(final View v, final int duration, @AnimationLogic final int m,
                               final Consumer<AnimationParams> c) {
        final Animator animator = runningAnimators.get(v);
        if (animator instanceof ValueAnimator) {
            ((ValueAnimator) animator).reverse();
            return;
        }
        NewPipeAnimator.get().set(v, c, m).go(duration);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Moving parts
    //////////////////////////////////////////////////////////////////////////*/

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DECELERATE_INTERPOLATOR, ACC_DECELERATE_INTERPOLATOR,
            SLIDE_OPEN_ANIMATION, EXPAND_TEXT_ANIMATION})
    public @interface MovingPart { }

    // magic numbers
    public static final int UNSPECIFIED = -1;
    public static final int DECELERATE_INTERPOLATOR = 1;
    public static final int ACC_DECELERATE_INTERPOLATOR = 2;
    public static final int SLIDE_OPEN_ANIMATION = 3;
    public static final int EXPAND_TEXT_ANIMATION = 4;

    private static Object getMovingPart(@MovingPart final int res) {
        final Iterator<Entry<Object, Integer>> it = movingParts.entrySet().iterator();
        while (it.hasNext()) {
            // probably not the most efficient way to get an entry by value, fortunately
            // the list shouldn't be too long in practice (the object is used as key
            // in WeakHashMap so that it'll be automatically removed when gc'ed)
            final Entry<Object, Integer> entry = it.next();
            if (entry.getValue() == res) {
                return entry.getKey();
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
    public static NewPipeAnimationLogic setupAnimationLogic(final Animator animator,
                                                            @AnimationLogic final int m) {
        final NewPipeAnimationLogic logic = (NewPipeAnimationLogic) getMovingPart(m);
        if (logic != null) {
            if (animator instanceof ValueAnimator) {
                ((ValueAnimator) animator).addUpdateListener(logic);
            }
            animator.addListener(logic);
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
                return ((NewPipeAnimator) animation).getAnimationParams();
            }
            return null;
        }
        protected static View getView(final Animator animation) {
            if (animation instanceof NewPipeAnimator) {
                final AnimationParams p = getAnimationParams(animation);
                if (p != null) {
                    return p.getView();
                }
            } else if (animation instanceof ObjectAnimator) {
                final Object o = ((ObjectAnimator) animation).getTarget();
                if (o instanceof View) {
                    return (View) o;
                }
            }
            return null;
        }
        /* returns true if the animator is reversed halfway and didn't make its full course */
        protected static boolean abandoned(final Animator animation) {
            if (animation instanceof NewPipeAnimator) {
                final AnimationParams params = ((NewPipeAnimator) animation).getAnimationParams();
                if (params != null) {
                    return (int) ((ValueAnimator) animation).getAnimatedValue() != params.to();
                }
            }
            return false;
        }

        @CallSuper
        @Override
        public void onAnimationEnd(final Animator animation) {
            final View v = getView(animation);
            if (v != null) {
                runningAnimators.remove(v);
            }
            // make sure to remove animator from running list first before cleanup()
            // which would remove reference to the animated View
            cleanup(animation);
            recycle(animation);
        }
        @CallSuper
        @Override
        public void onAnimationStart(final Animator animation) {
            final View v = getView(animation);
            if (v != null) {
                runningAnimators.put(v, animation);
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
        /* our own abstracted interface to be implemented by sub-classes */
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

    /* a variation of SlideOpenAnimation specifically for expanding TextView */
    // ref: https://medium.com/@yuriyskul/expandable-textview-using-staticlayouts-data-f9bc9cbdf283
    public static class ExpandTextAnimation extends SlideOpenAnimation {
        @Override
        public void imputeParams(final View t, final AnimationParams p) {
            // setup initial and target values
            p.from(t.getHeight());

            p.forText().fromLines(((TextView) t).getMaxLines());
            final int lines = p.forText().toLines();
            if (lines != UNSPECIFIED) {
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
            } else if (p.forText().toLines() != UNSPECIFIED) {
                ((TextView) t).setMaxLines(p.forText().toLines());
            }
            t.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        @Override
        public void animationStart(final View t, final AnimationParams p) {
            if (p.isCollapsing() && p.forText().toLines() != UNSPECIFIED) {
                ((TextView) t).setMaxLines(p.forText().fromLines());
            }
        }
    }

    public static class AnimationParams {
        private WeakReference<View> target;
        private int initialValue = UNSPECIFIED;
        private int targetValue = UNSPECIFIED;
        private boolean isCollapsing;
        private OnAnimateListener listener;
        private boolean listenerInTarget;

        @CallSuper
        public void reset() {
            target = null;
            initialValue = UNSPECIFIED;
            targetValue = UNSPECIFIED;
            isCollapsing = false;
            listener = null;
            listenerInTarget = false;
        }
        public static AnimationParams newInstance(final View v) {
            final AnimationParams params;
            if (v instanceof TextView) {
                params = new TextAnimationParams();
            } else {
                params = new AnimationParams();
            }
            return params.setView(v);
        }
        public static boolean typeMatch(final AnimationParams params, final View v,
                                        @AnimationLogic final int m) {
            return (v instanceof TextView) == (params instanceof TextAnimationParams);
        }
        public AnimationParams apply(final Consumer<AnimationParams> c) {
            if (c != null) {
                c.accept(this);
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
            return toType(this, TextAnimationParams.class);
        }
    }
    public static class TextAnimationParams extends AnimationParams {
        private int oldLines = UNSPECIFIED;
        private int newLines = UNSPECIFIED;

        @Override
        public void reset() {
            super.reset();
            oldLines = UNSPECIFIED;
            newLines = UNSPECIFIED;
        }
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
            }
            // always reapply values to animator just in case
            if (animationParams != null) {
                setIntValues(animationParams.from(), animationParams.to());
            }
        }
        public AnimationParams getAnimationParams() {
            return animationParams;
        }
        public static NewPipeAnimator get() {
            final NewPipeAnimator a = popOldAnimators(NewPipeAnimator.class);
            return a != null ? a : new NewPipeAnimator();
        }
        public NewPipeAnimator set(final View v, final Consumer<AnimationParams> c,
                                   @AnimationLogic final int m) {
            return set(
                    // opportunistically reuse the existing AnimationParams in situ if feasible
                    (animationParams != null && AnimationParams.typeMatch(animationParams, v, m)
                            ? animationParams.setView(v) : AnimationParams.newInstance(v)).apply(c),
                    m);
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
