package org.schabi.newpipe.fragments.list.channel;

import static org.schabi.newpipe.ktx.ViewUtils.animate;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.evernote.android.state.State;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.jakewharton.rxbinding4.view.RxView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
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
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.feed.notifications.NotificationHelper;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.local.subscription.ChannelGroupCreation;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.profiles.ProfileRecord;
import org.schabi.newpipe.settings.notifications.NotificationConfigDialog;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorApiCompat;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ExpandableSearchViewHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ExtractorImageCompat;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import coil3.util.CoilUtils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelFragment extends BaseStateFragment<ChannelInfo>
        implements StateSaver.WriteRead {

    private static final int BUTTON_DEBOUNCE_INTERVAL = 100;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;
    @State
    protected String selectedChannelVideoSort;
    @State
    protected String channelSearchQuery = "";
    @State
    protected boolean channelSearchExpanded;

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
    private MenuItem menuNotificationKeywordsButton;
    private MenuItem menuAutomaticDownloadsButton;
    private MenuItem menuSubscribeProfileButton;
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

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        final MenuProvider menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull final Menu menu,
                                     @NonNull final MenuInflater inflater) {
                inflater.inflate(R.menu.menu_channel, menu);

                final MenuItem searchItem = menu.findItem(R.id.menu_item_search_content);
                ExpandableSearchViewHelper.configure(
                        searchItem,
                        getString(R.string.contextual_search_hint, name),
                        channelSearchQuery,
                        channelSearchExpanded,
                        query -> {
                            channelSearchQuery = ContextualSearchHelper.normalizeQuery(query);
                            applySearchToCurrentTab();
                        },
                        expanded -> channelSearchExpanded = expanded);

                if (DEBUG) {
                    Log.d(TAG, "onCreateOptionsMenu() called with: "
                            + "menu = [" + menu + "], inflater = [" + inflater + "]");
                }

            }

            @Override
            public void onPrepareMenu(@NonNull final Menu menu) {
                // MainFragment remains resumed underneath detail fragments and contributes its
                // global search action to the same activity menu. A channel has its own contextual
                // search, so showing both actions is ambiguous and the global action can navigate
                // away to SearchFragment unexpectedly.
                final MenuItem globalSearchItem = menu.findItem(R.id.action_search);
                if (globalSearchItem != null) {
                    globalSearchItem.setVisible(false);
                }
                menuRssButton = menu.findItem(R.id.menu_item_rss);
                menuNotifyButton = menu.findItem(R.id.menu_item_notify);
                menuNotificationKeywordsButton =
                        menu.findItem(R.id.menu_item_notification_keywords);
                menuAutomaticDownloadsButton = menu.findItem(R.id.menu_item_automatic_downloads);
                menuSubscribeProfileButton = menu.findItem(R.id.menu_item_subscribe_profile);
                updateSubscribeProfileButton();
                updateRssButton();
                updateNotifyButton(channelSubscription);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull final MenuItem item) {
                final int itemId = item.getItemId();
                if (itemId == R.id.menu_item_notify) {
                    final boolean value = !item.isChecked();
                    item.setEnabled(false);
                    setNotify(value);
                } else if (itemId == R.id.menu_item_notification_keywords) {
                    showNotificationConfigDialog();
                } else if (itemId == R.id.menu_item_automatic_downloads) {
                    if (channelSubscription != null && currentInfo != null) {
                        org.schabi.newpipe.download.AutomaticDownloads.configure(requireContext(),
                                channelSubscription.getUid(), currentInfo.getServiceId(),
                                currentInfo.getUrl(), currentInfo.getName());
                    }
                } else if (itemId == R.id.menu_item_subscribe_profile) {
                    showSubscribeToProfileDialog();
                } else if (itemId == R.id.action_settings) {
                    NavigationHelper.openSettings(requireContext());
                } else if (itemId == R.id.menu_item_rss) {
                    if (currentInfo != null) {
                        ShareUtils.openUrlInApp(requireContext(), currentInfo.getFeedUrl());
                    }
                } else if (itemId == R.id.menu_item_openInBrowser) {
                    if (currentInfo != null) {
                        ShareUtils.openUrlInBrowser(requireContext(),
                                currentInfo.getOriginalUrl());
                    }
                } else if (itemId == R.id.menu_item_share) {
                    if (currentInfo != null) {
                        ShareUtils.shareText(requireContext(), name,
                                currentInfo.getOriginalUrl(), currentInfo.getAvatars());
                    }
                } else {
                    return false;
                }
                return true;
            }
        };
        activity.addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void useAsFrontPage(final boolean value) {
        super.useAsFrontPage(value);
        updateSwipeState();
    }

    @Override // called from onViewCreated in BaseFragment.onViewCreated
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        tabAdapter = new TabAdapter(getChildFragmentManager());
        binding.viewPager.setAdapter(tabAdapter);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
        updateSwipeState();

        setTitle(name);
        binding.channelTitleView.setText(name);
        if (!ImageStrategy.shouldLoadImages()) {
            // do not waste space for the banner if it is not going to be loaded
            binding.channelBannerContainer.setVisibility(View.GONE);
        }
        applyLandscapeHeaderPolicy();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLandscapeHeaderPolicy();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSwipeState();
    }

    static boolean shouldUseCompactLandscapeHeader(final int orientation,
                                                   final boolean largeScreenDevice) {
        return orientation == Configuration.ORIENTATION_LANDSCAPE && !largeScreenDevice;
    }

    private void applyLandscapeHeaderPolicy() {
        if (binding == null || activity == null) {
            return;
        }

        final boolean largeScreenDevice = DeviceUtils.isTablet(activity)
                || DeviceUtils.isTv(activity)
                || DeviceUtils.isDesktopMode(activity);
        final boolean compactLandscape = shouldUseCompactLandscapeHeader(
                getResources().getConfiguration().orientation,
                largeScreenDevice);

        final ViewGroup.LayoutParams rawLayoutParams = binding.channelMetadataRow.getLayoutParams();
        if (rawLayoutParams instanceof AppBarLayout.LayoutParams) {
            final AppBarLayout.LayoutParams layoutParams =
                    (AppBarLayout.LayoutParams) rawLayoutParams;
            layoutParams.setScrollFlags(compactLandscape
                    ? AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL : 0);
            binding.channelMetadataRow.setLayoutParams(layoutParams);
        }

        if (compactLandscape) {
            binding.channelBannerContainer.setVisibility(View.GONE);
        } else if (currentInfo != null
                && ImageStrategy.shouldLoadImages()
                && !currentInfo.getBanners().isEmpty()) {
            binding.channelBannerContainer.setVisibility(View.VISIBLE);
        }
    }

    private void updateSwipeState() {
        if (binding == null || getContext() == null) {
            return;
        }
        final boolean swipeMainTabsOnPinnedChannels = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(
                        R.string.swipe_main_tabs_on_pinned_channels_key), true);
        binding.viewPager.setSwipeEnabled(ChannelSwipePolicy.isChannelSwipeEnabled(
                useAsFrontPage, swipeMainTabsOnPinnedChannels));
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
        binding.channelGroupButton.setOnClickListener(v -> showChannelGroupsDialog());
        binding.viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                applySearchToTab(position);
            }
        });
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
    // Channel Subscription
    //////////////////////////////////////////////////////////////////////////*/

    private void monitorSubscription(final ChannelInfo info) {
        final Consumer<Throwable> onError = (final Throwable throwable) -> {
            animate(binding.channelSubscriptionActions, false, 100);
            showSnackBarError(new ErrorInfo(throwable, UserAction.SUBSCRIPTION_GET,
                    "Get subscription status", currentInfo));
        };

        final Observable<List<SubscriptionEntity>> observable = subscriptionManager
                .getSubscriptionFlowable(info.getServiceId(), info.getUrl())
                .toObservable();

        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError));

        disposables.add(observable
                .map(subscriptions -> subscriptions.isEmpty()
                        || !subscriptionManager
                        .isSubscribedInCurrentMode(subscriptions.get(0)))
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isNotSubscribed -> updateSubscribeButton(!isNotSubscribed), onError));

        disposables.add(observable
                .map(subscriptions -> subscriptions.isEmpty()
                        || !subscriptionManager
                        .isSubscribedInCurrentMode(subscriptions.get(0)))
                .distinctUntilChanged()
                .skip(1) // channel has just been opened
                .filter(x -> NotificationHelper.areNewStreamsNotificationsEnabled(requireContext()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isNotSubscribed -> {
                    if (!isNotSubscribed) {
                        showNotifySnackbar();
                    }
                }, onError));
    }

    private Function<Object, Object> mapOnSubscribe(final SubscriptionEntity subscription) {
        return (@NonNull final Object o) -> {
            subscriptionManager.insertSubscription(subscription);
            return o;
        };
    }

    private Function<Object, Object> mapOnUnsubscribe(final SubscriptionEntity subscription) {
        return (@NonNull final Object o) -> {
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
        final Consumer<Object> onNext = (@NonNull final Object o) -> {
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
        return (final List<SubscriptionEntity> subscriptionEntities) -> {
            if (DEBUG) {
                Log.d(TAG, "subscriptionManager.subscriptionTable.doOnNext() called with: "
                        + "subscriptionEntities = [" + subscriptionEntities + "]");
            }
            if (subscribeButtonMonitor != null) {
                subscribeButtonMonitor.dispose();
            }

            final boolean subscribedInCurrentMode = !subscriptionEntities.isEmpty()
                    && subscriptionManager
                    .isSubscribedInCurrentMode(subscriptionEntities.get(0));
            if (!subscribedInCurrentMode) {
                if (DEBUG) {
                    Log.d(TAG, "No subscription to this channel!");
                }
                final SubscriptionEntity channel;
                if (subscriptionEntities.isEmpty()) {
                    channel = new SubscriptionEntity();
                    channel.setServiceId(info.getServiceId());
                    channel.setUrl(info.getUrl());
                    channel.setName(info.getName());
                    channel.setHandle(info.getHandle());
                    channel.setAvatarUrl(ImageStrategy.imageListToDbUrl(info.getAvatars()));
                    channel.setDescription(ExtractorApiCompat.descriptionText(info));
                    channel.setSubscriberCount(info.getSubscriberCount());
                } else {
                    channel = subscriptionEntities.get(0);
                }
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
        if (!KidsMode.isSubscriptionAllowed(requireContext(), serviceId)) {
            binding.channelSubscriptionActions.setVisibility(View.GONE);
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "updateSubscribeButton() called with: "
                    + "isSubscribed = [" + isSubscribed + "]");
        }

        binding.channelSubscribeButton.setActivated(isSubscribed);
        binding.channelGroupButton.setActivated(isSubscribed);
        binding.channelSubscribeButton.setText(isSubscribed
                ? R.string.subscribed_button_title : R.string.subscribe_button_title);

        animate(binding.channelSubscriptionActions, true, 100, AnimationType.LIGHT_SCALE_AND_ALPHA);
    }

    private void showChannelGroupsDialog() {
        if (!KidsMode.isSubscriptionAllowed(requireContext(), serviceId)) {
            return;
        }
        final ChannelInfo info = currentInfo;
        if (info == null) {
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        final String profileId = ProfileManager.getActiveProfileId(appContext);
        final FeedDatabaseManager feedManager = new FeedDatabaseManager(appContext, profileId);
        final SubscriptionManager activeManager = new SubscriptionManager(appContext, profileId);

        disposables.add(
                feedManager.groups()
                        .firstOrError()
                        .flatMap(groups -> activeManager
                                .getSubscriptionFlowable(
                                        info.getServiceId(),
                                        info.getUrl())
                                .firstOrError()
                                .flatMap(subscriptions -> {
                                    final SubscriptionEntity existing =
                                            subscriptions.isEmpty()
                                                    ? null : subscriptions.get(0);
                                    final boolean subscribedInCurrentMode =
                                            existing != null
                                                    && activeManager
                                                    .isSubscribedInCurrentMode(existing);
                                    if (existing == null) {
                                        return Single.just(
                                                new GroupPickerData(
                                                        profileId,
                                                        groups,
                                                        null,
                                                        false,
                                                        new HashSet<>()));
                                    }
                                    return feedManager
                                            .groupIdsForSubscription(existing.getUid())
                                            .firstOrError()
                                            .map(groupIds -> new GroupPickerData(
                                                    profileId,
                                                    groups,
                                                    existing,
                                                    subscribedInCurrentMode,
                                                    new HashSet<>(groupIds)));
                                }))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::showChannelGroupsPicker,
                                error -> showSnackBarError(new ErrorInfo(
                                        error,
                                        UserAction.SUBSCRIPTION_CHANGE,
                                        "Loading channel groups for " + info.getUrl(),
                                        info)))
        );
    }

    private void showChannelGroupsPicker(@NonNull final GroupPickerData data) {
        if (data.groups.isEmpty()) {
            ChannelGroupCreation.open(this, currentInfo, data.profileId, data.subscription,
                    data.subscribedInCurrentMode, disposables);
            return;
        }

        final CharSequence[] labels = new CharSequence[data.groups.size()];
        final boolean[] checked = new boolean[data.groups.size()];
        final Set<Long> selectedGroupIds = new HashSet<>(data.selectedGroupIds);
        for (int i = 0; i < data.groups.size(); i++) {
            final FeedGroupEntity group = data.groups.get(i);
            labels[i] = group.getName();
            checked[i] = selectedGroupIds.contains(group.getUid());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.channel_groups_dialog_title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    final long groupId = data.groups.get(which).getUid();
                    if (isChecked) {
                        selectedGroupIds.add(groupId);
                    } else {
                        selectedGroupIds.remove(groupId);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.channel_groups_create_from_channel, (dialog, which) ->
                        ChannelGroupCreation.open(this, currentInfo, data.profileId,
                                data.subscription, data.subscribedInCurrentMode, disposables))
                .setPositiveButton(R.string.save, (dialog, which) ->
                        applyChannelGroups(data, selectedGroupIds))
                .show();
    }

    private void applyChannelGroups(
            @NonNull final GroupPickerData data,
            @NonNull final Set<Long> selectedGroupIds) {
        final ChannelInfo info = currentInfo;
        if (info == null) {
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        disposables.add(
                Single.fromCallable(() -> {
                            final SubscriptionManager manager =
                                    new SubscriptionManager(appContext, data.profileId);
                            SubscriptionEntity subscription = data.subscription;

                            if (!selectedGroupIds.isEmpty()
                                    && (subscription == null
                                    || !data.subscribedInCurrentMode)) {
                                subscription = manager.insertSubscriptionAndReturn(
                                        SubscriptionEntity.from(info));
                            }
                            if (subscription == null) {
                                return false;
                            }

                            new FeedDatabaseManager(appContext, data.profileId)
                                    .setGroupsForSubscription(
                                            subscription.getUid(),
                                            new ArrayList<>(selectedGroupIds))
                                    .blockingAwait();
                            return true;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                changed -> {
                                    if (changed && binding != null) {
                                        Snackbar.make(
                                                binding.getRoot(),
                                                R.string.channel_groups_updated,
                                                Snackbar.LENGTH_SHORT
                                        ).show();
                                    }
                                },
                                error -> showSnackBarError(new ErrorInfo(
                                        error,
                                        UserAction.SUBSCRIPTION_CHANGE,
                                        "Updating channel groups for " + info.getUrl(),
                                        info)))
        );
    }

    private void showSubscribeToProfileDialog() {
        if (!KidsMode.isSubscriptionAllowed(requireContext(), serviceId)) {
            return;
        }
        final ChannelInfo info = currentInfo;
        if (info == null) {
            return;
        }

        final String activeProfileId = ProfileManager.getActiveProfileId(requireContext());
        final List<ProfileRecord> otherProfiles = new ArrayList<>();
        for (final ProfileRecord profile : ProfileManager.getProfiles(requireContext())) {
            if (!profile.getId().equals(activeProfileId)) {
                otherProfiles.add(profile);
            }
        }
        if (otherProfiles.isEmpty()) {
            return;
        }

        final CharSequence[] labels = new CharSequence[otherProfiles.size()];
        for (int i = 0; i < otherProfiles.size(); i++) {
            labels[i] = ProfileManager.getDisplayName(requireContext(), otherProfiles.get(i));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.channel_subscribe_to_profile_title)
                .setItems(labels, (dialog, which) ->
                        subscribeToProfile(otherProfiles.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void subscribeToProfile(@NonNull final ProfileRecord profile) {
        final ChannelInfo info = currentInfo;
        if (info == null) {
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        final String displayName =
                ProfileManager.getDisplayName(requireContext(), profile);
        disposables.add(
                Single.fromCallable(() -> {
                            final SubscriptionManager manager =
                                    new SubscriptionManager(appContext, profile.getId());
                            return manager.insertSubscriptionAndReturn(
                                    SubscriptionEntity.from(info));
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                ignored -> {
                                    if (binding != null) {
                                        Snackbar.make(
                                                binding.getRoot(),
                                                getString(
                                                        R.string.channel_subscribed_to_profile,
                                                        displayName),
                                                Snackbar.LENGTH_LONG
                                        ).show();
                                    }
                                },
                                error -> showSnackBarError(new ErrorInfo(
                                        error,
                                        UserAction.SUBSCRIPTION_CHANGE,
                                        "Subscribing " + info.getUrl()
                                                + " to profile " + displayName,
                                        info)))
        );
    }

    private static final class GroupPickerData {
        @NonNull
        private final String profileId;
        @NonNull
        private final List<FeedGroupEntity> groups;
        @Nullable
        private final SubscriptionEntity subscription;
        private final boolean subscribedInCurrentMode;
        @NonNull
        private final Set<Long> selectedGroupIds;

        private GroupPickerData(
                @NonNull final String profileId,
                @NonNull final List<FeedGroupEntity> groups,
                @Nullable final SubscriptionEntity subscription,
                final boolean subscribedInCurrentMode,
                @NonNull final Set<Long> selectedGroupIds) {
            this.profileId = profileId;
            this.groups = groups;
            this.subscription = subscription;
            this.subscribedInCurrentMode = subscribedInCurrentMode;
            this.selectedGroupIds = selectedGroupIds;
        }
    }

    private void updateSubscribeProfileButton() {
        if (menuSubscribeProfileButton == null || getContext() == null) {
            return;
        }
        menuSubscribeProfileButton.setVisible(
                currentInfo != null
                        && KidsMode.isSubscriptionAllowed(requireContext(), serviceId)
                        && ProfileManager.getProfiles(requireContext()).size() > 1);
    }

    private void updateRssButton() {
        if (menuRssButton == null || currentInfo == null) {
            return;
        }
        menuRssButton.setVisible(!TextUtils.isEmpty(currentInfo.getFeedUrl()));
    }

    private void updateNotifyButton(@Nullable final SubscriptionEntity subscription) {
        if (menuNotifyButton == null || menuNotificationKeywordsButton == null) {
            return;
        }
        if (subscription != null) {
            menuNotifyButton.setEnabled(
                    NotificationHelper.areNewStreamsNotificationsEnabled(requireContext())
            );
            menuNotifyButton.setChecked(
                    subscription.getNotificationMode() != NotificationMode.DISABLED
            );
        }

        menuNotifyButton.setVisible(subscription != null);
        menuNotificationKeywordsButton.setVisible(subscription != null);
        if (menuAutomaticDownloadsButton != null) {
            menuAutomaticDownloadsButton.setVisible(subscription != null);
        }
    }

    private void showNotificationConfigDialog() {
        final SubscriptionEntity subscription = channelSubscription;
        if (subscription == null) {
            return;
        }

        final String title = currentInfo == null ? name : currentInfo.getName();
        NotificationConfigDialog.show(
                this,
                title,
                subscription.getNotificationMode(),
                subscription.getNotificationKeywords(),
                (mode, keywords) -> updateNotificationSettings(
                        subscription, mode, keywords)
        );
    }

    private void updateNotificationSettings(
            @NonNull final SubscriptionEntity subscription,
            final int mode,
            @NonNull final String keywords) {
        disposables.add(
                subscriptionManager
                        .updateNotificationSettings(
                                subscription.getServiceId(),
                                subscription.getUrl(),
                                mode,
                                keywords)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
        );
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
                final String tab = ChannelTabHelper.getTabName(linkHandler);
                if (tab != null && ChannelTabHelper.showChannelTab(context, preferences, tab)
                        && (!org.schabi.newpipe.extractor.linkhandler.ChannelTabs.SEARCH.equals(tab)
                        || KidsMode.isSearchAllowed(context, serviceId))) {
                    final ChannelTabFragment channelTabFragment =
                            ChannelTabFragment.getInstance(
                                    serviceId,
                                    linkHandler,
                                    name,
                                    selectedChannelVideoSort);
                    channelTabFragment.useAsFrontPage(useAsFrontPage);
                    channelTabFragment.setContextualSearchQuery(channelSearchQuery);
                    tabAdapter.addFragment(channelTabFragment,
                            context.getString(ChannelTabHelper.getTranslationKey(tab)));
                }
            }

            if (ChannelTabHelper.showChannelTab(
                    context, preferences, R.string.show_channel_tabs_about)) {
                tabAdapter.addFragment(
                        new ChannelAboutFragment(currentInfo),
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
        applySearchToCurrentTab();
    }

    private void applySearchToCurrentTab() {
        if (binding != null) {
            applySearchToTab(binding.viewPager.getCurrentItem());
        }
    }

    private void applySearchToTab(final int position) {
        if (tabAdapter == null || position < 0 || position >= tabAdapter.getCount()) {
            return;
        }
        final androidx.fragment.app.Fragment fragment = tabAdapter.getItem(position);
        if (fragment instanceof ContextualSearchable) {
            ((ContextualSearchable) fragment).setContextualSearchQuery(channelSearchQuery);
        }
    }

    void setSelectedChannelVideoSort(@NonNull final String selectedSort) {
        selectedChannelVideoSort = selectedSort;
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
        updateSubscribeProfileButton();
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
                        url == null ? "No URL" : url, serviceId, url)));
    }

    @Override
    public void showLoading() {
        super.showLoading();
        CoilUtils.dispose(binding.channelAvatarView);
        CoilHelper.INSTANCE.clearBanner(binding.channelBannerImage);
        CoilUtils.dispose(binding.subChannelAvatarView);
        binding.channelGroupButton.setVisibility(View.GONE);
        animate(binding.channelSubscriptionActions, false, 100);
    }

    @Override
    public void handleResult(@NonNull final ChannelInfo result) {
        super.handleResult(result);
        currentInfo = result;
        setInitialData(result.getServiceId(), result.getOriginalUrl(), result.getName());

        if (ImageStrategy.shouldLoadImages() && !result.getBanners().isEmpty()) {
            binding.channelBannerContainer.setVisibility(View.VISIBLE);
            binding.channelBannerImage.setVisibility(View.VISIBLE);
            CoilHelper.INSTANCE.loadBanner(binding.channelBannerImage, result.getBanners());
        } else {
            // do not waste space for the banner, if the user disabled images or there is not one
            CoilHelper.INSTANCE.clearBanner(binding.channelBannerImage);
            binding.channelBannerImage.setImageDrawable(null);
            binding.channelBannerImage.setVisibility(View.GONE);
            binding.channelBannerContainer.setVisibility(View.GONE);
        }
        applyLandscapeHeaderPolicy();

        CoilHelper.INSTANCE.loadAvatar(binding.channelAvatarView, result.getAvatars());
        CoilHelper.INSTANCE.loadAvatar(binding.subChannelAvatarView,
                ExtractorImageCompat.parentChannelAvatarImages(result));

        binding.channelTitleView.setText(result.getName());
        binding.channelGroupButton.setVisibility(View.VISIBLE);
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

        updateRssButton();
        updateSubscribeProfileButton();

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
