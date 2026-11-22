package org.schabi.newpipe.player.seekbarpreview;

import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType;
import static org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper.getSeekbarPreviewThumbnailType;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.Frameset;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import coil3.request.Disposable;

/** Coordinates asynchronous storyboard requests from the main thread. */
public class SeekbarPreviewThumbnailHolder {
    private static final int CACHE_BYTES = 8 * 1024 * 1024;
    private final LruCache<Integer, Bitmap> pages = new LruCache<>(CACHE_BYTES) {
        @Override
        protected int sizeOf(final Integer key, final Bitmap bitmap) {
            // Bound both memory (8 MiB) and the number of retained pages (at most three).
            return Math.max(CACHE_BYTES / 3, bitmap.getAllocationByteCount());
        }
    };
    private final BitmapLoader loader;
    private Context context;
    private Frameset frameset;
    private List<Frameset> fallbackFramesets = List.of();
    private int activeFramesetIndex;
    private Disposable request;
    private long generation;
    private int pendingPage = -1;
    private int positionMs;
    private Consumer<Bitmap> consumer;

    public SeekbarPreviewThumbnailHolder() {
        this((ctx, url, callback) ->
                CoilHelper.INSTANCE.loadSeekbarPreview(ctx, url, callback));
    }

    SeekbarPreviewThumbnailHolder(final BitmapLoader loader) {
        this.loader = loader;
    }

    public void resetFrom(@NonNull final Context newContext, final List<Frameset> framesets) {
        resetFrom(newContext, framesets, getSeekbarPreviewThumbnailType(newContext));
    }

    void resetFrom(final Context newContext, final List<Frameset> framesets, final int type) {
        clear();
        context = newContext.getApplicationContext();
        if (type != SeekbarPreviewThumbnailType.NONE) {
            final Comparator<Frameset> quality = Comparator.comparingLong(
                    fs -> (long) fs.getFrameHeight() * fs.getFrameWidth());
            fallbackFramesets = framesets.stream()
                    .filter(SeekbarPreviewThumbnailHolder::isValid)
                    .sorted(type == SeekbarPreviewThumbnailType.HIGH_QUALITY
                            ? quality.reversed() : quality)
                    .toList();
            if (!fallbackFramesets.isEmpty()) {
                frameset = fallbackFramesets.get(0);
            }
        }
    }

    private static boolean isValid(final Frameset value) {
        return value.getDurationPerFrame() > 0 && value.getTotalCount() > 0
                && value.getFrameWidth() > 0 && value.getFrameHeight() > 0
                && value.getFramesPerPageX() > 0 && value.getFramesPerPageY() > 0
                && !value.getUrls().isEmpty();
    }

    /**
     * Supplies a fresh cropped bitmap; the receiver owns and may recycle it.
     * @param positionInMs the requested seek position
     * @param callback receives the crop, or null while unavailable, on the main thread
     */
    public void requestBitmapAt(final int positionInMs, final Consumer<Bitmap> callback) {
        if (frameset == null) {
            callback.accept(null);
            return;
        }
        positionMs = Math.max(0, positionInMs);
        final int page = boundsAt(positionMs)[0];
        if (page >= frameset.getUrls().size()) {
            cancelRequest();
            callback.accept(null);
            return;
        }
        final Bitmap cached = pages.get(page);
        if (cached != null) {
            cancelRequest();
            callback.accept(crop(cached, positionMs));
            return;
        }
        consumer = callback;
        if (page == pendingPage) {
            return; // Keep one in-flight request, delivering the latest position on this page.
        }
        cancelRequest();
        consumer = callback;
        pendingPage = page;
        final long token = generation;
        callback.accept(null);
        request = loader.load(context, frameset.getUrls().get(page), bitmap -> {
            if (token != generation) {
                return;
            }
            pendingPage = -1;
            request = null;
            final Bitmap crop = crop(bitmap, positionMs);
            if (crop == null && activeFramesetIndex + 1 < fallbackFramesets.size()) {
                // A storyboard page may be blocked, empty or malformed even when metadata
                // exists. Try the next advertised quality level without re-extracting.
                final Consumer<Bitmap> receiver = consumer;
                activeFramesetIndex++;
                frameset = fallbackFramesets.get(activeFramesetIndex);
                pages.evictAll();
                if (receiver != null) {
                    requestBitmapAt(positionMs, receiver);
                }
                return;
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                pages.put(page, bitmap);
            }
            final Consumer<Bitmap> receiver = consumer;
            consumer = null;
            if (receiver != null) {
                receiver.accept(crop);
            } else if (crop != null) {
                crop.recycle();
            }
        });
    }

    private int[] boundsAt(final int position) {
        // Frameset's endpoint is inclusive; clamp to the last actual frame instead.
        final long lastFrameMs = (long) (frameset.getTotalCount() - 1)
                * frameset.getDurationPerFrame();
        return frameset.getFrameBoundsAt(Math.min(position, lastFrameMs));
    }

    @Nullable
    private Bitmap crop(@Nullable final Bitmap bitmap, final int position) {
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() < frameset.getFrameWidth()
                || bitmap.getHeight() < frameset.getFrameHeight()) {
            return null;
        }
        final int[] bounds = boundsAt(position);
        final int left = Math.min(bounds[1], bitmap.getWidth() - frameset.getFrameWidth());
        final int top = Math.min(bounds[2], bitmap.getHeight() - frameset.getFrameHeight());
        final Bitmap result = Bitmap.createBitmap(bitmap, left, top,
                frameset.getFrameWidth(), frameset.getFrameHeight());
        // The UI recycles its crop. Never give it a bitmap owned by Coil or this cache.
        return result == bitmap ? result.copy(Bitmap.Config.ARGB_8888, false) : result;
    }

    public void cancelRequest() {
        generation++;
        pendingPage = -1;
        consumer = null;
        if (request != null) {
            request.dispose();
            request = null;
        }
    }

    public void clear() {
        cancelRequest();
        frameset = null;
        fallbackFramesets = List.of();
        activeFramesetIndex = 0;
        context = null;
        // Coil owns the page bitmaps. Drop references without recycling shared images.
        pages.evictAll();
    }

    interface BitmapLoader {
        Disposable load(Context context, String url, Consumer<Bitmap> callback);
    }
}
