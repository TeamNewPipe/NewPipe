package org.schabi.newpipe.history;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.schabi.newpipe.history.model.HistoryEntry;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;


/**
 * Adapter for history entries
 * @param <E> the type of the entries
 * @param <VH> the type of the view holder
 */
public abstract class HistoryEntryAdapter<E extends HistoryEntry, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private final ArrayList<E> mEntries;
    private final DateFormat mDateFormat;
    private OnHistoryItemClickListener<E> onHistoryItemClickListener = null;


    public HistoryEntryAdapter(Context context) {
        super();
        mEntries = new ArrayList<>();
        mDateFormat = android.text.format.DateFormat.getDateFormat(context.getApplicationContext());

        setHasStableIds(true);
    }

    public void setEntries(E[] historyEntries) {
        mEntries.clear();
        Collections.addAll(mEntries, historyEntries);
        notifyDataSetChanged();
    }

    public void clear() {
        mEntries.clear();
        notifyDataSetChanged();
    }

    protected String getFormattedDate(Date date) {
        return mDateFormat.format(date);
    }

    @Override
    public long getItemId(int position) {
        return mEntries.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        final E entry = mEntries.get(position);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OnHistoryItemClickListener<E> historyItemClickListener = onHistoryItemClickListener;
                if(historyItemClickListener != null) {
                    historyItemClickListener.onHistoryItemClick(entry);
                }
            }
        });
        onBindViewHolder(holder, entry, position);
    }

    @Override
    public void onViewRecycled(VH holder) {
        super.onViewRecycled(holder);
        holder.itemView.setOnClickListener(null);
    }

    abstract void onBindViewHolder(VH holder, E entry, int position);

    public void setOnHistoryItemClickListener(@Nullable OnHistoryItemClickListener<E> onHistoryItemClickListener) {
        this.onHistoryItemClickListener = onHistoryItemClickListener;
    }

    public boolean isEmpty() {
        return mEntries.isEmpty();
    }

    public interface OnHistoryItemClickListener<E extends HistoryEntry> {
        void onHistoryItemClick(E historyItem);
    }
}
