package org.schabi.newpipe.settings.preferencesearch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.databinding.SettingsPreferencesearchListItemResultBinding
import java.util.function.Consumer

internal class PreferenceSearchAdapter() : ListAdapter<PreferenceSearchItem?, PreferenceSearchAdapter.PreferenceViewHolder>(PreferenceCallback()) {
    private var onItemClickListener: Consumer<PreferenceSearchItem?>? = null
    public override fun onCreateViewHolder(parent: ViewGroup,
                                           viewType: Int): PreferenceViewHolder {
        return PreferenceViewHolder(SettingsPreferencesearchListItemResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false))
    }

    public override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        val item: PreferenceSearchItem? = getItem(position)
        holder.binding.title.setText(item!!.getTitle())
        if (item.getSummary().isEmpty()) {
            holder.binding.summary.setVisibility(View.GONE)
        } else {
            holder.binding.summary.setVisibility(View.VISIBLE)
            holder.binding.summary.setText(item.getSummary())
        }
        if (item.getBreadcrumbs().isEmpty()) {
            holder.binding.breadcrumbs.setVisibility(View.GONE)
        } else {
            holder.binding.breadcrumbs.setVisibility(View.VISIBLE)
            holder.binding.breadcrumbs.setText(item.getBreadcrumbs())
        }
        holder.itemView.setOnClickListener(View.OnClickListener({ v: View? ->
            if (onItemClickListener != null) {
                onItemClickListener!!.accept(item)
            }
        }))
    }

    fun setOnItemClickListener(onItemClickListener: Consumer<PreferenceSearchItem?>?) {
        this.onItemClickListener = onItemClickListener
    }

    internal class PreferenceViewHolder(val binding: SettingsPreferencesearchListItemResultBinding) : RecyclerView.ViewHolder(binding.getRoot())
    private class PreferenceCallback() : DiffUtil.ItemCallback<PreferenceSearchItem>() {
        public override fun areItemsTheSame(oldItem: PreferenceSearchItem,
                                            newItem: PreferenceSearchItem): Boolean {
            return (oldItem.getKey() == newItem.getKey())
        }

        public override fun areContentsTheSame(oldItem: PreferenceSearchItem,
                                               newItem: PreferenceSearchItem): Boolean {
            return (oldItem.getAllRelevantSearchFields() == newItem
                    .getAllRelevantSearchFields())
        }
    }
}
