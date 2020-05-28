package org.schabi.newpipe.local.holder;

import android.text.TextUtils;
import android.view.ViewGroup;

import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class RemotePlaylistItemHolder extends PlaylistItemHolder {
    public RemotePlaylistItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, parent);
    }

    RemotePlaylistItemHolder(final ItemHandler itemHandler, final int layoutId,
                             final ViewGroup parent) {
        super(itemHandler, layoutId, parent);
    }

    @Override
    public void updateFromItem(final PlaylistLocalItem item,
                               final HistoryRecordManager historyRecordManager) {
        PlaylistRemoteEntity entity = (PlaylistRemoteEntity) item;

        itemTitleView.setText(entity.getName());
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), entity.getStreamCount()));

        // Here is where the uploader name is set in the bookmarked playlists library
        if (!TextUtils.isEmpty(entity.getUploader())) {
            itemUploaderView.setText(Localization.concatenateStrings(entity.getUploader(),
                    NewPipe.getNameOfService(entity.getServiceId())));
        } else {
            itemUploaderView.setText(NewPipe.getNameOfService(entity.getServiceId()));
        }


        itemHandler.displayImage(entity.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);

        super.updateFromItem(item, historyRecordManager);
    }
}
