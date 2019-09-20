/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * FixedGridLayoutManager.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Version of GridLayoutManager that works around https://issuetracker.google.com/issues/37067220
public class FixedGridLayoutManager extends GridLayoutManager {
    public FixedGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    public FixedGridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public FixedGridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    @Override
    public View onFocusSearchFailed(View focused, int focusDirection, RecyclerView.Recycler recycler, RecyclerView.State state) {
        FocusFinder ff = FocusFinder.getInstance();

        View result = ff.findNextFocus((ViewGroup) focused.getParent(), focused, focusDirection);
        if (result != null) {
            return super.onFocusSearchFailed(focused, focusDirection, recycler, state);
        }

        if (focusDirection == View.FOCUS_DOWN) {
            scrollVerticallyBy(10, recycler, state);
            return null;
        }

        return super.onFocusSearchFailed(focused, focusDirection, recycler, state);
    }
}
