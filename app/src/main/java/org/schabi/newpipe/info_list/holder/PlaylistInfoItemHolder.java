package org.schabi.newpipe.info_list.holder;

import android.content.Context;
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
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.playlist.PlaylistPlayQueue;
import org.schabi.newpipe.util.NavigationHelper;

public class PlaylistInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;
    public final ImageButton itemActionDropdown;

    public PlaylistInfoItemHolder(InfoItemBuilder infoItemBuilder, ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_item, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemActionDropdown = itemView.findViewById(R.id.itemActionDropdown);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem) {
        if (!(infoItem instanceof PlaylistInfoItem)) return;
        final PlaylistInfoItem item = (PlaylistInfoItem) infoItem;

        itemTitleView.setText(item.name);
        itemStreamCountView.setText(item.stream_count + "");
        itemUploaderView.setText(item.uploader_name);

        itemBuilder.getImageLoader()
                .displayImage(item.thumbnail_url, itemThumbnailView, DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                    itemBuilder.getOnPlaylistSelectedListener().selected(item);
                }
            }
        });

        enableActionDropdown(item);
    }

    private void enableActionDropdown(final PlaylistInfoItem item) {
        itemActionDropdown.setVisibility(View.VISIBLE);
        itemActionDropdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PopupMenu actionMenu = getStreamDropdown(itemBuilder.getContext(), itemActionDropdown, item);
                if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                    itemBuilder.getOnPlaylistSelectedListener().dropdownClicked(item, actionMenu);
                }
                actionMenu.show();
            }
        });
    }

    private PopupMenu getStreamDropdown(final Context context, final View anchor, final PlaylistInfoItem infoItem) {
        PopupMenu actionMenu = new PopupMenu(context, anchor);

        final MenuItem mainPlay = actionMenu.getMenu().add(R.string.play_all);
        mainPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnMainPlayer(context, new PlaylistPlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem popupPlay = actionMenu.getMenu().add(R.string.controls_popup_title);
        popupPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnPopupPlayer(context, new PlaylistPlayQueue(infoItem));
                return true;
            }
        });

        final MenuItem backgroundPlay = actionMenu.getMenu().add(R.string.controls_background_title);
        backgroundPlay.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                NavigationHelper.playOnBackgroundPlayer(context, new PlaylistPlayQueue(infoItem));
                return true;
            }
        });

        return actionMenu;
    }
    /**
     * Display options for playlist thumbnails
     */
    public static final DisplayImageOptions DISPLAY_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageOnLoading(R.drawable.dummy_thumbnail_playlist)
                    .showImageForEmptyUri(R.drawable.dummy_thumbnail_playlist)
                    .showImageOnFail(R.drawable.dummy_thumbnail_playlist)
                    .build();
}
