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
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.InfoCache;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
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
public class FeedFragment extends BaseListFragment<FeedInfo, Void> {

    static final boolean DEBUG = MainActivity.DEBUG;

    private static final int OFF_SCREEN_ITEMS_COUNT = 3;
    private static final int MIN_ITEMS_INITIAL_LOAD = 8;

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private FeedState currentState = FeedState.INITIALIZING;
    private AtomicBoolean loadedItemsUpToDate = new AtomicBoolean();
    private Calendar itemsUpdateDate;
    private Set<String> displayedItems = new HashSet<>();

    private int feedLoadCount = MIN_ITEMS_INITIAL_LOAD;

    private SubscriptionsObserver subscriptionsObserver;
    private FeedItemsSubscriber feedItemsSubscriber;

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
        setUpdateTimeText();
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
            if (currentState != FeedState.LOADING_SUBSCRIPTIONS) {
                // Clear the cache in order to download new items.
                InfoCache.getInstance().clearCache();

                reloadContent();
            }
        });
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
        objectsToSave.add(currentState);
        objectsToSave.add(loadedItemsUpToDate);
        objectsToSave.add(itemsUpdateDate);
        objectsToSave.add(displayedItems);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        currentState = (FeedState) savedObjects.poll();
        loadedItemsUpToDate = (AtomicBoolean) savedObjects.poll();
        itemsUpdateDate = (Calendar) savedObjects.poll();
        displayedItems = (Set<String>) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Loader
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void startLoading(boolean forceLoad) {
        if (DEBUG) Log.d(TAG, "startLoading(forceLoad = " + forceLoad + ")");

        if (currentState != FeedState.INITIALIZING) {
            currentState.doEnterState(this);
        }

        if (feedInfoCache == null) {
            feedInfoCache = new FeedInfoCache(activity);

            FeedInfo cachedFeedInfo = feedInfoCache.read();
            if (cachedFeedInfo != null) {
                handleResult(cachedFeedInfo);
            }
        }

        if (subscriptionsObserver == null) {
            setState(FeedState.LOADING_SUBSCRIPTIONS);

            subscriptionsObserver = new SubscriptionsObserver(this);
            subscriptionService.getSubscription()
                    .toObservable()
                    .onErrorReturnItem(Collections.emptyList())
                    .observeOn(Schedulers.io())
                    .subscribe(subscriptionsObserver);
        }
    }

    @Override
    public void handleResult(@NonNull FeedInfo result) {
        if (DEBUG) Log.d(TAG, "handleResult(result = " + result + ")");

        if (feedItemsSubscriber != null) {
            feedItemsSubscriber.dispose();
        }

        if (infoListAdapter != null && !loadedItemsUpToDate.get()) {
            infoListAdapter.clearStreamItemList();
            itemsUpdateDate = result.getLastUpdated();

            feedInfoCache.store(result);
        }

        setState(FeedState.LOADING_ITEMS);
        setUpdateTimeText();

        feedItemsSubscriber = new FeedItemsSubscriber(this, infoListAdapter);
        Flowable.fromIterable(result.getInfoItems())
                .observeOn(AndroidSchedulers.mainThread())
                .buffer(feedLoadCount)
                .subscribe(feedItemsSubscriber);
    }

    private void setUpdateTimeText() {
        if (itemsUpdateDate != null) {
            String updateTime = DateUtils.getRelativeDateTimeString(activity,
                    itemsUpdateDate.getTimeInMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString();
            refreshText.setText(getString(R.string.feed_last_updated, updateTime));
        }
    }

    @Override
    protected void loadMoreItems() {
        delayHandler.removeCallbacksAndMessages(null);

        // Do not load more items if new items are currently being
        // downloaded by the SubscriptionsObserver. They will be added once it is finished.
        if (currentState == FeedState.LOADING_SUBSCRIPTIONS) {
            return;
        }

        // Add a little of a delay when requesting more items because the cache is so fast,
        // that the view seems stuck to the user when he scroll to the bottom
        delayHandler.postDelayed(this::requestFeed, 300);
    }

    @Override
    protected boolean hasMoreItems() {
        return currentState == FeedState.IDLE;
    }

    private final Handler delayHandler = new Handler();

    private void requestFeed() {
        if (DEBUG) Log.d(TAG, "requestFeed(); feedItemsSubscriber = " + feedItemsSubscriber);
        if (feedItemsSubscriber == null) return;

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

        displayedItems.clear();
        loadedItemsUpToDate.set(false);
        setState(FeedState.INITIALIZING);
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

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Fragment States
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Switches the state of the feed and takes care of all visual changes.
     * @param nextState The state to switch to
     */
    synchronized void setState(final FeedState nextState) {
        if (currentState != nextState) {
            final FeedState previousState = currentState;
            currentState = nextState;

            activity.runOnUiThread(() -> {
                previousState.doLeaveState(FeedFragment.this);
                nextState.doEnterState(FeedFragment.this);
            });
        }
    }

    /**
     * Represents the different state the feed can be in.
     */
    enum FeedState {
        /**
         * The feed is initializing either because it was just created or because it was reset.
         */
        INITIALIZING,

        /**
         * The feed is currently loading new videos from every subscribed channel.
         */
        LOADING_SUBSCRIPTIONS {
            @Override
            void enterState (FeedFragment feed) {
                feed.isLoading.set(true);
                feed.refreshIcon.startAnimation(feed.refreshRotation);

            }

            @Override
            void leaveState(FeedFragment feed) {
                feed.isLoading.set(false);
                feed.loadedItemsUpToDate.set(true);
                feed.refreshIcon.clearAnimation();
            }
        },

        /**
         * The feed is loading new items to display.
         */
        LOADING_ITEMS {
            @Override
            void enterState(FeedFragment feed) {
                feed.showListFooter(true);
                feed.showLoading();
            }
        },

        /**
         * The feed is not doing anything, but there are still new items to display.
         */
        IDLE,

        /**
         * The feed has displayed all available new items.
         */
        All_ITEMS_LOADED {
            @Override
            void enterState(FeedFragment feed) {
                feed.showListFooter(false);
                feed.hideLoading();
            }
        },
        ;

        void doEnterState(FeedFragment feed) {
            if (DEBUG) Log.d(feed.TAG, "Entering state " + toString());
            enterState(feed);
        }

        void enterState(FeedFragment feed) {
            // Do nothing by default.
        }

        void doLeaveState(FeedFragment feed) {
            if (DEBUG) Log.d(feed.TAG, "Leaving state " + toString());
            leaveState(feed);
        }

        void leaveState(FeedFragment feed) {
            // Do nothing by default.
        }


    }
}
