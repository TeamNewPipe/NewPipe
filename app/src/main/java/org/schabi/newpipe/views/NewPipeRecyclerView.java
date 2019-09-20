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
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class NewPipeRecyclerView extends RecyclerView {
    private static final String TAG = "FixedRecyclerView";

    public NewPipeRecyclerView(@NonNull Context context) {
        super(context);
    }

    public NewPipeRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NewPipeRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public View focusSearch(int direction) {
        return null;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        return null;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        View found = super.focusSearch(focused, direction);
        if (found != null) {
            found.requestFocus(direction);
            return true;
        }

        if (direction == View.FOCUS_UP) {
            if (canScrollVertically(-1)) {
                scrollBy(0, -10);
                return true;
            }

            return false;
        }

        return super.dispatchUnhandledMove(focused, direction);
    }
}
