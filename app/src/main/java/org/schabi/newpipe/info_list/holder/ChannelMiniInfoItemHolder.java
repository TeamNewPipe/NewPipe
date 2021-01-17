package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.TextViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

import de.hdodenhof.circleimageview.CircleImageView;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    public final CircleImageView itemThumbnailView;
    public final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;

    ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    public ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @NonNull
    @Override
    public Disposable updateFromItem(final InfoItem infoItem,
                                     final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof ChannelInfoItem)) {
            return Disposable.disposed();
        }
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        final CompositeDisposable compositeDisposable = new CompositeDisposable(
                TextViewUtils.computeAndSetPrecomputedText(itemTitleView, item.getName()),
                TextViewUtils.computeAndSetPrecomputedText(itemAdditionalDetailView,
                        getDetailLine(item))
        );

        itemBuilder.getImageLoader()
                .displayImage(item.getThumbnailUrl(),
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().held(item);
            }
            return true;
        });

        return compositeDisposable;
    }

    protected String getDetailLine(final ChannelInfoItem item) {
        String details = "";
        if (item.getSubscriberCount() >= 0) {
            details += Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        }
        return details;
    }
}
