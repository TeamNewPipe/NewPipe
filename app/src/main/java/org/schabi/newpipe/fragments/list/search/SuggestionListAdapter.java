package org.schabi.newpipe.fragments.list.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ktx.TextViewUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class SuggestionListAdapter
        extends RecyclerView.Adapter<SuggestionListAdapter.SuggestionItemHolder> {
    private final ArrayList<SuggestionItem> items = new ArrayList<>();
    private final Context context;
    private OnSuggestionItemSelected listener;
    private boolean showSuggestionHistory = true;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public SuggestionListAdapter(final Context context) {
        this.context = context;
    }

    public void clearBackgroundTasks() {
        compositeDisposable.clear();
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

    @Override
    public SuggestionItemHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new SuggestionItemHolder(LayoutInflater.from(context)
                .inflate(R.layout.item_search_suggestion, parent, false));
    }

    @Override
    public void onBindViewHolder(final SuggestionItemHolder holder, final int position) {
        final SuggestionItem currentItem = getItem(position);
        holder.updateFrom(currentItem);
        holder.queryView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemSelected(currentItem);
            }
        });
        holder.queryView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionItemLongClick(currentItem);
            }
            return true;
        });
        holder.insertView.setOnClickListener(v -> {
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

    public final class SuggestionItemHolder extends RecyclerView.ViewHolder {
        private final TextView itemSuggestionQuery;
        private final ImageView suggestionIcon;
        private final View queryView;
        private final View insertView;

        // Cache some ids, as they can potentially be constantly updated/recycled
        private final int historyResId;
        private final int searchResId;

        private SuggestionItemHolder(final View rootView) {
            super(rootView);
            suggestionIcon = rootView.findViewById(R.id.item_suggestion_icon);
            itemSuggestionQuery = rootView.findViewById(R.id.item_suggestion_query);

            queryView = rootView.findViewById(R.id.suggestion_search);
            insertView = rootView.findViewById(R.id.suggestion_insert);

            historyResId = R.drawable.ic_history;
            searchResId = R.drawable.ic_search;
        }

        private void updateFrom(final SuggestionItem item) {
            suggestionIcon.setImageResource(item.fromHistory ? historyResId : searchResId);
            compositeDisposable.add(TextViewUtils.computeAndSetPrecomputedText(itemSuggestionQuery,
                    item.query));
        }
    }
}
