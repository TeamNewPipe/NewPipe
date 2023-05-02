package org.schabi.newpipe.util.image;

import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.BitmapCompat;

import com.squareup.picasso.Cache;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Transformation;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Image.ResolutionLevel;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class PicassoHelper {
    private static final String TAG = PicassoHelper.class.getSimpleName();
    private static final String PLAYER_THUMBNAIL_TRANSFORMATION_KEY =
            "PICASSO_PLAYER_THUMBNAIL_TRANSFORMATION_KEY";

    private PicassoHelper() {
    }

    private static Cache picassoCache;
    private static OkHttpClient picassoDownloaderClient;

    // suppress because terminate() is called in App.onTerminate(), preventing leaks
    @SuppressLint("StaticFieldLeak")
    private static Picasso picassoInstance;

    private static PreferredImageQuality preferredImageQuality = PreferredImageQuality.MEDIUM;

    public static void init(final Context context) {
        picassoCache = new LruCache(10 * 1024 * 1024);
        picassoDownloaderClient = new OkHttpClient.Builder()
                .cache(new okhttp3.Cache(new File(context.getExternalCacheDir(), "picasso"),
                        50 * 1024 * 1024))
                // this should already be the default timeout in OkHttp3, but just to be sure...
                .callTimeout(15, TimeUnit.SECONDS)
                .build();

        picassoInstance = new Picasso.Builder(context)
                .memoryCache(picassoCache) // memory cache
                .downloader(new OkHttp3Downloader(picassoDownloaderClient)) // disk cache
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .build();
    }

    public static void terminate() {
        picassoCache = null;
        picassoDownloaderClient = null;

        if (picassoInstance != null) {
            picassoInstance.shutdown();
            picassoInstance = null;
        }
    }

    public static void clearCache(final Context context) throws IOException {
        picassoInstance.shutdown();
        picassoCache.clear(); // clear memory cache
        final okhttp3.Cache diskCache = picassoDownloaderClient.cache();
        if (diskCache != null) {
            diskCache.delete(); // clear disk cache
        }
        init(context);
    }

    public static void cancelTag(final Object tag) {
        picassoInstance.cancelTag(tag);
    }

    public static void setIndicatorsEnabled(final boolean enabled) {
        picassoInstance.setIndicatorsEnabled(enabled); // useful for debugging
    }

    public static void setPreferredImageQuality(final PreferredImageQuality preferredImageQuality) {
        PicassoHelper.preferredImageQuality = preferredImageQuality;
    }

    public static boolean shouldLoadImages() {
        return preferredImageQuality != PreferredImageQuality.NONE;
    }


    public static RequestCreator loadAvatar(final List<Image> images) {
        return loadImageDefault(images, R.drawable.placeholder_person);
    }

    public static RequestCreator loadAvatar(final String url) {
        return loadImageDefault(url, R.drawable.placeholder_person);
    }

    public static RequestCreator loadThumbnail(final List<Image> images) {
        return loadImageDefault(images, R.drawable.placeholder_thumbnail_video);
    }

    public static RequestCreator loadThumbnail(final String url) {
        return loadImageDefault(url, R.drawable.placeholder_thumbnail_video);
    }

    public static RequestCreator loadDetailsThumbnail(final List<Image> images) {
        return loadImageDefault(choosePreferredImage(images),
                R.drawable.placeholder_thumbnail_video, false);
    }

    public static RequestCreator loadBanner(final List<Image> images) {
        return loadImageDefault(images, R.drawable.placeholder_channel_banner);
    }

    public static RequestCreator loadPlaylistThumbnail(final List<Image> images) {
        return loadImageDefault(images, R.drawable.placeholder_thumbnail_playlist);
    }

    public static RequestCreator loadPlaylistThumbnail(final String url) {
        return loadImageDefault(url, R.drawable.placeholder_thumbnail_playlist);
    }

    public static RequestCreator loadSeekbarThumbnailPreview(final String url) {
        return picassoInstance.load(url);
    }

    public static RequestCreator loadNotificationIcon(final String url) {
        return loadImageDefault(url, R.drawable.ic_newpipe_triangle_white);
    }


    public static RequestCreator loadScaledDownThumbnail(final Context context,
                                                         final List<Image> images) {
        // scale down the notification thumbnail for performance
        return PicassoHelper.loadThumbnail(images)
                .transform(new Transformation() {
                    @Override
                    public Bitmap transform(final Bitmap source) {
                        if (DEBUG) {
                            Log.d(TAG, "Thumbnail - transform() called");
                        }

                        final float notificationThumbnailWidth = Math.min(
                                context.getResources()
                                        .getDimension(R.dimen.player_notification_thumbnail_width),
                                source.getWidth());

                        final Bitmap result = BitmapCompat.createScaledBitmap(
                                source,
                                (int) notificationThumbnailWidth,
                                (int) (source.getHeight()
                                        / (source.getWidth() / notificationThumbnailWidth)),
                                null,
                                true);

                        if (result == source || !result.isMutable()) {
                            // create a new mutable bitmap to prevent strange crashes on some
                            // devices (see #4638)
                            final Bitmap copied = BitmapCompat.createScaledBitmap(
                                    source,
                                    (int) notificationThumbnailWidth - 1,
                                    (int) (source.getHeight() / (source.getWidth()
                                            / (notificationThumbnailWidth - 1))),
                                    null,
                                    true);
                            source.recycle();
                            return copied;
                        } else {
                            source.recycle();
                            return result;
                        }
                    }

                    @Override
                    public String key() {
                        return PLAYER_THUMBNAIL_TRANSFORMATION_KEY;
                    }
                });
    }

    @Nullable
    public static Bitmap getImageFromCacheIfPresent(final String imageUrl) {
        // URLs in the internal cache finish with \n so we need to add \n to image URLs
        return picassoCache.get(imageUrl + "\n");
    }


    private static RequestCreator loadImageDefault(final List<Image> images,
                                                   final int placeholderResId) {
        return loadImageDefault(choosePreferredImage(images), placeholderResId);
    }

    private static RequestCreator loadImageDefault(final String url,
                                                   final int placeholderResId) {
        return loadImageDefault(url, placeholderResId, true);
    }

    private static RequestCreator loadImageDefault(@Nullable final String url,
                                                   final int placeholderResId,
                                                   final boolean showPlaceholderWhileLoading) {
        if (isNullOrEmpty(url)) {
            return picassoInstance
                    .load((String) null)
                    .placeholder(placeholderResId) // show placeholder when no image should load
                    .error(placeholderResId);
        } else {
            final RequestCreator requestCreator = picassoInstance
                    .load(url)
                    .error(placeholderResId);
            if (showPlaceholderWhileLoading) {
                requestCreator.placeholder(placeholderResId);
            }
            return requestCreator;
        }
    }

    @Nullable
    public static String choosePreferredImage(final List<Image> images) {
        final Comparator<Image> comparator;
        switch (preferredImageQuality) {
            case NONE:
                return null;
            case HIGH:
                comparator = Comparator.comparingInt(Image::getHeight).reversed();
                break;
            default:
            case MEDIUM:
                comparator = Comparator.comparingInt(image -> Math.abs(image.getHeight() - 450));
                break;
            case LOW:
                comparator = Comparator.comparingInt(Image::getHeight);
                break;
        }

        return images.stream()
                .filter(image -> image.getEstimatedResolutionLevel() != ResolutionLevel.UNKNOWN)
                .min(comparator)
                .map(Image::getUrl)
                .orElseGet(() -> images.stream()
                        .findAny()
                        .map(Image::getUrl)
                        .orElse(null));
    }

    @NonNull
    public static List<Image> urlToImageList(@Nullable final String url) {
        if (url == null) {
            return List.of();
        } else {
            return List.of(new Image(url, -1, -1, ResolutionLevel.UNKNOWN));
        }
    }
}
