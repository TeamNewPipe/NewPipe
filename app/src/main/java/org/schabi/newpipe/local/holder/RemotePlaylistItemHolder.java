package org.schabi.newpipe.local.holder;

import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.ktx.TextViewUtils;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

import java.time.format.DateTimeFormatter;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class RemotePlaylistItemHolder extends PlaylistItemHolder {
    public RemotePlaylistItemHolder(final LocalItemBuilder infoItemBuilder,
                                    final ViewGroup parent) {
        super(infoItemBuilder, parent);
    }

    RemotePlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
    }

    @NonNull
    @Override
    public Disposable updateFromItem(final LocalItem localItem,
                                     final HistoryRecordManager historyRecordManager,
                                     final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistRemoteEntity)) {
            return Disposable.disposed();
        }
        final PlaylistRemoteEntity item = (PlaylistRemoteEntity) localItem;

        final String uploaderText = !TextUtils.isEmpty(item.getUploader())
                ? Localization.concatenateStrings(item.getUploader(),
                NewPipe.getNameOfService(item.getServiceId()))
                : NewPipe.getNameOfService(item.getServiceId());
        final CompositeDisposable compositeDisposable = new CompositeDisposable(
                TextViewUtils.computeAndSetPrecomputedText(itemTitleView, item.getName()),
                TextViewUtils.computeAndSetPrecomputedText(itemStreamCountView,
                        Localization.localizeStreamCountMini(itemStreamCountView.getContext(),
                                item.getStreamCount())),
                // Here is where the uploader name is set in the bookmarked playlists library
                TextViewUtils.computeAndSetPrecomputedText(itemUploaderView, uploaderText)
        );

        itemBuilder.displayImage(item.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);

        compositeDisposable.add(super.updateFromItem(localItem, historyRecordManager,
                dateTimeFormatter));

        return compositeDisposable;
    }
}
