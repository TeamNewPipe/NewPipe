package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.InfoItemBuilder;

public class ChannelCardInfoItemHolder extends ChannelMiniInfoItemHolder {
    public ChannelCardInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_channel_card_item, parent);
    }

    @Override
    protected int getDescriptionMaxLineCount(@Nullable final String content) {
        // Based on `list_channel_card_item` left side content (thumbnail 100dp
        // + additional details), Right side description can grow up to 8 lines.
        return 8;
    }
}
