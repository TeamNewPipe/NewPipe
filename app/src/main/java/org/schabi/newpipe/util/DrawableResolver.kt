package org.schabi.newpipe.util

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes

/**
 * Utility class for resolving [Drawables](Drawable)
 */
class DrawableResolver {
    companion object {
        @JvmStatic
        fun resolveDrawable(context: Context, @AttrRes attrResId: Int): Drawable? {
            return androidx.core.content.ContextCompat.getDrawable(
                context,
                android.util.TypedValue().apply {
                    context.theme.resolveAttribute(
                        attrResId,
                        this,
                        true
                    )
                }.resourceId
            )
        }
    }
}
