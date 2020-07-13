package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.tabs.TabLayout;

/**
 * A TabLayout that is scrollable when tabs exceed its width.
 * Hides when there are less than 2 tabs.
 */
public class ScrollableTabLayout extends TabLayout {
    private static final String TAG = ScrollableTabLayout.class.getSimpleName();

    private int layoutWidth = 0;
    private int prevVisibility = View.GONE;

    public ScrollableTabLayout(final Context context) {
        super(context);
    }

    public ScrollableTabLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollableTabLayout(final Context context, final AttributeSet attrs,
                               final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r,
                            final int b) {
        super.onLayout(changed, l, t, r, b);

        remeasureTabs();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        layoutWidth = w;
    }

    @Override
    public void addTab(@NonNull final Tab tab, final int position, final boolean setSelected) {
        super.addTab(tab, position, setSelected);

        hasMultipleTabs();

        // Adding a tab won't decrease total tabs' width so tabMode won't have to change to FIXED
        if (getTabMode() != MODE_SCROLLABLE) {
            remeasureTabs();
        }
    }

    @Override
    public void removeTabAt(final int position) {
        super.removeTabAt(position);

        hasMultipleTabs();

        // Removing a tab won't increase total tabs' width
        // so tabMode won't have to change to SCROLLABLE
        if (getTabMode() != MODE_FIXED) {
            remeasureTabs();
        }
    }

    @Override
    protected void onVisibilityChanged(final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        // Check width if some tabs have been added/removed while ScrollableTabLayout was invisible
        // We don't have to check if it was GONE because then requestLayout() will be called
        if (changedView == this) {
            if (prevVisibility == View.INVISIBLE) {
                remeasureTabs();
            }
            prevVisibility = visibility;
        }
    }

    private void setMode(final int mode) {
        if (mode == getTabMode()) {
            return;
        }

        setTabMode(mode);
    }

    /**
     * Make ScrollableTabLayout not visible if there are less than two tabs.
     */
    private void hasMultipleTabs() {
        if (getTabCount() > 1) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    /**
     * Calculate minimal width required by tabs and set tabMode accordingly.
     */
    private void remeasureTabs() {
        if (prevVisibility != View.VISIBLE) {
            return;
        }
        if (layoutWidth == 0) {
            return;
        }

        final int count = getTabCount();
        int contentWidth = 0;
        for (int i = 0; i < count; i++) {
            View child = getTabAt(i).view;
            if (child.getVisibility() == View.VISIBLE) {
                // Use tab's minimum requested width should actual content be too small
                contentWidth += Math.max(child.getMinimumWidth(), child.getMeasuredWidth());
            }
        }

        if (contentWidth > layoutWidth) {
            setMode(TabLayout.MODE_SCROLLABLE);
        } else {
            setMode(TabLayout.MODE_FIXED);
        }
    }
}
