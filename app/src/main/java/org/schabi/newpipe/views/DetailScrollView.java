package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * Created by the-scrabi on 02.12.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * DetailScrollView.java is part of NewPipe.
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

public class DetailScrollView extends ScrollView {

    OnScrollViewListener mOnScrollViewListener = null;

    public DetailScrollView(Context context) {
        super(context);
    }

    public DetailScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DetailScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public interface OnScrollViewListener {
        void onScrollChanged( DetailScrollView v, int l, int t, int oldl, int oldt );
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        mOnScrollViewListener.onScrollChanged(this, l, t, oldl, oldt);
        super.onScrollChanged(l, t, oldl, oldt);
    }

    public void setOnScrollViewListener(OnScrollViewListener listener) {
        mOnScrollViewListener = listener;
    }
}
