package org.schabi.newpipe.util.image

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.widget.ImageViewCompat
import coil3.executeBlocking
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.target
import coil3.request.transformations
import coil3.size.Size
import coil3.target.Target
import coil3.toBitmap
import coil3.transform.Transformation
import coil3.util.CoilUtils
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Consumer
import kotlin.math.min
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.ktx.scale
import org.schabi.newpipe.profiles.ProfileManager

object CoilHelper {
    private val TAG = CoilHelper::class.java.simpleName
    private val avatarRequestTokens = Collections.synchronizedMap(WeakHashMap<ImageView, Any>())

    @JvmOverloads
    fun loadBitmapBlocking(
        context: Context,
        url: String?,
        @DrawableRes placeholderResId: Int = 0
    ): Bitmap? = context.imageLoader
        .executeBlocking(getImageRequest(context, url, placeholderResId).build())
        .image
        ?.toBitmap()

    internal fun loadCachedThumbnailBlocking(context: Context, url: String, size: Int): Bitmap? = context.imageLoader
        .executeBlocking(
            getImageRequest(context, url, 0, false)
                .networkCachePolicy(CachePolicy.DISABLED)
                .allowHardware(false)
                .size(size)
                .build()
        ).image?.toBitmap()

    /** Cancellable storyboard request; its target receives results on the main thread. */
    fun loadSeekbarPreview(
        context: Context,
        url: String,
        callback: Consumer<Bitmap?>
    ): Disposable {
        val request =
            getImageRequest(context, url, 0, false)
                .allowHardware(false)
                // The seek-preview holder owns a bounded page cache. Avoid retaining all pages
                // a second time in the shared image loader's memory cache.
                .memoryCachePolicy(CachePolicy.DISABLED)
                .target(
                    onSuccess = { callback.accept(it.toBitmap()) },
                    onError = { callback.accept(null) }
                ).build()
        return context.imageLoader.enqueue(request)
    }

    fun loadAvatar(
        target: ImageView,
        images: List<Image?>?
    ) {
        val candidates = avatarCandidateUrls(images)
        clearAvatar(target)
        val requestToken = Any()
        avatarRequestTokens[target] = requestToken
        loadAvatarCandidate(target, candidates, 0, requestToken)
    }

    fun loadAvatar(
        target: ImageView,
        url: String?
    ) {
        clearAvatar(target)
        loadImageDefault(target, url, R.drawable.placeholder_person)
    }

    /** Loads the direct avatar URL exposed by comment items. */
    fun loadCommentAvatar(
        target: ImageView,
        url: String?
    ) {
        clearAvatar(target)
        loadImageDefault(target, normalizeCommentAvatarUrl(url), R.drawable.placeholder_person)
    }

    fun clearAvatar(target: ImageView) {
        ImageViewCompat.setImageTintList(target, null)
        target.clearColorFilter()
        avatarRequestTokens[target] = Any()
        CoilUtils.dispose(target)
        target.setImageResource(R.drawable.placeholder_person)
    }

    private fun loadAvatarCandidate(
        target: ImageView,
        candidates: List<String>,
        index: Int,
        requestToken: Any
    ) {
        if (avatarRequestTokens[target] !== requestToken) {
            return
        }

        val url = candidates.getOrNull(index)
        if (url == null) {
            target.setImageResource(R.drawable.placeholder_person)
            avatarRequestTokens.remove(target)
            return
        }

        val request =
            getImageRequest(target.context, url, R.drawable.placeholder_person)
                .target(target)
                .listener(
                    onError = { _, _ ->
                        if (avatarRequestTokens[target] === requestToken) {
                            // Let Coil finish detaching the failed ViewTarget request before
                            // attaching the fallback request to the same ImageView.
                            target.post {
                                if (avatarRequestTokens[target] === requestToken) {
                                    loadAvatarCandidate(
                                        target,
                                        candidates,
                                        index + 1,
                                        requestToken
                                    )
                                }
                            }
                        }
                    },
                    onSuccess = { _, _ ->
                        if (avatarRequestTokens[target] === requestToken) {
                            avatarRequestTokens.remove(target)
                        }
                    }
                ).build()
        target.context.imageLoader.enqueue(request)
    }

    fun loadThumbnail(
        target: ImageView,
        images: List<Image>
    ) {
        loadImageDefault(target, images, R.drawable.placeholder_thumbnail_video)
    }

    fun loadThumbnail(
        target: ImageView,
        url: String?
    ) {
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_video)
    }

    fun clearImage(target: ImageView) {
        CoilUtils.dispose(target)
        target.setImageDrawable(null)
    }

    fun loadRadioLogo(target: ImageView, url: String?) {
        val context = target.context
        val request = getImageRequest(context, url, R.drawable.ic_radio)
            .fallback(R.drawable.ic_radio)
            .target(target)
            .build()
        context.imageLoader.enqueue(request)
    }

    fun loadScaledDownThumbnail(
        context: Context,
        images: List<Image>,
        target: Target
    ): Disposable {
        val url = ImageStrategy.choosePreferredImage(images)
        val request =
            getImageRequest(context, url, R.drawable.placeholder_thumbnail_video)
                .target(target)
                .transformations(
                    object : Transformation() {
                        override val cacheKey = "COIL_PLAYER_THUMBNAIL_TRANSFORMATION_KEY"

                        override suspend fun transform(
                            input: Bitmap,
                            size: Size
                        ): Bitmap {
                            if (MainActivity.DEBUG) {
                                Log.d(TAG, "Thumbnail - transform() called")
                            }

                            val notificationThumbnailWidth =
                                min(
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
                    }
                ).build()

        return context.imageLoader.enqueue(request)
    }

    fun loadDetailsThumbnail(
        target: ImageView,
        images: List<Image>
    ) {
        val url = ImageStrategy.choosePreferredImage(images)
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_video, false)
    }

    fun loadBanner(
        target: ImageView,
        images: List<Image>
    ) {
        CoilUtils.dispose(target)
        ImageViewCompat.setImageTintList(target, null)
        target.clearColorFilter()
        target.visibility = View.VISIBLE
        (target.parent as? View)?.visibility = View.VISIBLE
        loadImageDefault(target, images, R.drawable.placeholder_channel_banner)
    }

    fun clearBanner(target: ImageView) {
        CoilUtils.dispose(target)
        target.setImageDrawable(null)
        target.visibility = View.GONE
        (target.parent as? View)?.visibility = View.GONE
    }

    fun loadPlaylistThumbnail(
        target: ImageView,
        images: List<Image>
    ) {
        loadImageDefault(target, images, R.drawable.placeholder_thumbnail_playlist)
    }

    fun loadPlaylistThumbnail(
        target: ImageView,
        url: String?
    ) {
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
        val request =
            getImageRequest(target.context, url, placeholderResId, showPlaceholder)
                .target(target)
                .build()
        target.context.imageLoader.enqueue(request)
    }

    internal fun getImageRequest(
        context: Context,
        url: String?,
        @DrawableRes placeholderResId: Int,
        showPlaceholderWhileLoading: Boolean = true
    ): ImageRequest.Builder {
        // if the URL was chosen with `choosePreferredImage` it will be null, but check again
        // `shouldLoadImages` in case the URL was chosen with `imageListToDbUrl` (which is the case
        // for URLs stored in the database)
        val takenUrl = url?.takeIf { it.isNotEmpty() && ImageStrategy.shouldLoadImages() }
        val pluginHeaders = takenUrl?.let { imageUrl ->
            ServiceList.all().asSequence()
                .filter { it.serviceId >= ServiceList.FIRST_DYNAMIC_PLUGIN_ID }
                .mapNotNull { service ->
                    runCatching { service.getImageRequestHeaders(imageUrl) }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                }.firstOrNull()
        }
        // Authenticated plugin artwork must not reuse another profile's cached response.
        val cacheUrl = if (pluginHeaders != null) {
            "plugin:${ProfileManager.getActiveProfileId(context)}:$takenUrl"
        } else {
            takenUrl
        }

        // Android 10 detail panels use a software layer to prevent text flicker. Any image
        // loaded here can later be reused in that panel, so its cached bitmap must be drawable
        // on a software canvas even when the current target is hardware accelerated.
        val softwareImages = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ||
            org.schabi.newpipe.themes.ThemeChrome.requiresSoftwareImages(context)
        return ImageRequest
            .Builder(context)
            .allowHardware(!softwareImages)
            .data(takenUrl)
            .error(placeholderResId)
            .memoryCacheKey(cacheUrl?.let { if (softwareImages) "wizestream-software-backdrop:$it" else it })
            .diskCacheKey(cacheUrl)
            .apply {
                if (pluginHeaders != null) {
                    val headers = NetworkHeaders.Builder()
                    pluginHeaders.forEach { (name, value) -> headers.set(name, value) }
                    httpHeaders(headers.build())
                }
                if (takenUrl != null || showPlaceholderWhileLoading) {
                    placeholder(placeholderResId)
                }
            }
    }
}

internal fun avatarCandidateUrls(images: List<Image?>?): List<String> = imageCandidateUrls(images)

private fun imageCandidateUrls(images: List<Image?>?): List<String> {
    if (!ImageStrategy.shouldLoadImages() || images.isNullOrEmpty()) {
        return emptyList()
    }

    val validImages = images
        .filterNotNull()
        .filter { image -> normalizedImageUrl(image) != null }
    if (validImages.isEmpty()) {
        return emptyList()
    }

    val preferred = ImageStrategy.choosePreferredImage(validImages)
    return buildList {
        preferred?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        validImages.mapNotNull(::normalizedImageUrl).forEach(::add)
    }.distinct()
}

private fun normalizedImageUrl(image: Image): String? {
    val url: String? = image.url
    return url?.trim()?.takeIf(String::isNotEmpty)
}

internal fun normalizeCommentAvatarUrl(url: String?): String? {
    val normalized = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return when {
        normalized.startsWith("//") -> "https:$normalized"
        normalized.startsWith("https://", ignoreCase = true) -> normalized
        normalized.startsWith("http://", ignoreCase = true) -> normalized
        else -> null
    }
}
