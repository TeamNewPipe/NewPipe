package org.schabi.newpipe.fragments.channel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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

import org.schabi.newpipe.ImageErrorLoadingListener;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.fragments.BaseFragment;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.workers.ChannelExtractorWorker;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;

public class ChannelFragment extends BaseFragment implements ChannelExtractorWorker.OnChannelInfoReceive {
    private final String TAG = "ChannelFragment@" + Integer.toHexString(hashCode());

    private static final String INFO_LIST_KEY = "info_list_key";
    private static final String CHANNEL_INFO_KEY = "channel_info_key";
    private static final String PAGE_NUMBER_KEY = "page_number_key";

    private InfoListAdapter infoListAdapter;

    private ChannelExtractorWorker currentChannelWorker;
    private ChannelInfo currentChannelInfo;
    private int serviceId = -1;
    private String channelName = "";
    private String channelUrl = "";
    private int pageNumber = 0;
    private boolean hasNextPage = true;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private RecyclerView channelVideosList;

    private View headerRootLayout;
    private ImageView headerChannelBanner;
    private ImageView headerAvatarView;
    private TextView headerTitleView;
    private TextView headerSubscribersTextView;
    private Button headerRssButton;

    /*////////////////////////////////////////////////////////////////////////*/

    public ChannelFragment() {
    }

    public static Fragment getInstance(int serviceId, String channelUrl, String name) {
        ChannelFragment instance = new ChannelFragment();
        instance.setChannel(serviceId, channelUrl, name);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            channelUrl = savedInstanceState.getString(Constants.KEY_URL);
            channelName = savedInstanceState.getString(Constants.KEY_TITLE);
            serviceId = savedInstanceState.getInt(Constants.KEY_SERVICE_ID, -1);

            pageNumber = savedInstanceState.getInt(PAGE_NUMBER_KEY, 0);
            Serializable serializable = savedInstanceState.getSerializable(CHANNEL_INFO_KEY);
            if (serializable instanceof ChannelInfo) currentChannelInfo = (ChannelInfo) serializable;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView() called with: inflater = [" + inflater + "], container = [" + container + "], savedInstanceState = [" + savedInstanceState + "]");
        return inflater.inflate(R.layout.fragment_channel, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (currentChannelInfo == null) loadPage(0);
        else handleChannelInfo(currentChannelInfo, false, false);
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        headerAvatarView.setImageBitmap(null);
        headerChannelBanner.setImageBitmap(null);
        channelVideosList.removeAllViews();

        channelVideosList = null;
        headerRootLayout = null;
        headerChannelBanner = null;
        headerAvatarView = null;
        headerTitleView = null;
        headerSubscribersTextView = null;
        headerRssButton = null;

        super.onDestroyView();
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.d(TAG, "onResume() called");
        super.onResume();
        if (wasLoading.getAndSet(false) && (currentChannelWorker == null || !currentChannelWorker.isRunning())) {
            loadPage(pageNumber);
        }
    }

    @Override
    public void onStop() {
        if (DEBUG) Log.d(TAG, "onStop() called");
        super.onStop();
        wasLoading.set(currentChannelWorker != null && currentChannelWorker.isRunning());
        if (currentChannelWorker != null && currentChannelWorker.isRunning()) currentChannelWorker.cancel();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (DEBUG) Log.d(TAG, "onSaveInstanceState() called with: outState = [" + outState + "]");
        super.onSaveInstanceState(outState);
        outState.putString(Constants.KEY_URL, channelUrl);
        outState.putString(Constants.KEY_TITLE, channelName);
        outState.putInt(Constants.KEY_SERVICE_ID, serviceId);

        outState.putSerializable(INFO_LIST_KEY, ((ArrayList<InfoItem>) infoListAdapter.getItemsList()));
        outState.putSerializable(CHANNEL_INFO_KEY, currentChannelInfo);
        outState.putInt(PAGE_NUMBER_KEY, pageNumber);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "], inflater = [" + inflater + "]");
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_channel, menu);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.menu_item_openInBrowser: {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(channelUrl));
                startActivity(Intent.createChooser(intent, getString(R.string.choose_browser)));
                return true;
            }
            case R.id.menu_item_share:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, channelUrl);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        channelVideosList = (RecyclerView) rootView.findViewById(R.id.channel_streams_view);

        channelVideosList.setLayoutManager(new LinearLayoutManager(activity));
        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(activity, rootView);
            if (savedInstanceState != null) {
                //noinspection unchecked
                ArrayList<InfoItem> serializable = (ArrayList<InfoItem>) savedInstanceState.getSerializable(INFO_LIST_KEY);
                infoListAdapter.addInfoItemList(serializable);
            }
        }

        channelVideosList.setAdapter(infoListAdapter);
        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.channel_header, channelVideosList, false);
        infoListAdapter.setHeader(headerRootLayout);
        infoListAdapter.setFooter(activity.getLayoutInflater().inflate(R.layout.pignate_footer, channelVideosList, false));

        headerChannelBanner = (ImageView) headerRootLayout.findViewById(R.id.channel_banner_image);
        headerAvatarView = (ImageView) headerRootLayout.findViewById(R.id.channel_avatar_view);
        headerTitleView = (TextView) headerRootLayout.findViewById(R.id.channel_title_view);
        headerSubscribersTextView = (TextView) headerRootLayout.findViewById(R.id.channel_subscriber_view);
        headerRssButton = (Button) headerRootLayout.findViewById(R.id.channel_rss_button);
    }

    protected void initListeners() {
        super.initListeners();

        infoListAdapter.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(int serviceId, String url, String title) {
                if (DEBUG) Log.d(TAG, "selected() called with: serviceId = [" + serviceId + "], url = [" + url + "], title = [" + title + "]");
                NavigationHelper.openVideoDetail(onItemSelectedListener, serviceId, url, title);
            }
        });

        channelVideosList.clearOnScrollListeners();
        channelVideosList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                //check for scroll down
                if (dy > 0) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) channelVideosList.getLayoutManager();

                    visibleItemCount = layoutManager.getChildCount();
                    totalItemCount = layoutManager.getItemCount();
                    pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount && (currentChannelWorker == null || !currentChannelWorker.isRunning()) && hasNextPage && !isLoading.get()) {
                        pageNumber++;
                        loadMoreVideos();
                    }
                }
            }
        });

        headerRssButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (DEBUG) Log.d(TAG, "onClick() called with: view = [" + view + "] feed url > " + currentChannelInfo.feed_url);
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(currentChannelInfo.feed_url));
                startActivity(i);
            }
        });
    }

    @Override
    protected void reloadContent() {
        if (DEBUG) Log.d(TAG, "reloadContent() called");
        currentChannelInfo = null;
        infoListAdapter.clearStreamItemList();
        loadPage(0);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private String buildSubscriberString(long count) {
        String out = NumberFormat.getNumberInstance().format(count);
        out += " " + getString(count > 1 ? R.string.subscriber_plural : R.string.subscriber);
        return out;
    }

    private void loadPage(int page) {
        if (DEBUG) Log.d(TAG, "loadPage() called with: page = [" + page + "]");
        if (currentChannelWorker != null && currentChannelWorker.isRunning()) currentChannelWorker.cancel();
        isLoading.set(true);
        pageNumber = page;
        infoListAdapter.showFooter(false);

        animateView(loadingProgressBar, true, 200);
        animateView(errorPanel, false, 200);

        imageLoader.cancelDisplayTask(headerChannelBanner);
        imageLoader.cancelDisplayTask(headerAvatarView);

        headerRssButton.setVisibility(View.GONE);
        headerSubscribersTextView.setVisibility(View.GONE);

        headerTitleView.setText(channelName != null ? channelName : "");
        headerChannelBanner.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.channel_banner));
        headerAvatarView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(channelName != null ? channelName : "");

        currentChannelWorker = new ChannelExtractorWorker(activity, serviceId, channelUrl, page, false, this);
        currentChannelWorker.start();
    }

    private void loadMoreVideos() {
        if (DEBUG) Log.d(TAG, "loadMoreVideos() called");
        if (currentChannelWorker != null && currentChannelWorker.isRunning()) currentChannelWorker.cancel();
        isLoading.set(true);
        currentChannelWorker = new ChannelExtractorWorker(activity, serviceId, channelUrl, pageNumber, true, this);
        currentChannelWorker.start();
    }

    private void setChannel(int serviceId, String channelUrl, String name) {
        this.serviceId = serviceId;
        this.channelUrl = channelUrl;
        this.channelName = name;
    }

    private void handleChannelInfo(ChannelInfo info, boolean onlyVideos, boolean addVideos) {
        currentChannelInfo = info;

        animateView(errorPanel, false, 300);
        animateView(channelVideosList, true, 200);
        animateView(loadingProgressBar, false, 200);

        if (!onlyVideos) {
            headerRootLayout.setVisibility(View.VISIBLE);
            //animateView(loadingProgressBar, false, 200, null);

            if (!TextUtils.isEmpty(info.channel_name)) {
                if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(info.channel_name);
                headerTitleView.setText(info.channel_name);
                channelName = info.channel_name;
            } else channelName = "";

            if (!TextUtils.isEmpty(info.banner_url)) {
                imageLoader.displayImage(info.banner_url, headerChannelBanner, displayImageOptions,  new ImageErrorLoadingListener(activity, getView(), info.service_id));
            }

            if (!TextUtils.isEmpty(info.avatar_url)) {
                headerAvatarView.setVisibility(View.VISIBLE);
                imageLoader.displayImage(info.avatar_url, headerAvatarView, displayImageOptions, new ImageErrorLoadingListener(activity, getView(), info.service_id));
            }

            if (info.subscriberCount != -1) {
                headerSubscribersTextView.setText(buildSubscriberString(info.subscriberCount));
                headerSubscribersTextView.setVisibility(View.VISIBLE);
            } else headerSubscribersTextView.setVisibility(View.GONE);

            if (!TextUtils.isEmpty(info.feed_url)) headerRssButton.setVisibility(View.VISIBLE);
            else headerRssButton.setVisibility(View.INVISIBLE);

            infoListAdapter.showFooter(true);
        }

        hasNextPage = info.hasNextPage;
        if (!hasNextPage) infoListAdapter.showFooter(false);

        //if (!listRestored) {
        if (addVideos) infoListAdapter.addInfoItemList(info.related_streams);
        //}
    }

    @Override
    protected void setErrorMessage(String message, boolean showRetryButton) {
        super.setErrorMessage(message, showRetryButton);

        animateView(channelVideosList, false, 200);
        currentChannelInfo = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnChannelInfoReceiveListener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onReceive(ChannelInfo info, boolean onlyVideos) {
        if (DEBUG) Log.d(TAG, "onReceive() called with: info = [" + info + "]");
        if (info == null || isRemoving() || !isVisible()) return;

        handleChannelInfo(info, onlyVideos, true);
        isLoading.set(false);
    }

    @Override
    public void onError(int messageId) {
        if (DEBUG) Log.d(TAG, "onError() called with: messageId = [" + messageId + "]");
        setErrorMessage(getString(messageId), true);
    }

    @Override
    public void onUnrecoverableError(Exception exception) {
        if (DEBUG) Log.d(TAG, "onUnrecoverableError() called with: exception = [" + exception + "]");
        activity.finish();
    }
}
