package org.schabi.newpipe.fragments.detail;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * The behaviour of this scroll view is exactly equal to that of {@link NestedScrollView} except
 * that it does not scroll automatically to give focus to views (unwanted since everything would
 * move around when clicking on links, see #5453).
 */
public class DescriptionNestedScrollView extends NestedScrollView {

    public DescriptionNestedScrollView(@NonNull final Context context) {
        super(context);
    }

    public DescriptionNestedScrollView(@NonNull final Context context,
                                       @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public DescriptionNestedScrollView(@NonNull final Context context,
                                       @Nullable final AttributeSet attrs,
                                       final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(final Rect rect) {
        // Override this to return always 0 so that the description scroll view is not scrolled
        // around when the user presses on a link
        return 0;
    }

    @Override
    public void scrollTo(final int x, final int y) {
        // Override this to prevent any scrolling
    }

    @Override
    public void scrollBy(final int x, final int y) {
        // Override this to prevent any scrolling
    }
}
