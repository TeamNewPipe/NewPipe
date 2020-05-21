package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.info_list.ItemBuilder;
import org.schabi.newpipe.info_list.holder.ItemHolder;
import org.schabi.newpipe.local.history.HistoryRecordManager;

public abstract class PlaylistItemHolder extends ItemHolder {
    public final ImageView itemThumbnailView;
    final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    public PlaylistItemHolder(final ItemBuilder itemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(itemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
    }

    public PlaylistItemHolder(final ItemBuilder itemBuilder, final ViewGroup parent) {
        this(itemBuilder, R.layout.list_playlist_mini_item, parent);
    }

    @Override
    public void updateFromItem(final Object item,
                               final HistoryRecordManager historyRecordManager) {
        if (!(item instanceof LocalItem)) {
            return;
        }
        LocalItem localItem = (LocalItem) item;

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnLocalItemSelectedListener() != null) {
                itemBuilder.getOnLocalItemSelectedListener().selected(localItem);
            }
        });

        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnLocalItemSelectedListener() != null) {
                itemBuilder.getOnLocalItemSelectedListener().held(localItem);
            }
            return true;
        });
    }
}
