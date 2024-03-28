package org.schabi.newpipe.fragments.list.channel;

import static org.schabi.newpipe.ktx.TextViewUtils.animateTextColor;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateBackgroundColor;

import android.content.Context;
import android.content.SharedPreferences;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.jakewharton.rxbinding4.view.RxView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.NotificationMode;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.databinding.FragmentChannelBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.detail.TabAdapter;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.feed.notifications.NotificationHelper;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelFragment extends BaseStateFragment<ChannelInfo>
        implements StateSaver.WriteRead {

    private static final int BUTTON_DEBOUNCE_INTERVAL = 100;
    private static final String PICASSO_CHANNEL_TAG = "PICASSO_CHANNEL_TAG";

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private ChannelInfo currentInfo;
    private Disposable currentWorker;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private Disposable subscribeButtonMonitor;
    private SubscriptionManager subscriptionManager;
    private int lastTab;
    private boolean channelContentNotSupported = false;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private FragmentChannelBinding binding;
    private TabAdapter tabAdapter;

    private MenuItem menuRssButton;
    private MenuItem menuNotifyButton;
    private SubscriptionEntity channelSubscription;

    public static ChannelFragment getInstance(final int serviceId, final String url,
                                              final String name) {
        final ChannelFragment instance = new ChannelFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    private void setInitialData(final int sid, final String u, final String title) {
        this.serviceId = sid;
        this.url = u;
        this.name = !TextUtils.isEmpty(title) ? title : "";
    }


    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        subscriptionManager = new SubscriptionManager(activity);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentChannelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override // called from onViewCreated in BaseFragment.onViewCreated
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        tabAdapter = new TabAdapter(getChildFragmentManager());
        binding.viewPager.setAdapter(tabAdapter);
        binding.tabLayout.setupWithViewPager(binding.viewPager);

        setTitle(name);
        binding.channelTitleView.setText(name);
        if (!ImageStrategy.shouldLoadImages()) {
            // do not waste space for the banner if it is not going to be loaded
            binding.channelBannerImage.setImageDrawable(null);
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        final View.OnClickListener openSubChannel = v -> {
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
        };
        binding.subChannelAvatarView.setOnClickListener(openSubChannel);
        binding.subChannelTitleView.setOnClickListener(openSubChannel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        disposables.clear();
        binding = null;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_channel, menu);

        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menuRssButton = menu.findItem(R.id.menu_item_rss);
        menuNotifyButton = menu.findItem(R.id.menu_item_notify);
        updateNotifyButton(channelSubscription);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_notify:
                final boolean value = !item.isChecked();
                item.setEnabled(false);
                setNotify(value);
                break;
            case R.id.action_settings:
                NavigationHelper.openSettings(requireContext());
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
                            currentInfo.getAvatars());
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
            animate(binding.channelSubscribeButton, false, 100);
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

    private Function<Object, Object> mapOnSubscribe(final SubscriptionEntity subscription) {
        return (@NonNull Object o) -> {
            subscriptionManager.insertSubscription(subscription);
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

    private Disposable monitorSubscribeButton(final Function<Object, Object> action) {
        final Consumer<Object> onNext = (@NonNull Object o) -> {
            if (DEBUG) {
                Log.d(TAG, "Changed subscription status to this channel!");
            }
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                showSnackBarError(new ErrorInfo(throwable, UserAction.SUBSCRIPTION_CHANGE,
                        "Changing subscription for " + currentInfo.getUrl(), currentInfo));

        /* Emit clicks from main thread unto io thread */
        return RxView.clicks(binding.channelSubscribeButton)
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
                        ImageStrategy.imageListToDbUrl(info.getAvatars()),
                        info.getDescription(),
                        info.getSubscriberCount());
                channelSubscription = null;
                updateNotifyButton(null);
                subscribeButtonMonitor = monitorSubscribeButton(mapOnSubscribe(channel));
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Found subscription to this channel!");
                }
                channelSubscription = subscriptionEntities.get(0);
                updateNotifyButton(channelSubscription);
                subscribeButtonMonitor =
                        monitorSubscribeButton(mapOnUnsubscribe(channelSubscription));
            }
        };
    }

    private void updateSubscribeButton(final boolean isSubscribed) {
        if (DEBUG) {
            Log.d(TAG, "updateSubscribeButton() called with: "
                    + "isSubscribed = [" + isSubscribed + "]");
        }

        final boolean isButtonVisible = binding.channelSubscribeButton.getVisibility()
                == View.VISIBLE;
        final int backgroundDuration = isButtonVisible ? 300 : 0;
        final int textDuration = isButtonVisible ? 200 : 0;

        final int subscribedBackground = ContextCompat
                .getColor(activity, R.color.subscribed_background_color);
        final int subscribedText = ContextCompat.getColor(activity, R.color.subscribed_text_color);
        final int subscribeBackground = ColorUtils.blendARGB(ThemeHelper
                .resolveColorFromAttr(activity, R.attr.colorPrimary), subscribedBackground, 0.35f);
        final int subscribeText = ContextCompat.getColor(activity, R.color.subscribe_text_color);

        if (isSubscribed) {
            binding.channelSubscribeButton.setText(R.string.subscribed_button_title);
            animateBackgroundColor(binding.channelSubscribeButton, backgroundDuration,
                    subscribeBackground, subscribedBackground);
            animateTextColor(binding.channelSubscribeButton, textDuration, subscribeText,
                    subscribedText);
        } else {
            binding.channelSubscribeButton.setText(R.string.subscribe_button_title);
            animateBackgroundColor(binding.channelSubscribeButton, backgroundDuration,
                    subscribedBackground, subscribeBackground);
            animateTextColor(binding.channelSubscribeButton, textDuration, subscribedText,
                    subscribeText);
        }

        animate(binding.channelSubscribeButton, true, 100, AnimationType.LIGHT_SCALE_AND_ALPHA);
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
        Snackbar.make(binding.getRoot(), R.string.you_successfully_subscribed, Snackbar.LENGTH_LONG)
                .setAction(R.string.get_notified, v -> setNotify(true))
                .setActionTextColor(Color.YELLOW)
                .show();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void updateTabs() {
        tabAdapter.clearAllItems();

        if (currentInfo != null && !channelContentNotSupported) {
            final Context context = requireContext();
            final SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(context);

            for (final ListLinkHandler linkHandler : currentInfo.getTabs()) {
                final String tab = linkHandler.getContentFilters().get(0);
                if (ChannelTabHelper.showChannelTab(context, preferences, tab)) {
                    final ChannelTabFragment channelTabFragment =
                            ChannelTabFragment.getInstance(serviceId, linkHandler, name);
                    channelTabFragment.useAsFrontPage(useAsFrontPage);
                    tabAdapter.addFragment(channelTabFragment,
                            context.getString(ChannelTabHelper.getTranslationKey(tab)));
                }
            }

            if (ChannelTabHelper.showChannelTab(
                    context, preferences, R.string.show_channel_tabs_about)) {
                tabAdapter.addFragment(
                        ChannelAboutFragment.getInstance(currentInfo),
                        context.getString(R.string.channel_tab_about));
            }
        }

        tabAdapter.notifyDataSetUpdate();

        for (int i = 0; i < tabAdapter.getCount(); i++) {
            binding.tabLayout.getTabAt(i).setText(tabAdapter.getItemTitle(i));
        }

        // Restore previously selected tab
        final TabLayout.Tab ltab = binding.tabLayout.getTabAt(lastTab);
        if (ltab != null) {
            binding.tabLayout.selectTab(ltab);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public String generateSuffix() {
        return null;
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        objectsToSave.add(currentInfo);
        objectsToSave.add(binding == null ? 0 : binding.tabLayout.getSelectedTabPosition());
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) {
        currentInfo = (ChannelInfo) savedObjects.poll();
        lastTab = (Integer) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt("LastTab", binding.tabLayout.getSelectedTabPosition());
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        lastTab = savedInstanceState.getInt("LastTab", 0);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (currentInfo == null) {
            startLoading(false);
        } else {
            handleResult(currentInfo);
        }
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        currentInfo = null;
        updateTabs();
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad);
    }

    private void runWorker(final boolean forceLoad) {
        currentWorker = ExtractorHelper.getChannelInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    isLoading.set(false);
                    handleResult(result);
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_CHANNEL,
                        url == null ? "No URL" : url, serviceId)));
    }

    @Override
    public void showLoading() {
        super.showLoading();
        PicassoHelper.cancelTag(PICASSO_CHANNEL_TAG);
        animate(binding.channelSubscribeButton, false, 100);
    }

    @Override
    public void handleResult(@NonNull final ChannelInfo result) {
        super.handleResult(result);
        currentInfo = result;
        setInitialData(result.getServiceId(), result.getOriginalUrl(), result.getName());

        if (ImageStrategy.shouldLoadImages() && !result.getBanners().isEmpty()) {
            PicassoHelper.loadBanner(result.getBanners()).tag(PICASSO_CHANNEL_TAG)
                    .into(binding.channelBannerImage);
        } else {
            // do not waste space for the banner, if the user disabled images or there is not one
            binding.channelBannerImage.setImageDrawable(null);
        }

        PicassoHelper.loadAvatar(result.getAvatars()).tag(PICASSO_CHANNEL_TAG)
                .into(binding.channelAvatarView);
        PicassoHelper.loadAvatar(result.getParentChannelAvatars()).tag(PICASSO_CHANNEL_TAG)
                .into(binding.subChannelAvatarView);

        binding.channelTitleView.setText(result.getName());
        binding.channelSubscriberView.setVisibility(View.VISIBLE);
        if (result.getSubscriberCount() >= 0) {
            binding.channelSubscriberView.setText(Localization
                    .shortSubscriberCount(activity, result.getSubscriberCount()));
        } else {
            binding.channelSubscriberView.setText(R.string.subscribers_count_not_available);
        }

        if (!TextUtils.isEmpty(currentInfo.getParentChannelName())) {
            binding.subChannelTitleView.setText(String.format(
                    getString(R.string.channel_created_by),
                    currentInfo.getParentChannelName())
            );
            binding.subChannelTitleView.setVisibility(View.VISIBLE);
            binding.subChannelAvatarView.setVisibility(View.VISIBLE);
        }

        if (menuRssButton != null) {
            menuRssButton.setVisible(!TextUtils.isEmpty(result.getFeedUrl()));
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

        updateTabs();
        updateSubscription(result);
        monitorSubscription(result);
    }

    private void showContentNotSupportedIfNeeded() {
        // channelBinding might not be initialized when handleResult() is called
        // (e.g. after rotating the screen, #6696)
        if (!channelContentNotSupported || binding == null) {
            return;
        }

        binding.errorContentNotSupported.setVisibility(View.VISIBLE);
        binding.channelKaomoji.setText("(︶︹︺)");
        binding.channelKaomoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 45f);
    }
}
