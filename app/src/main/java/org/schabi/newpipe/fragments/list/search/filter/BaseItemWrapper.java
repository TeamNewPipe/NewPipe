// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import androidx.annotation.NonNull;

public abstract class BaseItemWrapper implements SearchFilterLogic.IUiItemWrapper {
    @NonNull
    protected final FilterItem item;

    protected BaseItemWrapper(@NonNull final FilterItem item) {
        this.item = item;
    }

    @Override
    public int getItemId() {
        return item.getIdentifier();
    }
}
