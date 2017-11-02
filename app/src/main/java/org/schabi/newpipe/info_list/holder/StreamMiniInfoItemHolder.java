package org.schabi.newpipe.info_list.holder;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;

public class StreamMiniInfoItemHolder extends InfoItemHolder {

    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    public final ImageButton itemActionDropdown;

    StreamMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, int layoutId, ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemActionDropdown = itemView.findViewById(R.id.itemActionDropdown);
    }

    public StreamMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem) {
        if (!(infoItem instanceof StreamInfoItem)) return;
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemVideoTitleView.setText(item.name);
        itemUploaderView.setText(item.uploader_name);

        if (item.duration > 0) {
            itemDurationView.setText(Localization.getDurationString(item.duration));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(), R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
        } else if (item.stream_type == StreamType.LIVE_STREAM) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(), R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
        } else {
            itemDurationView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.getImageLoader()
                .displayImage(item.thumbnail_url, itemThumbnailView, StreamInfoItemHolder.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (itemBuilder.getOnStreamSelectedListener() != null) {
                    itemBuilder.getOnStreamSelectedListener().selected(item);
                }
            }
        });

        switch (item.stream_type) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case FILE:
                enableActionDropdown(item);
                break;
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
            case NONE:
            default:
                disableActionDropdown();
                break;
        }
    }

    private void enableActionDropdown(final StreamInfoItem item) {
        itemActionDropdown.setClickable(true);
        itemActionDropdown.setVisibility(View.VISIBLE);
        itemActionDropdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PopupMenu actionMenu = getStreamDropdown(itemBuilder.getContext(), itemActionDropdown, item);
                if (itemBuilder.getOnStreamSelectedListener() != null) {
                    itemBuilder.getOnStreamSelectedListener().dropdownClicked(item, actionMenu);
                }
                actionMenu.show();
            }
        });
    }

    private void disableActionDropdown() {
        itemActionDropdown.setVisibility(View.GONE);
        itemActionDropdown.setClickable(false);
        itemActionDropdown.setOnClickListener(null);
    }

    private PopupMenu getStreamDropdown(final Context context, final View anchor, final StreamInfoItem infoItem) {
        PopupMenu actionMenu = new PopupMenu(context, anchor);

        final MenuItem backgroundEnqueue = actionMenu.getMenu().add(R.string.enqueue_on_background);
        backgroundEnqueue.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.enqueueOnBackgroundPlayer(context, new SinglePlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem popupEnqueue = actionMenu.getMenu().add(R.string.enqueue_on_popup);
        popupEnqueue.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.enqueueOnPopupPlayer(context, new SinglePlayQueue(infoItem));
                return true;
            }
        });
        
        final MenuItem mainPlay = actionMenu.getMenu().add(R.string.play_all);
        mainPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnMainPlayer(context, new SinglePlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem popupPlay = actionMenu.getMenu().add(R.string.controls_popup_title);
        popupPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnPopupPlayer(context, new SinglePlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem backgroundPlay = actionMenu.getMenu().add(R.string.controls_background_title);
        backgroundPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnBackgroundPlayer(context, new SinglePlayQueue(infoItem));
                return true;
            }
        });

        return actionMenu;
    }

    /**
     * Display options for stream thumbnails
     */
    public static final DisplayImageOptions DISPLAY_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageOnFail(R.drawable.dummy_thumbnail)
                    .showImageForEmptyUri(R.drawable.dummy_thumbnail)
                    .showImageOnLoading(R.drawable.dummy_thumbnail)
                    .build();
}
