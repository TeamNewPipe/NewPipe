package org.schabi.newpipe.player.seekbarpreview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.annotation.IntDef
import androidx.core.graphics.BitmapCompat
import androidx.core.math.MathUtils
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils
import java.util.function.IntSupplier

/**
 * Helper for the seekbar preview.
 */
object SeekbarPreviewThumbnailHelper {
    // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
    // or it fails with an IllegalArgumentException
    // https://stackoverflow.com/a/54744028
    val TAG: String = "SeekbarPrevThumbHelper"

    ////////////////////////////////////////////////////////////////////////////
    // Settings Resolution
    ///////////////////////////////////////////////////////////////////////////
    @SeekbarPreviewThumbnailType
    fun getSeekbarPreviewThumbnailType(context: Context): Int {
        val type: String? = PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getString(R.string.seekbar_preview_thumbnail_key), "")
        if ((type == context.getString(R.string.seekbar_preview_thumbnail_none))) {
            return SeekbarPreviewThumbnailType.NONE
        } else if ((type == context.getString(R.string.seekbar_preview_thumbnail_low_quality))) {
            return SeekbarPreviewThumbnailType.LOW_QUALITY
        } else {
            return SeekbarPreviewThumbnailType.HIGH_QUALITY // default
        }
    }

    fun tryResizeAndSetSeekbarPreviewThumbnail(
            context: Context,
            previewThumbnail: Bitmap?,
            currentSeekbarPreviewThumbnail: ImageView,
            baseViewWidthSupplier: IntSupplier) {
        if (previewThumbnail == null) {
            currentSeekbarPreviewThumbnail.setVisibility(View.GONE)
            return
        }
        currentSeekbarPreviewThumbnail.setVisibility(View.VISIBLE)

        // Resize original bitmap
        try {
            val srcWidth: Int = if (previewThumbnail.getWidth() > 0) previewThumbnail.getWidth() else 1
            val newWidth: Int = MathUtils.clamp( // Use 1/4 of the width for the preview
                    Math.round(baseViewWidthSupplier.getAsInt() / 4f),  // But have a min width of 10dp
                    DeviceUtils.dpToPx(10, context),  // And scaling more than that factor looks really pixelated -> max
                    Math.round(srcWidth * 2.5f))
            val scaleFactor: Float = newWidth.toFloat() / srcWidth
            val newHeight: Int = (previewThumbnail.getHeight() * scaleFactor).toInt()
            currentSeekbarPreviewThumbnail.setImageBitmap(BitmapCompat
                    .createScaledBitmap(previewThumbnail, newWidth, newHeight, null, true))
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to resize and set seekbar preview thumbnail", ex)
            currentSeekbarPreviewThumbnail.setVisibility(View.GONE)
        } finally {
            previewThumbnail.recycle()
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef([SeekbarPreviewThumbnailType.HIGH_QUALITY, SeekbarPreviewThumbnailType.LOW_QUALITY, SeekbarPreviewThumbnailType.NONE])
    annotation class SeekbarPreviewThumbnailType() {
        companion object {
            val HIGH_QUALITY: Int = 0
            val LOW_QUALITY: Int = 1
            val NONE: Int = 2
        }
    }
}
