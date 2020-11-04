package org.schabi.newpipe.util;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

public final class BitmapUtils {
    private BitmapUtils() { }

    @Nullable
    public static Bitmap centerCrop(final Bitmap inputBitmap, final int newWidth,
                                    final int newHeight) {
        if (inputBitmap == null || inputBitmap.isRecycled()) {
            return null;
        }

        final float sourceWidth = inputBitmap.getWidth();
        final float sourceHeight = inputBitmap.getHeight();

        final float xScale = newWidth / sourceWidth;
        final float yScale = newHeight / sourceHeight;

        final float newXScale;
        final float newYScale;

        if (yScale > xScale) {
            newXScale = xScale / yScale;
            newYScale = 1.0f;
        } else {
            newXScale = 1.0f;
            newYScale = yScale / xScale;
        }

        final float scaledWidth = newXScale * sourceWidth;
        final float scaledHeight = newYScale * sourceHeight;

        final int left = (int) ((sourceWidth - scaledWidth) / 2);
        final int top = (int) ((sourceHeight - scaledHeight) / 2);
        final int width = (int) scaledWidth;
        final int height = (int) scaledHeight;

        return Bitmap.createBitmap(inputBitmap, left, top, width, height);
    }
}
