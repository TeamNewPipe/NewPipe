package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.TextViewUtils;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class StreamMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    private final AnimatedProgressBar itemProgressView;

    StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @NonNull
    @Override
    public Disposable updateFromItem(final InfoItem infoItem,
                                     final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof StreamInfoItem)) {
            return Disposable.disposed();
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        final CompositeDisposable compositeDisposable = new CompositeDisposable(
                TextViewUtils.computeAndSetPrecomputedText(itemVideoTitleView, item.getName()),
                TextViewUtils.computeAndSetPrecomputedText(itemUploaderView, item.getUploaderName())
        );

        if (item.getDuration() > 0) {
            compositeDisposable.add(TextViewUtils.computeAndSetPrecomputedText(itemDurationView,
                    Localization.getDurationString(item.getDuration())));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            final StreamStateEntity state2 = historyRecordManager.loadStreamState(infoItem)
                    .blockingGet()[0];
            if (state2 != null) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state2.getProgressMillis()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else if (item.getStreamType() == StreamType.LIVE_STREAM
                || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM) {
            compositeDisposable.add(TextViewUtils.computeAndSetPrecomputedText(itemDurationView,
                    R.string.duration_live));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.getImageLoader()
                .displayImage(item.getThumbnailUrl(),
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().selected(item);
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
                enableLongClick(item);
                break;
            case FILE:
            case NONE:
            default:
                disableLongClick();
                break;
        }

        return compositeDisposable;
    }

    @Override
    public void updateState(final InfoItem infoItem,
                            final HistoryRecordManager historyRecordManager) {
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        final StreamStateEntity state
                = historyRecordManager.loadStreamState(infoItem).blockingGet()[0];
        if (state != null && item.getDuration() > 0
                && item.getStreamType() != StreamType.LIVE_STREAM) {
            itemProgressView.setMax((int) item.getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
        }
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
