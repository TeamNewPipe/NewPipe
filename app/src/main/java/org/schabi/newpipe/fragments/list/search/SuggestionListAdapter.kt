package org.schabi.newpipe.fragments.list.search

import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ItemSearchSuggestionBinding
import org.schabi.newpipe.fragments.list.search.SuggestionListAdapter.SuggestionItemHolder

class SuggestionListAdapter() : ListAdapter<SuggestionItem?, SuggestionItemHolder>(SuggestionItemCallback()) {
    private var listener: OnSuggestionItemSelected? = null
    fun setListener(listener: OnSuggestionItemSelected?) {
        this.listener = listener
    }

    public override fun onCreateViewHolder(parent: ViewGroup,
                                           viewType: Int): SuggestionItemHolder {
        return SuggestionItemHolder(ItemSearchSuggestionBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false))
    }

    public override fun onBindViewHolder(holder: SuggestionItemHolder, position: Int) {
        val currentItem: SuggestionItem? = getItem(position)
        holder.updateFrom(currentItem)
        holder.itemBinding.suggestionSearch.setOnClickListener(View.OnClickListener({ v: View? ->
            if (listener != null) {
                listener!!.onSuggestionItemSelected(currentItem)
            }
        }))
        holder.itemBinding.suggestionSearch.setOnLongClickListener(OnLongClickListener({ v: View? ->
            if (listener != null) {
                listener!!.onSuggestionItemLongClick(currentItem)
            }
            true
        }))
        holder.itemBinding.suggestionInsert.setOnClickListener(View.OnClickListener({ v: View? ->
            if (listener != null) {
                listener!!.onSuggestionItemInserted(currentItem)
            }
        }))
    }

    open interface OnSuggestionItemSelected {
        fun onSuggestionItemSelected(item: SuggestionItem?)
        fun onSuggestionItemInserted(item: SuggestionItem?)
        fun onSuggestionItemLongClick(item: SuggestionItem?)
    }

    class SuggestionItemHolder(val itemBinding: ItemSearchSuggestionBinding) : RecyclerView.ViewHolder(itemBinding.getRoot()) {
        fun updateFrom(item: SuggestionItem?) {
            itemBinding.itemSuggestionIcon.setImageResource(if (item!!.fromHistory) R.drawable.ic_history else R.drawable.ic_search)
            itemBinding.itemSuggestionQuery.setText(item.query)
        }
    }

    private class SuggestionItemCallback() : DiffUtil.ItemCallback<SuggestionItem>() {
        public override fun areItemsTheSame(oldItem: SuggestionItem,
                                            newItem: SuggestionItem): Boolean {
            return (oldItem.fromHistory == newItem.fromHistory
                    && (oldItem.query == newItem.query))
        }

        public override fun areContentsTheSame(oldItem: SuggestionItem,
                                               newItem: SuggestionItem): Boolean {
            return true // items' contents never change; the list of items themselves does
        }
    }
}
