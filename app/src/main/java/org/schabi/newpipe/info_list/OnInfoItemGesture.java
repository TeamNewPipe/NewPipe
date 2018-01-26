package org.schabi.newpipe.info_list;

import android.support.v7.widget.RecyclerView;

import org.schabi.newpipe.extractor.InfoItem;

public abstract class OnInfoItemGesture<T extends InfoItem> {

    public abstract void selected(T selectedItem);

    public void held(T selectedItem) {
        // Optional gesture
    }

    public void drag(T selectedItem, RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }
}
