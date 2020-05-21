package org.schabi.newpipe.local.holder;

import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.info_list.ItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class LocalPlaylistItemHolder extends PlaylistItemHolder {
    public LocalPlaylistItemHolder(final ItemBuilder itemBuilder, final ViewGroup parent) {
        super(itemBuilder, parent);
    }

    LocalPlaylistItemHolder(final ItemBuilder itemBuilder, final int layoutId,
                            final ViewGroup parent) {
        super(itemBuilder, layoutId, parent);
    }

    @Override
    public void updateFromItem(final Object item,
                               final HistoryRecordManager historyRecordManager) {
        if (!(item instanceof PlaylistMetadataEntry)) {
            return;
        }
        final PlaylistMetadataEntry localItem = (PlaylistMetadataEntry) item;

        itemTitleView.setText(localItem.name);
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), localItem.streamCount));
        itemUploaderView.setVisibility(View.INVISIBLE);

        itemBuilder.displayImage(localItem.thumbnailUrl, itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);

        super.updateFromItem(item, historyRecordManager);
    }
}
