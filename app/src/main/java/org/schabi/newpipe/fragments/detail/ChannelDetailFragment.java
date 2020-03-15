package org.schabi.newpipe.fragments.detail;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.jakewharton.rxbinding2.view.RxView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.list.channel.ChannelTabFragment;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ShareUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateBackgroundColor;
import static org.schabi.newpipe.util.AnimationUtils.animateTextColor;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class ChannelDetailFragment
        extends BaseStateFragment<ChannelInfo>
        implements BackPressable {

    private CompositeDisposable disposables = new CompositeDisposable();
    private Disposable subscribeButtonMonitor;
    private SubscriptionManager subscriptionManager;

    private int updateFlags = 0;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private ChannelInfo currentInfo;
    private Disposable currentWorker;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private AppBarLayout appBarLayout;
    private ViewPager viewPager;
    private TabAdaptor pageAdapter;
    private TabLayout tabLayout;

    private ImageView headerChannelBanner;
    private ImageView headerAvatarView;
    private TextView headerTitleView;
    private TextView headerSubscribersTextView;
    private Button headerSubscribeButton;


    /*////////////////////////////////////////////////////////////////////////*/

    public static ChannelDetailFragment getInstance(int serviceId, String channelUrl, String name) {
        ChannelDetailFragment instance = new ChannelDetailFragment();
        instance.setInitialData(serviceId, channelUrl, name);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        subscriptionManager = new SubscriptionManager(activity);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_detail, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (currentWorker != null) currentWorker.dispose();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .edit()
                .putString(getString(R.string.stream_info_selected_tab_key), pageAdapter.getItemTitle(viewPager.getCurrentItem()))
                .apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (updateFlags != 0) {
            updateFlags = 0;
        }

        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            selectAndLoadChannel(serviceId, url, name);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (currentWorker != null) currentWorker.dispose();
        if (disposables != null) disposables.clear();
        if (subscribeButtonMonitor != null) subscribeButtonMonitor.dispose();

        currentWorker = null;
        disposables = null;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                NavigationHelper.openChannelDetailFragment(getFragmentManager(), serviceId, url, name);
            } else Log.e(TAG, "ReCaptcha failed");
        } else {
            Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    private static final String INFO_KEY = "info_key";
    private static final String STACK_KEY = "stack_key";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!isLoading.get() && currentInfo != null && isVisible()) {
            outState.putSerializable(INFO_KEY, currentInfo);
        }

        outState.putSerializable(STACK_KEY, stack);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof ChannelInfo) {
            currentInfo = (ChannelInfo) serializable;
            InfoCache.getInstance().putInfo(serviceId, url, currentInfo, InfoItem.InfoType.CHANNEL);
        }

        serializable = savedState.getSerializable(STACK_KEY);
        if (serializable instanceof Collection) {
            //noinspection unchecked
            stack.addAll((Collection<? extends StackItem>) serializable);
        }

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        appBarLayout = rootView.findViewById(R.id.appbarlayout);
        viewPager = rootView.findViewById(R.id.viewpager);
        pageAdapter = new TabAdaptor(getChildFragmentManager()) {
            @Nullable
            public CharSequence getPageTitle(int position) {
                if (position < 0 || position >= mFragmentTitleList.size()) {
                    return null;
                }
                return mFragmentTitleList.get(position);
            }
        };
        viewPager.setAdapter(pageAdapter);
        tabLayout = rootView.findViewById(R.id.tablayout);
        tabLayout.setupWithViewPager(viewPager);

        headerChannelBanner = rootView.findViewById(R.id.channel_banner_image);
        headerAvatarView = rootView.findViewById(R.id.channel_avatar_view);
        headerTitleView = rootView.findViewById(R.id.channel_title_view);
        headerSubscribersTextView = rootView.findViewById(R.id.channel_subscriber_view);
        headerSubscribeButton = rootView.findViewById(R.id.channel_subscribe_button);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        ActionBar supportActionBar = activity.getSupportActionBar();
        if (useAsFrontPage && supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        } else {
            inflater.inflate(R.menu.menu_channel, menu);

            if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu +
                    "], inflater = [" + inflater + "]");
            MenuItem menuRssButton = menu.findItem(R.id.menu_item_rss);

            if (currentInfo != null) menuRssButton.setVisible(!TextUtils.isEmpty(currentInfo.getFeedUrl()));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            NavigationHelper.openSettings(requireContext());
            return true;
        }

        if (isLoading.get()) {
            // if still loading, block menu buttons related to channel info
            return true;
        }

        switch (id) {
            case R.id.menu_item_share: {
                if (currentInfo != null) {
                    ShareUtils.shareUrl(requireContext(), currentInfo.getName(), currentInfo.getOriginalUrl());
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getOriginalUrl());
                }
                return true;
            }
            case R.id.menu_item_rss:
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getFeedUrl());
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void setupActionBar(final ChannelInfo info) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandler() called with: info = [" + info + "]");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current channel.
     */
    private final LinkedList<StackItem> stack = new LinkedList<>();

    private void pushToStack(int serviceId, String channelUrl, String name) {
        if (DEBUG) {
            Log.d(TAG, "pushToStack() called with: serviceId = ["
                    + serviceId + "], channelUrl = [" + channelUrl + "], name = [" + name + "]");
        }

        if (stack.size() > 0
                && stack.peek().getServiceId() == serviceId
                && stack.peek().getUrl().equals(channelUrl)) {
            Log.d(TAG, "pushToStack() called with: serviceId == peek.serviceId = ["
                    + serviceId + "], channelUrl == peek.getUrl = [" + channelUrl + "]");
            return;
        } else {
            Log.d(TAG, "pushToStack() wasn't equal");
        }

        stack.push(new StackItem(serviceId, channelUrl, name));
    }

    @Override
    public boolean onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");
        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size() <= 1) return false;
        // Remove top
        stack.pop();
        // Get stack item from the new top
        StackItem peek = stack.peek();

        selectAndLoadChannel(peek.getServiceId(),
                peek.getUrl(),
                !TextUtils.isEmpty(peek.getTitle())
                        ? peek.getTitle()
                        : "");
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Channel Subscription
    //////////////////////////////////////////////////////////////////////////*/

    private static final int BUTTON_DEBOUNCE_INTERVAL = 100;

    private void monitorSubscription(final ChannelInfo info) {
        final Consumer<Throwable> onError = (Throwable throwable) -> {
            animateView(headerSubscribeButton, false, 100);
            showSnackBarError(throwable, UserAction.SUBSCRIPTION,
                    NewPipe.getNameOfService(currentInfo.getServiceId()),
                    "Get subscription status",
                    0);
        };

        final Observable<List<SubscriptionEntity>> observable = subscriptionManager.subscriptionTable()
                .getSubscriptionFlowable(info.getServiceId(), info.getUrl())
                .toObservable();

        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError));

        disposables.add(observable
                // Some updates are very rapid (when calling the updateSubscription(info), for example)
                // so only update the UI for the latest emission ("sync" the subscribe button's state)
                .debounce(100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((List<SubscriptionEntity> subscriptionEntities) ->
                                updateSubscribeButton(!subscriptionEntities.isEmpty())
                        , onError));

    }

    private Function<Object, Object> mapOnSubscribe(final SubscriptionEntity subscription, ChannelInfo info) {
        return (@NonNull Object o) -> {
            subscriptionManager.insertSubscription(subscription, info);
            return o;
        };
    }

    private Function<Object, Object> mapOnUnsubscribe(final SubscriptionEntity subscription) {
        return (@NonNull Object o) -> {
            subscriptionManager.deleteSubscription(subscription);
            return o;
        };
    }

    private void updateSubscription(final ChannelInfo info) {
        if (DEBUG) Log.d(TAG, "updateSubscription() called with: info = [" + info + "]");
        final Action onComplete = () -> {
            if (DEBUG) Log.d(TAG, "Updated subscription: " + info.getUrl());
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                onUnrecoverableError(throwable,
                        UserAction.SUBSCRIPTION,
                        NewPipe.getNameOfService(info.getServiceId()),
                        "Updating Subscription for " + info.getUrl(),
                        R.string.subscription_update_failed);

        disposables.add(subscriptionManager.updateChannelInfo(info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, onError));
    }

    private Disposable monitorSubscribeButton(final Button subscribeButton, final Function<Object, Object> action) {
        final Consumer<Object> onNext = (@NonNull Object o) -> {
            if (DEBUG) Log.d(TAG, "Changed subscription status to this channel!");
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                onUnrecoverableError(throwable,
                        UserAction.SUBSCRIPTION,
                        NewPipe.getNameOfService(currentInfo.getServiceId()),
                        "Subscription Change",
                        R.string.subscription_change_failed);

        /* Emit clicks from main thread unto io thread */
        return RxView.clicks(subscribeButton)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .debounce(BUTTON_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS) // Ignore rapid clicks
                .map(action)
                .subscribe(onNext, onError);
    }

    private Consumer<List<SubscriptionEntity>> getSubscribeUpdateMonitor(final ChannelInfo info) {
        return (List<SubscriptionEntity> subscriptionEntities) -> {
            if (DEBUG)
                Log.d(TAG, "subscriptionService.subscriptionTable.doOnNext() called with: subscriptionEntities = [" + subscriptionEntities + "]");
            if (subscribeButtonMonitor != null) subscribeButtonMonitor.dispose();

            if (subscriptionEntities.isEmpty()) {
                if (DEBUG) Log.d(TAG, "No subscription to this channel!");
                SubscriptionEntity channel = new SubscriptionEntity();
                channel.setServiceId(info.getServiceId());
                channel.setUrl(info.getUrl());
                channel.setData(info.getName(),
                        info.getAvatarUrl(),
                        info.getDescription(),
                        info.getSubscriberCount());
                subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton, mapOnSubscribe(channel, info));
            } else {
                if (DEBUG) Log.d(TAG, "Found subscription to this channel!");
                final SubscriptionEntity subscription = subscriptionEntities.get(0);
                subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton, mapOnUnsubscribe(subscription));
            }
        };
    }

    private void updateSubscribeButton(boolean isSubscribed) {
        if (DEBUG) Log.d(TAG, "updateSubscribeButton() called with: isSubscribed = [" + isSubscribed + "]");

        boolean isButtonVisible = headerSubscribeButton.getVisibility() == View.VISIBLE;
        int backgroundDuration = isButtonVisible ? 300 : 0;
        int textDuration = isButtonVisible ? 200 : 0;

        int subscribeBackground = ContextCompat.getColor(activity, R.color.subscribe_background_color);
        int subscribeText = ContextCompat.getColor(activity, R.color.subscribe_text_color);
        int subscribedBackground = ContextCompat.getColor(activity, R.color.subscribed_background_color);
        int subscribedText = ContextCompat.getColor(activity, R.color.subscribed_text_color);

        if (!isSubscribed) {
            headerSubscribeButton.setText(R.string.subscribe_button_title);
            animateBackgroundColor(headerSubscribeButton, backgroundDuration, subscribedBackground, subscribeBackground);
            animateTextColor(headerSubscribeButton, textDuration, subscribedText, subscribeText);
        } else {
            headerSubscribeButton.setText(R.string.subscribed_button_title);
            animateBackgroundColor(headerSubscribeButton, backgroundDuration, subscribeBackground, subscribedBackground);
            animateTextColor(headerSubscribeButton, textDuration, subscribeText, subscribedText);
        }

        animateView(headerSubscribeButton, AnimationUtils.Type.LIGHT_SCALE_AND_ALPHA, true, 100);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (currentInfo == null) prepareAndLoadInfo();
        else prepareAndHandleInfo(currentInfo);
    }

    private void selectAndLoadChannel(int serviceId, String channelUrl, String name) {
        setInitialData(serviceId, channelUrl, name);
        prepareAndLoadInfo();
    }

    private void prepareAndHandleInfo(final ChannelInfo info) {
        if (DEBUG) Log.d(TAG, "prepareAndHandleInfo() called with: info = ["
                + info + "], scrollToTop = [" + false + "]");

        setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        pushToStack(serviceId, url, name);
        showLoading();
        initTabs();

        handleResult(info);
    }

    private void prepareAndLoadInfo() {
        appBarLayout.setExpanded(true, true);
        pushToStack(serviceId, url, name);
        startLoading(false);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) currentWorker.dispose();

        currentWorker = ExtractorHelper.getChannelInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull ChannelInfo result) -> {
                    isLoading.set(false);
                    currentInfo = result;
                    handleResult(result);
                }, (@NonNull Throwable throwable) -> {
                    isLoading.set(false);
                    onError(throwable);
                });

    }

    private void initTabs() {
        pageAdapter.clearAllItems();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void setInitialData(int serviceId, String url, String name) {
        this.serviceId = serviceId;
        this.url = url;
        this.name = !TextUtils.isEmpty(name) ? name : "";
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();

        headerTitleView.setText(name != null ? name : "");

        animateView(headerSubscribeButton, false, 100);
    }

    private void initThumbnailViews(@NonNull ChannelInfo info) {
        if (!TextUtils.isEmpty(info.getAvatarUrl())) {
            imageLoader.displayImage(info.getAvatarUrl(), headerAvatarView,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }

        if (!TextUtils.isEmpty(info.getBannerUrl())) {
            imageLoader.displayImage(info.getBannerUrl(), headerChannelBanner,
                    ImageDisplayConstants.DISPLAY_BANNER_OPTIONS);
        }
    }

    @Override
    public void handleResult(@NonNull ChannelInfo info) {
        super.handleResult(info);

        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName());

        for (ChannelTabInfo tabInfo : info.getTabs()) {
            String name;
            switch (tabInfo.getName()) {
                default:
                case "Videos":
                    name = getString(R.string.channel_tab_videos);
                    break;
                case "Playlists":
                    name = getString(R.string.channel_tab_playlists);
                    break;
                case "Popular tracks":
                    name = getString(R.string.channel_tab_popular_tracks);
                    break;
                case "Tracks":
                    name = getString(R.string.channel_tab_tracks);
                    break;
                case "Albums":
                    name = getString(R.string.channel_tab_albums);
                    break;
                case "Reposts":
                    name = getString(R.string.channel_tab_reposts);
                    break;
                case "Events":
                    name = getString(R.string.channel_tab_events);
                    break;
            }
            pageAdapter.addFragment(ChannelTabFragment.getInstance(tabInfo), name);
        }

        pageAdapter.notifyDataSetUpdate();

        if (pageAdapter.getCount() < 2) {
            tabLayout.setVisibility(View.GONE);
        } else {
            tabLayout.setVisibility(View.VISIBLE);
        }

        headerSubscribersTextView.setVisibility(View.VISIBLE);
        if (info.getSubscriberCount() >= 0) {
            headerSubscribersTextView.setText(Localization.shortSubscriberCount(activity, info.getSubscriberCount()));
        } else {
            headerSubscribersTextView.setText(R.string.subscribers_count_not_available);
        }

        if (disposables != null) disposables.clear();
        if (subscribeButtonMonitor != null) subscribeButtonMonitor.dispose();
        updateSubscription(info);
        monitorSubscription(info);

        pushToStack(serviceId, url, name);

        setupActionBar(info);
        initThumbnailViews(info);

        activity.invalidateOptionsMenu();

        setTitle(info.getName());

        if (!info.getErrors().isEmpty()) {
            showSnackBarError(info.getErrors(),
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(info.getServiceId()),
                    info.getUrl(),
                    0);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        if (exception instanceof ContentNotAvailableException) {
            showError(getString(R.string.content_not_available), false);
        } else {
            int errorId = exception instanceof ExtractionException ? R.string.parsing_error : R.string.general_error;
            onUnrecoverableError(exception,
                    UserAction.REQUESTED_CHANNEL,
                    NewPipe.getNameOfService(serviceId),
                    url,
                    errorId);
        }
        return true;
    }
}
