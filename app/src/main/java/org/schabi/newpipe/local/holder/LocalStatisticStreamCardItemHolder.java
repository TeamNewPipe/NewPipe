package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.LocalItemBuilder;

public class LocalStatisticStreamCardItemHolder extends LocalStatisticStreamItemHolder {
    public LocalStatisticStreamCardItemHolder(final LocalItemBuilder infoItemBuilder,
                                              final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);
    }
}
