package org.schabi.newpipe.settings.preferencesearch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.databinding.SettingsPreferencesearchListItemResultBinding;

import java.util.function.Consumer;

class PreferenceSearchAdapter
        extends ListAdapter<PreferenceSearchItem, PreferenceSearchAdapter.PreferenceViewHolder> {
    private Consumer<PreferenceSearchItem> onItemClickListener;

    PreferenceSearchAdapter() {
        super(new PreferenceCallback());
    }

    @NonNull
    @Override
    public PreferenceViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
        return new PreferenceViewHolder(SettingsPreferencesearchListItemResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder, final int position) {
        final PreferenceSearchItem item = getItem(position);

        holder.binding.title.setText(item.getTitle());

        if (item.getSummary().isEmpty()) {
            holder.binding.summary.setVisibility(View.GONE);
        } else {
            holder.binding.summary.setVisibility(View.VISIBLE);
            holder.binding.summary.setText(item.getSummary());
        }

        if (item.getBreadcrumbs().isEmpty()) {
            holder.binding.breadcrumbs.setVisibility(View.GONE);
        } else {
            holder.binding.breadcrumbs.setVisibility(View.VISIBLE);
            holder.binding.breadcrumbs.setText(item.getBreadcrumbs());
        }

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.accept(item);
            }
        });
    }

    void setOnItemClickListener(final Consumer<PreferenceSearchItem> onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    static class PreferenceViewHolder extends RecyclerView.ViewHolder {
        final SettingsPreferencesearchListItemResultBinding binding;

        PreferenceViewHolder(final SettingsPreferencesearchListItemResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class PreferenceCallback extends DiffUtil.ItemCallback<PreferenceSearchItem> {
        @Override
        public boolean areItemsTheSame(@NonNull final PreferenceSearchItem oldItem,
                                       @NonNull final PreferenceSearchItem newItem) {
            return oldItem.getKey().equals(newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull final PreferenceSearchItem oldItem,
                                          @NonNull final PreferenceSearchItem newItem) {
            return oldItem.getAllRelevantSearchFields().equals(newItem
                    .getAllRelevantSearchFields());
        }
    }
}
