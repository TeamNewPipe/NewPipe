package org.schabi.newpipe.player.seekbarpreview;

import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType;
import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.getSeekbarPreviewThumbnailType;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import com.google.common.base.Stopwatch;

import org.schabi.newpipe.extractor.stream.Frameset;
import org.schabi.newpipe.util.image.PicassoHelper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SeekbarPreviewThumbnailHolder {

    // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
    // or it fails with an IllegalArgumentException
    // https://stackoverflow.com/a/54744028
    public static final String TAG = "SeekbarPrevThumbHolder";

    // Key = Position of the picture in milliseconds
    // Supplier = Supplies the bitmap for that position
    private final SparseArrayCompat<Supplier<Bitmap>> seekbarPreviewData =
            new SparseArrayCompat<>();

    // This ensures that if the reset is still undergoing
    // and another reset starts, only the last reset is processed
    private UUID currentUpdateRequestIdentifier = UUID.randomUUID();

    public void resetFrom(@NonNull final Context context, final List<Frameset> framesets) {
        final int seekbarPreviewType = getSeekbarPreviewThumbnailType(context);

        final UUID updateRequestIdentifier = UUID.randomUUID();
        this.currentUpdateRequestIdentifier = updateRequestIdentifier;

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                resetFromAsync(seekbarPreviewType, framesets, updateRequestIdentifier);
            } catch (final Exception ex) {
                Log.e(TAG, "Failed to execute async", ex);
            }
        });
        // ensure that the executorService stops/destroys it's threads
        // after the task is finished
        executorService.shutdown();
    }

    private void resetFromAsync(final int seekbarPreviewType, final List<Frameset> framesets,
                                final UUID updateRequestIdentifier) {
        Log.d(TAG, "Clearing seekbarPreviewData");
        synchronized (seekbarPreviewData) {
            seekbarPreviewData.clear();
        }

        if (seekbarPreviewType == SeekbarPreviewThumbnailType.NONE) {
            Log.d(TAG, "Not processing seekbarPreviewData due to settings");
            return;
        }

        final Frameset frameset = getFrameSetForType(framesets, seekbarPreviewType);
        if (frameset == null) {
            Log.d(TAG, "No frameset was found to fill seekbarPreviewData");
            return;
        }

        Log.d(TAG, "Frameset quality info: "
                + "[width=" + frameset.getFrameWidth()
                + ", heigh=" + frameset.getFrameHeight() + "]");

        // Abort method execution if we are not the latest request
        if (!isRequestIdentifierCurrent(updateRequestIdentifier)) {
            return;
        }

        generateDataFrom(frameset, updateRequestIdentifier);
    }

    private Frameset getFrameSetForType(final List<Frameset> framesets,
                                        final int seekbarPreviewType) {
        if (seekbarPreviewType == SeekbarPreviewThumbnailType.HIGH_QUALITY) {
            Log.d(TAG, "Strategy for seekbarPreviewData: high quality");
            return framesets.stream()
                    .max(Comparator.comparingInt(fs -> fs.getFrameHeight() * fs.getFrameWidth()))
                    .orElse(null);
        } else {
            Log.d(TAG, "Strategy for seekbarPreviewData: low quality");
            return framesets.stream()
                    .min(Comparator.comparingInt(fs -> fs.getFrameHeight() * fs.getFrameWidth()))
                    .orElse(null);
        }
    }

    private void generateDataFrom(final Frameset frameset, final UUID updateRequestIdentifier) {
        Log.d(TAG, "Starting generation of seekbarPreviewData");
        final Stopwatch sw = Log.isLoggable(TAG, Log.DEBUG) ? Stopwatch.createStarted() : null;

        int currentPosMs = 0;
        int pos = 1;

        final int urlFrameCount = frameset.getFramesPerPageX() * frameset.getFramesPerPageY();

        // Process each url in the frameset
        for (final String url : frameset.getUrls()) {
            // get the bitmap
            final Bitmap srcBitMap = getBitMapFrom(url);

            // The data is not added directly to "seekbarPreviewData" due to
            // concurrency and checks for "updateRequestIdentifier"
            final var generatedDataForUrl = new SparseArrayCompat<Supplier<Bitmap>>(urlFrameCount);

            // The bitmap consists of several images, which we process here
            // foreach frame in the returned bitmap
            for (int i = 0; i < urlFrameCount; i++) {
                // Frames outside the video length are skipped
                if (pos > frameset.getTotalCount()) {
                    break;
                }

                // Get the bounds where the frame is found
                final int[] bounds = frameset.getFrameBoundsAt(currentPosMs);
                generatedDataForUrl.put(currentPosMs,
                                        createBitmapSupplier(srcBitMap, bounds, frameset));

                currentPosMs += frameset.getDurationPerFrame();
                pos++;
            }

            // Check if we are still the latest request
            // If not abort method execution
            if (isRequestIdentifierCurrent(updateRequestIdentifier)) {
                synchronized (seekbarPreviewData) {
                    seekbarPreviewData.putAll(generatedDataForUrl);
                }
            } else {
                Log.d(TAG, "Aborted of generation of seekbarPreviewData");
                break;
            }
        }

        if (sw != null) {
            Log.d(TAG, "Generation of seekbarPreviewData took " + sw.stop());
        }
    }

    private Supplier<Bitmap> createBitmapSupplier(final Bitmap srcBitMap,
                                                  final int[] bounds,
                                                  final Frameset frameset) {
        return () -> {
            // It can happen, that the original bitmap could not be downloaded
            // (or it was recycled though that should not happen)
            // In such a case - we don't want a NullPointer/
            // "cannot use a recycled source in createBitmap" Exception -> simply return null
            if (srcBitMap == null || srcBitMap.isRecycled()) {
                return null;
            }

            // Under some rare circumstances the YouTube API returns slightly too small storyboards,
            // (or not the matching frame width/height)
            // This would lead to createBitmap cutting out a bitmap that is out of bounds,
            // so we need to adjust the bounds accordingly
            if (srcBitMap.getWidth() < bounds[1] + frameset.getFrameWidth()) {
                bounds[1] = srcBitMap.getWidth() - frameset.getFrameWidth();
            }

            if (srcBitMap.getHeight() < bounds[2] + frameset.getFrameHeight()) {
                bounds[2] = srcBitMap.getHeight() - frameset.getFrameHeight();
            }

            // Cut out the corresponding bitmap form the "srcBitMap"
            final Bitmap cutOutBitmap = Bitmap.createBitmap(srcBitMap, bounds[1], bounds[2],
                    frameset.getFrameWidth(), frameset.getFrameHeight());

            // If the cut out bitmap is identical to its source,
            // we need to copy the bitmap to create a new instance.
            // createBitmap allows itself to return the original object that is was created with
            // this leads to recycled bitmaps being returned (if they are identical)
            // Reference: https://stackoverflow.com/a/23683075 + first comment
            // Fixes: https://github.com/TeamNewPipe/NewPipe/issues/11461
            return cutOutBitmap == srcBitMap
                    ? cutOutBitmap.copy(cutOutBitmap.getConfig(), true) : cutOutBitmap;
        };
    }

    @Nullable
    private Bitmap getBitMapFrom(final String url) {
        if (url == null) {
            Log.w(TAG, "url is null; This should never happen");
            return null;
        }

        final Stopwatch sw = Log.isLoggable(TAG, Log.DEBUG) ? Stopwatch.createStarted() : null;
        try {
            Log.d(TAG, "Downloading bitmap for seekbarPreview from '" + url + "'");

            // Gets the bitmap within the timeout of 15 seconds imposed by default by OkHttpClient
            // Ensure that your are not running on the main-Thread this will otherwise hang
            final Bitmap bitmap = PicassoHelper.loadSeekbarThumbnailPreview(url).get();

            if (sw != null) {
                Log.d(TAG, "Download of bitmap for seekbarPreview from '" + url + "' took "
                        + sw.stop());
            }

            return bitmap;
        } catch (final Exception ex) {
            Log.w(TAG, "Failed to get bitmap for seekbarPreview from url='" + url
                    + "' in time", ex);
            return null;
        }
    }

    private boolean isRequestIdentifierCurrent(final UUID requestIdentifier) {
        return this.currentUpdateRequestIdentifier.equals(requestIdentifier);
    }

    public Optional<Bitmap> getBitmapAt(final int positionInMs) {
        // Get the frame supplier closest to the requested position
        Supplier<Bitmap> closestFrame = () -> null;
        synchronized (seekbarPreviewData) {
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < seekbarPreviewData.size(); i++) {
                final int pos = Math.abs(seekbarPreviewData.keyAt(i) - positionInMs);
                if (pos < min) {
                    closestFrame = seekbarPreviewData.valueAt(i);
                    min = pos;
                }
            }
        }

        return Optional.ofNullable(closestFrame.get());
    }
}
