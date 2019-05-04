package org.schabi.newpipe.util;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

public class BitmapUtils {

    @Nullable
    public static Bitmap centerCrop(Bitmap inputBitmap, int newWidth, int newHeight) {
        if (inputBitmap == null || inputBitmap.isRecycled()) {
            return null;
        }

        int sourceWidth = inputBitmap.getWidth();
        int sourceHeight = inputBitmap.getHeight();

        int xScale = newWidth / sourceWidth;
        int yScale = newHeight / sourceHeight;

        double newXScale;
        double newYScale;

        if (yScale > xScale) {
            newXScale = (1.0 / yScale) * xScale;
            newYScale = 1.0;
        } else {
            newXScale = 1.0;
            newYScale = (1.0 / xScale) * yScale;
        }

        double scaledWidth = newXScale * sourceWidth;
        double scaledHeight = newYScale * sourceHeight;

        int left = (int) ((sourceWidth - scaledWidth) / 2);
        int top = (int) ((sourceHeight - scaledHeight) / 2);
        int width = (int) scaledWidth;
        int height = (int) scaledHeight;

        return Bitmap.createBitmap(inputBitmap, left, top, width, height);
    }

}
