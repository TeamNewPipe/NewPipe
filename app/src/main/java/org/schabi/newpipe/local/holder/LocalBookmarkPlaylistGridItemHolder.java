package org.schabi.newpipe.local.holder;

import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.LocalItemBuilder;

public class LocalBookmarkPlaylistGridItemHolder extends LocalBookmarkPlaylistItemHolder {
    public LocalBookmarkPlaylistGridItemHolder(final LocalItemBuilder infoItemBuilder,
                                                final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_grid_item, parent);

        final View handle = itemView.findViewById(R.id.itemHandle);
        if (handle != null) {
            handle.setVisibility(View.VISIBLE);
        }
    }
}
