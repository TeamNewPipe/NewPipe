package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.InfoItemBuilder;

/**
 * YewPlay: the first channel of the search results, shown as a featured card.
 */
public class ChannelFeaturedInfoItemHolder extends ChannelMiniInfoItemHolder {
    public ChannelFeaturedInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                         final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_channel_featured_item, parent);
    }
}
