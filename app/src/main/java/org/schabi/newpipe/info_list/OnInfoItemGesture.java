package org.schabi.newpipe.info_list;

import org.schabi.newpipe.extractor.InfoItem;

public abstract class OnInfoItemGesture<T extends InfoItem> {

    public abstract void selected(T selectedItem);

    public void held(T selectedItem) {
        // Optional gesture
    }
}
