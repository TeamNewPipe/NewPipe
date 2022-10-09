package org.schabi.newpipe.fragments.list.sponsorblock;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.SponsorBlockSegment;
import org.schabi.newpipe.util.SponsorBlockUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SponsorBlockSegmentListAdapter extends
        RecyclerView.Adapter<SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder> {
    private final Context context;
    private final ArrayList<SponsorBlockSegment> sponsorBlockSegments = new ArrayList<>();

    public SponsorBlockSegmentListAdapter(final Context context) {
        this.context = context;
    }

    public void setItems(final SponsorBlockSegment[] items) {
        this.sponsorBlockSegments.clear();

        if (items != null) {
            Collections.addAll(this.sponsorBlockSegments, items);
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
        final SponsorBlockSegment sponsorBlockSegment = sponsorBlockSegments.get(position);
        holder.updateFrom(sponsorBlockSegment);
    }

    @Override
    public int getItemCount() {
        return sponsorBlockSegments.size();
    }

    public static class SponsorBlockSegmentItemViewHolder extends RecyclerView.ViewHolder {
        private final View itemSegmentColorView;
        private final TextView itemSegmentNameTextView;
        private final TextView itemSegmentStartTimeTextView;
        private final TextView itemSegmentEndTimeTextView;
        private Disposable voteSubscriber;
        private String segmentUuid;
        private boolean isVoting;
        private boolean hasUpVoted;
        private boolean hasDownVoted;
        private boolean hasResetVote;

        public SponsorBlockSegmentItemViewHolder(@NonNull final View itemView) {
            super(itemView);

            itemSegmentColorView = itemView.findViewById(R.id.item_segment_color_view);
            itemSegmentNameTextView = itemView.findViewById(
                    R.id.item_segment_category_name_textview);
            itemSegmentStartTimeTextView = itemView.findViewById(
                    R.id.item_segment_start_time_textview);
            itemSegmentEndTimeTextView = itemView.findViewById(R.id.item_segment_end_time_textview);

            // voting
            //  1 = up
            //  0 = down
            //  20 = reset
            final ImageView segmentVoteUpImageView =
                    itemView.findViewById(R.id.item_segment_vote_up_imageview);
            segmentVoteUpImageView.setOnClickListener(v -> vote(1));
            segmentVoteUpImageView.setOnLongClickListener(v -> {
                vote(20);
                return true;
            });
            final ImageView segmentVoteDownImageView =
                    itemView.findViewById(R.id.item_segment_vote_down_imageview);
            segmentVoteDownImageView.setOnClickListener(v -> vote(0));
            segmentVoteDownImageView.setOnLongClickListener(v -> {
                vote(20);
                return true;
            });
        }

        private void updateFrom(final SponsorBlockSegment sponsorBlockSegment) {
            // uuid
            segmentUuid = sponsorBlockSegment.uuid;

            // category color
            final Integer segmentColor =
                    SponsorBlockUtils.parseSegmentCategory(
                            sponsorBlockSegment.category, itemView.getContext());
            if (segmentColor != null) {
                itemSegmentColorView.setBackgroundColor(segmentColor);
            }

            // category name
            final String friendlyCategoryName =
                    SponsorBlockUtils.getFriendlyCategoryName(sponsorBlockSegment.category);
            itemSegmentNameTextView.setText(friendlyCategoryName);

            // from
            final String startText = millisecondsToString(sponsorBlockSegment.startTime);
            itemSegmentStartTimeTextView.setText(startText);

            // to
            final String endText = millisecondsToString(sponsorBlockSegment.endTime);
            itemSegmentEndTimeTextView.setText(endText);
        }

        // TODO: move this somewhere else (like some generic utility helper method location)
        private String millisecondsToString(final double milliseconds) {
            final int seconds = (int) (milliseconds / 1000) % 60;
            final int minutes = (int) ((milliseconds / (1000 * 60)) % 60);
            final int hours = (int) ((milliseconds / (1000 * 60 * 60)) % 24);

            return String.format(Locale.getDefault(),
                    "%02d:%02d:%02d", hours, minutes, seconds);
        }

        private void vote(final int value) {
            if (segmentUuid == null) {
                return;
            }

            if (isVoting) {
                return;
            }

            if (voteSubscriber != null) {
                voteSubscriber.dispose();
            }

            // these 3 checks prevent the user from continuously spamming votes
            // (not entirely sure if we need this)

            if (value == 0 && hasDownVoted) {
                return;
            }

            if (value == 1 && hasUpVoted) {
                return;
            }

            if (value == 20 && hasResetVote) {
                return;
            }

            final Context context = itemView.getContext();

            voteSubscriber = Single.fromCallable(() -> {
                        isVoting = true;
                        return SponsorBlockUtils.submitSegmentVote(context, segmentUuid, value);
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        isVoting = false;
                        final String toastMessage;
                        if (!result) {
                            toastMessage = "Failed to vote on segment";
                        } else if (value == 0) {
                            hasDownVoted = true;
                            hasUpVoted = false;
                            hasResetVote = false;
                            toastMessage = context.getString(
                                    R.string.sponsor_block_segment_voted_down_toast);
                        } else if (value == 1) {
                            hasDownVoted = false;
                            hasUpVoted = true;
                            hasResetVote = false;
                            toastMessage = context.getString(
                                    R.string.sponsor_block_segment_voted_up_toast);
                        } else if (value == 20) {
                            hasDownVoted = false;
                            hasUpVoted = false;
                            hasResetVote = true;
                            toastMessage = context.getString(
                                    R.string.sponsor_block_segment_reset_vote_toast);
                        } else {
                            return;
                        }
                        Toast.makeText(context,
                                toastMessage,
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
