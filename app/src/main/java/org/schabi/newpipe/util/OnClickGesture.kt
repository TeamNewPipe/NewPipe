package org.schabi.newpipe.util

import androidx.recyclerview.widget.RecyclerView

open interface OnClickGesture<T> {
    fun selected(selectedItem: T)
    fun held(selectedItem: T) {
        // Optional gesture
    }

    fun drag(selectedItem: T, viewHolder: RecyclerView.ViewHolder?) {
        // Optional gesture
    }
}
