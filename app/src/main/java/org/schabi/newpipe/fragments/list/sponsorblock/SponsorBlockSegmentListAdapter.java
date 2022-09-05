package org.schabi.newpipe.fragments.list.sponsorblock;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.VideoSegment;

import java.util.ArrayList;
import java.util.Collections;

public class SponsorBlockSegmentListAdapter extends
        RecyclerView.Adapter<SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder> {
    private final Context context;
    private final ArrayList<VideoSegment> segments = new ArrayList<>();

    public SponsorBlockSegmentListAdapter(final Context context) {
        this.context = context;
    }
    public void setItems(final VideoSegment[] items) {
        this.segments.clear();

        if (items != null) {
            Collections.addAll(this.segments, items);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent, final int viewType) {
        return new SponsorBlockSegmentItemViewHolder(
                LayoutInflater
                        .from(context)
                        .inflate(R.layout.list_segments_item, parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder holder,
            final int position) {
        final VideoSegment segment = segments.get(position);
        holder.updateFrom(segment);
    }

    @Override
    public int getItemCount() {
        return segments.size();
    }

    public static class SponsorBlockSegmentItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView segmentNameTextView;

        public SponsorBlockSegmentItemViewHolder(@NonNull final View itemView) {
            super(itemView);

            segmentNameTextView = itemView.findViewById(R.id.category_name);
        }

        private void updateFrom(final VideoSegment segment) {
            segmentNameTextView.setText(segment.category);
        }
    }
}
