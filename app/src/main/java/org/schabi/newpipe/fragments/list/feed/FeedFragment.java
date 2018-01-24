package org.schabi.newpipe.fragments.list.feed;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;
import org.schabi.newpipe.report.UserAction;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * The "What's New" feed.
 * <p>
 *     It displays the newest streams of every subscribed channel.
 * </p>
 */
public class FeedFragment extends BaseListFragment<List<StreamInfoItem>, Void> {

    static final boolean DEBUG = MainActivity.DEBUG;

    private static final int OFF_SCREEN_ITEMS_COUNT = 3;
    private static final int MIN_ITEMS_INITIAL_LOAD = 8;

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private AtomicBoolean areAllItemsDisplayed = new AtomicBoolean(false);
    private HashSet<String> displayedItems = new HashSet<>();

    private int feedLoadCount = MIN_ITEMS_INITIAL_LOAD;

    private SubscriptionsObserver subscriptionsObserver;
    private FeedItemsSubscriber feedItemsSubscriber;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, Bundle savedInstanceState) {
        feedLoadCount = howManyItemsToLoad();
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        delayHandler.removeCallbacksAndMessages(null);
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
    public void reloadContent() {
        resetFragment();
        super.reloadContent();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // StateSaving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(areAllItemsDisplayed);
        objectsToSave.add(displayedItems);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        areAllItemsDisplayed = (AtomicBoolean) savedObjects.poll();
        displayedItems = (HashSet<String>) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Loader
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void startLoading(boolean forceLoad) {
        if (DEBUG) Log.d(TAG, "startLoading(forceLoad = " + forceLoad + ")");

        if (subscriptionsObserver == null) {
            isLoading.set(true);
            showLoading();
            showListFooter(true);

            subscriptionsObserver = new SubscriptionsObserver(this);
            subscriptionService.getSubscription()
                    .toObservable()
                    .onErrorReturnItem(Collections.emptyList())
                    .observeOn(Schedulers.io())
                    .subscribe(subscriptionsObserver);
        }
    }

    @Override
    public void handleResult(@NonNull List<StreamInfoItem> result) {
        if (DEBUG) Log.d(TAG, "handleResult(result = [" + result.size() + "])");

        setAllItemsDisplayed(false);
        isLoading.set(true);
        showLoading();

        if (feedItemsSubscriber != null) {
            feedItemsSubscriber.dispose();
        }

        feedItemsSubscriber = new FeedItemsSubscriber(this, infoListAdapter);
        Flowable.fromIterable(result)
                .observeOn(AndroidSchedulers.mainThread())
                .buffer(feedLoadCount)
                .subscribe(feedItemsSubscriber);
    }

    /**
     * Sets a flag that indicates whether all items have finished loading.
     * @param areAllItemsDisplayed Whether all items have been displayed
     */
    void setAllItemsDisplayed(boolean areAllItemsDisplayed) {
        this.areAllItemsDisplayed.set(areAllItemsDisplayed);
    }

    /**
     * Sets a flag that indicates that loading has finished.
     */
    void setLoadingFinished() {
        this.isLoading.set(false);
    }

    @Override
    protected void loadMoreItems() {
        isLoading.set(true);
        delayHandler.removeCallbacksAndMessages(null);

        // Do not load more items if new items are currently being
        // downloaded by the SubscriptionsObserver. They will be added once it is finished.
        if (subscriptionsObserver != null && subscriptionsObserver.areItemsBeingLoaded()) {
            return;
        }

        // Add a little of a delay when requesting more items because the cache is so fast,
        // that the view seems stuck to the user when he scroll to the bottom
        delayHandler.postDelayed(this::requestFeed, 300);
    }

    @Override
    protected boolean hasMoreItems() {
        return !areAllItemsDisplayed.get();
    }

    private final Handler delayHandler = new Handler();

    private void requestFeed() {
        if (DEBUG) Log.d(TAG, "requestFeed(); feedItemsSubscriber = " + feedItemsSubscriber);
        if (feedItemsSubscriber == null) return;

        isLoading.set(true);
        delayHandler.removeCallbacksAndMessages(null);
        feedItemsSubscriber.requestNext();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Keeping track of added items
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Checks whether an item is already displayed in the feed.
     * @param item The item in question
     * @return Whether the item is displayed
     */
    boolean isItemAlreadyDisplayed(final InfoItem item) {
        return displayedItems.contains(getItemIdent(item));
    }

    /**
     * Marks the item as displayed in the feed.
     * The method must be called every time an item is actually displayed.
     * @param item The item in question
     */
    void setItemDisplayed(final InfoItem item) {
        displayedItems.add(getItemIdent(item));
    }

    /**
     * @param item The item in question.
     * @return An identifier used to keep track of items that have already been displayed.
     */
    private String getItemIdent(final InfoItem item) {
        return item.getServiceId() + item.getUrl();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void resetFragment() {
        if (DEBUG) Log.d(TAG, "resetFragment()");
        delayHandler.removeCallbacksAndMessages(null);
        disposeEverything();

        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();
        setAllItemsDisplayed(false);
        showListFooter(false);
        displayedItems.clear();
    }

    private void disposeEverything() {
        if (subscriptionsObserver != null) {
            subscriptionsObserver.dispose();
            subscriptionsObserver = null;
        }

        if (feedItemsSubscriber != null) {
            feedItemsSubscriber.dispose();
            feedItemsSubscriber = null;
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
        super.showError(message, showRetryButton);
    }

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        int errorId = exception instanceof ExtractionException ? R.string.parsing_error : R.string.general_error;
        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE, "none", "Requesting feed", errorId);
        return true;
    }
}
