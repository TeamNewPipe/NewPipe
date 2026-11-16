package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.FeedGroupIcon

object HomeTabIconPicker {
    private const val COLUMN_COUNT = 6

    @JvmStatic
    fun show(context: Context, tab: Tab, onChanged: Runnable) {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, COLUMN_COUNT)
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 336)
            )
        }

        lateinit var dialog: AlertDialog
        recyclerView.adapter = IconAdapter(tab.customIconId) { icon ->
            tab.customIconId = icon.id
            onChanged.run()
            dialog.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.customize_tab_icon)
            .setView(recyclerView)
            .setNeutralButton(R.string.use_default_icon) { _, _ ->
                tab.customIconId = Tab.DEFAULT_ICON_ID
                onChanged.run()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private class IconAdapter(
        private val selectedIconId: Int,
        private val onSelected: (FeedGroupIcon) -> Unit
    ) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {
        private val icons = FeedGroupIcon.entries

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val size = dp(parent.context, 56)
            val button = AppCompatImageButton(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(
                    dp(context, 14),
                    dp(context, 14),
                    dp(context, 14),
                    dp(context, 14)
                )
            }
            return IconViewHolder(button)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val icon = icons[position]
            holder.bind(icon, icon.id == selectedIconId, onSelected)
        }

        override fun getItemCount(): Int = icons.size

        class IconViewHolder(
            private val button: AppCompatImageButton
        ) : RecyclerView.ViewHolder(button) {
            fun bind(
                icon: FeedGroupIcon,
                selected: Boolean,
                onSelected: (FeedGroupIcon) -> Unit
            ) {
                button.setImageResource(icon.drawableResource)
                button.contentDescription = icon.name
                    .lowercase(Locale.getDefault())
                    .replace('_', ' ')
                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                button.isSelected = selected

                val backgroundColor = if (selected) {
                    MaterialColors.getColor(
                        button,
                        com.google.android.material.R.attr.colorSecondaryContainer
                    )
                } else {
                    Color.TRANSPARENT
                }
                val iconColor = MaterialColors.getColor(
                    button,
                    if (selected) {
                        com.google.android.material.R.attr.colorOnSecondaryContainer
                    } else {
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    }
                )
                button.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(backgroundColor)
                }
                ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(iconColor))
                button.setOnClickListener { onSelected(icon) }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
