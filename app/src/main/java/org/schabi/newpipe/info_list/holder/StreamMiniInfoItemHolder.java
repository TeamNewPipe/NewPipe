package org.schabi.newpipe.info_list.holder;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.download.DownloadedBadgeBinder;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.StreamUploaderNavigation;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.AiSListContentHelper;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ShortsThumbnailPolicy;
import org.schabi.newpipe.util.MembersOnlyContentHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ExtractorImageCompat;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class StreamMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    public final TextView itemMembersOnlyView;
    private final View itemUploaderRoot;
    private final ImageView itemUploaderAvatarView;
    private final AnimatedProgressBar itemProgressView;
    private Disposable progressLoad;
    private int progressGeneration;
    private final org.schabi.newpipe.sponsorblock.FullVideoLabelBadge sponsorshipBadge;

    StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemMembersOnlyView = itemView.findViewById(R.id.itemMembersOnlyView);
        itemUploaderRoot = itemView.findViewById(R.id.itemUploaderRoot);
        itemUploaderAvatarView = itemView.findViewById(R.id.itemUploaderAvatarView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
        sponsorshipBadge = new org.schabi.newpipe.sponsorblock.FullVideoLabelBadge(
                (android.widget.FrameLayout) itemThumbnailView.getParent());
    }

    public StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    private void updateDurationMarginForProgress() {
        updateDurationMarginForProgress(itemProgressView.getProgress());
    }

    private void updateDurationMarginForProgress(final int progress) {
        if (itemDurationView == null || itemProgressView == null) {
            return;
        }

        final ViewGroup.LayoutParams layoutParams = itemDurationView.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        final boolean shouldRaiseDuration = itemProgressView.getVisibility() == View.VISIBLE
                && progress > 0;
        final int bottomMargin = itemDurationView.getResources().getDimensionPixelSize(
                shouldRaiseDuration
                        ? R.dimen.stream_thumbnail_duration_margin_with_progress
                        : R.dimen.video_item_search_duration_margin);
        final ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.bottomMargin != bottomMargin) {
            marginLayoutParams.bottomMargin = bottomMargin;
            itemDurationView.setLayoutParams(marginLayoutParams);
            itemDurationView.requestLayout();
        }
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        sponsorshipBadge.clear();
        cancelProgressLoad();
        itemProgressView.setVisibility(View.GONE);
        itemProgressView.setProgress(0);
        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;
        sponsorshipBadge.bind(item.getServiceId(), item.getUrl());
        DownloadedBadgeBinder.bind(itemThumbnailView, item.getServiceId(), item.getUrl());

        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());
        final boolean possibleAi = AiSListContentHelper.shouldLabel(
                itemBuilder.getContext(),
                item.getServiceId(),
                item.getUploaderUrl(),
                item.getUploaderName());
        if (item.requiresMembership() && possibleAi) {
            itemMembersOnlyView.setText(Localization.concatenateStrings(
                    itemBuilder.getContext().getString(R.string.members_only),
                    itemBuilder.getContext().getString(R.string.aislist_possible_ai_badge)));
            itemMembersOnlyView.setVisibility(View.VISIBLE);
        } else if (item.requiresMembership()) {
            itemMembersOnlyView.setText(R.string.members_only);
            itemMembersOnlyView.setVisibility(View.VISIBLE);
        } else if (possibleAi) {
            itemMembersOnlyView.setText(R.string.aislist_possible_ai_badge);
            itemMembersOnlyView.setVisibility(View.VISIBLE);
        } else {
            itemMembersOnlyView.setVisibility(View.GONE);
        }

        bindUploader(item);

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            loadProgress(item, historyRecordManager, false);
        } else if (StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }
        updateDurationMarginForProgress();

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemThumbnailView.setScaleType(ShortsThumbnailPolicy.scaleType(item));
        CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView,
                ExtractorImageCompat.thumbnailImages(item));

        itemView.setOnClickListener(view -> {
            if (item.requiresMembership()) {
                MembersOnlyContentHelper.showExplanation(itemBuilder.getContext());
            } else if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().selected(item);
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
            case POST_LIVE_STREAM:
            case POST_LIVE_AUDIO_STREAM:
                enableLongClick(item);
                break;
            case NONE:
            default:
                disableLongClick();
                break;
        }
    }

    private void bindUploader(final StreamInfoItem item) {
        // The mini layout deliberately keeps its existing compact text-only presentation.
        if (itemUploaderRoot == null || itemUploaderAvatarView == null) {
            return;
        }

        final List<Image> uploaderAvatars = ExtractorImageCompat.uploaderAvatarImages(item);
        if (uploaderAvatars.isEmpty()) {
            CoilHelper.INSTANCE.clearAvatar(itemUploaderAvatarView);
            itemUploaderAvatarView.setVisibility(View.GONE);
        } else {
            itemUploaderAvatarView.setVisibility(View.VISIBLE);
            CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatarView, uploaderAvatars);
        }

        final ChannelInfoItem channel = StreamUploaderNavigation.fromStream(item);
        final OnClickGesture<ChannelInfoItem> listener =
                itemBuilder.getOnChannelSelectedListener();
        // The full-width metadata container can include blank space. Only the
        // actual avatar and channel name should navigate away from the video.
        itemUploaderRoot.setOnClickListener(null);
        itemUploaderRoot.setClickable(false);
        itemUploaderRoot.setFocusable(false);
        itemUploaderRoot.setContentDescription(null);
        if (channel != null && listener != null) {
            final String description = itemBuilder.getContext().getString(
                    R.string.open_channel, item.getUploaderName());
            bindUploaderClick(itemUploaderView, description, view -> listener.selected(channel));
            bindUploaderClick(itemUploaderAvatarView, description,
                    view -> listener.selected(channel));
            itemUploaderAvatarView.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        } else {
            bindUploaderClick(itemUploaderView, null, null);
            bindUploaderClick(itemUploaderAvatarView, null, null);
            itemUploaderAvatarView.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    private void bindUploaderClick(final View target, final String description,
                                   final View.OnClickListener listener) {
        target.setOnClickListener(listener);
        target.setClickable(listener != null);
        target.setFocusable(listener != null);
        target.setContentDescription(description);
    }

    @Override
    public void updateState(final InfoItem infoItem,
                            final HistoryRecordManager historyRecordManager) {
        if (infoItem instanceof StreamInfoItem) {
            loadProgress((StreamInfoItem) infoItem, historyRecordManager, true);
        }
    }

    private void loadProgress(final StreamInfoItem item,
                              final HistoryRecordManager historyRecordManager,
                              final boolean animate) {
        cancelProgressLoad();
        if (item.getDuration() <= 0 || StreamTypeUtil.isLiveStream(item.getStreamType())
                || !DependentPreferenceHelper
                        .getPositionsInListsEnabled(itemProgressView.getContext())) {
            applyProgress(item, null, animate);
            return;
        }
        final int generation = progressGeneration;
        progressLoad = historyRecordManager.loadStreamState(item)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(states -> {
                    if (generation == progressGeneration) {
                        applyProgress(item, states.length == 0 ? null : states[0], animate);
                    }
                }, error -> {
                    if (generation == progressGeneration) {
                        applyProgress(item, null, false);
                        Log.w("StreamMiniInfoHolder", "Unable to load watch progress", error);
                    }
                });
    }

    private void applyProgress(final StreamInfoItem item, final StreamStateEntity state,
                               final boolean animate) {
        if (state != null) {
            final int progress = (int) TimeUnit.MILLISECONDS
                    .toSeconds(state.getProgressMillis());
            itemProgressView.setMax((int) item.getDuration());
            if (animate && itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated(progress);
            } else {
                itemProgressView.setProgress(progress);
                if (animate) {
                    ViewUtils.animate(itemProgressView, true, 500);
                } else {
                    itemProgressView.setVisibility(View.VISIBLE);
                }
            }
            updateDurationMarginForProgress(progress);
        } else {
            itemProgressView.setVisibility(View.GONE);
            itemProgressView.setProgress(0);
            updateDurationMarginForProgress(0);
        }
    }

    private void cancelProgressLoad() {
        progressGeneration++;
        itemProgressView.animate().setListener(null).cancel();
        itemProgressView.clearAnimation();
        itemProgressView.setAlpha(1.0f);
        if (progressLoad != null) {
            progressLoad.dispose();
            progressLoad = null;
        }
    }

    @Override
    public void recycle() {
        sponsorshipBadge.clear();
        cancelProgressLoad();
        itemProgressView.setVisibility(View.GONE);
        itemProgressView.setProgress(0);
        super.recycle();
    }

    private void enableLongClick(final StreamInfoItem item) {
        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().held(item);
            }
            return true;
        });
    }

    private void disableLongClick() {
        itemView.setLongClickable(false);
        itemView.setOnLongClickListener(null);
    }
}
