package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.LocalItemBuilder;

/**
 * Playlist card layout.
 */
public class LocalPlaylistCardItemHolder extends LocalPlaylistItemHolder {

    public LocalPlaylistCardItemHolder(final LocalItemBuilder infoItemBuilder,
                                       final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_card_item, parent);
    }
}
