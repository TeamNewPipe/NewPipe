package org.schabi.newpipe.player.seekbarpreview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.collection.SparseArrayCompat
import com.google.common.base.Stopwatch
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType
import org.schabi.newpipe.util.image.PicassoHelper
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Supplier
import java.util.function.ToIntFunction
import kotlin.math.abs

class SeekbarPreviewThumbnailHolder() {
    // Key = Position of the picture in milliseconds
    // Supplier = Supplies the bitmap for that position
    private val seekbarPreviewData: SparseArrayCompat<Supplier<Bitmap?>> = SparseArrayCompat()

    // This ensures that if the reset is still undergoing
    // and another reset starts, only the last reset is processed
    private var currentUpdateRequestIdentifier: UUID = UUID.randomUUID()
    fun resetFrom(context: Context, framesets: List<Frameset?>) {
        val seekbarPreviewType: Int = SeekbarPreviewThumbnailHelper.getSeekbarPreviewThumbnailType(context)
        val updateRequestIdentifier: UUID = UUID.randomUUID()
        currentUpdateRequestIdentifier = updateRequestIdentifier
        val executorService: ExecutorService = Executors.newSingleThreadExecutor()
        executorService.submit(Runnable({
            try {
                resetFromAsync(seekbarPreviewType, framesets, updateRequestIdentifier)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to execute async", ex)
            }
        }))
        // ensure that the executorService stops/destroys it's threads
        // after the task is finished
        executorService.shutdown()
    }

    private fun resetFromAsync(seekbarPreviewType: Int, framesets: List<Frameset?>,
                               updateRequestIdentifier: UUID) {
        Log.d(TAG, "Clearing seekbarPreviewData")
        synchronized(seekbarPreviewData, { seekbarPreviewData.clear() })
        if (seekbarPreviewType == SeekbarPreviewThumbnailType.Companion.NONE) {
            Log.d(TAG, "Not processing seekbarPreviewData due to settings")
            return
        }
        val frameset: Frameset? = getFrameSetForType(framesets, seekbarPreviewType)
        if (frameset == null) {
            Log.d(TAG, "No frameset was found to fill seekbarPreviewData")
            return
        }
        Log.d(TAG, ("Frameset quality info: "
                + "[width=" + frameset.getFrameWidth()
                + ", heigh=" + frameset.getFrameHeight() + "]"))

        // Abort method execution if we are not the latest request
        if (!isRequestIdentifierCurrent(updateRequestIdentifier)) {
            return
        }
        generateDataFrom(frameset, updateRequestIdentifier)
    }

    private fun getFrameSetForType(framesets: List<Frameset?>,
                                   seekbarPreviewType: Int): Frameset? {
        if (seekbarPreviewType == SeekbarPreviewThumbnailType.Companion.HIGH_QUALITY) {
            Log.d(TAG, "Strategy for seekbarPreviewData: high quality")
            return framesets.stream()
                    .max(Comparator.comparingInt(ToIntFunction({ fs: Frameset? -> fs!!.getFrameHeight() * fs.getFrameWidth() })))
                    .orElse(null)
        } else {
            Log.d(TAG, "Strategy for seekbarPreviewData: low quality")
            return framesets.stream()
                    .min(Comparator.comparingInt(ToIntFunction({ fs: Frameset? -> fs!!.getFrameHeight() * fs.getFrameWidth() })))
                    .orElse(null)
        }
    }

    private fun generateDataFrom(frameset: Frameset, updateRequestIdentifier: UUID) {
        Log.d(TAG, "Starting generation of seekbarPreviewData")
        val sw: Stopwatch? = if (Log.isLoggable(TAG, Log.DEBUG)) Stopwatch.createStarted() else null
        var currentPosMs: Int = 0
        var pos: Int = 1
        val urlFrameCount: Int = frameset.getFramesPerPageX() * frameset.getFramesPerPageY()

        // Process each url in the frameset
        for (url: String in frameset.getUrls()) {
            // get the bitmap
            val srcBitMap: Bitmap? = getBitMapFrom(url)

            // The data is not added directly to "seekbarPreviewData" due to
            // concurrency and checks for "updateRequestIdentifier"
            val generatedDataForUrl: SparseArrayCompat<Supplier<Bitmap?>> = SparseArrayCompat(urlFrameCount)

            // The bitmap consists of several images, which we process here
            // foreach frame in the returned bitmap
            for (i in 0 until urlFrameCount) {
                // Frames outside the video length are skipped
                if (pos > frameset.getTotalCount()) {
                    break
                }

                // Get the bounds where the frame is found
                val bounds: IntArray = frameset.getFrameBoundsAt(currentPosMs.toLong())
                generatedDataForUrl.put(currentPosMs, Supplier<Bitmap?>({

                    // It can happen, that the original bitmap could not be downloaded
                    // In such a case - we don't want a NullPointer - simply return null
                    if (srcBitMap == null) {
                        return@put null
                    }
                    Bitmap.createBitmap(srcBitMap, bounds.get(1), bounds.get(2),
                            frameset.getFrameWidth(), frameset.getFrameHeight())
                }))
                currentPosMs += frameset.getDurationPerFrame()
                pos++
            }

            // Check if we are still the latest request
            // If not abort method execution
            if (isRequestIdentifierCurrent(updateRequestIdentifier)) {
                synchronized(seekbarPreviewData, { seekbarPreviewData.putAll(generatedDataForUrl) })
            } else {
                Log.d(TAG, "Aborted of generation of seekbarPreviewData")
                break
            }
        }
        if (sw != null) {
            Log.d(TAG, "Generation of seekbarPreviewData took " + sw.stop())
        }
    }

    private fun getBitMapFrom(url: String?): Bitmap? {
        if (url == null) {
            Log.w(TAG, "url is null; This should never happen")
            return null
        }
        val sw: Stopwatch? = if (Log.isLoggable(TAG, Log.DEBUG)) Stopwatch.createStarted() else null
        try {
            Log.d(TAG, "Downloading bitmap for seekbarPreview from '" + url + "'")

            // Gets the bitmap within the timeout of 15 seconds imposed by default by OkHttpClient
            // Ensure that your are not running on the main-Thread this will otherwise hang
            val bitmap: Bitmap = PicassoHelper.loadSeekbarThumbnailPreview(url).get()
            if (sw != null) {
                Log.d(TAG, ("Download of bitmap for seekbarPreview from '" + url + "' took "
                        + sw.stop()))
            }
            return bitmap
        } catch (ex: Exception) {
            Log.w(TAG, ("Failed to get bitmap for seekbarPreview from url='" + url
                    + "' in time"), ex)
            return null
        }
    }

    private fun isRequestIdentifierCurrent(requestIdentifier: UUID): Boolean {
        return (currentUpdateRequestIdentifier == requestIdentifier)
    }

    fun getBitmapAt(positionInMs: Int): Optional<Bitmap?> {
        // Get the frame supplier closest to the requested position
        var closestFrame: Supplier<Bitmap?> = Supplier({ null })
        synchronized(seekbarPreviewData, {
            var min: Int = Int.MAX_VALUE
            for (i in 0 until seekbarPreviewData.size()) {
                val pos: Int = abs((seekbarPreviewData.keyAt(i) - positionInMs).toDouble()).toInt()
                if (pos < min) {
                    closestFrame = seekbarPreviewData.valueAt(i)
                    min = pos
                }
            }
        })
        return Optional.ofNullable(closestFrame.get())
    }

    companion object {
        // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
        // or it fails with an IllegalArgumentException
        // https://stackoverflow.com/a/54744028
        val TAG: String = "SeekbarPrevThumbHolder"
    }
}
