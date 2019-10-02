package android.support.design.widget;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.OverScroller;

import java.lang.reflect.Field;

// check this https://stackoverflow.com/questions/56849221/recyclerview-fling-causes-laggy-while-appbarlayout-is-scrolling/57997489#57997489
public final class FlingBehavior extends AppBarLayout.Behavior {

    public FlingBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
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