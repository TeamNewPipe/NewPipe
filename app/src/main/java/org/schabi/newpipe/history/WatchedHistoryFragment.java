package org.schabi.newpipe.history;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;


public class WatchedHistoryFragment extends HistoryFragment<StreamHistoryEntry> {

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
    protected StreamHistoryAdapter createAdapter() {
        return new StreamHistoryAdapter(getContext());
    }

    @Override
    protected Single<List<Long>> insert(Collection<StreamHistoryEntry> entries) {
        return historyRecordManager.insertStreamHistory(entries);
    }

    @Override
    protected Single<Integer> delete(Collection<StreamHistoryEntry> entries) {
        return historyRecordManager.deleteStreamHistory(entries);
    }

    @NonNull
    @Override
    protected Flowable<List<StreamHistoryEntry>> getAll() {
        return historyRecordManager.getStreamHistory();
    }

    @Override
    public void onHistoryItemClick(StreamHistoryEntry historyItem) {
        NavigationHelper.openVideoDetail(getContext(), historyItem.serviceId, historyItem.url,
                historyItem.title);
    }

    @Override
    public void onHistoryItemLongClick(StreamHistoryEntry item) {
        new AlertDialog.Builder(activity)
                .setTitle(item.title)
                .setMessage(R.string.delete_stream_history_prompt)
                .setCancelable(true)
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_one, (dialog, i) -> {
                    final Single<Integer> onDelete = historyRecordManager
                            .deleteStreamHistory(Collections.singleton(item))
                            .observeOn(AndroidSchedulers.mainThread());
                    disposables.add(onDelete.subscribe());
                    makeSnackbar(R.string.item_deleted);
                })
                .setNegativeButton(R.string.delete_all, (dialog, i) -> {
                    final Single<Integer> onDeleteAll = historyRecordManager
                            .deleteStreamHistory(item.streamId)
                            .observeOn(AndroidSchedulers.mainThread());
                    disposables.add(onDeleteAll.subscribe());
                    makeSnackbar(R.string.item_deleted);
                })
                .show();
    }

    private static class StreamHistoryAdapter extends HistoryEntryAdapter<StreamHistoryEntry, ViewHolder> {

        StreamHistoryAdapter(Context context) {
            super(context);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View itemView = inflater.inflate(R.layout.list_stream_item, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.itemView.setOnClickListener(null);
            ImageLoader.getInstance()
                    .cancelDisplayTask(holder.thumbnailView);
        }

        @Override
        void onBindViewHolder(ViewHolder holder, StreamHistoryEntry entry, int position) {
            holder.date.setText(getFormattedDate(entry.accessDate));
            holder.streamTitle.setText(entry.title);
            holder.uploader.setText(entry.uploader);
            holder.duration.setText(Localization.getDurationString(entry.duration));
            ImageLoader.getInstance().displayImage(entry.thumbnailUrl, holder.thumbnailView,
                    StreamInfoItemHolder.DISPLAY_THUMBNAIL_OPTIONS);
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
