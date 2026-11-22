package org.schabi.newpipe.settings.custom

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import org.schabi.newpipe.R
import org.schabi.newpipe.player.notification.NotificationConstants

/**
 * Read-only representation of selected notification actions. This never sends notifications or
 * saves settings; it reflects pending edits in the existing action configuration screen.
 */
class NotificationActionPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {
    private val actionsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    init {
        isHorizontalScrollBarEnabled = true
        addView(actionsRow)
    }

    fun update(actions: List<Int>, compact: List<Int>) {
        actionsRow.removeAllViews()
        NotificationActionPreviewModel.positions(actions.size, Build.VERSION.SDK_INT).forEach { index ->
            val action = actions[index]
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val inset = (8 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                alpha = if (index in compact) 1f else 0.65f
            }
            val iconId = NotificationConstants.ACTION_ICONS[action]
            val icon = ImageView(context).apply {
                if (iconId != 0) {
                    setImageDrawable(AppCompatResources.getDrawable(context, iconId))
                }
                contentDescription = NotificationConstants.getActionName(context, action)
                layoutParams = LinearLayout.LayoutParams(
                    (28 * resources.displayMetrics.density).toInt(),
                    (28 * resources.displayMetrics.density).toInt()
                )
            }
            val label = TextView(context).apply {
                text = NotificationConstants.getActionName(context, action)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                maxLines = 1
            }
            cell.addView(icon)
            cell.addView(label)
            actionsRow.addView(
                cell,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }
}

internal object NotificationActionPreviewModel {
    fun positions(slotCount: Int, sdk: Int): List<Int> {
        val start = if (sdk >= Build.VERSION_CODES.TIRAMISU) 3 else 0
        return (start until slotCount.coerceIn(0, 5)).toList()
    }
}
