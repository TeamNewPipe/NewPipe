package org.schabi.newpipe.history;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.history.dao.HistoryDAO;
import org.schabi.newpipe.history.model.WatchHistoryEntry;
import org.schabi.newpipe.util.NavigationHelper;

public class WatchedHistoryFragment extends HistoryFragment<WatchHistoryEntry> {

    @NonNull
    public static WatchedHistoryFragment newInstance() {
        return new WatchedHistoryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @StringRes
    @Override
    int getEnabledConfigKey() {
        return R.string.enable_watch_history_key;
    }

    @NonNull
    @Override
    protected WatchedHistoryAdapter createAdapter() {
        return new WatchedHistoryAdapter(getContext());
    }

    @NonNull
    @Override
    protected HistoryDAO<WatchHistoryEntry> createHistoryDAO(Context context) {
        return HistoryDatabase.getInstance(context).watchHistoryDAO();
    }

    @Override
    public void onHistoryItemClick(WatchHistoryEntry historyItem) {
        NavigationHelper.openVideoDetail(getContext(),
                historyItem.getServiceId(),
                historyItem.getUrl(),
                historyItem.getTitle());
    }

    private static class WatchedHistoryAdapter extends HistoryEntryAdapter<WatchHistoryEntry, ViewHolder> {

        public WatchedHistoryAdapter(Context context) {
            super(context);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View itemView = inflater.inflate(R.layout.item_watch_history, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.itemView.setOnClickListener(null);
        }

        @Override
        void onBindViewHolder(ViewHolder holder, WatchHistoryEntry entry, int position) {
            holder.date.setText(getFormattedDate(entry.getCreationDate()));
            holder.streamTitle.setText(entry.getTitle());
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView date;
        private final TextView streamTitle;

        public ViewHolder(View itemView) {
            super(itemView);
            date = (TextView) itemView.findViewById(R.id.history_date);
            streamTitle = (TextView) itemView.findViewById(R.id.stream_title);
        }
    }
}
