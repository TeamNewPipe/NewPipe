package org.schabi.newpipe.history;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.dao.HistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.util.NavigationHelper;

public class SearchHistoryFragment extends HistoryFragment<SearchHistoryEntry> {

    @NonNull
    public static SearchHistoryFragment newInstance() {
        return new SearchHistoryFragment();
    }

    @NonNull
    @Override
    protected SearchHistoryAdapter createAdapter() {
        return new SearchHistoryAdapter(getContext());
    }

    @StringRes
    @Override
    int getEnabledConfigKey() {
        return R.string.enable_search_history_key;
    }

    @NonNull
    @Override
    protected HistoryDAO<SearchHistoryEntry> createHistoryDAO() {
        return NewPipeDatabase.getInstance().searchHistoryDAO();
    }

    @Override
    public void onHistoryItemClick(SearchHistoryEntry historyItem) {
        NavigationHelper.openSearch(getContext(), historyItem.getServiceId(), historyItem.getSearch());
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView search;
        private final TextView time;

        public ViewHolder(View itemView) {
            super(itemView);
            search = itemView.findViewById(R.id.search);
            time = itemView.findViewById(R.id.time);
        }
    }

    protected class SearchHistoryAdapter extends HistoryEntryAdapter<SearchHistoryEntry, ViewHolder> {


        public SearchHistoryAdapter(Context context) {
            super(context);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View rootView = inflater.inflate(R.layout.item_search_history, parent, false);
            return new ViewHolder(rootView);
        }

        @Override
        void onBindViewHolder(ViewHolder holder, SearchHistoryEntry entry, int position) {
            holder.search.setText(entry.getSearch());
            holder.time.setText(getFormattedDate(entry.getCreationDate()));
        }
    }
}
