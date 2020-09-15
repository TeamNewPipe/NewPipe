package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import com.google.android.material.appbar.CollapsingToolbarLayout;

public class CustomCollapsingToolbarLayout extends CollapsingToolbarLayout {
    public CustomCollapsingToolbarLayout(@NonNull final Context context) {
        super(context);
        overrideListener();
    }

    public CustomCollapsingToolbarLayout(@NonNull final Context context,
                                         @Nullable final AttributeSet attrs) {
        super(context, attrs);
        overrideListener();
    }

    public CustomCollapsingToolbarLayout(@NonNull final Context context,
                                         @Nullable final AttributeSet attrs,
                                         final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        overrideListener();
    }

    /**
     * CollapsingToolbarLayout overrides our logic with fitsSystemWindows and ruins the layout.
     * Override Google's method
     * */
    public void overrideListener() {
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> insets);
    }
}
