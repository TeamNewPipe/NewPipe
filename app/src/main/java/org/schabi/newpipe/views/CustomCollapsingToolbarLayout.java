package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
     * CollapsingToolbarLayout sets it's own setOnApplyInsetsListener which consumes
     * system insets {@link CollapsingToolbarLayout#onWindowInsetChanged(WindowInsetsCompat)}
     * so we will not receive them in subviews with fitsSystemWindows = true.
     * Override Google's behavior
     * */
    public void overrideListener() {
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> insets);
    }
}
