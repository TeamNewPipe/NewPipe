package org.schabi.newpipe.fragments.list.search;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ItemSearchSuggestionBinding;

import java.util.ArrayList;
import java.util.List;

public class SuggestionListAdapter
        extends RecyclerView.Adapter<SuggestionListAdapter.SuggestionItemHolder> {
    private final ArrayList<SuggestionItem> items = new ArrayList<>();
    private final Context context;
    private OnSuggestionItemSelected listener;
    private boolean showSuggestionHistory = true;

    public SuggestionListAdapter(final Context context) {
        this.context = context;
    }

    public void setItems(final List<SuggestionItem> items) {
        this.items.clear();
        if (showSuggestionHistory) {
            this.items.addAll(items);
        } else {
            // remove history items if history is disabled
            for (final SuggestionItem item : items) {
                if (!item.fromHistory) {
                    this.items.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setListener(final OnSuggestionItemSelected listener) {
        this.listener = listener;
    }

    public void setShowSuggestionHistory(final boolean v) {
        showSuggestionHistory = v;
    }

    @NonNull
    @Override
    public SuggestionItemHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
        return new SuggestionItemHolder(ItemSearchSuggestionBinding
                .inflate(LayoutInflater.from(context), parent, false));
    }

    @Override
    public void onBindViewHolder(final SuggestionItemHolder holder, final int position) {
        final SuggestionItem currentItem = getItem(position);
        holder.updateFrom(currentItem);
        holder.binding.suggestionSearch.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemSelected(currentItem);
            }
        });
        holder.binding.suggestionSearch.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemLongClick(currentItem);
            }
            return true;
        });
        holder.binding.suggestionInsert.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemInserted(currentItem);
            }
        });
    }

    SuggestionItem getItem(final int position) {
        return items.get(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    public interface OnSuggestionItemSelected {
        void onSuggestionItemSelected(SuggestionItem item);

        void onSuggestionItemInserted(SuggestionItem item);

        void onSuggestionItemLongClick(SuggestionItem item);
    }

    public static final class SuggestionItemHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSuggestionBinding binding;

        // Cache some ids, as they can potentially be constantly updated/recycled
        private final int historyResId;
        private final int searchResId;

        private SuggestionItemHolder(final ItemSearchSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            historyResId = resolveResourceIdFromAttr(itemView.getContext(), R.attr.ic_history);
            searchResId = resolveResourceIdFromAttr(itemView.getContext(), R.attr.ic_search);
        }

        private static int resolveResourceIdFromAttr(final Context context,
                                                     @AttrRes final int attr) {
            final TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
            final int attributeResourceId = a.getResourceId(0, 0);
            a.recycle();
            return attributeResourceId;
        }

        private void updateFrom(final SuggestionItem item) {
            binding.itemSuggestionIcon.setImageResource(item.fromHistory ? historyResId
                    : searchResId);
            binding.itemSuggestionQuery.setText(item.query);
        }
    }
}
