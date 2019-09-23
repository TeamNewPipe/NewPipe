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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.ArrayList;

public final class FocusAwareDrawerLayout extends DrawerLayout {
    public FocusAwareDrawerLayout(@NonNull Context context) {
        super(context);
    }

    public FocusAwareDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusAwareDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
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

    // this override isn't strictly necessary, but it is helpful when DrawerLayout isn't the topmost
    // view in hierarchy (such as when system or builtin appcompat ActionBar is used)
    @Override
    @SuppressLint("RtlHardcoded")
    public void openDrawer(@NonNull View drawerView, boolean animate) {
        super.openDrawer(drawerView, animate);

        LayoutParams params = (LayoutParams) drawerView.getLayoutParams();

        int gravity = GravityCompat.getAbsoluteGravity(params.gravity, ViewCompat.getLayoutDirection(this));

        int direction = 0;

        switch (gravity) {
            case Gravity.LEFT:
                direction = FOCUS_LEFT;
                break;
            case Gravity.RIGHT:
                direction = FOCUS_RIGHT;
                break;
            case Gravity.TOP:
                direction = FOCUS_UP;
                break;
            case Gravity.BOTTOM:
                direction = FOCUS_DOWN;
                break;
        }

        drawerView.requestFocus(direction);
    }
}
