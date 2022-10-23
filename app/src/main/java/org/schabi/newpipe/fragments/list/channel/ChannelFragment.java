package org.schabi.newpipe.fragments.list.channel;

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

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentChannelBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabHandler;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.detail.TabAdapter;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelFragment extends BaseStateFragment<ChannelInfo> {
    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private ChannelInfo currentInfo;
    private Disposable currentWorker;

    private MenuItem menuRssButton;

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
    public void onDestroy() {
        super.onDestroy();
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
        updateRssButton();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
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

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void updateTabs() {
        tabAdapter.clearAllItems();

        if (currentInfo != null) {
            tabAdapter.addFragment(ChannelVideosFragment.getInstance(currentInfo), "Videos");

            for (final ChannelTabHandler tab : currentInfo.getTabs()) {
                tabAdapter.addFragment(
                        ChannelTabFragment.getInstance(serviceId, tab), tab.getTab().name());
            }

            final String description = currentInfo.getDescription();
            if (!description.isEmpty()) {
                tabAdapter.addFragment(ChannelInfoFragment.getInstance(description), "Info");
            }
        }

        tabAdapter.notifyDataSetUpdate();

        for (int i = 0; i < tabAdapter.getCount(); i++) {
            binding.tabLayout.getTabAt(i).setText(tabAdapter.getItemTitle(i));
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
    public void handleResult(@NonNull final ChannelInfo info) {
        super.handleResult(info);

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName());
        updateTabs();
        updateRssButton();
    }
}
