package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.TextViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class PlaylistMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    private final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    public PlaylistMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                      final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
    }

    public PlaylistMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                      final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_playlist_mini_item, parent);
    }

    @NonNull
    @Override
    public Disposable updateFromItem(final InfoItem infoItem,
                                     final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof PlaylistInfoItem)) {
            return Disposable.disposed();
        }
        final PlaylistInfoItem item = (PlaylistInfoItem) infoItem;

        final CompositeDisposable compositeDisposable = new CompositeDisposable(
                TextViewUtils.computeAndSetPrecomputedText(itemTitleView, item.getName()),
                TextViewUtils.computeAndSetPrecomputedText(itemStreamCountView,
                        Localization.localizeStreamCountMini(itemStreamCountView.getContext(),
                                item.getStreamCount())),
                TextViewUtils.computeAndSetPrecomputedText(itemUploaderView, item.getUploaderName())
        );

        itemBuilder.getImageLoader()
                .displayImage(item.getThumbnailUrl(), itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                itemBuilder.getOnPlaylistSelectedListener().selected(item);
            }
        });

        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                itemBuilder.getOnPlaylistSelectedListener().held(item);
            }
            return true;
        });

        return compositeDisposable;
    }
}
