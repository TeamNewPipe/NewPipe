package org.schabi.newpipe.fragments.list.feed;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.InfoCache;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * The "What's New" feed.
 * <p>
 *     It displays the newest streams of every subscribed channel.
 * </p>
 */
public class FeedFragment extends BaseListFragment<FeedInfo, Void> {

    static final boolean DEBUG = MainActivity.DEBUG;

    private static final int OFF_SCREEN_ITEMS_COUNT = 3;
    private static final int MIN_ITEMS_INITIAL_LOAD = 8;

    private int feedLoadCount = MIN_ITEMS_INITIAL_LOAD;

    private FeedSubscriptionService feedSubscriptionService = FeedSubscriptionService.getInstance();
    private Disposable subscriptionsDisposable;

    private FeedItemsSubscriber feedItemsSubscriber;
    private FeedInfo currentFeedInfo;
    private AtomicReference<FeedInfo> newAvailableFeedInfo = new AtomicReference<>();

    private View refreshButton;
    private TextView refreshText;
    private ImageView refreshIcon;
    private Animation refreshRotation;

    private FeedInfoCache feedInfoCache;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, Bundle savedInstanceState) {
        feedLoadCount = howManyItemsToLoad();
        setRetainInstance(true);
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        delayHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateViewState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposeEverything();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(R.string.fragment_whats_new);

            if (useAsFrontPage) {
                supportActionBar.setDisplayShowTitleEnabled(true);
            }
        }
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        refreshButton = rootView.findViewById(R.id.refreshButton);
        refreshText = rootView.findViewById(R.id.refreshText);
        refreshIcon = rootView.findViewById(R.id.refreshIcon);

        refreshRotation = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        refreshRotation.setRepeatCount(Animation.INFINITE);
        refreshRotation.setDuration(1200);
        refreshRotation.setInterpolator(new LinearInterpolator());
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        refreshButton.setOnClickListener(v -> {
            FeedInfo newFeedInfo = newAvailableFeedInfo.getAndSet(null);
            if (newFeedInfo != null) {
                displayFeedInfo(newFeedInfo);
            } else if (!feedSubscriptionService.areSubscriptionsBeingLoaded()) {
                // Clear the cache in order to download new items.
                InfoCache.getInstance().clearCache();

                disposeFeedSubscription();
                startLoading(true);
            }

            updateViewState();
        });
    }

    @Override
    public void reloadContent() {
        resetFragment();
        super.reloadContent();
        hideLoading();
        refreshButton.setVisibility(View.VISIBLE);
        updateViewState();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // StateSaving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(currentFeedInfo);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        currentFeedInfo = (FeedInfo) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Loader
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void startLoading(boolean forceLoad) {
        if (DEBUG) Log.d(TAG, "startLoading(forceLoad = " + forceLoad + ")");

        if (feedInfoCache == null) {
            feedInfoCache = new FeedInfoCache(activity);

            FeedInfo cachedFeedInfo = feedInfoCache.read();
            if (cachedFeedInfo != null) {
                displayFeedInfo(cachedFeedInfo);
                feedSubscriptionService.setBaseFeedInfo(cachedFeedInfo);
            }
        }

        if (subscriptionsDisposable == null) {
            subscriptionsDisposable = feedSubscriptionService.getFeedInfoObservable()
                    .subscribe(this::handleResult, this::onError);
        }
    }

    @Override
    public void handleResult(@NonNull FeedInfo result) {
        if (DEBUG) Log.d(TAG, "handleResult(result = " + result + ")");

        if (userHasNotViewedItems()) {
            displayFeedInfo(result);
        } else if (result.isNewerThan(currentFeedInfo)) {
            newAvailableFeedInfo.set(result);
            feedSubscriptionService.setBaseFeedInfo(result);
        } else {
            // The streams did not change, but the date did.
            currentFeedInfo = result;
        }

        updateViewState();
        feedInfoCache.store(result);
    }

    private boolean userHasNotViewedItems() {
        return feedItemsSubscriber == null
                || !feedItemsSubscriber.haveItemsBeenRequested();
    }

    private void displayFeedInfo(FeedInfo feedInfo) {
        if (DEBUG) Log.d(TAG, "displayFeedInfo(feedInfo = " + feedInfo + ")");

        if (feedItemsSubscriber != null) {
            feedItemsSubscriber.dispose();
        }

        infoListAdapter.clearStreamItemList();
        currentFeedInfo = feedInfo;

        feedItemsSubscriber = new FeedItemsSubscriber(this, infoListAdapter);
        Flowable.fromIterable(feedInfo.getInfoItems())
                .observeOn(AndroidSchedulers.mainThread())
                .buffer(feedLoadCount)
                .subscribe(feedItemsSubscriber);
    }

    @Override
    protected void loadMoreItems() {
        if (DEBUG) Log.d(TAG, "loadMoreItems()");
        delayHandler.removeCallbacksAndMessages(null);

        // Add a little of a delay when requesting more items because the cache is so fast,
        // that the view seems stuck to the user when he scroll to the bottom
        delayHandler.postDelayed(this::requestFeed, 300);
    }

    @Override
    protected boolean hasMoreItems() {
        return feedItemsSubscriber != null && feedItemsSubscriber.areMoreItemsAvailable();
    }

    private final Handler delayHandler = new Handler();

    private void requestFeed() {
        if (DEBUG) Log.d(TAG, "requestFeed(); feedItemsSubscriber = " + feedItemsSubscriber);
        if (feedItemsSubscriber == null) return;

        delayHandler.removeCallbacksAndMessages(null);
        feedItemsSubscriber.requestNext();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Fragment View State
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Handles updating the state of all visual elements of the feed in one place.
     * Called whenever the state may have changed.
     */
    void updateViewState() {
        if (DEBUG) Log.d(TAG, "updateViewState()");

        updateRefreshText();
        updateRefreshAnimation();
        updateLoadingSpinner();
    }

    private void updateRefreshText() {
        if (newAvailableFeedInfo.get() != null) {
            refreshText.setText(R.string.feed_new_items_available);
        } else if (currentFeedInfo != null) {
            String updateTime = DateUtils.getRelativeDateTimeString(activity,
                    currentFeedInfo.getLastUpdated().getTimeInMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString();
            refreshText.setText(getString(R.string.feed_last_updated, updateTime));
        }
    }

    private void updateRefreshAnimation() {
        if (subscriptionsDisposable == null
                || feedSubscriptionService.areSubscriptionsBeingLoaded()) {
            refreshIcon.startAnimation(refreshRotation);
        } else {
            refreshIcon.clearAnimation();
        }
    }

    private void updateLoadingSpinner() {
        if (subscriptionsDisposable == null) {
            // Do not change the loading state when the feed is not subscribed to any items
            // (e.g. if the error panel is shown because this would also hide the panel).
            return;
        }

        if (feedItemsSubscriber == null || feedItemsSubscriber.areMoreItemsAvailable()) {
            showListFooter(true);
            showLoading();
        } else {
            showListFooter(false);
            hideLoading();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void resetFragment() {
        if (DEBUG) Log.d(TAG, "resetFragment()");
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();
        delayHandler.removeCallbacksAndMessages(null);
        disposeEverything();
    }

    private void disposeEverything() {
        disposeFeedSubscription();

        if (feedItemsSubscriber != null) {
            feedItemsSubscriber.dispose();
            feedItemsSubscriber = null;
        }
    }

    private void disposeFeedSubscription() {
        if (subscriptionsDisposable != null) {
            subscriptionsDisposable.dispose();
            subscriptionsDisposable = null;
        }
    }

    private int howManyItemsToLoad() {
        int heightPixels = getResources().getDisplayMetrics().heightPixels;
        int itemHeightPixels = activity.getResources().getDimensionPixelSize(R.dimen.video_item_search_height);

        int nrItemsToLoad;
        if (itemHeightPixels > 0) {
            nrItemsToLoad = heightPixels / itemHeightPixels + OFF_SCREEN_ITEMS_COUNT;
        } else {
            nrItemsToLoad = MIN_ITEMS_INITIAL_LOAD;
        }

        return Math.max(MIN_ITEMS_INITIAL_LOAD, nrItemsToLoad);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showError(String message, boolean showRetryButton) {
        resetFragment();
        refreshButton.setVisibility(View.GONE);
        super.showError(message, showRetryButton);
    }

    @Override
    protected boolean onError(Throwable exception) {
        int errorId = exception instanceof ExtractionException ? R.string.parsing_error : R.string.general_error;

        // If a network error occurs and some items have already been displayed (e.g. from cache),
        // show a snack-bar error in order not to hide the displayed items.
        if (!infoListAdapter.getItemsList().isEmpty() && exception instanceof IOException) {
            showSnackBarError(exception, UserAction.SOMETHING_ELSE,
                    "none", "Requesting feed", errorId);
            updateViewState();
            return true;
        }

        if (super.onError(exception)) return true;

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE, "none", "Requesting feed", errorId);
        return true;
    }
}
