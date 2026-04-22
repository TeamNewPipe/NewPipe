package org.schabi.newpipe.player.bulletComments;

import android.annotation.SuppressLint;
import android.util.Log;

import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.views.BulletCommentsView;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MovieBulletCommentsPlayer {
    public MovieBulletCommentsPlayer(final BulletCommentsView bulletCommentsView) {
        super();
        this.bulletCommentsView = bulletCommentsView;
    }

    private final String TAG = "MovieBCPlayer";
    protected int serviceId;
    protected String url;
    protected final BulletCommentsView bulletCommentsView;
    protected List<BulletCommentsInfoItem> commentsInfoItems;
    private BulletCommentsExtractor extractor;
    public boolean isRoundPlayStream = false;

    /**
     * Set data. Call before init().
     *
     * @param newServiceId Service id.
     * @param newUrl       Url.
     */
    public void setInitialData(final int newServiceId, final String newUrl) {
        Log.d(TAG, "setInitialData() serviceId=" + newServiceId + " url=" + newUrl);
        this.serviceId = newServiceId;
        this.url = newUrl;
    }

    public final Duration interval = Duration.ofMillis(50);
    protected boolean isLoading = false;

    /**
     * Fetch comments and init. Call after setInitialData().
     */
    @SuppressLint("CheckResult")
    public void init() {
        Log.d(TAG, "init() called for url=" + this.url);
        this.bulletCommentsView.clearComments();
        isLoading = true;
        try {
            ExtractorHelper.getBulletCommentsInfo(this.serviceId, this.url, false)
                    .filter(Objects::nonNull)
                    .map((BulletCommentsInfo commentsInfo) -> {
                                extractor = commentsInfo.getBulletCommentsExtractor();
                                extractor.reconnect();
                                return commentsInfo.getRelatedItems();
                            }
                    )
                    .filter(Objects::nonNull)
                    .map(s -> s.stream().toArray(BulletCommentsInfoItem[]::new))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((BulletCommentsInfoItem[] newCommentsInfoItems) -> {
                                this.commentsInfoItems = Arrays.asList(newCommentsInfoItems);
                                Log.d(TAG, "init() success: got "
                                        + newCommentsInfoItems.length
                                        + " initial comments for " + this.url);
                                if (extractor != null) {
                                    Log.d(TAG, "init() extractor isLive=" + extractor.isLive()
                                            + " isDisabled=" + extractor.isDisabled());
                                }
                                isLoading = false;
                            },
                            throwable -> Log.e(TAG, Log.getStackTraceString(throwable))
                    );
        } catch (final Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    protected Duration lastPosition = Duration.ZERO;

    /**
     * Draw all comments which duration is between last
     * drawUntilPosition and current drawUntilPosition.
     *
     * @param drawUntilPosition Duration to draw comments until.
     */
    public void drawComments(final Duration drawUntilPosition) {
        if (isLoading) {
            return;
        }
        Log.v(TAG, "drawComments() position=" + drawUntilPosition.toMillis()
                + "ms extractor=" + (extractor != null ? "yes" : "null"));
        final BulletCommentsInfoItem[] nextCommentsInfoItems;
        if (extractor.isDisabled()) {
            return;
        }
        if (extractor != null && extractor.isLive()) {
            // have to put all messages we get to the pool as they only appear once
            try {
                nextCommentsInfoItems = extractor.getLiveMessages()
                        .stream().toArray(BulletCommentsInfoItem[]::new);
                if (drawUntilPosition.compareTo(Duration.ofSeconds(Long.MAX_VALUE)) != 0) {
                    extractor.setCurrentPlayPosition(drawUntilPosition.toMillis());
                }
            } catch (final ParsingException e) {
                Log.e(TAG, "drawComments() getLiveMessages failed", e);
                throw new RuntimeException(e);
            }
        } else {  // we can filter the messages because we have the full list
            if (drawUntilPosition.toString().equals("PT0.049S")) {
                return;
            }
            nextCommentsInfoItems = commentsInfoItems
                    .stream()
                    .filter(item -> {
                                final Duration d = item.getDuration();
                                return d.compareTo(lastPosition) >= 0
                                        && d.compareTo(drawUntilPosition) < 0;
                            }
                    )
                    .toArray(BulletCommentsInfoItem[]::new);
        }
        Log.v(TAG, "drawComments() drawing " + nextCommentsInfoItems.length + " comments");
        bulletCommentsView.drawComments(nextCommentsInfoItems, drawUntilPosition);
        this.lastPosition = drawUntilPosition;
    }

    /**
     * Resume comments. (Avoids drawing massive comments after skipping.)
     *
     * @param currentPosition Current position.
     */
    public void start(final Duration currentPosition) {
        Log.d(TAG, "start() position=" + currentPosition.toMillis() + "ms");
        this.lastPosition = currentPosition;
        bulletCommentsView.resumeComments();
    }

    /**
     * Pause comments.
     */
    public void pause() {
        Log.d(TAG, "pause() called");
        bulletCommentsView.pauseComments();
    }

    /**
     * Clear comments.
     */
    public void clear() {
        Log.d(TAG, "clear() called");
        bulletCommentsView.clearComments();
    }

    public void disconnect() {
        Log.d(TAG, "disconnect() called");
        if (extractor != null && extractor.isLive()) {
            extractor.disconnect();
        }
    }

    /**
     * Draw all comments after max(movieDuration - interval, lastPosition).
     *
     * @param movieDuration The duration of the movie, used to avoid drawing too many comments.
     */
    public void complete(final Duration movieDuration) {
        Log.d(TAG, "complete() called");
        if (lastPosition == null) {
            return; //is actually finished
        }
        final Duration minimumLastPosition = movieDuration.minus(interval);
        if (minimumLastPosition.compareTo(lastPosition) >= 0) {
            lastPosition = minimumLastPosition;
        }

        //Show all comments.
        drawComments(Duration.ofSeconds(Long.MAX_VALUE));
    }

    public String getUrl() {
        return url;
    }
}
