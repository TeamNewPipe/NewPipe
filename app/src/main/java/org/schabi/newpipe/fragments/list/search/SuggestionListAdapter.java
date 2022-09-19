package org.schabi.newpipe.fragments.list.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ItemSearchSuggestionBinding;

public class SuggestionListAdapter
        extends ListAdapter<SuggestionItem, SuggestionListAdapter.SuggestionItemHolder> {
    private OnSuggestionItemSelected listener;

    public SuggestionListAdapter() {
        super(new SuggestionItemCallback());
    }

    public void setListener(final OnSuggestionItemSelected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SuggestionItemHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
        return new SuggestionItemHolder(ItemSearchSuggestionBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(final SuggestionItemHolder holder, final int position) {
        final SuggestionItem currentItem = getItem(position);
        holder.updateFrom(currentItem);
        holder.itemBinding.suggestionSearch.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemSelected(currentItem);
            }
        });
        holder.itemBinding.suggestionSearch.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemLongClick(currentItem);
            }
            return true;
        });
        holder.itemBinding.suggestionInsert.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemInserted(currentItem);
            }
        });
    }

    public interface OnSuggestionItemSelected {
        void onSuggestionItemSelected(SuggestionItem item);

        void onSuggestionItemInserted(SuggestionItem item);

        void onSuggestionItemLongClick(SuggestionItem item);
    }

    public static final class SuggestionItemHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSuggestionBinding itemBinding;

        private SuggestionItemHolder(final ItemSearchSuggestionBinding binding) {
            super(binding.getRoot());
            this.itemBinding = binding;
        }

        private void updateFrom(final SuggestionItem item) {
            itemBinding.itemSuggestionIcon.setImageResource(item.fromHistory ? R.drawable.ic_history
                    : R.drawable.ic_search);
            itemBinding.itemSuggestionQuery.setText(item.query);
        }
    }

    private static class SuggestionItemCallback extends DiffUtil.ItemCallback<SuggestionItem> {
        @Override
        public boolean areItemsTheSame(@NonNull final SuggestionItem oldItem,
                                       @NonNull final SuggestionItem newItem) {
            return oldItem.fromHistory == newItem.fromHistory
                    && oldItem.query.equals(newItem.query);
        }

        @Override
        public boolean areContentsTheSame(@NonNull final SuggestionItem oldItem,
                                          @NonNull final SuggestionItem newItem) {
            return true; // items' contents never change; the list of items themselves does
        }
    }
}
