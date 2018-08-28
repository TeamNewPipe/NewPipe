package org.schabi.newpipe.player.playqueue;

import android.content.Context;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class PlayQueueItemBuilder {

    private static final String TAG = PlayQueueItemBuilder.class.toString();

    public interface OnSelectedListener {
        void selected(PlayQueueItem item, View view);
        void held(PlayQueueItem item, View view);
        void onStartDrag(PlayQueueItemHolder viewHolder);
    }

    private OnSelectedListener onItemClickListener;

    public PlayQueueItemBuilder(final Context context) {}

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.onItemClickListener = listener;
    }

    public void buildStreamInfoItem(final PlayQueueItemHolder holder, final PlayQueueItem item) {
        if (!TextUtils.isEmpty(item.getTitle())) holder.itemVideoTitleView.setText(item.getTitle());
        holder.itemAdditionalDetailsView.setText(Localization.concatenateStrings(item.getUploader(),
                NewPipe.getNameOfService(item.getServiceId())));

        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(Localization.getDurationString(item.getDuration()));
        } else {
            holder.itemDurationView.setVisibility(View.GONE);
        }

        ImageLoader.getInstance().displayImage(item.getThumbnailUrl(), holder.itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        holder.itemRoot.setOnClickListener(view -> {
            if (onItemClickListener != null) {
                onItemClickListener.selected(item, view);
            }
        });

        holder.itemRoot.setOnLongClickListener(view -> {
            if (onItemClickListener != null) {
                onItemClickListener.held(item, view);
                return true;
            }
            return false;
        });

        holder.itemThumbnailView.setOnTouchListener(getOnTouchListener(holder));
        holder.itemHandle.setOnTouchListener(getOnTouchListener(holder));
    }

    private View.OnTouchListener getOnTouchListener(final PlayQueueItemHolder holder) {
        return (view, motionEvent) -> {
            view.performClick();
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN
                    && onItemClickListener != null) {
                onItemClickListener.onStartDrag(holder);
            }
            return false;
        };
    }
}
