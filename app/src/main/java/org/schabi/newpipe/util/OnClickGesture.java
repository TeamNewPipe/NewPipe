package org.schabi.newpipe.util;

import androidx.recyclerview.widget.RecyclerView;

public interface OnClickGesture<T> {
    void selected(T selectedItem);

    default void held(final T selectedItem) {
        // Optional gesture
    }

    default void drag(final T selectedItem, final RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }
}
