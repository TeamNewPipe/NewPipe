package org.schabi.newpipe.local.feed;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.local.subscription.SubscriptionService;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class FeedFragment extends BaseListFragment<List<SubscriptionEntity>, Void> {

    private static final int OFF_SCREEN_ITEMS_COUNT = 3;
    private static final int MIN_ITEMS_INITIAL_LOAD = 8;
    private int FEED_LOAD_COUNT = MIN_ITEMS_INITIAL_LOAD;

    private AtomicInteger numLoadedChunks = new AtomicInteger(1);
    private AtomicInteger numChannels = new AtomicInteger(0);
    private AtomicInteger numLoadedChannels = new AtomicInteger(0);
    private AtomicBoolean hasStartedLoading = new AtomicBoolean(false);

    private SubscriptionService subscriptionService;
    private static YoutubeStreamLinkHandlerFactory youtubeStreamLinkHandler =
        YoutubeStreamLinkHandlerFactory.getInstance();

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Disposable subscriptionObserver;

    private Set<String> videoIds = new HashSet<>();
    private Map<String, String> videoIsoTimeStrLookup = new HashMap<>();
    private List<InfoItem> videoItems = new ArrayList<>();
    private Set<String> loadedSubscriptionEntities = new HashSet<>();

    private AtomicBoolean shouldSkipUpdate = new AtomicBoolean(false);
    private AtomicBoolean isScrolling = new AtomicBoolean(false);

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subscriptionService = SubscriptionService.getInstance(activity);

        FEED_LOAD_COUNT = howManyItemsToLoad();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        if(!useAsFrontPage) {
            setTitle(activity.getString(R.string.fragment_whats_new));
        }
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
        // Start fetching remaining channels, if any
        startLoading(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        disposeEverything();
        subscriptionService = null;
        compositeDisposable = null;
        subscriptionObserver = null;
    }

    @Override
    public void onDestroyView() {
        // Do not monitor for updates when user is not viewing the feed fragment.
        // This is a waste of bandwidth.
        disposeEverything();
        super.onDestroyView();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (activity != null && isVisibleToUser) {
            setTitle(activity.getString(R.string.fragment_whats_new));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        ActionBar supportActionBar = activity.getSupportActionBar();

        if(useAsFrontPage) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            //supportActionBar.setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    public void reloadContent() {
        resetFragment();
        super.reloadContent();
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        itemsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    delayHandler.postDelayed(() -> isScrolling.set(false), 200);
                } else {
                    isScrolling.set(true);
                }
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // StateSaving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(loadedSubscriptionEntities);
        objectsToSave.add(videoIds);
        objectsToSave.add(videoIsoTimeStrLookup);
        objectsToSave.add(videoItems);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        loadedSubscriptionEntities = (Set<String>) savedObjects.poll();
        videoIds = (Set<String>) savedObjects.poll();
        videoIsoTimeStrLookup = (Map<String, String>) savedObjects.poll();
        videoItems = (List<InfoItem>) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Feed Loader
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void startLoading(boolean forceLoad) {
        if (DEBUG) Log.d(TAG, "startLoading() called with: forceLoad = [" + forceLoad + "]");

        if (!hasStartedLoading.get() || forceLoad) {
            if (subscriptionObserver != null) subscriptionObserver.dispose();

            setLoadingState(true);
            subscriptionObserver = subscriptionService.getSubscription()
                .onErrorReturnItem(Collections.emptyList())
                .observeOn(Schedulers.io())
                .subscribe(this::handleResult, this::handleError);
            hasStartedLoading.set(true);
        }
    }

    @Override
    public void handleResult(@android.support.annotation.NonNull List<SubscriptionEntity> result) {
        if (result.isEmpty()) {
            delayHandler.post(() -> {
                setLoadingState(false);
                infoListAdapter.clearStreamItemList();
                showEmptyState();
            });
            return;
        }

        List<SubscriptionEntity> filteredResult = new ArrayList<>();

        for (SubscriptionEntity subscriptionEntity: result) {
            if (loadedSubscriptionEntities.contains(subscriptionEntity.getUrl())) continue;
            filteredResult.add(subscriptionEntity);
        }

        numChannels.set(filteredResult.size());
        compositeDisposable.add(
            Flowable.fromIterable(filteredResult)
                .observeOn(Schedulers.io())
                .subscribe(this::handleReceiveSubscriptionEntity, this::handleError));

        // Start item list UI update scheduler
        delayHandler.postDelayed(this::updateItemsList, 3200);
    }

    private void handleReceiveSubscriptionEntity(SubscriptionEntity subscriptionEntity) {
        compositeDisposable.add(
            subscriptionService.getChannelInfo(subscriptionEntity)
                .observeOn(Schedulers.io())
                .onErrorComplete((@NonNull Throwable throwable) -> FeedFragment.super.onError(
                    throwable))
                .subscribe(this.getReceiveChannelInfoHandler(subscriptionEntity.getUrl()),
                    getFetchChannelInfoErrorHandler(subscriptionEntity.getServiceId(), subscriptionEntity.getUrl())));
    }

    private Consumer<ChannelInfo> getReceiveChannelInfoHandler(String url) {
        return channelInfo -> {
            addVideoIsoTimeStrsToLookup(channelInfo.getPublishIsoTimeStrLookup());

            List<StreamInfoItem> relatedItems = channelInfo.getRelatedItems();

            for (InfoItem item : relatedItems) {
                String videoId = tryRetrieveVideoIdFromUrl(item.getUrl());

                if (videoId == null || videoIds.contains(videoId)) continue;

                String isoTimeStr = videoIsoTimeStrLookup.get(videoId);

                if (isoTimeStr == null) continue;

                insertVideoItem(videoId, isoTimeStr, item);
            }

            numLoadedChannels.incrementAndGet();
            loadedSubscriptionEntities.add(url);
        };
    }

    private synchronized void addVideoIsoTimeStrsToLookup(Map<String, String> lookup) {
        videoIsoTimeStrLookup.putAll(lookup);
    }

    private synchronized void insertVideoItem(String videoId, String isoTimeStr, InfoItem item) {
        videoIds.add(videoId);

        int insertPosition = ListUtils.binarySearchUpperBound(
            videoItems,
            isoTimeStr,
            x -> videoIsoTimeStrLookup.get(tryRetrieveVideoIdFromUrl(x.getUrl())),
            (a, b) -> b.compareTo(a));
        videoItems.add(insertPosition, item);
    }

    private Consumer<Throwable> getFetchChannelInfoErrorHandler(int serviceId, String url) {
        return ex -> showSnackBarError(ex, UserAction.SUBSCRIPTION, NewPipe.getNameOfService(serviceId), url, 0);
    }

    private void handleError(Throwable ex) {
        delayHandler.post(() -> this.onError(ex));
    }

    @Override
    protected void loadMoreItems() {
        if (!isLoading.get()) {
            setLoadingState(true);
            shouldSkipUpdate.set(true);
            numLoadedChunks.incrementAndGet();
        }
    }

    @Override
    protected boolean hasMoreItems() {
        return videoItems.size() > getNumVisibleItems();
    }

    private void updateItemsList() {
        List<InfoItem> viewItemsList = infoListAdapter.getItemsList();
        int viewItemSize = viewItemsList.size();
        int numVisibleItems = getNumVisibleItems();

        if (!shouldSkipUpdate.getAndSet(false) && !isScrolling.get()) {
            int minDirtyIndex = Integer.MAX_VALUE;
            int maxDirtyIndex = Integer.MIN_VALUE;
            boolean isDirty = false;

            for (int i = 0; i < numVisibleItems; i++) {
                InfoItem videoItem = videoItems.get(i);

                if (i < viewItemSize) {
                    // Just do shallow comparison, since this is cheaper
                    if (videoItem != viewItemsList.get(i)) {
                        viewItemsList.set(i, videoItem);
                        isDirty = true;
                    }
                } else {
                    viewItemsList.add(i, videoItem);
                    isDirty = true;
                }

                // Update minDirtyIndex and maxDirtyIndex only if the item list has been modified
                if (isDirty) {
                    if (i < minDirtyIndex) {
                        minDirtyIndex = i;
                    }
                    if (i > maxDirtyIndex) {
                        maxDirtyIndex = i;
                    }
                }
            }

            // Notify the infoListAdapter the range of changes only if the item list has been modified
            if (minDirtyIndex < maxDirtyIndex) {
                infoListAdapter.notifyItemRangeChanged(minDirtyIndex, maxDirtyIndex - minDirtyIndex + 1);
            }

            setLoadingState(false);
        }

        // Schedule next item list UI update
        if (isScrolling.get()) {
            delayHandler.postDelayed(this::updateItemsList, 200);
        } else if (numLoadedChannels.get() < numChannels.get()) {
            // Schedule next task with longer delay if the background thread is still fetching recent videos
            delayHandler.postDelayed(this::updateItemsList, 1000);
        } else if (hasMoreItems()) {
            delayHandler.postDelayed(this::updateItemsList, 300);
        }
    }

    private final Handler delayHandler = new Handler();

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void resetFragment() {
        if (DEBUG) Log.d(TAG, "resetFragment() called");
        if (subscriptionObserver != null) subscriptionObserver.dispose();
        if (compositeDisposable != null) compositeDisposable.clear();
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();

        showListFooter(false);
    }

    private void disposeEverything() {
        if (subscriptionObserver != null) subscriptionObserver.dispose();
        if (compositeDisposable != null) compositeDisposable.clear();
        hasStartedLoading.set(false);
    }

    private int howManyItemsToLoad() {
        int heightPixels = getResources().getDisplayMetrics().heightPixels;
        int itemHeightPixels = activity.getResources().getDimensionPixelSize(R.dimen.video_item_search_height);

        int items = itemHeightPixels > 0
                ? heightPixels / itemHeightPixels + OFF_SCREEN_ITEMS_COUNT
                : MIN_ITEMS_INITIAL_LOAD;
        return Math.max(MIN_ITEMS_INITIAL_LOAD, items);
    }

    @Nullable
    private String tryRetrieveVideoIdFromUrl(String url) {
        try {
            return youtubeStreamLinkHandler.getId(url);
        } catch (Exception ex) {
            if (DEBUG)
                Log.d(TAG, "Failed to retrieve video ID from \"" + url + "\"", ex);

            showSnackBarError(ex,
                UserAction.SOMETHING_ELSE,
                "none",
                "",
                R.string.general_error);

            return null;
        }
    }

    private int getNumVisibleItems() {
        return Math.min(videoItems.size(), numLoadedChunks.get() * FEED_LOAD_COUNT);
    }

    private void setLoadingState(boolean isLoading) {
        this.isLoading.set(isLoading);
        if (isLoading) {
            showLoading();
        } else {
            hideLoading();
        }
        showListFooter(isLoading);
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

        int errorId = exception instanceof ExtractionException
                ? R.string.parsing_error
                : R.string.general_error;
        onUnrecoverableError(exception,
                UserAction.SOMETHING_ELSE,
                "none",
                "Requesting feed",
                errorId);
        return true;
    }
}
