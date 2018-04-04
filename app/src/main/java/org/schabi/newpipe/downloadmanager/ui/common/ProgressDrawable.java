package org.schabi.newpipe.downloadmanager.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

/**
 * Copyright (C) 2014 Peter Cai
 * Changes by Christian Schabesberger (C) 2018
 *
 * org.schabi.newpipe.downloadmanager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.schabi.newpipe.downloadmanager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with org.schabi.newpipe.downloadmanager.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ProgressDrawable extends Drawable {
    private float mProgress;
    private int mBackgroundColor, mForegroundColor;

    public ProgressDrawable(Context context, @ColorRes int background, @ColorRes int foreground) {
        this(ContextCompat.getColor(context, background), ContextCompat.getColor(context, foreground));
    }

    public ProgressDrawable(int background, int foreground) {
        mBackgroundColor = background;
        mForegroundColor = foreground;
    }

    public void setProgress(float progress) {
        mProgress = progress;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        Paint paint = new Paint();

        paint.setColor(mBackgroundColor);
        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(mForegroundColor);
        canvas.drawRect(0, 0, (int) (mProgress * width), height, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        // Unsupported
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        // Unsupported
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

}
