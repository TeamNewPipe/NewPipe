package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemBuilder;

public class PlaylistGridInfoItemHolder extends PlaylistMiniInfoItemHolder {
    public PlaylistGridInfoItemHolder(final ItemBuilder itemBuilder,
                                      final ViewGroup parent) {
        super(itemBuilder, R.layout.list_playlist_grid_item, parent);
    }
}
