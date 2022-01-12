package org.schabi.newpipe.player.seekbarpreview;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.annotation.Retention;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType.HIGH_QUALITY;
import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType.LOW_QUALITY;
import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType.NONE;

/**
 * Helper for the seekbar preview.
 */
public final class SeekbarPreviewThumbnailHelper {

    // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
    // or it fails with an IllegalArgumentException
    // https://stackoverflow.com/a/54744028
    public static final String TAG = "SeekbarPrevThumbHelper";

    private SeekbarPreviewThumbnailHelper() {
        // No impl pls
    }

    @Retention(SOURCE)
    @IntDef({HIGH_QUALITY, LOW_QUALITY,
            NONE})
    public @interface SeekbarPreviewThumbnailType {
        int HIGH_QUALITY = 0;
        int LOW_QUALITY = 1;
        int NONE = 2;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Settings Resolution
    ///////////////////////////////////////////////////////////////////////////

    @SeekbarPreviewThumbnailType
    public static int getSeekbarPreviewThumbnailType(@NonNull final Context context) {
        final String type = PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getString(R.string.seekbar_preview_thumbnail_key), "");
        if (type.equals(context.getString(R.string.seekbar_preview_thumbnail_none))) {
            return NONE;
        } else if (type.equals(context.getString(R.string.seekbar_preview_thumbnail_low_quality))) {
            return LOW_QUALITY;
        } else {
            return HIGH_QUALITY; // default
        }
    }

    public static void tryResizeAndSetSeekbarPreviewThumbnail(
            @NonNull final Context context,
            @NonNull final Optional<Bitmap> optPreviewThumbnail,
            @NonNull final ImageView currentSeekbarPreviewThumbnail,
            @NonNull final IntSupplier baseViewWidthSupplier) {

        if (!optPreviewThumbnail.isPresent()) {
            currentSeekbarPreviewThumbnail.setVisibility(View.GONE);
            return;
        }

        currentSeekbarPreviewThumbnail.setVisibility(View.VISIBLE);
        final Bitmap srcBitmap = optPreviewThumbnail.get();

        // Resize original bitmap
        try {
            Objects.requireNonNull(srcBitmap);

            final int srcWidth = srcBitmap.getWidth() > 0 ? srcBitmap.getWidth() : 1;
            final int newWidth = Math.max(
                    Math.min(
                            // Use 1/4 of the width for the preview
                            Math.round(baseViewWidthSupplier.getAsInt() / 4f),
                            // Scaling more than that factor looks really pixelated -> max
                            Math.round(srcWidth * 2.5f)
                    ),
                    // Min width = 10dp
                    DeviceUtils.dpToPx(10, context)
            );

            final float scaleFactor = (float) newWidth / srcWidth;
            final int newHeight = (int) (srcBitmap.getHeight() * scaleFactor);

            currentSeekbarPreviewThumbnail.setImageBitmap(
                    Bitmap.createScaledBitmap(srcBitmap, newWidth, newHeight, true));
        } catch (final Exception ex) {
            Log.e(TAG, "Failed to resize and set seekbar preview thumbnail", ex);
            currentSeekbarPreviewThumbnail.setVisibility(View.GONE);
        } finally {
            srcBitmap.recycle();
        }
    }
}
