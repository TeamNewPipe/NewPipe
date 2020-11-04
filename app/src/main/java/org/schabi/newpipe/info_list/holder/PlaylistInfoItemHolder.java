package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.InfoItemBuilder;

public class PlaylistInfoItemHolder extends PlaylistMiniInfoItemHolder {
    public PlaylistInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_item, parent);
    }
}
