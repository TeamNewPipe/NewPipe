package org.schabi.newpipe.settings.preferencesearch;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.databinding.SettingsPreferencesearchListItemResultBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class PreferenceSearchAdapter
        extends RecyclerView.Adapter<PreferenceSearchAdapter.PreferenceViewHolder> {
    private List<PreferenceSearchItem> dataset = new ArrayList<>();
    private Consumer<PreferenceSearchItem> onItemClickListener;

    @NonNull
    @Override
    public PreferenceViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        return new PreferenceViewHolder(
                SettingsPreferencesearchListItemResultBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final PreferenceViewHolder holder,
            final int position
    ) {
        final PreferenceSearchItem item = dataset.get(position);

        holder.binding.title.setText(item.getTitle());

        if (TextUtils.isEmpty(item.getSummary())) {
            holder.binding.summary.setVisibility(View.GONE);
        } else {
            holder.binding.summary.setVisibility(View.VISIBLE);
            holder.binding.summary.setText(item.getSummary());
        }

        if (TextUtils.isEmpty(item.getBreadcrumbs())) {
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

    void setContent(final List<PreferenceSearchItem> items) {
        dataset = new ArrayList<>(items);
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return dataset.size();
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
}
