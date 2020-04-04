package org.schabi.newpipe.util;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import org.schabi.newpipe.R;

public final class ImageDisplayConstants {
    private static final int BITMAP_FADE_IN_DURATION_MILLIS = 250;

    /**
     * This constant contains the base display options.
     */
    private static final DisplayImageOptions BASE_DISPLAY_IMAGE_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .resetViewBeforeLoading(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .displayer(new FadeInBitmapDisplayer(BITMAP_FADE_IN_DURATION_MILLIS))
                    .build();

    /*//////////////////////////////////////////////////////////////////////////
    // DisplayImageOptions default configurations
    //////////////////////////////////////////////////////////////////////////*/

    public static final DisplayImageOptions DISPLAY_AVATAR_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageForEmptyUri(R.drawable.buddy)
                    .showImageOnFail(R.drawable.buddy)
                    .build();

    public static final DisplayImageOptions DISPLAY_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageForEmptyUri(R.drawable.dummy_thumbnail)
                    .showImageOnFail(R.drawable.dummy_thumbnail)
                    .build();

    public static final DisplayImageOptions DISPLAY_BANNER_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageForEmptyUri(R.drawable.channel_banner)
                    .showImageOnFail(R.drawable.channel_banner)
                    .build();

    public static final DisplayImageOptions DISPLAY_PLAYLIST_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
                    .showImageForEmptyUri(R.drawable.dummy_thumbnail_playlist)
                    .showImageOnFail(R.drawable.dummy_thumbnail_playlist)
                    .build();

    private ImageDisplayConstants() { }
}
