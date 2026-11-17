package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.navigationrail.NavigationRailView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

/** Keep remote focus within the current home tab, then return it to the navigation rail. */
public final class TvHomeViewPager extends ViewPager {
    public TvHomeViewPager(@NonNull final Context context) {
        super(context);
    }

    public TvHomeViewPager(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setCurrentItem(final int item) {
        if (HomeTabTransition.shouldJump(
                getCurrentItem(), item, getOffscreenPageLimit())) {
            super.setCurrentItem(item, false);
        } else {
            super.setCurrentItem(item);
        }
    }

    @Override
    public void setCurrentItem(final int item, final boolean smoothScroll) {
        super.setCurrentItem(item, smoothScroll
                && !HomeTabTransition.shouldJump(
                        getCurrentItem(), item, getOffscreenPageLimit()));
    }

    @Override
    public boolean arrowScroll(final int direction) {
        final View navigation = getRootView().findViewById(R.id.main_navigation_rail);
        if (!DeviceUtils.isTv(getContext())
                || !(navigation instanceof NavigationRailView)
                || !navigation.isShown()
                || (direction != FOCUS_LEFT && direction != FOCUS_RIGHT)) {
            return super.arrowScroll(direction);
        }

        final View focused = findFocus();
        final View next = FocusFinder.getInstance().findNextFocus(this,
                focused == this ? null : focused, direction);
        if (next != null && next != this && next != focused
                && (focused == null || focused == this
                || isInDirection(focused, next, direction))
                && next.requestFocus()) {
            return true;
        }

        if (direction == FOCUS_LEFT) {
            final NavigationRailView rail = (NavigationRailView) navigation;
            final View selected = rail.findViewById(rail.getSelectedItemId());
            return selected != null && selected.requestFocus();
        }
        return false;
    }

    private boolean isInDirection(@NonNull final View current, @NonNull final View next,
                                  final int direction) {
        final Rect currentRect = new Rect();
        final Rect nextRect = new Rect();
        current.getDrawingRect(currentRect);
        next.getDrawingRect(nextRect);
        offsetDescendantRectToMyCoords(current, currentRect);
        offsetDescendantRectToMyCoords(next, nextRect);
        return direction == FOCUS_LEFT
                ? nextRect.left < currentRect.left : nextRect.left > currentRect.left;
    }
}
