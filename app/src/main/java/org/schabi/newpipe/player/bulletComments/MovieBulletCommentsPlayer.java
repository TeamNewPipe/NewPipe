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
     * @param serviceId Service id.
     * @param url       Url.
     */
    public void setInitialData(final int serviceId, final String url) {
        this.serviceId = serviceId;
        this.url = url;
    }

    public final Duration INTERVAL = Duration.ofMillis(50);
    protected boolean isLoading = false;

    /**
     * Fetch comments and init. Call after setInitialData().
     */
    @SuppressLint("CheckResult")
    public void init() {
        this.bulletCommentsView.clearComments();
        isLoading = true;
        //See also: BaseListInfoFragment.java line 142
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
                                Log.d(TAG, "Got "
                                        + newCommentsInfoItems.length
                                        + " comments."
                                        + this.url);
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
        BulletCommentsInfoItem[] nextCommentsInfoItems;
        //Log.d(TAG, "Showing comments between "+ lastDuration + " and " + drawUntilPosition);
        if(extractor.isDisabled()){
            return ;
        }
        if(extractor!= null && extractor.isLive()){ // have to put all messages we get to the pool as they only appear once
            try {
                nextCommentsInfoItems = extractor.getLiveMessages()
                        .stream().toArray(BulletCommentsInfoItem[]::new);
                if(drawUntilPosition.compareTo(Duration.ofSeconds(Long.MAX_VALUE)) != 0){
                    extractor.setCurrentPlayPosition(drawUntilPosition.toMillis());
                }
            } catch (ParsingException e) {
                throw new RuntimeException(e);
            }
        }else {  // we can filter the messages because we have the full list
            if(drawUntilPosition.toString().equals("PT0.049S")){
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
        bulletCommentsView.drawComments(nextCommentsInfoItems, drawUntilPosition);
        this.lastPosition = drawUntilPosition;
    }

    /**
     * Resume comments. (Avoids drawing massive comments after skipping.)
     *
     * @param currentPosition Current position.
     */
    public void start(final Duration currentPosition) {
        this.lastPosition = currentPosition;
        bulletCommentsView.resumeComments();
    }

    /**
     * Pause comments.
     */
    public void pause() {
        bulletCommentsView.pauseComments();
    }

    /**
     * Clear comments.
     */
    public void clear() {
        bulletCommentsView.clearComments();
    }

    public void disconnect(){
        if(extractor!= null && extractor.isLive()){
            extractor.disconnect();
        }
    }

    /**
     * Draw all comments after max(movieDuration - INTERVAL, lastPosition).
     *
     * @param movieDuration The duration of the movie, used to avoid drawing too many comments.
     */
    public void complete(final Duration movieDuration) {
        if (lastPosition == null) {
            return; //is actually finished
        }
        final Duration minimumLastPosition = movieDuration.minus(INTERVAL);
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
