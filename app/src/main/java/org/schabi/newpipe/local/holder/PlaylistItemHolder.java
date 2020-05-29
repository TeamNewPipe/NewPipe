package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.info_list.ItemHolderWithToolbar;
import org.schabi.newpipe.local.history.HistoryRecordManager;

public abstract class PlaylistItemHolder<ItemType extends PlaylistLocalItem>
        extends ItemHolderWithToolbar<ItemType> {
    public final ImageView itemThumbnailView;
    final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    public PlaylistItemHolder(final Class<ItemType> itemClass,
                              final ItemHandler itemHandler,
                              final int layoutId,
                              final ViewGroup parent) {
        super(itemClass, itemHandler, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
    }

    public PlaylistItemHolder(final Class<ItemType> itemClass,
                              final ItemHandler itemHandler,
                              final ViewGroup parent) {
        this(itemClass, itemHandler, R.layout.list_playlist_mini_item, parent);
    }

    @Override
    public void updateFromItem(final ItemType item,
                               final HistoryRecordManager historyRecordManager) {
        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemHandler.getOnLocalItemSelectedListener() != null) {
                itemHandler.getOnLocalItemSelectedListener().held(item);
            }
            return true;
        });
    }
}
