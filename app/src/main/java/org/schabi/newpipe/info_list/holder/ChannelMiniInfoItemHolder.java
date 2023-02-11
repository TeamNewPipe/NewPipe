package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.Localization;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    private final ImageView itemThumbnailView;
    private final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;
    private final TextView itemChannelDescriptionView;

    ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemChannelDescriptionView = itemView.findViewById(R.id.itemChannelDescriptionView);
    }

    public ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof ChannelInfoItem)) {
            return;
        }
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        itemTitleView.setText(item.getName());

        final String detailLine = getDetailLine(item);
        if (detailLine == null) {
            itemAdditionalDetailView.setVisibility(View.GONE);
        } else {
            itemAdditionalDetailView.setVisibility(View.VISIBLE);
            itemAdditionalDetailView.setText(getDetailLine(item));
        }

        PicassoHelper.loadAvatar(item.getThumbnailUrl()).into(itemThumbnailView);

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

        if (itemChannelDescriptionView != null) {
            // itemChannelDescriptionView will be null in the mini variant
            if (Utils.isBlank(item.getDescription())) {
                itemChannelDescriptionView.setVisibility(View.GONE);
            } else {
                itemChannelDescriptionView.setVisibility(View.VISIBLE);
                itemChannelDescriptionView.setText(item.getDescription());
                itemChannelDescriptionView.setMaxLines(detailLine == null ? 3 : 2);
            }
        }
    }

    @Nullable
    private String getDetailLine(final ChannelInfoItem item) {
        if (item.getStreamCount() >= 0 && item.getSubscriberCount() >= 0) {
            return Localization.concatenateStrings(
                    Localization.shortSubscriberCount(itemBuilder.getContext(),
                            item.getSubscriberCount()),
                    Localization.localizeStreamCount(itemBuilder.getContext(),
                            item.getStreamCount()));
        } else if (item.getStreamCount() >= 0) {
            return Localization.localizeStreamCount(itemBuilder.getContext(),
                    item.getStreamCount());
        } else if (item.getSubscriberCount() >= 0) {
            return Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        } else {
            return null;
        }
    }
}
