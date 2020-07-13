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

        float sourceWidth = inputBitmap.getWidth();
        float sourceHeight = inputBitmap.getHeight();

        float xScale = newWidth / sourceWidth;
        float yScale = newHeight / sourceHeight;

        float newXScale;
        float newYScale;

        if (yScale > xScale) {
            newXScale = xScale / yScale;
            newYScale = 1.0f;
        } else {
            newXScale = 1.0f;
            newYScale = yScale / xScale;
        }

        float scaledWidth = newXScale * sourceWidth;
        float scaledHeight = newYScale * sourceHeight;

        int left = (int) ((sourceWidth - scaledWidth) / 2);
        int top = (int) ((sourceHeight - scaledHeight) / 2);
        int width = (int) scaledWidth;
        int height = (int) scaledHeight;

        return Bitmap.createBitmap(inputBitmap, left, top, width, height);
    }

}
