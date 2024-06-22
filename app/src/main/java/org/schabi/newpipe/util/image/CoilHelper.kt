package org.schabi.newpipe.util.image

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.executeBlocking
import coil.imageLoader
import coil.request.ImageRequest
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image

object CoilHelper {
    fun loadBitmap(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()
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
            .apply {
                if (takenUrl != null || showPlaceholderWhileLoading) {
                    placeholder(placeholderResId)
                }
            }
    }
}
