package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import org.schabi.newpipe.R

/** Searchable Home tab picker. Filtered positions always map to the original tab indices. */
object SearchableTabChooser {
    internal fun visibleIndices(
        names: List<Pair<String, String?>>,
        headers: List<Boolean>,
        query: String
    ): List<Int> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return names.indices.toList()
        val visible = mutableListOf<Int>()
        for (index in names.indices) {
            val titleMatches = names[index].first.lowercase(Locale.ROOT).contains(normalized)
            val descriptionMatches =
                names[index].second.orEmpty().lowercase(Locale.ROOT).contains(normalized)
            if (!headers[index] && (titleMatches || descriptionMatches)) {
                visible.add(index)
            }
        }
        return visible
    }

    @JvmStatic
    fun show(
        context: Context,
        items: Array<AddTabDialog.ChooseTabListItem>,
        actions: DialogInterface.OnClickListener
    ) {
        val dp = context.resources.displayMetrics.density
        val names = items.map { it.itemName to it.itemSubtitle }
        val headers = items.map { it.header }
        val indices = visibleIndices(names, headers, "").toMutableList()
        val inflater = LayoutInflater.from(context)
        val adapter = object : BaseAdapter() {
            override fun getCount() = indices.size

            override fun getItem(position: Int) = items[indices[position]]

            override fun getItemId(position: Int) = indices[position].toLong()

            override fun areAllItemsEnabled() = false

            override fun isEnabled(position: Int) = !getItem(position).header

            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled ?: inflater.inflate(
                    R.layout.list_choose_tabs_dialog,
                    parent,
                    false
                )
                val entry = getItem(position)
                val icon = row.findViewById<AppCompatImageView>(R.id.tabIcon)
                val title = row.findViewById<TextView>(R.id.tabName)
                val subtitle = row.findViewById<TextView>(R.id.tabSubtitle)
                title.text = entry.itemName
                title.setTypeface(
                    null,
                    if (entry.header) Typeface.BOLD else Typeface.NORMAL
                )
                icon.visibility = if (entry.header) View.GONE else View.VISIBLE
                if (!entry.header) {
                    icon.setImageResource(
                        entry.itemIcon.takeIf { it > 0 } ?: R.drawable.ic_whatshot
                    )
                }
                subtitle.visibility =
                    if (entry.header || entry.itemSubtitle.isNullOrEmpty()) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                subtitle.text = entry.itemSubtitle.orEmpty()
                row.isEnabled = !entry.header
                return row
            }
        }

        val search = EditText(context).apply {
            hint = context.getString(R.string.search)
            isSingleLine = true
            setPadding(
                (16 * dp).toInt(),
                (8 * dp).toInt(),
                (16 * dp).toInt(),
                (8 * dp).toInt()
            )
        }
        val list = ListView(context).apply {
            this.adapter = adapter
            isFastScrollEnabled = true
            isFastScrollAlwaysVisible = items.size > 25
            dividerHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(search)
            addView(list)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tab_choose)
            .setView(body)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val originalIndex = indices[position]
            if (!items[originalIndex].header) {
                actions.onClick(dialog, originalIndex)
                dialog.dismiss()
            }
        }
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    indices.clear()
                    indices.addAll(visibleIndices(names, headers, s?.toString() ?: ""))
                    adapter.notifyDataSetChanged()
                    list.setSelection(0)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
        dialog.show()
    }
}
