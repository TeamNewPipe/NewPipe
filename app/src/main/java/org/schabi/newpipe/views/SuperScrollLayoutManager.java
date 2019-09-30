/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * SuperScrollLayoutManager.java is part of NewPipe.
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
import android.graphics.Rect;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public final class SuperScrollLayoutManager extends LinearLayoutManager {
    private final Rect handy = new Rect();

    public SuperScrollLayoutManager(Context context) {
        super(context);
    }

    @Override
    public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child, @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
        if (!parent.isInTouchMode()) {
            // only activate when in directional navigation mode (Android TV etc) — fine grained
            // touch scrolling is better served by nested scroll system

            if (!focusedChildVisible || getFocusedChild() == child) {
                handy.set(rect);

                parent.offsetDescendantRectToMyCoords(child, handy);

                parent.requestRectangleOnScreen(handy, immediate);
            }
        }

        return super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible);
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
