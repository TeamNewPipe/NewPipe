package org.schabi.newpipe.info_list.holder;

import android.annotation.SuppressLint;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class StreamMiniInfoItemHolder extends InfoItemHolder {

    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;

    private boolean longClickEnabled;

    StreamMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, int layoutId, ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
    }

    public StreamMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem) {
        if (!(infoItem instanceof StreamInfoItem)) return;
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
        } else if (item.getStreamType() == StreamType.LIVE_STREAM) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
        } else {
            itemDurationView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.getImageLoader()
                .displayImage(item.getThumbnailUrl(),
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetectorCompat longClickListener = new GestureDetectorCompat(itemView.getContext(), new LongClickListener());
            private boolean twoFingerClickDetected = false;
            private boolean longClickDetected = false;

            class LongClickListener extends GestureDetector.SimpleOnGestureListener {
                @Override
                public void onLongPress(MotionEvent event) {
                    if (itemBuilder.getOnStreamSelectedListener() != null) {
                        itemBuilder.getOnStreamSelectedListener().held(item);
                    }
                    longClickDetected = true;
                }
            }


            @SuppressLint("ClickableViewAccessibility") // calling onTouchEvent instead of performClick
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.onTouchEvent(event);
                if(longClickEnabled) {
                    longClickListener.onTouchEvent(event);
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_POINTER_UP:
                        if (itemBuilder.getOnStreamSelectedListener() != null)
                            itemBuilder.getOnStreamSelectedListener().twoFingerClick(item);
                        twoFingerClickDetected = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!twoFingerClickDetected && !longClickDetected) {
                            if (itemBuilder.getOnStreamSelectedListener() != null) {
                                itemBuilder.getOnStreamSelectedListener().selected(item);
                            }
                        }

                        // reset, since no finger is touching the screen anymore
                        twoFingerClickDetected = false;
                        longClickDetected = false;
                        break;
                }

                return true;
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
                longClickEnabled = true;
                break;
            case FILE:
            case NONE:
            default:
                longClickEnabled = false;
                break;
        }
    }
}
