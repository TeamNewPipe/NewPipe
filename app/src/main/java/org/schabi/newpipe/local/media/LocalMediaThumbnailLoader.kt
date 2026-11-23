/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import coil3.util.CoilUtils
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.DevicePerformance
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/** Loads thumbnails plus embedded or fallback artwork from device-local media. */
object LocalMediaThumbnailLoader {
    private const val THUMBNAIL_WIDTH = 512
    private const val THUMBNAIL_HEIGHT = 288
    private const val ARTWORK_MAX_DIMENSION = 512
    private const val MAXIMUM_PENDING_THUMBNAILS = 64

    private val thumbnailExecutor by lazy {
        val threads = DevicePerformance.localThumbnailLoaderThreads(App.instance)
        ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAXIMUM_PENDING_THUMBNAILS),
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
    }
    private val playerExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheLock = Any()
    private val cacheGeneration = AtomicLong()
    private val cacheStorage = lazy {
        object : LruCache<String, Bitmap>(DevicePerformance.localThumbnailCacheBytes(App.instance)) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    }
    private val cache by cacheStorage

    internal fun invalidateCache() = synchronized(cacheLock) {
        cacheGeneration.incrementAndGet()
        if (cacheStorage.isInitialized()) cache.evictAll()
    }

    private fun cached(key: String, generation: Long): Bitmap? = synchronized(cacheLock) {
        if (generation == cacheGeneration.get()) cache[key] else null
    }

    private fun cache(key: String, bitmap: Bitmap, generation: Long) {
        synchronized(cacheLock) {
            if (generation == cacheGeneration.get()) cache.put(key, bitmap)
        }
    }

    @Volatile
    private var audioPlaceholder: Bitmap? = null

    fun interface BitmapCallback {
        fun onLoaded(bitmap: Bitmap?)
    }

    fun load(target: ImageView, item: LocalMediaItem) {
        load(
            target,
            item.contentUri,
            item.mediaStoreId,
            item.isVideo,
            item.mimeType,
            item.thumbnailUri
        )
    }

    fun load(target: ImageView, item: PlayQueueItem) {
        load(
            target,
            item.url,
            item.localMediaId,
            item.streamType == StreamType.VIDEO_STREAM,
            item.mimeType,
            item.localThumbnailUrl
        )
    }

    fun load(target: ImageView, entry: LocalMediaDocumentEntry) {
        load(
            target,
            entry.contentUri,
            -1L,
            entry.isVideo,
            entry.mimeType,
            if (entry.isVideo) entry.contentUri else entry.folderArtworkUri
        )
    }

    /**
     * Loads artwork for the player without retaining an Activity or View reference.
     * The callback is always dispatched on the main thread.
     */
    fun loadBitmap(
        context: Context,
        item: PlayQueueItem,
        callback: BitmapCallback
    ): Future<*> = loadLocalMediaWithRefresh(
        playerExecutor,
        { mainHandler.post(it) },
        cacheGeneration::get,
        { generation ->
            if (ImageStrategy.shouldLoadImages()) {
                loadBitmap(
                    context.applicationContext,
                    item.url,
                    item.localMediaId,
                    item.streamType == StreamType.VIDEO_STREAM,
                    item.mimeType,
                    item.localThumbnailUrl,
                    generation
                )
            } else {
                null
            }
        },
        callback::onLoaded
    )

    @Synchronized
    fun audioPlaceholderBitmap(context: Context): Bitmap? {
        audioPlaceholder?.let { return it }
        val drawable = ContextCompat.getDrawable(
            context.applicationContext,
            R.drawable.placeholder_thumbnail_audio
        ) ?: return null
        val bitmap = Bitmap.createBitmap(
            THUMBNAIL_WIDTH,
            THUMBNAIL_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        audioPlaceholder = bitmap
        return bitmap
    }

    private fun load(
        target: ImageView,
        contentUri: String,
        mediaStoreId: Long,
        isVideo: Boolean,
        mimeType: String?,
        thumbnailUri: String?
    ) {
        clear(target)
        target.setImageResource(
            if (isVideo) {
                R.drawable.placeholder_thumbnail_video
            } else {
                R.drawable.placeholder_thumbnail_audio
            }
        )
        val requestKey = "$isVideo:$contentUri"
        val generation = cacheGeneration.get()
        target.tag = requestKey

        if (!ImageStrategy.shouldLoadImages()) return

        cached(requestKey, generation)?.let {
            target.setImageBitmap(it)
            return
        }

        val context = target.context.applicationContext
        thumbnailExecutor.execute {
            val bitmap = loadBitmap(
                context,
                contentUri,
                mediaStoreId,
                isVideo,
                mimeType,
                thumbnailUri,
                generation
            )
            mainHandler.post {
                if (generation == cacheGeneration.get() && target.tag == requestKey && bitmap != null) target.setImageBitmap(bitmap)
            }
        }
    }

    fun clear(target: ImageView) {
        target.tag = null
        CoilUtils.dispose(target)
        target.setImageResource(R.drawable.placeholder_thumbnail_video)
    }

    @WorkerThread
    private fun loadBitmap(
        context: Context,
        contentUri: String,
        mediaStoreId: Long,
        isVideo: Boolean,
        mimeType: String?,
        thumbnailUri: String?,
        generation: Long
    ): Bitmap? {
        if (generation != cacheGeneration.get()) return null
        val requestKey = "$isVideo:$contentUri"
        cached(requestKey, generation)?.let { return it }
        val artwork = thumbnailUri
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let { CoilHelper.loadCachedThumbnailBlocking(context, it, ARTWORK_MAX_DIMENSION) }
        val resolver = context.contentResolver
        val bitmap = artwork ?: if (isVideo) {
            loadVideoThumbnail(resolver, contentUri, mediaStoreId)
        } else {
            loadEmbeddedArtwork(resolver, contentUri, mimeType)
                ?: loadArtworkUri(resolver, thumbnailUri)
        }
        if (bitmap != null) cache(requestKey, bitmap, generation)
        return bitmap
    }

    @WorkerThread
    private fun loadEmbeddedArtwork(
        resolver: ContentResolver,
        contentUri: String,
        mimeType: String?
    ): Bitmap? {
        val artwork = LocalMediaMetadataLoader.readCached(
            resolver,
            contentUri,
            mimeType
        ).artwork ?: return null
        return decodeArtwork(artwork)
    }

    @WorkerThread
    private fun loadArtworkUri(resolver: ContentResolver, artworkUri: String?): Bitmap? {
        if (artworkUri.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(artworkUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateArtworkSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    ARTWORK_MAX_DIMENSION
                )
            }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeArtwork(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateArtworkSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                ARTWORK_MAX_DIMENSION
            )
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    @WorkerThread
    private fun loadVideoThumbnail(
        resolver: ContentResolver,
        contentUri: String,
        mediaStoreId: Long
    ): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.loadThumbnail(
                    Uri.parse(contentUri),
                    Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    resolver,
                    mediaStoreId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

internal fun calculateArtworkSampleSize(width: Int, height: Int, maximumDimension: Int): Int {
    if (width <= 0 || height <= 0 || maximumDimension <= 0) return 1
    var sampleSize = 1
    val largestDimension = maxOf(width, height)
    while (largestDimension / (sampleSize * 2) >= maximumDimension) {
        sampleSize *= 2
    }
    return sampleSize
}
