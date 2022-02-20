package com.google.android.material.appbar;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import org.schabi.newpipe.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

// See https://stackoverflow.com/questions/56849221#57997489
public final class FlingBehavior extends AppBarLayout.Behavior {
    private final Rect focusScrollRect = new Rect();

    public FlingBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean allowScroll = true;
    private final Rect globalRect = new Rect();
    private final List<Integer> skipInterceptionOfElements = Arrays.asList(
            R.id.itemsListPanel, R.id.playbackSeekBar,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton);

    @Override
    public boolean onRequestChildRectangleOnScreen(
            @NonNull final CoordinatorLayout coordinatorLayout, @NonNull final AppBarLayout child,
            @NonNull final Rect rectangle, final boolean immediate) {
        focusScrollRect.set(rectangle);

        coordinatorLayout.offsetDescendantRectToMyCoords(child, focusScrollRect);

        final int height = coordinatorLayout.getHeight();

        if (focusScrollRect.top <= 0 && focusScrollRect.bottom >= height) {
            // the child is too big to fit inside ourselves completely, ignore request
            return false;
        }

        final int dy;

        if (focusScrollRect.bottom > height) {
            dy =  focusScrollRect.top;
        } else if (focusScrollRect.top < 0) {
            // scrolling up
            dy = -(height - focusScrollRect.bottom);
        } else {
            // nothing to do
            return false;
        }

        final int consumed = scroll(coordinatorLayout, child, dy, getMaxDragOffset(child), 0);

        return consumed == dy;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull final CoordinatorLayout parent,
                                         @NonNull final AppBarLayout child,
                                         @NonNull final MotionEvent ev) {
        for (final Integer element : skipInterceptionOfElements) {
            final View view = child.findViewById(element);
            if (view != null) {
                final boolean visible = view.getGlobalVisibleRect(globalRect);
                if (visible && globalRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    allowScroll = false;
                    return false;
                }
            }
        }
        allowScroll = true;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // remove reference to old nested scrolling child
                resetNestedScrollingChild();
                // Stop fling when your finger touches the screen
                stopAppBarLayoutFling();
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(parent, child, ev);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull final CoordinatorLayout parent,
                                       @NonNull final AppBarLayout child,
                                       @NonNull final View directTargetChild,
                                       final View target,
                                       final int nestedScrollAxes,
                                       final int type) {
        return allowScroll && super.onStartNestedScroll(
                parent, child, directTargetChild, target, nestedScrollAxes, type);
    }

    @Override
    public boolean onNestedFling(@NonNull final CoordinatorLayout coordinatorLayout,
                                 @NonNull final AppBarLayout child,
                                 @NonNull final View target, final float velocityX,
                                 final float velocityY, final boolean consumed) {
        return allowScroll && super.onNestedFling(
                coordinatorLayout, child, target, velocityX, velocityY, consumed);
    }

    @Nullable
    private OverScroller getScrollerField() {
        try {
            final Class<?> headerBehaviorType = this.getClass()
                    .getSuperclass().getSuperclass().getSuperclass();
            if (headerBehaviorType != null) {
                final Field field = headerBehaviorType.getDeclaredField("scroller");
                field.setAccessible(true);
                return ((OverScroller) field.get(this));
            }
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            // ?
        }
        return null;
    }

    @Nullable
    private Field getLastNestedScrollingChildRefField() {
        try {
            final Class<?> headerBehaviorType = this.getClass().getSuperclass().getSuperclass();
            if (headerBehaviorType != null) {
                final Field field
                        = headerBehaviorType.getDeclaredField("lastNestedScrollingChildRef");
                field.setAccessible(true);
                return field;
            }
        } catch (final NoSuchFieldException e) {
            // ?
        }
        return null;
    }

    private void resetNestedScrollingChild() {
        final Field field = getLastNestedScrollingChildRefField();
        if (field != null) {
            try {
                final Object value = field.get(this);
                if (value != null) {
                    field.set(this, null);
                }
            } catch (final IllegalAccessException e) {
                // ?
            }
        }
    }

    private void stopAppBarLayoutFling() {
        final OverScroller scroller = getScrollerField();
        if (scroller != null) {
            scroller.forceFinished(true);
        }
    }
}
