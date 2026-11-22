package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

/** Drag-and-drop editor that saves only stable action keys, not fragile view positions. */
object PortraitPlayerActionsDialog {
    @JvmStatic
    fun show(context: Context) {
        val order = PortraitPlayerActions.keys(context).toMutableList()
        val adapter = ActionAdapter(context, order)
        val preview = PortraitActionPreview(context, order)
        val scale = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * scale + 0.5f).toInt()
        val hint = TextView(context).apply {
            setText(R.string.portrait_player_actions_drag_hint)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
        }
        val drag = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in order.indices || to !in order.indices) return false
                val moved = order.removeAt(from)
                order.add(to, moved)
                adapter.notifyItemMoved(from, to)
                preview.update(order)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        drag.attachToRecyclerView(list)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview.view)
            addView(hint)
            addView(list)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.portrait_player_actions_title)
            .setView(content)
            .setPositiveButton(R.string.portrait_player_actions_save) { _, _ ->
                PortraitPlayerActions.save(context, order)
            }
            .setNeutralButton(R.string.portrait_player_actions_reset) { _, _ ->
                PortraitPlayerActions.reset(context)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class ActionAdapter(
        private val context: Context,
        private val keys: List<String>
    ) : RecyclerView.Adapter<ActionAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val dp = parent.resources.displayMetrics.density
            val row = TextView(context).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                isFocusable = true
                isLongClickable = true
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val label = PortraitPlayerActions.label(context, keys[position])
            holder.label.text = "☰  $label"
            holder.label.contentDescription = "$label. ${context.getString(R.string.portrait_player_actions_drag_hint)}"
        }

        override fun getItemCount(): Int = keys.size

        class Holder(val label: TextView) : RecyclerView.ViewHolder(label)
    }
}

/** Read-only miniature action strip. Reordering never touches playback controls or preferences. */
internal class PortraitActionPreview(
    context: Context,
    initial: List<String>
) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    val view = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = true
        addView(row)
        contentDescription = context.getString(R.string.portrait_actions_preview_title)
    }

    init {
        update(initial)
    }

    fun update(keys: List<String>) {
        row.removeAllViews()
        PortraitActionPreviewModel.visible(keys).forEach { key ->
            val label = TextView(row.context).apply {
                text = PortraitPlayerActions.label(context, key)
                val inset = (10 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            }
            row.addView(label)
        }
    }
}

internal object PortraitActionPreviewModel {
    private const val MAX_PREVIEW_ACTIONS = 8

    fun visible(keys: List<String>): List<String> = keys.take(MAX_PREVIEW_ACTIONS)
}
