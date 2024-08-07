package org.schabi.newpipe.util.image

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.executeBlocking
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Size
import coil.target.Target
import coil.transform.Transformation
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.ktx.scale
import kotlin.math.min

object CoilHelper {
    private val TAG = CoilHelper::class.java.simpleName

    @JvmOverloads
    fun loadBitmapBlocking(
        context: Context,
        url: String?,
        @DrawableRes placeholderResId: Int = 0
    ): Bitmap? {
        val request = getImageRequest(context, url, placeholderResId).build()
        return context.imageLoader.executeBlocking(request).drawable?.toBitmapOrNull()
    }

    fun loadAvatar(target: ImageView, images: List<Image>) {
        loadImageDefault(target, images, R.drawable.placeholder_person)
    }

    fun loadAvatar(target: ImageView, url: String?) {
        loadImageDefault(target, url, R.drawable.placeholder_person)
    }

    fun loadThumbnail(target: ImageView, images: List<Image>) {
        loadImageDefault(target, images, R.drawable.placeholder_thumbnail_video)
    }

    fun loadThumbnail(target: ImageView, url: String?) {
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_video)
    }

    fun loadScaledDownThumbnail(context: Context, images: List<Image>, target: Target): Disposable {
        val url = ImageStrategy.choosePreferredImage(images)
        val request = getImageRequest(context, url, R.drawable.placeholder_thumbnail_video)
            .target(target)
            .transformations(object : Transformation {
                override val cacheKey = "COIL_PLAYER_THUMBNAIL_TRANSFORMATION_KEY"

                override suspend fun transform(input: Bitmap, size: Size): Bitmap {
                    if (MainActivity.DEBUG) {
                        Log.d(TAG, "Thumbnail - transform() called")
                    }

                    val notificationThumbnailWidth = min(
                        context.resources.getDimension(R.dimen.player_notification_thumbnail_width),
                        input.width.toFloat()
                    ).toInt()

                    var newHeight = input.height / (input.width / notificationThumbnailWidth)
                    val result = input.scale(notificationThumbnailWidth, newHeight)

                    return if (result == input || !result.isMutable) {
                        // create a new mutable bitmap to prevent strange crashes on some
                        // devices (see #4638)
                        newHeight = input.height / (input.width / (notificationThumbnailWidth - 1))
                        input.scale(notificationThumbnailWidth, newHeight)
                    } else {
                        result
                    }
                }
            })
            .build()

        return context.imageLoader.enqueue(request)
    }

    fun loadDetailsThumbnail(target: ImageView, images: List<Image>) {
        val url = ImageStrategy.choosePreferredImage(images)
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_video, false)
    }

    fun loadBanner(target: ImageView, images: List<Image>) {
        loadImageDefault(target, images, R.drawable.placeholder_channel_banner)
    }

    fun loadPlaylistThumbnail(target: ImageView, images: List<Image>) {
        loadImageDefault(target, images, R.drawable.placeholder_thumbnail_playlist)
    }

    fun loadPlaylistThumbnail(target: ImageView, url: String?) {
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_playlist)
    }

    private fun loadImageDefault(
        target: ImageView,
        images: List<Image>,
        @DrawableRes placeholderResId: Int
    ) {
        loadImageDefault(target, ImageStrategy.choosePreferredImage(images), placeholderResId)
    }

    private fun loadImageDefault(
        target: ImageView,
        url: String?,
        @DrawableRes placeholderResId: Int,
        showPlaceholder: Boolean = true
    ) {
        val request = getImageRequest(target.context, url, placeholderResId, showPlaceholder)
            .target(target)
            .build()
        target.context.imageLoader.enqueue(request)
    }

    private fun getImageRequest(
        context: Context,
        url: String?,
        @DrawableRes placeholderResId: Int,
        showPlaceholderWhileLoading: Boolean = true
    ): ImageRequest.Builder {
        // if the URL was chosen with `choosePreferredImage` it will be null, but check again
        // `shouldLoadImages` in case the URL was chosen with `imageListToDbUrl` (which is the case
        // for URLs stored in the database)
        val takenUrl = url?.takeIf { it.isNotEmpty() && ImageStrategy.shouldLoadImages() }

        return ImageRequest.Builder(context)
            .data(takenUrl)
            .error(placeholderResId)
            .memoryCacheKey(takenUrl)
            .diskCacheKey(takenUrl)
            .apply {
                if (takenUrl != null || showPlaceholderWhileLoading) {
                    placeholder(placeholderResId)
                }
            }
    }
}
