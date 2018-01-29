package org.schabi.newpipe.fragments.local;

import android.support.v7.widget.RecyclerView;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.extractor.InfoItem;

public abstract class OnLocalItemGesture<T extends LocalItem> {

    public abstract void selected(T selectedItem);

    public void held(T selectedItem) {
        // Optional gesture
    }

    public void drag(T selectedItem, RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }
}
