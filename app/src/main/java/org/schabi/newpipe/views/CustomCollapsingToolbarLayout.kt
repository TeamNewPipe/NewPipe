package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.CollapsingToolbarLayout

class CustomCollapsingToolbarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.collapsingToolbarLayoutStyle
) : CollapsingToolbarLayout(context, attrs, defStyleAttr) {

    init {
        overrideListener()
    }

    fun overrideListener() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets -> insets }
    }
}
