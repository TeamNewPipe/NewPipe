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
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.dao.HistoryDAO;
import org.schabi.newpipe.database.history.model.WatchHistoryEntry;
import org.schabi.newpipe.util.NavigationHelper;

import static org.schabi.newpipe.info_list.InfoItemBuilder.getDurationString;

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
        return NewPipeDatabase.getInstance(context).watchHistoryDAO();
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
            View itemView = inflater.inflate(R.layout.stream_item, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.itemView.setOnClickListener(null);
            ImageLoader.getInstance()
                    .cancelDisplayTask(holder.thumbnailView);
        }

        @Override
        void onBindViewHolder(ViewHolder holder, WatchHistoryEntry entry, int position) {
            holder.date.setText(getFormattedDate(entry.getCreationDate()));
            holder.streamTitle.setText(entry.getTitle());
            holder.uploader.setText(entry.getUploader());
            holder.duration.setText(getDurationString(entry.getDuration()));
            ImageLoader.getInstance()
                    .displayImage(entry.getThumbnailURL(), holder.thumbnailView);
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView date;
        private final TextView streamTitle;
        private final ImageView thumbnailView;
        private final TextView uploader;
        private final TextView duration;

        public ViewHolder(View itemView) {
            super(itemView);
            thumbnailView = itemView.findViewById(R.id.itemThumbnailView);
            date = itemView.findViewById(R.id.itemAdditionalDetails);
            streamTitle = itemView.findViewById(R.id.itemVideoTitleView);
            uploader = itemView.findViewById(R.id.itemUploaderView);
            duration = itemView.findViewById(R.id.itemDurationView);
        }
    }
}
