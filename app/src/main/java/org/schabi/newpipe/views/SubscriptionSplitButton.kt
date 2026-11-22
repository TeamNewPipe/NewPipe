package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import org.schabi.newpipe.R

/** Two independent actions with connected shapes and mirrored outer corners in RTL. */
class SubscriptionSplitButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    init {
        // Align the button outlines, not the different text baselines of the label and icon.
        isBaselineAligned = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val primary = findViewById<MaterialButton>(R.id.channel_subscribe_button)
        val secondary = findViewById<MaterialButton>(R.id.channel_group_button)

        // Reset both natural heights so a shorter label, smaller font or hidden group action
        // can shrink the control again after either half previously needed extra height.
        primary?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        secondary?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (primary == null || secondary == null || secondary.visibility == View.GONE) {
            return
        }

        val requiredHeight = SubscriptionButtonSizing.height(
            primary.measuredHeight,
            secondary.measuredHeight,
            maxOf(primary.minimumHeight, secondary.minimumHeight)
        )
        if (primary.measuredHeight != requiredHeight || secondary.measuredHeight != requiredHeight) {
            primary.layoutParams.height = requiredHeight
            secondary.layoutParams.height = requiredHeight
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val primary = findViewById<MaterialButton>(R.id.channel_subscribe_button) ?: return
        val secondary = findViewById<MaterialButton>(R.id.channel_group_button) ?: return
        val radius = resources.getDimension(R.dimen.subscription_split_button_corner_radius)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val primaryShape = primary.shapeAppearanceModel.toBuilder().setAllCornerSizes(radius)
        val secondaryShape = secondary.shapeAppearanceModel.toBuilder().setAllCornerSizes(radius)
        if (secondary.visibility != View.GONE) {
            if (rtl) {
                primaryShape.setTopLeftCornerSize(0f).setBottomLeftCornerSize(0f)
                secondaryShape.setTopRightCornerSize(0f).setBottomRightCornerSize(0f)
            } else {
                primaryShape.setTopRightCornerSize(0f).setBottomRightCornerSize(0f)
                secondaryShape.setTopLeftCornerSize(0f).setBottomLeftCornerSize(0f)
            }
        }
        primary.shapeAppearanceModel = primaryShape.build()
        secondary.shapeAppearanceModel = secondaryShape.build()
    }
}

/** Measured height must follow text content rather than a fixed adjacent icon height. */
internal object SubscriptionButtonSizing {
    fun height(primary: Int, secondary: Int, minimum: Int): Int {
        return maxOf(primary, secondary, minimum)
    }
}
