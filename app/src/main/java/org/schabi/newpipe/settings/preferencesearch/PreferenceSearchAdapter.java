package org.schabi.newpipe.settings.preferencesearch;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class PreferenceSearchAdapter
        extends RecyclerView.Adapter<PreferenceSearchAdapter.PreferenceViewHolder> {
    private List<PreferenceSearchItem> dataset = new ArrayList<>();
    private Consumer<PreferenceSearchItem> onItemClickListener;

    @NonNull
    @Override
    public PreferenceSearchAdapter.PreferenceViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        return new PreferenceViewHolder(
            LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.settings_preferencesearch_list_item_result, parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final PreferenceSearchAdapter.PreferenceViewHolder holder,
            final int position
    ) {
        final PreferenceSearchItem item = dataset.get(position);

        holder.title.setText(item.getTitle());

        if (TextUtils.isEmpty(item.getSummary())) {
            holder.summary.setVisibility(View.GONE);
        } else {
            holder.summary.setVisibility(View.VISIBLE);
            holder.summary.setText(item.getSummary());
        }

        if (TextUtils.isEmpty(item.getBreadcrumbs())) {
            holder.breadcrumbs.setVisibility(View.GONE);
        } else {
            holder.breadcrumbs.setVisibility(View.VISIBLE);
            holder.breadcrumbs.setText(item.getBreadcrumbs());
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
        final TextView title;
        final TextView summary;
        final TextView breadcrumbs;

        PreferenceViewHolder(final View v) {
            super(v);
            title = v.findViewById(R.id.title);
            summary = v.findViewById(R.id.summary);
            breadcrumbs = v.findViewById(R.id.breadcrumbs);
        }
    }
}
