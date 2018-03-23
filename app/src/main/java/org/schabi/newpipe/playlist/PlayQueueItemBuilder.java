package org.schabi.newpipe.playlist;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.process.BitmapProcessor;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Localization;


public class PlayQueueItemBuilder {

    private static final String TAG = PlayQueueItemBuilder.class.toString();

    private final int thumbnailWidthPx;
    private final int thumbnailHeightPx;
    private final DisplayImageOptions imageOptions;

    public interface OnSelectedListener {
        void selected(PlayQueueItem item, View view);
        void held(PlayQueueItem item, View view);
        void onStartDrag(PlayQueueItemHolder viewHolder);
    }

    private OnSelectedListener onItemClickListener;

    public PlayQueueItemBuilder(final Context context) {
        thumbnailWidthPx = context.getResources().getDimensionPixelSize(R.dimen.play_queue_thumbnail_width);
        thumbnailHeightPx = context.getResources().getDimensionPixelSize(R.dimen.play_queue_thumbnail_height);
        imageOptions = buildImageOptions(thumbnailWidthPx, thumbnailHeightPx);
    }

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.onItemClickListener = listener;
    }

    public void buildStreamInfoItem(final PlayQueueItemHolder holder, final PlayQueueItem item) {
        if (!TextUtils.isEmpty(item.getTitle())) holder.itemVideoTitleView.setText(item.getTitle());
        if (!TextUtils.isEmpty(item.getUploader())) holder.itemAdditionalDetailsView.setText(item.getUploader());

        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(Localization.getDurationString(item.getDuration()));
        } else {
            holder.itemDurationView.setVisibility(View.GONE);
        }

        ImageLoader.getInstance().displayImage(item.getThumbnailUrl(), holder.itemThumbnailView, imageOptions);

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

    private DisplayImageOptions buildImageOptions(final int widthPx, final int heightPx) {
        final BitmapProcessor bitmapProcessor = bitmap -> {
            final Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, widthPx, heightPx, false);
            bitmap.recycle();
            return resizedBitmap;
        };

        return new DisplayImageOptions.Builder()
                .showImageOnFail(R.drawable.dummy_thumbnail)
                .showImageForEmptyUri(R.drawable.dummy_thumbnail)
                .showImageOnLoading(R.drawable.dummy_thumbnail)
                .bitmapConfig(Bitmap.Config.RGB_565) // Users won't be able to see much anyways
                .preProcessor(bitmapProcessor)
                .imageScaleType(ImageScaleType.EXACTLY)
                .cacheInMemory(true)
                .cacheOnDisk(true)
                .build();
    }
}
