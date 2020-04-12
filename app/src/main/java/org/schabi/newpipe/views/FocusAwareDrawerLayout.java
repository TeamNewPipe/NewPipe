/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * FocusAwareDrawerLayout.java is part of NewPipe.
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.ArrayList;

public final class FocusAwareDrawerLayout extends DrawerLayout {
    public FocusAwareDrawerLayout(@NonNull final Context context) {
        super(context);
    }

    public FocusAwareDrawerLayout(@NonNull final Context context,
                                  @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusAwareDrawerLayout(@NonNull final Context context,
                                  @Nullable final AttributeSet attrs,
                                  final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean onRequestFocusInDescendants(final int direction,
                                                  final Rect previouslyFocusedRect) {
        // SDK implementation of this method picks whatever visible View takes the focus first
        // without regard to addFocusables. If the open drawer is temporarily empty, the focus
        // escapes outside of it, which can be confusing

        boolean hasOpenPanels = false;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);

            DrawerLayout.LayoutParams lp = (DrawerLayout.LayoutParams) child.getLayoutParams();

            if (lp.gravity != 0 && isDrawerVisible(child)) {
                hasOpenPanels = true;

                if (child.requestFocus(direction, previouslyFocusedRect)) {
                    return true;
                }
            }
        }

        if (hasOpenPanels) {
            return false;
        }

        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public void addFocusables(final ArrayList<View> views, final int direction,
                              final int focusableMode) {
        boolean hasOpenPanels = false;
        View content = null;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);

            DrawerLayout.LayoutParams lp = (DrawerLayout.LayoutParams) child.getLayoutParams();

            if (lp.gravity == 0) {
                content = child;
            } else {
                if (isDrawerVisible(child)) {
                    hasOpenPanels = true;
                    child.addFocusables(views, direction, focusableMode);
                }
            }
        }

        if (content != null && !hasOpenPanels) {
            content.addFocusables(views, direction, focusableMode);
        }
    }

    // this override isn't strictly necessary, but it is helpful when DrawerLayout isn't
    // the topmost view in hierarchy (such as when system or builtin appcompat ActionBar is used)
    @Override
    @SuppressLint("RtlHardcoded")
    public void openDrawer(@NonNull final View drawerView, final boolean animate) {
        super.openDrawer(drawerView, animate);

        drawerView.requestFocus(FOCUS_FORWARD);
    }
}
