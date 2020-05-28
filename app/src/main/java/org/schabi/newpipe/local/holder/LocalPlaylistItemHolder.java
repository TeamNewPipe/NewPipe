package org.schabi.newpipe.local.holder;

import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class LocalPlaylistItemHolder extends PlaylistItemHolder {
    public LocalPlaylistItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, parent);
    }

    LocalPlaylistItemHolder(final ItemHandler itemHandler, final int layoutId,
                            final ViewGroup parent) {
        super(itemHandler, layoutId, parent);
    }

    @Override
    public void updateFromItem(final PlaylistLocalItem item,
                               final HistoryRecordManager historyRecordManager) {
        PlaylistMetadataEntry entry = (PlaylistMetadataEntry) item;

        itemTitleView.setText(entry.name);
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), entry.streamCount));
        itemUploaderView.setVisibility(View.INVISIBLE);

        itemHandler.displayImage(entry.thumbnailUrl, itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);

        super.updateFromItem(item, historyRecordManager);
    }
}
