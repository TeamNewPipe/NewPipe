package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemBuilder;

public class PlaylistInfoItemHolder extends PlaylistMiniInfoItemHolder {
    public PlaylistInfoItemHolder(final ItemBuilder itemBuilder, final ViewGroup parent) {
        super(itemBuilder, R.layout.list_playlist_item, parent);
    }
}
