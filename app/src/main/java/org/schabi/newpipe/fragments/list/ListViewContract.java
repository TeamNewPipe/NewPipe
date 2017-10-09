package org.schabi.newpipe.fragments.list;

import org.schabi.newpipe.fragments.ViewContract;

public interface ListViewContract<I, N> extends ViewContract<I> {
    void showListFooter(boolean show);

    void handleNextItems(N result);
}
