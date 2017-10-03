package org.schabi.newpipe.playlist;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Localization;


public class PlayQueueItemBuilder {

    private static final String TAG = PlayQueueItemBuilder.class.toString();

    public interface OnSelectedListener {
        void selected(PlayQueueItem item);
    }

    private OnSelectedListener onItemClickListener;

    public PlayQueueItemBuilder() {}

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.onItemClickListener = listener;
    }

    public void buildStreamInfoItem(PlayQueueItemHolder holder, final PlayQueueItem item) {
        if (!TextUtils.isEmpty(item.getTitle())) holder.itemVideoTitleView.setText(item.getTitle());
        if (!TextUtils.isEmpty(item.getUploader())) holder.itemAdditionalDetailsView.setText(item.getUploader());

        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(Localization.getDurationString(item.getDuration()));
        } else {
            holder.itemDurationView.setVisibility(View.GONE);
        }

        ImageLoader.getInstance().displayImage(item.getThumbnailUrl(), holder.itemThumbnailView, IMAGE_OPTIONS);

        holder.itemRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemClickListener != null) {
                    onItemClickListener.selected(item);
                }
            }
        });
    }

    private static final DisplayImageOptions IMAGE_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .showImageOnFail(R.drawable.dummy_thumbnail)
                    .showImageForEmptyUri(R.drawable.dummy_thumbnail)
                    .showImageOnLoading(R.drawable.dummy_thumbnail)
                    .build();
}
