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
 */
public class ScrollableTabLayout extends TabLayout {
    private static final String TAG = ScrollableTabLayout.class.getSimpleName();

    public ScrollableTabLayout(Context context) {
        super(context);
    }

    public ScrollableTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollableTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        setTabMode(TabLayout.MODE_FIXED);
        resetMode();
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
        if (getTabCount() == 0 || getTabAt(0).view == null) return;
        setTabMode(TabLayout.MODE_FIXED);

        int layoutWidth = getWidth();
        int minimumWidth = ((View) getTabAt(0).view).getMinimumWidth();
        if (minimumWidth * getTabCount() > layoutWidth) {
            setTabMode(TabLayout.MODE_SCROLLABLE);
            return;
        }

        int actualWidth = 0;
        for (int i = 0; i < getTabCount(); ++i) {
            if (getTabAt(i).view == null) return;
            actualWidth += ((View) getTabAt(i).view).getWidth();
            if (actualWidth > layoutWidth) {
                setTabMode(TabLayout.MODE_SCROLLABLE);
                return;
            }
        }
    }
}
