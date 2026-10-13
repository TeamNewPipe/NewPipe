package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.InfoItemBuilder;

/**
 * Card layout for stream.
 */
public class StreamCardInfoItemHolder extends StreamInfoItemHolder {

    public StreamCardInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);
        itemView.setBackground(org.schabi.newpipe.views.glass.GlassDrawable.createLiquidSurface(
                parent.getContext(), org.schabi.newpipe.views.glass.GlassDrawable.RADIUS_28));
        itemView.setClipToOutline(true);
        itemView.setElevation(org.schabi.newpipe.util.DeviceUtils.dpToPx(3, parent.getContext()));
    }
}
