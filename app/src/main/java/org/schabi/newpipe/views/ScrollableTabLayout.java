package org.schabi.newpipe.views;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import androidx.annotation.NonNull;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayout.Tab;

/**
 * A TabLayout that is scrollable when tabs exceed its width.
 * Hides when there are less than 2 tabs.
 */
public class ScrollableTabLayout extends TabLayout {
    private static final String TAG = ScrollableTabLayout.class.getSimpleName();

    public ScrollableTabLayout(Context context) {
        super(context);
    }

    public ScrollableTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTabMode(TabLayout.MODE_FIXED);
    }

    public ScrollableTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTabMode(TabLayout.MODE_FIXED);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (changed) {
            resetMode();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        resetMode();
    }

    @Override
    public void addTab(@NonNull Tab tab, int position, boolean setSelected) {
        super.addTab(tab, position, setSelected);

        resetMode();
    }

    @Override
    public void removeTabAt(int position) {
        super.removeTabAt(position);

        resetMode();
    }

    private void resetMode() {
        if (getTabCount() < 2) {
            setVisibility(View.GONE);
            return;
        } else {
            setVisibility(View.VISIBLE);
        }

        int layoutWidth = getWidth();
        if (layoutWidth == 0) return;

        setTabMode(TabLayout.MODE_FIXED);

        int tabsRequestedWidth = 0;
        for (int i = 0; i < getTabCount(); ++i) {
            tabsRequestedWidth += ((View) getTabAt(i).view).getMinimumWidth();
            if (tabsRequestedWidth > layoutWidth) {
                setTabMode(TabLayout.MODE_SCROLLABLE);
                return;
            }
        }
    }
}
