package org.schabi.newpipe.player.seekbarpreview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.collection.SparseArrayCompat
import com.google.common.base.Stopwatch
import java.util.Comparator
import java.util.Optional
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.schabi.newpipe.App
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.util.image.CoilHelper

class SeekbarPreviewThumbnailHolder {

    companion object {
        // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
        // or it fails with an IllegalArgumentException
        // https://stackoverflow.com/a/54744028
        const val TAG = "SeekbarPrevThumbHolder"
    }

    // Key = Position of the picture in milliseconds
    // Supplier = Supplies the bitmap for that position
    private val seekbarPreviewData = SparseArrayCompat<() -> Bitmap?>()

    // This ensures that if the reset is still undergoing
    // and another reset starts, only the last reset is processed
    private var currentUpdateRequestIdentifier = UUID.randomUUID()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var resetJob: Job? = null

    fun resetFrom(context: Context, framesets: List<Frameset>) {
        val seekbarPreviewType = SeekbarPreviewThumbnailHelper.getSeekbarPreviewThumbnailType(context)

        val updateRequestIdentifier = UUID.randomUUID()
        currentUpdateRequestIdentifier = updateRequestIdentifier

        resetJob?.cancel()
        resetJob = scope.launch {
            try {
                resetFromAsync(seekbarPreviewType, framesets, updateRequestIdentifier)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to execute async", ex)
            }
        }
    }

    private suspend fun resetFromAsync(
        seekbarPreviewType: Int,
        framesets: List<Frameset>,
        updateRequestIdentifier: UUID
    ) {
        Log.d(TAG, "Clearing seekbarPreviewData")
        synchronized(seekbarPreviewData) {
            seekbarPreviewData.clear()
        }

        if (seekbarPreviewType == SeekbarPreviewThumbnailHelper.NONE) {
            Log.d(TAG, "Not processing seekbarPreviewData due to settings")
            return
        }

        val frameset = getFrameSetForType(framesets, seekbarPreviewType)
        if (frameset == null) {
            Log.d(TAG, "No frameset was found to fill seekbarPreviewData")
            return
        }

        Log.d(
            TAG,
            "Frameset quality info: " +
                "[width=" + frameset.frameWidth +
                ", heigh=" + frameset.frameHeight + "]"
        )

        // Abort method execution if we are not the latest request
        if (!isRequestIdentifierCurrent(updateRequestIdentifier)) {
            return
        }

        generateDataFrom(frameset, updateRequestIdentifier)
    }

    private fun getFrameSetForType(
        framesets: List<Frameset>,
        seekbarPreviewType: Int
    ): Frameset? {
        return if (seekbarPreviewType == SeekbarPreviewThumbnailHelper.HIGH_QUALITY) {
            Log.d(TAG, "Strategy for seekbarPreviewData: high quality")
            framesets.stream()
                .max(Comparator.comparingInt { fs: Frameset -> fs.frameHeight * fs.frameWidth })
                .orElse(null)
        } else {
            Log.d(TAG, "Strategy for seekbarPreviewData: low quality")
            framesets.stream()
                .min(Comparator.comparingInt { fs: Frameset -> fs.frameHeight * fs.frameWidth })
                .orElse(null)
        }
    }

    private suspend fun generateDataFrom(frameset: Frameset, updateRequestIdentifier: UUID) {
        Log.d(TAG, "Starting generation of seekbarPreviewData")
        val sw = if (Log.isLoggable(TAG, Log.DEBUG)) Stopwatch.createStarted() else null

        var currentPosMs = 0
        var pos = 1

        val urlFrameCount = frameset.framesPerPageX * frameset.framesPerPageY

        // Process each url in the frameset
        for (url in frameset.urls) {
            yield() // Check for cancellation
            // get the bitmap
            val srcBitMap = getBitMapFrom(url)

            // The data is not added directly to "seekbarPreviewData" due to
            // concurrency and checks for "updateRequestIdentifier"
            val generatedDataForUrl = SparseArrayCompat<() -> Bitmap?>(urlFrameCount)

            // The bitmap consists of several images, which we process here
            // foreach frame in the returned bitmap
            for (i in 0 until urlFrameCount) {
                // Frames outside the video length are skipped
                if (pos > frameset.totalCount) {
                    break
                }

                // Get the bounds where the frame is found
                val bounds = frameset.getFrameBoundsAt(currentPosMs.toLong())
                generatedDataForUrl.put(
                    currentPosMs,
                    createBitmapSupplier(srcBitMap, bounds, frameset)
                )

                currentPosMs += frameset.durationPerFrame
                pos++
            }

            // Check if we are still the latest request
            // If not abort method execution
            if (isRequestIdentifierCurrent(updateRequestIdentifier)) {
                synchronized(seekbarPreviewData) {
                    seekbarPreviewData.putAll(generatedDataForUrl)
                }
            } else {
                Log.d(TAG, "Aborted of generation of seekbarPreviewData")
                break
            }
        }

        sw?.let {
            Log.d(TAG, "Generation of seekbarPreviewData took " + it.stop())
        }
    }

    private fun createBitmapSupplier(
        srcBitMap: Bitmap?,
        bounds: IntArray,
        frameset: Frameset
    ): () -> Bitmap? {
        return {
            // It can happen, that the original bitmap could not be downloaded
            // (or it was recycled though that should not happen)
            // In such a case - we don't want a NullPointer/
            // "cannot use a recycled source in createBitmap" Exception -> simply return null
            if (srcBitMap == null || srcBitMap.isRecycled) {
                null
            } else {
                // Under some rare circumstances the YouTube API returns slightly too small storyboards,
                // (or not the matching frame width/height)
                // This would lead to createBitmap cutting out a bitmap that is out of bounds,
                // so we need to adjust the bounds accordingly
                if (srcBitMap.width < bounds[1] + frameset.frameWidth) {
                    bounds[1] = srcBitMap.width - frameset.frameWidth
                }

                if (srcBitMap.height < bounds[2] + frameset.frameHeight) {
                    bounds[2] = srcBitMap.height - frameset.frameHeight
                }

                // Cut out the corresponding bitmap form the "srcBitMap"
                val cutOutBitmap = Bitmap.createBitmap(
                    srcBitMap,
                    bounds[1],
                    bounds[2],
                    frameset.frameWidth,
                    frameset.frameHeight
                )

                // If the cut out bitmap is identical to its source,
                // we need to copy the bitmap to create a new instance.
                // createBitmap allows itself to return the original object that is was created with
                // this leads to recycled bitmaps being returned (if they are identical)
                // Reference: https://stackoverflow.com/a/23683075 + first comment
                // Fixes: https://github.com/TeamNewPipe/NewPipe/issues/11461
                if (cutOutBitmap === srcBitMap) {
                    cutOutBitmap.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    cutOutBitmap
                }
            }
        }
    }

    private suspend fun getBitMapFrom(url: String?): Bitmap? {
        if (url == null) {
            Log.w(TAG, "url is null; This should never happen")
            return null
        }

        val sw = if (Log.isLoggable(TAG, Log.DEBUG)) Stopwatch.createStarted() else null
        return try {
            Log.d(TAG, "Downloading bitmap for seekbarPreview from '$url'")

            // Gets the bitmap within the timeout of 15 seconds imposed by default by OkHttpClient
            // Ensure that you are not running on the main thread, otherwise this will hang
            val bitmap = withContext(Dispatchers.IO) {
                CoilHelper.loadBitmapBlocking(App.instance, url)
            }

            sw?.let {
                Log.d(TAG, "Download of bitmap for seekbarPreview from '$url' took " + it.stop())
            }

            bitmap
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to get bitmap for seekbarPreview from url='$url' in time", ex)
            null
        }
    }

    private fun isRequestIdentifierCurrent(requestIdentifier: UUID): Boolean {
        return currentUpdateRequestIdentifier == requestIdentifier
    }

    fun getBitmapAt(positionInMs: Int): Optional<Bitmap> {
        // Get the frame supplier closest to the requested position
        var closestFrame: () -> Bitmap? = { null }
        synchronized(seekbarPreviewData) {
            var min = Int.MAX_VALUE
            for (i in 0 until seekbarPreviewData.size()) {
                val pos = Math.abs(seekbarPreviewData.keyAt(i) - positionInMs)
                if (pos < min) {
                    closestFrame = seekbarPreviewData.valueAt(i)
                    min = pos
                }
            }
        }

        return Optional.ofNullable(closestFrame())
    }

    fun destroy() {
        resetJob?.cancel()
    }
}
