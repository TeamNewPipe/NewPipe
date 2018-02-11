package org.schabi.newpipe.util;

import android.support.v7.widget.RecyclerView;

public abstract class OnClickGesture<T> {

    public abstract void selected(T selectedItem);

    public void held(T selectedItem) {
        // Optional gesture
    }

    public void drag(T selectedItem, RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }
}
