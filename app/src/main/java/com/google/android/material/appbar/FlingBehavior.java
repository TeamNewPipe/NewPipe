package com.google.android.material.appbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import java.lang.reflect.Field;

// check this https://stackoverflow.com/questions/56849221/recyclerview-fling-causes-laggy-while-appbarlayout-is-scrolling/57997489#57997489
public final class FlingBehavior extends AppBarLayout.Behavior {

    private final Rect focusScrollRect = new Rect();

    public FlingBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    @SuppressLint("NewApi")
    public boolean onRequestChildRectangleOnScreen(@NonNull CoordinatorLayout coordinatorLayout, @NonNull AppBarLayout child, @NonNull Rect rectangle, boolean immediate) {
        focusScrollRect.set(rectangle);

        coordinatorLayout.offsetDescendantRectToMyCoords(child, focusScrollRect);

        int height = coordinatorLayout.getHeight();

        if (focusScrollRect.top <= 0 && focusScrollRect.bottom >= height) {
            // the child is too big to fit inside ourselves completely, ignore request
            return false;
        }

        int offset = getTopAndBottomOffset();

        int dy;

        if (focusScrollRect.bottom > height) {
            dy =  focusScrollRect.bottom - height;
        } else if (focusScrollRect.top < 0) {
            // scrolling up
            dy = -focusScrollRect.top;

            child.setExpanded(true, false);
        } else {
            // nothing to do
            return false;
        }

        int newOffset = offset + dy;

        int consumed = scroll(coordinatorLayout, child, newOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);

        return consumed == dy;
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, AppBarLayout child, MotionEvent ev) {
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

    @Nullable
    private OverScroller getScrollerField() {
        try {
            Class<?> headerBehaviorType = this.getClass().getSuperclass().getSuperclass().getSuperclass();
            if (headerBehaviorType != null) {
                Field field = headerBehaviorType.getDeclaredField("scroller");
                field.setAccessible(true);
                return ((OverScroller) field.get(this));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // ?
        }
        return null;
    }

    @Nullable
    private Field getLastNestedScrollingChildRefField() {
        try {
            Class<?> headerBehaviorType = this.getClass().getSuperclass().getSuperclass();
            if (headerBehaviorType != null) {
                Field field = headerBehaviorType.getDeclaredField("lastNestedScrollingChildRef");
                field.setAccessible(true);
                return field;
            }
        } catch (NoSuchFieldException e) {
            // ?
        }
        return null;
    }

    private void resetNestedScrollingChild(){
        Field field = getLastNestedScrollingChildRefField();
        if(field != null){
            try {
                Object value = field.get(this);
                if(value != null) field.set(this, null);
            } catch (IllegalAccessException e) {
                // ?
            }
        }
    }

    private void stopAppBarLayoutFling() {
        OverScroller scroller = getScrollerField();
        if (scroller != null) scroller.forceFinished(true);
    }

}