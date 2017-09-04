package org.schabi.newpipe.fragments.list.channel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
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

import com.jakewharton.rxbinding2.view.RxView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
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

public class ChannelFragment extends BaseListInfoFragment<ChannelInfo> {

    private CompositeDisposable disposables = new CompositeDisposable();
    private Disposable subscribeButtonMonitor;
    private SubscriptionService subscriptionService;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View headerRootLayout;
    private ImageView headerChannelBanner;
    private ImageView headerAvatarView;
    private TextView headerTitleView;
    private TextView headerSubscribersTextView;
    private Button headerSubscribeButton;

    private MenuItem menuRssButton;

    public static ChannelFragment getInstance(int serviceId, String url, String name) {
        ChannelFragment instance = new ChannelFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        subscriptionService = SubscriptionService.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.clear();
        if (subscribeButtonMonitor != null) subscribeButtonMonitor.dispose();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    protected View getListHeader() {
        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.channel_header, itemsList, false);
        headerChannelBanner = headerRootLayout.findViewById(R.id.channel_banner_image);
        headerAvatarView = headerRootLayout.findViewById(R.id.channel_avatar_view);
        headerTitleView = headerRootLayout.findViewById(R.id.channel_title_view);
        headerSubscribersTextView = headerRootLayout.findViewById(R.id.channel_subscriber_view);
        headerSubscribeButton = headerRootLayout.findViewById(R.id.channel_subscribe_button);
        return headerRootLayout;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "], inflater = [" + inflater + "]");
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_channel, menu);

        menuRssButton = menu.findItem(R.id.menu_item_rss);
        if (currentInfo != null) {
            menuRssButton.setVisible(!TextUtils.isEmpty(currentInfo.feed_url));
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_rss: {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(currentInfo.feed_url));
                startActivity(intent);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Channel Subscription
    //////////////////////////////////////////////////////////////////////////*/

    private static final int BUTTON_DEBOUNCE_INTERVAL = 100;

    private void monitorSubscription(final ChannelInfo info) {
        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                animateView(headerSubscribeButton, false, 100);
                showSnackBarError(throwable, UserAction.SUBSCRIPTION, NewPipe.getNameOfService(currentInfo.service_id), "Get subscription status", 0);
            }
        };

        final Observable<List<SubscriptionEntity>> observable = subscriptionService.subscriptionTable()
                .getSubscription(info.service_id, info.url)
                .toObservable();

        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError));

        disposables.add(observable
                // Some updates are very rapid (when calling the updateSubscription(info), for example)
                // so only update the UI for the latest emission ("sync" the subscribe button's state)
                .debounce(100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<SubscriptionEntity>>() {
                    @Override
                    public void accept(List<SubscriptionEntity> subscriptionEntities) throws Exception {
                        updateSubscribeButton(!subscriptionEntities.isEmpty());
                    }
                }, onError));

    }

    private Function<Object, Object> mapOnSubscribe(final SubscriptionEntity subscription) {
        return new Function<Object, Object>() {
            @Override
            public Object apply(@NonNull Object o) throws Exception {
                subscriptionService.subscriptionTable().insert(subscription);
                return o;
            }
        };
    }

    private Function<Object, Object> mapOnUnsubscribe(final SubscriptionEntity subscription) {
        return new Function<Object, Object>() {
            @Override
            public Object apply(@NonNull Object o) throws Exception {
                subscriptionService.subscriptionTable().delete(subscription);
                return o;
            }
        };
    }

    private void updateSubscription(final ChannelInfo info) {
        if (DEBUG) Log.d(TAG, "updateSubscription() called with: info = [" + info + "]");
        final Action onComplete = new Action() {
            @Override
            public void run() throws Exception {
                if (DEBUG) Log.d(TAG, "Updated subscription: " + info.url);
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                onUnrecoverableError(throwable, UserAction.SUBSCRIPTION, NewPipe.getNameOfService(info.service_id), "Updating Subscription for " + info.url, R.string.subscription_update_failed);
            }
        };

        disposables.add(subscriptionService.updateChannelInfo(info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, onError));
    }

    private Disposable monitorSubscribeButton(final Button subscribeButton, final Function<Object, Object> action) {
        final Consumer<Object> onNext = new Consumer<Object>() {
            @Override
            public void accept(@NonNull Object o) throws Exception {
                if (DEBUG) Log.d(TAG, "Changed subscription status to this channel!");
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                onUnrecoverableError(throwable, UserAction.SUBSCRIPTION, NewPipe.getNameOfService(currentInfo.service_id), "Subscription Change", R.string.subscription_change_failed);
            }
        };

        /* Emit clicks from main thread unto io thread */
        return RxView.clicks(subscribeButton)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .debounce(BUTTON_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS) // Ignore rapid clicks
                .map(action)
                .subscribe(onNext, onError);
    }

    private Consumer<List<SubscriptionEntity>> getSubscribeUpdateMonitor(final ChannelInfo info) {
        return new Consumer<List<SubscriptionEntity>>() {
            @Override
            public void accept(List<SubscriptionEntity> subscriptionEntities) throws Exception {
                if (DEBUG)
                    Log.d(TAG, "subscriptionService.subscriptionTable.doOnNext() called with: subscriptionEntities = [" + subscriptionEntities + "]");
                if (subscribeButtonMonitor != null) subscribeButtonMonitor.dispose();

                if (subscriptionEntities.isEmpty()) {
                    if (DEBUG) Log.d(TAG, "No subscription to this channel!");
                    SubscriptionEntity channel = new SubscriptionEntity();
                    channel.setServiceId(info.service_id);
                    channel.setUrl(info.url);
                    channel.setData(info.name, info.avatar_url, info.description, info.subscriber_count);
                    subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton, mapOnSubscribe(channel));
                } else {
                    if (DEBUG) Log.d(TAG, "Found subscription to this channel!");
                    final SubscriptionEntity subscription = subscriptionEntities.get(0);
                    subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton, mapOnUnsubscribe(subscription));
                }
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
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.NextItemsResult> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelItems(serviceId, currentNextItemsUrl);
    }

    @Override
    protected Single<ChannelInfo> loadResult(boolean forceLoad) {
        return ExtractorHelper.getChannelInfo(serviceId, url, forceLoad);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();

        imageLoader.cancelDisplayTask(headerChannelBanner);
        imageLoader.cancelDisplayTask(headerAvatarView);
        animateView(headerSubscribeButton, false, 100);
    }

    @Override
    public void handleResult(@NonNull ChannelInfo result) {
        super.handleResult(result);

        headerRootLayout.setVisibility(View.VISIBLE);
        imageLoader.displayImage(result.banner_url, headerChannelBanner, DISPLAY_BANNER_OPTIONS);
        imageLoader.displayImage(result.avatar_url, headerAvatarView, DISPLAY_AVATAR_OPTIONS);

        if (result.subscriber_count != -1) {
            headerSubscribersTextView.setText(Localization.localizeSubscribersCount(activity, result.subscriber_count));
            headerSubscribersTextView.setVisibility(View.VISIBLE);
        } else headerSubscribersTextView.setVisibility(View.GONE);

        if (menuRssButton != null) menuRssButton.setVisible(!TextUtils.isEmpty(result.feed_url));

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.REQUESTED_CHANNEL, NewPipe.getNameOfService(result.service_id), result.url, 0);
        }

        if (disposables != null) disposables.clear();
        if (subscribeButtonMonitor != null) subscribeButtonMonitor.dispose();
        updateSubscription(result);
        monitorSubscription(result);
    }

    @Override
    public void handleNextItems(ListExtractor.NextItemsResult result) {
        super.handleNextItems(result);

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.REQUESTED_CHANNEL, NewPipe.getNameOfService(serviceId),
                    "Get next page of: " + url, R.string.general_error);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnError
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        int errorId = exception instanceof ExtractionException ? R.string.parsing_error : R.string.general_error;
        onUnrecoverableError(exception, UserAction.REQUESTED_CHANNEL, NewPipe.getNameOfService(serviceId), url, errorId);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        headerTitleView.setText(title);
    }
}
