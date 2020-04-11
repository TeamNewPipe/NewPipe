/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * NewPipeRecyclerView.java is part of NewPipe.
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
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class NewPipeRecyclerView extends RecyclerView {
    private static final String TAG = "NewPipeRecyclerView";

    private Rect focusRect = new Rect();
    private Rect tempFocus = new Rect();

    private boolean allowDpadScroll = true;

    public NewPipeRecyclerView(@NonNull final Context context) {
        super(context);

        init();
    }

    public NewPipeRecyclerView(@NonNull final Context context,
                               @Nullable final AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public NewPipeRecyclerView(@NonNull final Context context,
                               @Nullable final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        init();
    }

    private void init() {
        setFocusable(true);

        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    public void setFocusScrollAllowed(final boolean allowed) {
        this.allowDpadScroll = allowed;
    }

    @Override
    public View focusSearch(final View focused, final int direction) {
        // RecyclerView has buggy focusSearch(), that calls into Adapter several times,
        // but ultimately fails to produce correct results in many cases. To add insult to injury,
        // it's focusSearch() hard-codes several behaviors, incompatible with widely accepted focus
        // handling practices: RecyclerView does not allow Adapter to give focus to itself (!!) and
        // always checks, that returned View is located in "correct" direction (which prevents us
        // from temporarily giving focus to special hidden View).
        return null;
    }

    @Override
    protected void removeDetachedView(final View child, final boolean animate) {
        if (child.hasFocus()) {
            // If the focused child is being removed (can happen during very fast scrolling),
            // temporarily give focus to ourselves. This will usually result in another child
            // gaining focus (which one does not really matter, because at that point scrolling
            // is FAST, and that child will soon be off-screen too)
            requestFocus();
        }

        super.removeDetachedView(child, animate);
    }

    // we override focusSearch to always return null, so all moves moves lead to
    // dispatchUnhandledMove(). As added advantage, we can fully swallow some kinds of moves
    // (such as downward movement, that happens when loading additional contents is in progress

    @Override
    public boolean dispatchUnhandledMove(final View focused, final int direction) {
        tempFocus.setEmpty();

        // save focus rect before further manipulation (both focusSearch() and scrollBy()
        // can mess with focused View by moving it off-screen and detaching)

        if (focused != null) {
            View focusedItem = findContainingItemView(focused);
            if (focusedItem != null) {
                focusedItem.getHitRect(focusRect);
            }
        }

        // call focusSearch() to initiate layout, but disregard returned View for now
        View adapterResult = super.focusSearch(focused, direction);
        if (adapterResult != null && !isOutside(adapterResult)) {
            adapterResult.requestFocus(direction);
            return true;
        }

        if (arrowScroll(direction)) {
            // if RecyclerView can not yield focus, but there is still some scrolling space in
            // indicated, direction, scroll some fixed amount in that direction
            // (the same logic in ScrollView)
            return true;
        }

        if (focused != this && direction == FOCUS_DOWN && !allowDpadScroll) {
            Log.i(TAG, "Consuming downward scroll: content load in progress");
            return true;
        }

        if (tryFocusFinder(direction)) {
            return true;
        }

        if (adapterResult != null) {
            adapterResult.requestFocus(direction);
            return true;
        }

        return super.dispatchUnhandledMove(focused, direction);
    }

    private boolean tryFocusFinder(final int direction) {
        if (Build.VERSION.SDK_INT >= 28) {
            // Android 9 implemented bunch of handy changes to focus, that render code below less
            // useful, and also broke findNextFocusFromRect in way, that render this hack useless
            return false;
        }

        FocusFinder finder = FocusFinder.getInstance();

        // try to use FocusFinder instead of adapter
        ViewGroup root = (ViewGroup) getRootView();

        tempFocus.set(focusRect);

        root.offsetDescendantRectToMyCoords(this, tempFocus);

        View focusFinderResult = finder.findNextFocusFromRect(root, tempFocus, direction);
        if (focusFinderResult != null && !isOutside(focusFinderResult)) {
            focusFinderResult.requestFocus(direction);
            return true;
        }

        // look for focus in our ancestors, increasing search scope with each failure
        // this provides much better locality than using FocusFinder with root
        ViewGroup parent = (ViewGroup) getParent();

        while (parent != root) {
            tempFocus.set(focusRect);

            parent.offsetDescendantRectToMyCoords(this, tempFocus);

            View candidate = finder.findNextFocusFromRect(parent, tempFocus, direction);
            if (candidate != null && candidate.requestFocus(direction)) {
                return true;
            }

            parent = (ViewGroup) parent.getParent();
        }

        return false;
    }

    private boolean arrowScroll(final int direction) {
        switch (direction) {
            case FOCUS_DOWN:
                if (!canScrollVertically(1)) {
                    return false;
                }
                scrollBy(0, 100);
                break;
            case FOCUS_UP:
                if (!canScrollVertically(-1)) {
                    return false;
                }
                scrollBy(0, -100);
                break;
            case FOCUS_LEFT:
                if (!canScrollHorizontally(-1)) {
                    return false;
                }
                scrollBy(-100, 0);
                break;
            case FOCUS_RIGHT:
                if (!canScrollHorizontally(-1)) {
                    return false;
                }
                scrollBy(100, 0);
                break;
            default:
                return false;
        }

        return true;
    }

    private boolean isOutside(final View view) {
        return findContainingItemView(view) == null;
    }
}
