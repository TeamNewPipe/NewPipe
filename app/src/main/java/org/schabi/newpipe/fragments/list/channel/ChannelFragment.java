package org.schabi.newpipe.fragments.list.channel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.NotificationMode;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.databinding.FragmentChannelBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.detail.TabAdapter;
import org.schabi.newpipe.local.feed.notifications.NotificationHelper;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.List;
import java.util.Queue;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelFragment extends BaseStateFragment<ChannelInfo>
        implements StateSaver.WriteRead {
    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private ChannelInfo currentInfo;
    private Disposable currentWorker;
    private Disposable subscriptionMonitor;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private SubscriptionManager subscriptionManager;
    private int lastTab;

    private MenuItem menuRssButton;
    private MenuItem menuNotifyButton;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private FragmentChannelBinding binding;
    private TabAdapter tabAdapter;

    public static ChannelFragment getInstance(final int serviceId, final String url,
                                              final String name) {
        final ChannelFragment instance = new ChannelFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    public ChannelFragment() {
        super();
    }

    protected void setInitialData(final int sid, final String u, final String title) {
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

        if (savedInstanceState != null) {
            lastTab = savedInstanceState.getInt("LastTab");
        } else {
            lastTab = 0;
        }
    }

    @Override
    public void onAttach(final @NonNull Context context) {
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

    @Override // called from onViewCreated in {@link BaseFragment#onViewCreated}
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        tabAdapter = new TabAdapter(getChildFragmentManager());
        binding.viewPager.setAdapter(tabAdapter);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
    }

    @Override
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("LastTab", binding.tabLayout.getSelectedTabPosition());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        if (subscriptionMonitor != null) {
            subscriptionMonitor.dispose();
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
        menuRssButton = menu.findItem(R.id.menu_item_rss);
        menuNotifyButton = menu.findItem(R.id.menu_item_notify);
        updateRssButton();
        monitorSubscription();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
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
                            currentInfo.getAvatarUrl());
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void updateRssButton() {
        if (currentInfo != null && menuRssButton != null) {
            menuRssButton.setVisible(!TextUtils.isEmpty(currentInfo.getFeedUrl()));
        }
    }

    private void monitorSubscription() {
        if (currentInfo != null) {
            final Observable<List<SubscriptionEntity>> observable = subscriptionManager
                    .subscriptionTable()
                    .getSubscriptionFlowable(currentInfo.getServiceId(), currentInfo.getUrl())
                    .toObservable();

            if (subscriptionMonitor != null) {
                subscriptionMonitor.dispose();
            }
            subscriptionMonitor = observable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getSubscribeUpdateMonitor());
        }
    }

    private Consumer<List<SubscriptionEntity>> getSubscribeUpdateMonitor() {
        return (List<SubscriptionEntity> subscriptionEntities) -> {
            if (subscriptionEntities.isEmpty()) {
                updateNotifyButton(null);
            } else {
                final SubscriptionEntity subscription = subscriptionEntities.get(0);
                updateNotifyButton(subscription);
            }
        };
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

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isContentUnsupported() {
        for (final Throwable throwable : currentInfo.getErrors()) {
            if (throwable instanceof ContentNotSupportedException) {
                return true;
            }
        }
        return false;
    }

    private void updateTabs() {
        tabAdapter.clearAllItems();

        if (currentInfo != null) {
            if (isContentUnsupported()) {
                showEmptyState();
                binding.errorContentNotSupported.setVisibility(View.VISIBLE);
            } else {
                tabAdapter.addFragment(
                        ChannelVideosFragment.getInstance(currentInfo), "Videos");

                final Context context = getContext();
                final SharedPreferences preferences = PreferenceManager
                        .getDefaultSharedPreferences(context);

                for (final ListLinkHandler linkHandler : currentInfo.getTabs()) {
                    final String tab = linkHandler.getContentFilters().get(0);
                    if (ChannelTabHelper.showChannelTab(context, preferences, tab)) {
                        tabAdapter.addFragment(
                                ChannelTabFragment.getInstance(serviceId, linkHandler),
                                context.getString(ChannelTabHelper.getTranslationKey(tab)));
                    }
                }

                final String description = currentInfo.getDescription();
                if (description != null && !description.isEmpty()
                        && ChannelTabHelper.showChannelTab(
                        context, preferences, R.string.show_channel_tabs_info)) {
                    tabAdapter.addFragment(
                            ChannelInfoFragment.getInstance(currentInfo), "Info");
                }
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
        if (binding != null) {
            objectsToSave.add(binding.tabLayout.getSelectedTabPosition());
        } else {
            objectsToSave.add(0);
        }
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) {
        currentInfo = (ChannelInfo) savedObjects.poll();
        lastTab = (Integer) savedObjects.poll();
    }

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
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                        url == null ? "no url" : url, serviceId)));
    }

    @Override
    public void handleResult(@NonNull final ChannelInfo result) {
        super.handleResult(result);
        currentInfo = result;
        setInitialData(result.getServiceId(), result.getOriginalUrl(), result.getName());

        updateTabs();
        updateRssButton();
        monitorSubscription();
    }
}
