package org.schabi.newpipe.fragments.list.channel;

import static org.schabi.newpipe.ktx.TextViewUtils.animateTextColor;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateBackgroundColor;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.rxbinding4.view.RxView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.NotificationMode;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.databinding.ChannelHeaderBinding;
import org.schabi.newpipe.databinding.FragmentChannelBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.local.feed.notifications.NotificationHelper;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelFragment extends BaseListInfoFragment<StreamInfoItem, ChannelInfo>
        implements View.OnClickListener {

    private static final int BUTTON_DEBOUNCE_INTERVAL = 100;
    private static final String PICASSO_CHANNEL_TAG = "PICASSO_CHANNEL_TAG";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private Disposable subscribeButtonMonitor;

    private boolean channelContentNotSupported = false;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private SubscriptionManager subscriptionManager;

    private FragmentChannelBinding channelBinding;
    private ChannelHeaderBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;

    private MenuItem menuRssButton;
    private MenuItem menuNotifyButton;

    public static ChannelFragment getInstance(final int serviceId, final String url,
                                              final String name) {
        final ChannelFragment instance = new ChannelFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    public ChannelFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null && useAsFrontPage) {
            setTitle(currentInfo != null ? currentInfo.getName() : name);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        subscriptionManager = new SubscriptionManager(activity);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        channelBinding = FragmentChannelBinding.bind(rootView);
        showContentNotSupportedIfNeeded();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
        if (subscribeButtonMonitor != null) {
            subscribeButtonMonitor.dispose();
        }
        channelBinding = null;
        headerBinding = null;
        playlistControlBinding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        headerBinding = ChannelHeaderBinding
                .inflate(activity.getLayoutInflater(), itemsList, false);
        playlistControlBinding = headerBinding.playlistControl;

        return headerBinding::getRoot;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        headerBinding.subChannelTitleView.setOnClickListener(this);
        headerBinding.subChannelAvatarView.setOnClickListener(this);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (useAsFrontPage && supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        } else {
            inflater.inflate(R.menu.menu_channel, menu);

            if (DEBUG) {
                Log.d(TAG, "onCreateOptionsMenu() called with: "
                        + "menu = [" + menu + "], inflater = [" + inflater + "]");
            }
            menuRssButton = menu.findItem(R.id.menu_item_rss);
            menuNotifyButton = menu.findItem(R.id.menu_item_notify);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                NavigationHelper.openSettings(requireContext());
                break;
            case R.id.menu_item_notify:
                final boolean value = !item.isChecked();
                item.setEnabled(false);
                setNotify(value);
                break;
            case R.id.menu_item_rss:
                if (currentInfo != null) {
                    ShareUtils.openUrlInApp(requireContext(), currentInfo.getFeedUrl());
                }
                break;
            case R.id.menu_item_openInBrowser:
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getOriginalUrl());
                }
                break;
            case R.id.menu_item_share:
                if (currentInfo != null) {
                    ShareUtils.shareText(requireContext(), name, currentInfo.getOriginalUrl(),
                            currentInfo.getAvatarUrl());
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Channel Subscription
    //////////////////////////////////////////////////////////////////////////*/

    private void monitorSubscription(final ChannelInfo info) {
        final Consumer<Throwable> onError = (Throwable throwable) -> {
            animate(headerBinding.channelSubscribeButton, false, 100);
            showSnackBarError(new ErrorInfo(throwable, UserAction.SUBSCRIPTION_GET,
                    "Get subscription status", currentInfo));
        };

        final Observable<List<SubscriptionEntity>> observable = subscriptionManager
                .subscriptionTable()
                .getSubscriptionFlowable(info.getServiceId(), info.getUrl())
                .toObservable();

        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError));

        disposables.add(observable
                .map(List::isEmpty)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isEmpty -> updateSubscribeButton(!isEmpty), onError));

        disposables.add(observable
                .map(List::isEmpty)
                .distinctUntilChanged()
                .skip(1) // channel has just been opened
                .filter(x -> NotificationHelper.areNewStreamsNotificationsEnabled(requireContext()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isEmpty -> {
                    if (!isEmpty) {
                        showNotifySnackbar();
                    }
                }, onError));
    }

    private Function<Object, Object> mapOnSubscribe(final SubscriptionEntity subscription,
                                                    final ChannelInfo info) {
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
        if (DEBUG) {
            Log.d(TAG, "updateSubscription() called with: info = [" + info + "]");
        }
        final Action onComplete = () -> {
            if (DEBUG) {
                Log.d(TAG, "Updated subscription: " + info.getUrl());
            }
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                showSnackBarError(new ErrorInfo(throwable, UserAction.SUBSCRIPTION_UPDATE,
                        "Updating subscription for " + info.getUrl(), info));

        disposables.add(subscriptionManager.updateChannelInfo(info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, onError));
    }

    private Disposable monitorSubscribeButton(final Button subscribeButton,
                                              final Function<Object, Object> action) {
        final Consumer<Object> onNext = (@NonNull Object o) -> {
            if (DEBUG) {
                Log.d(TAG, "Changed subscription status to this channel!");
            }
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                showSnackBarError(new ErrorInfo(throwable, UserAction.SUBSCRIPTION_CHANGE,
                        "Changing subscription for " + currentInfo.getUrl(), currentInfo));

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
            if (DEBUG) {
                Log.d(TAG, "subscriptionManager.subscriptionTable.doOnNext() called with: "
                        + "subscriptionEntities = [" + subscriptionEntities + "]");
            }
            if (subscribeButtonMonitor != null) {
                subscribeButtonMonitor.dispose();
            }

            if (subscriptionEntities.isEmpty()) {
                if (DEBUG) {
                    Log.d(TAG, "No subscription to this channel!");
                }
                final SubscriptionEntity channel = new SubscriptionEntity();
                channel.setServiceId(info.getServiceId());
                channel.setUrl(info.getUrl());
                channel.setData(info.getName(),
                        info.getAvatarUrl(),
                        info.getDescription(),
                        info.getSubscriberCount());
                updateNotifyButton(null);
                subscribeButtonMonitor = monitorSubscribeButton(
                        headerBinding.channelSubscribeButton, mapOnSubscribe(channel, info));
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Found subscription to this channel!");
                }
                final SubscriptionEntity subscription = subscriptionEntities.get(0);
                updateNotifyButton(subscription);
                subscribeButtonMonitor = monitorSubscribeButton(
                        headerBinding.channelSubscribeButton, mapOnUnsubscribe(subscription));
            }
        };
    }

    private void updateSubscribeButton(final boolean isSubscribed) {
        if (DEBUG) {
            Log.d(TAG, "updateSubscribeButton() called with: "
                    + "isSubscribed = [" + isSubscribed + "]");
        }

        final boolean isButtonVisible = headerBinding.channelSubscribeButton.getVisibility()
                == View.VISIBLE;
        final int backgroundDuration = isButtonVisible ? 300 : 0;
        final int textDuration = isButtonVisible ? 200 : 0;

        final int subscribeBackground = ThemeHelper
                .resolveColorFromAttr(activity, R.attr.colorPrimary);
        final int subscribeText = ContextCompat.getColor(activity, R.color.subscribe_text_color);
        final int subscribedBackground = ContextCompat
                .getColor(activity, R.color.subscribed_background_color);
        final int subscribedText = ContextCompat.getColor(activity, R.color.subscribed_text_color);

        if (!isSubscribed) {
            headerBinding.channelSubscribeButton.setText(R.string.subscribe_button_title);
            animateBackgroundColor(headerBinding.channelSubscribeButton, backgroundDuration,
                    subscribedBackground, subscribeBackground);
            animateTextColor(headerBinding.channelSubscribeButton, textDuration, subscribedText,
                    subscribeText);
        } else {
            headerBinding.channelSubscribeButton.setText(R.string.subscribed_button_title);
            animateBackgroundColor(headerBinding.channelSubscribeButton, backgroundDuration,
                    subscribeBackground, subscribedBackground);
            animateTextColor(headerBinding.channelSubscribeButton, textDuration, subscribeText,
                    subscribedText);
        }

        animate(headerBinding.channelSubscribeButton, true, 100,
                AnimationType.LIGHT_SCALE_AND_ALPHA);
    }

    private void updateNotifyButton(@Nullable final SubscriptionEntity subscription) {
        if (menuNotifyButton == null) {
            return;
        }
        if (subscription != null) {
            menuNotifyButton.setEnabled(
                    NotificationHelper.areNewStreamsNotificationsEnabled(requireContext())
            );
            menuNotifyButton.setChecked(
                    subscription.getNotificationMode() == NotificationMode.ENABLED
            );
        }

        menuNotifyButton.setVisible(subscription != null);
    }

    private void setNotify(final boolean isEnabled) {
        disposables.add(
                subscriptionManager
                        .updateNotificationMode(
                                currentInfo.getServiceId(),
                                currentInfo.getUrl(),
                                isEnabled ? NotificationMode.ENABLED : NotificationMode.DISABLED)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
        );
    }

    /**
     * Show a snackbar with the option to enable notifications on new streams for this channel.
     */
    private void showNotifySnackbar() {
        Snackbar.make(itemsList, R.string.you_successfully_subscribed, Snackbar.LENGTH_LONG)
                .setAction(R.string.get_notified, v -> setNotify(true))
                .setActionTextColor(Color.YELLOW)
                .show();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelItems(serviceId, url, currentNextPage);
    }

    @Override
    protected Single<ChannelInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getChannelInfo(serviceId, url, forceLoad);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(final View v) {
        if (isLoading.get() || currentInfo == null) {
            return;
        }

        switch (v.getId()) {
            case R.id.sub_channel_avatar_view:
            case R.id.sub_channel_title_view:
                if (!TextUtils.isEmpty(currentInfo.getParentChannelUrl())) {
                    try {
                        NavigationHelper.openChannelFragment(getFM(), currentInfo.getServiceId(),
                                currentInfo.getParentChannelUrl(),
                                currentInfo.getParentChannelName());
                    } catch (final Exception e) {
                        ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e);
                    }
                } else if (DEBUG) {
                    Log.i(TAG, "Can't open parent channel because we got no channel URL");
                }
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        PicassoHelper.cancelTag(PICASSO_CHANNEL_TAG);
        animate(headerBinding.channelSubscribeButton, false, 100);
    }

    @Override
    public void handleResult(@NonNull final ChannelInfo result) {
        super.handleResult(result);

        headerBinding.getRoot().setVisibility(View.VISIBLE);
        PicassoHelper.loadBanner(result.getBannerUrl()).tag(PICASSO_CHANNEL_TAG)
                .into(headerBinding.channelBannerImage);
        PicassoHelper.loadAvatar(result.getAvatarUrl()).tag(PICASSO_CHANNEL_TAG)
                .into(headerBinding.channelAvatarView);
        PicassoHelper.loadAvatar(result.getParentChannelAvatarUrl()).tag(PICASSO_CHANNEL_TAG)
                .into(headerBinding.subChannelAvatarView);

        headerBinding.channelSubscriberView.setVisibility(View.VISIBLE);
        if (result.getSubscriberCount() >= 0) {
            headerBinding.channelSubscriberView.setText(Localization
                    .shortSubscriberCount(activity, result.getSubscriberCount()));
        } else {
            headerBinding.channelSubscriberView.setText(R.string.subscribers_count_not_available);
        }

        if (!TextUtils.isEmpty(currentInfo.getParentChannelName())) {
            headerBinding.subChannelTitleView.setText(String.format(
                    getString(R.string.channel_created_by),
                    currentInfo.getParentChannelName())
            );
            headerBinding.subChannelTitleView.setVisibility(View.VISIBLE);
            headerBinding.subChannelAvatarView.setVisibility(View.VISIBLE);
        } else {
            headerBinding.subChannelTitleView.setVisibility(View.GONE);
        }

        if (menuRssButton != null) {
            menuRssButton.setVisible(!TextUtils.isEmpty(result.getFeedUrl()));
        }

        // PlaylistControls should be visible only if there is some item in
        // infoListAdapter other than header
        if (infoListAdapter.getItemCount() != 1) {
            playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
        } else {
            playlistControlBinding.getRoot().setVisibility(View.GONE);
        }

        channelContentNotSupported = false;
        for (final Throwable throwable : result.getErrors()) {
            if (throwable instanceof ContentNotSupportedException) {
                channelContentNotSupported = true;
                showContentNotSupportedIfNeeded();
                break;
            }
        }

        disposables.clear();
        if (subscribeButtonMonitor != null) {
            subscribeButtonMonitor.dispose();
        }
        updateSubscription(result);
        monitorSubscription(result);

        playlistControlBinding.playlistCtrlPlayAllButton
                .setOnClickListener(view -> NavigationHelper
                        .playOnMainPlayer(activity, getPlayQueue()));
        playlistControlBinding.playlistCtrlPlayPopupButton
                .setOnClickListener(view -> NavigationHelper
                        .playOnPopupPlayer(activity, getPlayQueue(), false));
        playlistControlBinding.playlistCtrlPlayBgButton
                .setOnClickListener(view -> NavigationHelper
                        .playOnBackgroundPlayer(activity, getPlayQueue(), false));

        playlistControlBinding.playlistCtrlPlayPopupButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, getPlayQueue(), PlayerType.POPUP);
            return true;
        });

        playlistControlBinding.playlistCtrlPlayBgButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, getPlayQueue(), PlayerType.AUDIO);
            return true;
        });
    }

    private void showContentNotSupportedIfNeeded() {
        // channelBinding might not be initialized when handleResult() is called
        // (e.g. after rotating the screen, #6696)
        if (!channelContentNotSupported || channelBinding == null) {
            return;
        }

        channelBinding.errorContentNotSupported.setVisibility(View.VISIBLE);
        channelBinding.channelKaomoji.setText("(︶︹︺)");
        channelBinding.channelKaomoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 45f);
        channelBinding.channelNoVideos.setVisibility(View.GONE);
    }

    private PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new ChannelPlayQueue(currentInfo.getServiceId(), currentInfo.getUrl(),
                currentInfo.getNextPage(), streamItems, 0);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        if (!useAsFrontPage) {
            headerBinding.channelTitleView.setText(title);
        }
    }
}
