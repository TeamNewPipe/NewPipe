package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.StaffInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.PicassoHelper;

public class StaffInfoItemHolder extends InfoItemHolder {

    public final ImageView itemThumbnailView;
    public final TextView itemStaffNameView;
    private final TextView itemStaffTitleView;

    public StaffInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                               final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.detail_staff_thumbnail_view);
        itemStaffNameView = itemView.findViewById(R.id.detail_staff_name_text_view);
        itemStaffTitleView = itemView.findViewById(R.id.detail_staff_title_text_view);
    }

    public StaffInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                               final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_staff_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {

        if (!(infoItem instanceof StaffInfoItem)) {
            return;
        }
        StaffInfoItem item = (StaffInfoItem) infoItem;
        itemStaffNameView.setText(item.getName());
        itemStaffTitleView.setText(item.getTitle());

        PicassoHelper.loadScaledDownThumbnail(itemThumbnailView.getContext(), infoItem.getThumbnailUrl())
                .into(itemThumbnailView);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item.toChannelInfoItem());
            }
        });
    }
}
