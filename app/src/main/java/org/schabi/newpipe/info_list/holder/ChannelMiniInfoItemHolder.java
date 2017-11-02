package org.schabi.newpipe.info_list.holder;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.playlist.ChannelPlayQueue;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    public final CircleImageView itemThumbnailView;
    public final TextView itemTitleView;
    public final TextView itemAdditionalDetailView;
    public final ImageButton itemActionDropdown;

    ChannelMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, int layoutId, ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemActionDropdown = itemView.findViewById(R.id.itemActionDropdown);
    }

    public ChannelMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem) {
        if (!(infoItem instanceof ChannelInfoItem)) return;
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        itemTitleView.setText(item.name);
        itemAdditionalDetailView.setText(getDetailLine(item));

        itemBuilder.getImageLoader()
                .displayImage(item.thumbnail_url, itemThumbnailView, ChannelInfoItemHolder.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (itemBuilder.getOnChannelSelectedListener() != null) {
                    itemBuilder.getOnChannelSelectedListener().selected(item);
                }
            }
        });

        enableActionDropdown(item);
    }

    private void enableActionDropdown(final ChannelInfoItem item) {
        itemActionDropdown.setVisibility(View.VISIBLE);
        itemActionDropdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PopupMenu actionMenu = getStreamDropdown(itemBuilder.getContext(), itemActionDropdown, item);
                if (itemBuilder.getOnChannelSelectedListener() != null) {
                    itemBuilder.getOnChannelSelectedListener().dropdownClicked(item, actionMenu);
                }
                actionMenu.show();
            }
        });
    }

    private PopupMenu getStreamDropdown(final Context context, final View anchor, final ChannelInfoItem infoItem) {
        PopupMenu actionMenu = new PopupMenu(context, anchor);

        final MenuItem mainPlay = actionMenu.getMenu().add(R.string.play_all);
        mainPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnMainPlayer(context, new ChannelPlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem popupPlay = actionMenu.getMenu().add(R.string.controls_popup_title);
        popupPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnPopupPlayer(context, new ChannelPlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem backgroundPlay = actionMenu.getMenu().add(R.string.controls_background_title);
        backgroundPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnBackgroundPlayer(context, new ChannelPlayQueue(infoItem));
                return true;
            }
        });

        return actionMenu;
    }

    protected String getDetailLine(final ChannelInfoItem item) {
        String details = "";
        if (item.subscriber_count >= 0) {
            details += Localization.shortSubscriberCount(itemBuilder.getContext(), item.subscriber_count);
        }
        return details;
    }

    /**
     * Display options for channel thumbnails
     */
    public static final DisplayImageOptions DISPLAY_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageOnLoading(R.drawable.buddy_channel_item)
                    .showImageForEmptyUri(R.drawable.buddy_channel_item)
                    .showImageOnFail(R.drawable.buddy_channel_item)
                    .build();
}
