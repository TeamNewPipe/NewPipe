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
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FeedFragment extends BaseListFragment<List<StreamInfoItem>, Void> {

    private static final int OFF_SCREEN_ITEMS_COUNT = 3;
    private static final int MIN_ITEMS_INITIAL_LOAD = 8;

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private AtomicBoolean allItemsLoaded = new AtomicBoolean(false);
    private HashSet<String> itemsLoaded = new HashSet<>();

    private int feedLoadCount = MIN_ITEMS_INITIAL_LOAD;

    private CompositeDisposable feedDisposable = new CompositeDisposable();
    private Disposable subscriptionsConsumerDisposable;
    private FeedItemsSubscriber feedItemsSubscriber;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, Bundle savedInstanceState) {
        feedLoadCount = howManyItemsToLoad();
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        disposeEverything();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (wasLoading.get()) doInitialLoadLogic();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        disposeEverything();
        disposeSubscriptionsConsumer();
        feedDisposable = null;
        feedItemsSubscriber = null;
    }

//    @Override
//    public void onDestroyView() {
//        // Do not monitor for updates when user is not viewing the feed fragment.
//        // This is a waste of bandwidth.
//        disposeEverything();
//        super.onDestroyView();
//    }

    /*@Override
    protected RecyclerView.LayoutManager getListLayoutManager() {
        boolean isPortrait = getResources().getDisplayMetrics().heightPixels > getResources().getDisplayMetrics().widthPixels;
        return new GridLayoutManager(activity, isPortrait ? 1 : 2);
    }*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(R.string.fragment_whats_new);

            if (useAsFrontPage) {
                supportActionBar.setDisplayShowTitleEnabled(true);
                //supportActionBar.setDisplayShowTitleEnabled(false);
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
        objectsToSave.add(allItemsLoaded);
        objectsToSave.add(itemsLoaded);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        allItemsLoaded = (AtomicBoolean) savedObjects.poll();
        itemsLoaded = (HashSet<String>) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Loader
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void startLoading(boolean forceLoad) {
        if (DEBUG) Log.d(TAG, "startLoading(forceLoad = " + forceLoad + ")");
        if (subscriptionsConsumerDisposable != null) {
            return;
        }

        if (allItemsLoaded.get()) {
            if (infoListAdapter.getItemsList().size() == 0) {
                showEmptyState();
            } else {
                showListFooter(false);
                hideLoading();
            }

            isLoading.set(false);
            return;
        }

        isLoading.set(true);
        showLoading();
        showListFooter(true);

        this.subscriptionsConsumerDisposable =
                subscriptionService.getSubscription()
                .onErrorReturnItem(Collections.emptyList())
                .observeOn(Schedulers.io())
                .subscribe(new SubscriptionsConsumer(this), this::onError);
    }

    @Override
    public void handleResult(@NonNull List<StreamInfoItem> result) {
        if (DEBUG) Log.d(TAG, "handleResult([" + result.size() + "])");

        setAllItemsLoaded(false);
        isLoading.set(true);
        showLoading();

        feedItemsSubscriber = new FeedItemsSubscriber(this, infoListAdapter);
        Flowable.fromIterable(result)
                .observeOn(AndroidSchedulers.mainThread())
                .buffer(feedLoadCount)
                .subscribe(feedItemsSubscriber);
    }

    boolean isItemAlreadyLoaded(String itemIdent) {
        boolean isItemAlreadyLoaded = itemsLoaded.contains(itemIdent);
        itemsLoaded.add(itemIdent);
        return isItemAlreadyLoaded;
    }

    /**
     * Sets a flag that indicates whether all items have finished loading.
     * @param allItemsLoaded Whether all items have been loaded
     */
    void setAllItemsLoaded(boolean allItemsLoaded) {
        this.allItemsLoaded.set(allItemsLoaded);
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
        // Add a little of a delay when requesting more items because the cache is so fast,
        // that the view seems stuck to the user when he scroll to the bottom
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestFeed();
            }
        }, 300);
    }

    @Override
    protected boolean hasMoreItems() {
        return !allItemsLoaded.get();
    }

    private final Handler delayHandler = new Handler();

    private void requestFeed() {
        if (DEBUG) Log.d(TAG, "requestFeed() feedItemsSubscriber = [" + feedItemsSubscriber + "]");
        if (feedItemsSubscriber == null) return;

        isLoading.set(true);
        delayHandler.removeCallbacksAndMessages(null);
        feedItemsSubscriber.requestNext();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void resetFragment() {
        if (DEBUG) Log.d(TAG, "resetFragment() called");
        disposeSubscriptionsConsumer();
        if (feedDisposable != null) feedDisposable.clear();
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();

        delayHandler.removeCallbacksAndMessages(null);
        setAllItemsLoaded(false);
        showListFooter(false);
        itemsLoaded.clear();
    }

    private void disposeEverything() {
        if (feedDisposable != null) feedDisposable.clear();
        if (feedItemsSubscriber != null) feedItemsSubscriber.cancel();
        delayHandler.removeCallbacksAndMessages(null);
    }

    private void disposeSubscriptionsConsumer() {
        if (DEBUG) Log.e(TAG, "Disposing SubscriptionsConsumer");
        if (subscriptionsConsumerDisposable != null) {
            subscriptionsConsumerDisposable.dispose();
            subscriptionsConsumerDisposable = null;
        }
    }

    private int howManyItemsToLoad() {
        int heightPixels = getResources().getDisplayMetrics().heightPixels;
        int itemHeightPixels = activity.getResources().getDimensionPixelSize(R.dimen.video_item_search_height);

        int items = itemHeightPixels > 0 ? heightPixels / itemHeightPixels + OFF_SCREEN_ITEMS_COUNT : MIN_ITEMS_INITIAL_LOAD;
        return Math.max(MIN_ITEMS_INITIAL_LOAD, items);
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
