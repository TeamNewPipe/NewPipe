package org.schabi.newpipe.player.seekbarpreview;

import android.graphics.Bitmap;
import android.view.View;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Listener for synchronously downloading of an image/bitmap.
 */
public class SyncImageLoadingListener extends SimpleImageLoadingListener {

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private Bitmap bitmap;
    private boolean cancelled = false;
    private FailReason failReason = null;

    @SuppressWarnings("checkstyle:HiddenField")
    @Override
    public void onLoadingFailed(
            final String imageUri,
            final View view,
            final FailReason failReason) {

        this.failReason = failReason;
        countDownLatch.countDown();
    }

    @Override
    public void onLoadingComplete(
            final String imageUri,
            final View view,
            final Bitmap loadedImage) {

        bitmap = loadedImage;
        countDownLatch.countDown();
    }

    @Override
    public void onLoadingCancelled(final String imageUri, final View view) {
        cancelled = true;
        countDownLatch.countDown();
    }

    public Bitmap waitForBitmapOrThrow(final long timeout, final TimeUnit timeUnit)
            throws InterruptedException, TimeoutException {

        // Wait for the download to finish
        if (!countDownLatch.await(timeout, timeUnit)) {
            throw new TimeoutException("Couldn't get the image in time");
        }

        if (isCancelled()) {
            throw new CancellationException("Download of image was cancelled");
        }

        if (getFailReason() != null) {
            throw new RuntimeException("Failed to download image" + getFailReason().getType(),
                    getFailReason().getCause());
        }

        if (getBitmap() == null) {
            throw new NullPointerException("Bitmap is null");
        }

        return getBitmap();
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public FailReason getFailReason() {
        return failReason;
    }
}
