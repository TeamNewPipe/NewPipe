package com.google.android.material.appbar;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.OverScroller;

import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.R;

import java.lang.reflect.Field;

// check this https://stackoverflow.com/questions/56849221/recyclerview-fling-causes-laggy-while-appbarlayout-is-scrolling/57997489#57997489
public final class FlingBehavior extends AppBarLayout.Behavior {

    public FlingBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean allowScroll = true;
    private Rect globalRect = new Rect();

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, AppBarLayout child, MotionEvent ev) {
        ViewGroup playQueue = child.findViewById(R.id.playQueuePanel);
        if (playQueue != null) {
            boolean visible = playQueue.getGlobalVisibleRect(globalRect);
            if (visible && globalRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                allowScroll = false;
                return false;
            }
        }
        allowScroll = true;

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // remove reference to old nested scrolling child
            resetNestedScrollingChild();
            // Stop fling when your finger touches the screen
            stopAppBarLayoutFling();
        }
        return super.onInterceptTouchEvent(parent, child, ev);
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout parent, AppBarLayout child, View directTargetChild, View target, int nestedScrollAxes, int type) {
        return allowScroll && super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type);
    }

    @Override
    public boolean onNestedFling(@NotNull CoordinatorLayout coordinatorLayout, @NotNull AppBarLayout child, @NotNull View target, float velocityX, float velocityY, boolean consumed) {
        return allowScroll && super.onNestedFling(coordinatorLayout, child, target, velocityX, velocityY, consumed);
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