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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public final class SuperScrollLayoutManager extends LinearLayoutManager {
    private final Rect handy = new Rect();

    private final ArrayList<View> focusables = new ArrayList<>();

    public SuperScrollLayoutManager(final Context context) {
        super(context);
    }

    @Override
    public boolean requestChildRectangleOnScreen(@NonNull final RecyclerView parent,
                                                 @NonNull final View child,
                                                 @NonNull final Rect rect,
                                                 final boolean immediate,
                                                 final boolean focusedChildVisible) {
        if (!parent.isInTouchMode()) {
            // only activate when in directional navigation mode (Android TV etc) — fine grained
            // touch scrolling is better served by nested scroll system

            if (!focusedChildVisible || getFocusedChild() == child) {
                handy.set(rect);

                parent.offsetDescendantRectToMyCoords(child, handy);

                parent.requestRectangleOnScreen(handy, immediate);
            }
        }

        return super.requestChildRectangleOnScreen(parent, child, rect, immediate,
                focusedChildVisible);
    }

    @Nullable
    @Override
    public View onInterceptFocusSearch(@NonNull final View focused, final int direction) {
        View focusedItem = findContainingItemView(focused);
        if (focusedItem == null) {
            return super.onInterceptFocusSearch(focused, direction);
        }

        int listDirection = getAbsoluteDirection(direction);
        if (listDirection == 0) {
            return super.onInterceptFocusSearch(focused, direction);
        }

        // FocusFinder has an oddity: it considers size of Views more important
        // than closeness to source View. This means, that big Views far away from current item
        // are preferred to smaller sub-View of closer item. Setting focusability of closer item
        // to FOCUS_AFTER_DESCENDANTS does not solve this, because ViewGroup#addFocusables omits
        // such parent itself from list, if any of children are focusable.
        // Fortunately we can intercept focus search and implement our own logic, based purely
        // on position along the LinearLayoutManager axis

        ViewGroup recycler = (ViewGroup) focusedItem.getParent();

        int sourcePosition = getPosition(focusedItem);
        if (sourcePosition == 0 && listDirection < 0) {
            return super.onInterceptFocusSearch(focused, direction);
        }

        View preferred = null;

        int distance = Integer.MAX_VALUE;

        focusables.clear();

        recycler.addFocusables(focusables, direction, recycler.isInTouchMode()
                ? View.FOCUSABLES_TOUCH_MODE
                : View.FOCUSABLES_ALL);

        try {
            for (View view : focusables) {
                if (view == focused || view == recycler) {
                    continue;
                }

                if (view == focusedItem) {
                    // do not pass focus back to the item View itself - it makes no sense
                    // (we can still pass focus to it's children however)
                    continue;
                }

                int candidate = getDistance(sourcePosition, view, listDirection);
                if (candidate < 0) {
                    continue;
                }

                if (candidate < distance) {
                    distance = candidate;
                    preferred = view;
                }
            }
        } finally {
            focusables.clear();
        }

        return preferred;
    }

    private int getAbsoluteDirection(final int direction) {
        switch (direction) {
            default:
                break;
            case View.FOCUS_FORWARD:
                return 1;
            case View.FOCUS_BACKWARD:
                return -1;
        }

        if (getOrientation() == RecyclerView.HORIZONTAL) {
            switch (direction) {
                default:
                    break;
                case View.FOCUS_LEFT:
                    return getReverseLayout() ? 1 : -1;
                case View.FOCUS_RIGHT:
                    return getReverseLayout() ? -1 : 1;
            }
        } else {
            switch (direction) {
                default:
                    break;
                case View.FOCUS_UP:
                    return getReverseLayout() ? 1 : -1;
                case View.FOCUS_DOWN:
                    return getReverseLayout() ? -1 : 1;
            }
        }

        return 0;
    }

    private int getDistance(final int sourcePosition, final View candidate, final int direction) {
        View itemView = findContainingItemView(candidate);
        if (itemView == null) {
            return -1;
        }

        int position = getPosition(itemView);

        return direction * (position - sourcePosition);
    }
}
