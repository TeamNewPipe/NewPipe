package org.schabi.newpipe.util;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;

public class WallpaperUtils {

    public static void showLockScreenThumbnail(@NonNull Context applicationContext, @Nullable Bitmap thumbnail) {
        // Flag FLAG_LOCK is only available in Nougat and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        if (thumbnail == null || thumbnail.isRecycled()) {
            // no thumbnail available
            return;
        }

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(applicationContext);
        if (!wallpaperManager.isSetWallpaperAllowed() || !wallpaperManager.isWallpaperSupported()) {
            // can't do anything with the wallpaper
            return;
        }

        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        Bitmap resultBitmap = BitmapUtils.centerCrop(
                thumbnail,
                screenWidth,
                screenHeight);

        if (resultBitmap == null) {
            // could not center crop the bitmap
            return;
        }

        try {
            wallpaperManager.setBitmap(
                    resultBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK);
        } catch (IOException e) {
            // do nothing when an exception occurs
            e.printStackTrace();

            if (resultBitmap != null && !resultBitmap.isRecycled()) {
                resultBitmap.recycle();
            }
        }
    }

    public static void hideLockScreenThumbnail(@NonNull Context applicationContext) {
        // Flag FLAG_LOCK is only available in Nougat and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(applicationContext);
        if (!wallpaperManager.isSetWallpaperAllowed() || !wallpaperManager.isWallpaperSupported()) {
            // can't do anything with the wallpaper
            return;
        }

        try {
            wallpaperManager.clear(WallpaperManager.FLAG_LOCK);
        } catch (IOException e) {
            // do nothing when an exception occurs
            e.printStackTrace();
        }
    }

}
