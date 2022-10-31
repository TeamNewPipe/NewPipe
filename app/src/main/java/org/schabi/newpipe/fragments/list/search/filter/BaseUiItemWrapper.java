// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.view.View;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import androidx.annotation.NonNull;

public abstract class BaseUiItemWrapper extends BaseItemWrapper {
    @NonNull
    protected final View view;

    protected BaseUiItemWrapper(@NonNull final FilterItem item,
                                @NonNull final View view) {
        super(item);
        this.view = view;
    }

    @Override
    public void setVisible(final boolean visible) {
        if (visible) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }
}
