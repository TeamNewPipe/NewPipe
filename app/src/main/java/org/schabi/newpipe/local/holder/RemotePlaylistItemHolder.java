package org.schabi.newpipe.local.holder;

import android.text.TextUtils;
import android.view.ViewGroup;

import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.info_list.ItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class RemotePlaylistItemHolder extends PlaylistItemHolder {
    public RemotePlaylistItemHolder(final ItemBuilder itemBuilder, final ViewGroup parent) {
        super(itemBuilder, parent);
    }

    RemotePlaylistItemHolder(final ItemBuilder itemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(itemBuilder, layoutId, parent);
    }

    @Override
    public void updateFromItem(final Object item,
                               final HistoryRecordManager historyRecordManager) {
        if (!(item instanceof PlaylistRemoteEntity)) {
            return;
        }
        final PlaylistRemoteEntity localItem = (PlaylistRemoteEntity) item;

        itemTitleView.setText(localItem.getName());
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), localItem.getStreamCount()));

        // Here is where the uploader name is set in the bookmarked playlists library
        if (!TextUtils.isEmpty(localItem.getUploader())) {
            itemUploaderView.setText(Localization.concatenateStrings(localItem.getUploader(),
                    NewPipe.getNameOfService(localItem.getServiceId())));
        } else {
            itemUploaderView.setText(NewPipe.getNameOfService(localItem.getServiceId()));
        }


        itemBuilder.displayImage(localItem.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);

        super.updateFromItem(item, historyRecordManager);
    }
}
