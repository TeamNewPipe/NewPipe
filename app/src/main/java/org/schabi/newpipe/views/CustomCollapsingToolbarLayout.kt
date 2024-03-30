package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.CollapsingToolbarLayout

class CustomCollapsingToolbarLayout : CollapsingToolbarLayout {
    constructor(context: Context) : super(context) {
        overrideListener()
    }

    constructor(context: Context,
                attrs: AttributeSet?) : super(context, attrs) {
        overrideListener()
    }

    constructor(context: Context,
                attrs: AttributeSet?,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        overrideListener()
    }

    /**
     * CollapsingToolbarLayout sets it's own setOnApplyInsetsListener which consumes
     * system insets [CollapsingToolbarLayout.onWindowInsetChanged]
     * so we will not receive them in subviews with fitsSystemWindows = true.
     * Override Google's behavior
     */
    fun overrideListener() {
        ViewCompat.setOnApplyWindowInsetsListener(this, androidx.core.view.OnApplyWindowInsetsListener({ v: View?, insets: WindowInsetsCompat? -> (insets)!! }))
    }
}
