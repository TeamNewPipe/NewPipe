package org.schabi.newpipe.fragments.channel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.ImageErrorLoadingListener;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.fragments.OnItemSelectedListener;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.workers.ChannelExtractorWorker;

import java.text.NumberFormat;

import static android.os.Build.VERSION.SDK_INT;


/**
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelFragment.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ChannelFragment extends Fragment implements ChannelExtractorWorker.OnChannelInfoReceive {
    private static final String TAG = "ChannelFragment";

    private AppCompatActivity activity;
    private OnItemSelectedListener onItemSelectedListener;
    private InfoListAdapter infoListAdapter;

    private ChannelExtractorWorker currentExtractorWorker;
    private ChannelInfo currentChannelInfo;
    private int serviceId = -1;
    private String channelName = "";
    private String channelUrl = "";

    private boolean isLoading = false;
    private int pageNumber = 0;
    private boolean hasNextPage = true;

    private ImageLoader imageLoader = ImageLoader.getInstance();

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View rootView = null;

    private RecyclerView channelVideosList;
    private LinearLayoutManager layoutManager;
    private ProgressBar loadingProgressBar;

    private View headerRootLayout;
    private ImageView headerChannelBanner;
    private ImageView headerAvatarView;
    private TextView headerTitleView;
    private TextView headerSubscriberView;
    private Button headerSubscriberButton;
    private View headerSubscriberLayout;

    /*////////////////////////////////////////////////////////////////////////*/

    public ChannelFragment() {
    }

    public static ChannelFragment newInstance(int serviceId, String url, String name) {
        ChannelFragment instance = newInstance();

        Bundle bundle = new Bundle();
        bundle.putString(Constants.KEY_URL, url);
        bundle.putString(Constants.KEY_TITLE, name);
        bundle.putInt(Constants.KEY_SERVICE_ID, serviceId);

        instance.setArguments(bundle);
        return instance;
    }

    public static ChannelFragment newInstance() {
        return new ChannelFragment();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = ((AppCompatActivity) context);
        onItemSelectedListener = ((OnItemSelectedListener) context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        isLoading = false;
        if (savedInstanceState != null) {
            channelUrl = savedInstanceState.getString(Constants.KEY_URL);
            channelName = savedInstanceState.getString(Constants.KEY_TITLE);
            serviceId = savedInstanceState.getInt(Constants.KEY_SERVICE_ID, -1);
        } else {
            try {
                Bundle args = getArguments();
                if (args != null) {
                    channelUrl = args.getString(Constants.KEY_URL);
                    channelName = args.getString(Constants.KEY_TITLE);
                    serviceId = args.getInt(Constants.KEY_SERVICE_ID, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
                ErrorActivity.reportError(getActivity(), e, null,
                        getActivity().findViewById(android.R.id.content),
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                NewPipe.getNameOfService(serviceId),
                                "", R.string.general_error));
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_channel, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        loadingProgressBar = (ProgressBar) view.findViewById(R.id.loading_progress_bar);
        channelVideosList = (RecyclerView) view.findViewById(R.id.channel_streams_view);

        infoListAdapter = new InfoListAdapter(activity, rootView);
        layoutManager = new LinearLayoutManager(activity);
        channelVideosList.setLayoutManager(layoutManager);

        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.channel_header, channelVideosList, false);
        infoListAdapter.setHeader(headerRootLayout);
        infoListAdapter.setFooter(activity.getLayoutInflater().inflate(R.layout.pignate_footer, channelVideosList, false));
        channelVideosList.setAdapter(infoListAdapter);

        headerChannelBanner = (ImageView) headerRootLayout.findViewById(R.id.channel_banner_image);
        headerAvatarView = (ImageView) headerRootLayout.findViewById(R.id.channel_avatar_view);
        headerTitleView = (TextView) headerRootLayout.findViewById(R.id.channel_title_view);
        headerSubscriberView = (TextView) headerRootLayout.findViewById(R.id.channel_subscriber_view);
        headerSubscriberButton = (Button) headerRootLayout.findViewById(R.id.channel_subscribe_button);
        headerSubscriberLayout = headerRootLayout.findViewById(R.id.channel_subscriber_layout);

        initListeners();

        isLoading = true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        headerAvatarView.setImageBitmap(null);
        headerChannelBanner.setImageBitmap(null);
        channelVideosList.removeAllViews();

        rootView = null;
        channelVideosList = null;
        layoutManager = null;
        loadingProgressBar = null;
        headerRootLayout = null;
        headerChannelBanner = null;
        headerAvatarView = null;
        headerTitleView = null;
        headerSubscriberView = null;
        headerSubscriberButton = null;
        headerSubscriberLayout = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isLoading) {
            requestData(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (currentExtractorWorker != null) currentExtractorWorker.cancel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        imageLoader.clearMemoryCache();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Constants.KEY_URL, channelUrl);
        outState.putString(Constants.KEY_TITLE, channelName);
        outState.putInt(Constants.KEY_SERVICE_ID, serviceId);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_channel, menu);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            //noinspection deprecation
            supportActionBar.setNavigationMode(0);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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

    private void initListeners() {
        infoListAdapter.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(int serviceId, String url, String title) {
                NavigationHelper.openVideoDetail(onItemSelectedListener, serviceId, url, title);
            }
        });

        // detect if list has ben scrolled to the bottom
        channelVideosList.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                //check for scroll down
                if (dy > 0) {
                    visibleItemCount = layoutManager.getChildCount();
                    totalItemCount = layoutManager.getItemCount();
                    pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount && !currentExtractorWorker.isRunning() && hasNextPage) {
                        pageNumber++;
                        requestData(true);
                    }
                }
            }
        });

        headerSubscriberButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, currentChannelInfo.feed_url);
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(currentChannelInfo.feed_url));
                startActivity(i);
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/
    private String buildSubscriberString(long count) {
        String out = NumberFormat.getNumberInstance().format(count);
        out += " " + getString(count > 1 ? R.string.subscriber_plural : R.string.subscriber);
        return out;
    }

    private void requestData(boolean onlyVideos) {
        if (currentExtractorWorker != null && currentExtractorWorker.isRunning()) currentExtractorWorker.cancel();

        isLoading = true;
        if (!onlyVideos) {
            //delete already displayed content
            loadingProgressBar.setVisibility(View.VISIBLE);
            infoListAdapter.clearSteamItemList();
            pageNumber = 0;
            headerSubscriberLayout.setVisibility(View.GONE);
            headerTitleView.setText(channelName != null ? channelName : "");
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(channelName != null ? channelName : "");
            if (SDK_INT >= 21) {
                headerChannelBanner.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.channel_banner));
                headerAvatarView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));
            }
            infoListAdapter.showFooter(false);
        }

        currentExtractorWorker = new ChannelExtractorWorker(activity, serviceId, channelUrl, pageNumber, this);
        currentExtractorWorker.setOnlyVideos(onlyVideos);
        currentExtractorWorker.start();
    }

    private void addVideos(ChannelInfo info) {
        infoListAdapter.addInfoItemList(info.related_streams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnChannelInfoReceiveListener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onReceive(ChannelInfo info) {
        if (info == null || isRemoving() || !isVisible()) return;

        currentChannelInfo = info;

        if (!currentExtractorWorker.isOnlyVideos()) {
            headerRootLayout.setVisibility(View.VISIBLE);
            loadingProgressBar.setVisibility(View.GONE);

            if (info.channel_name != null && !info.channel_name.isEmpty()) {
                if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(info.channel_name);
                headerTitleView.setText(info.channel_name);
                channelName = info.channel_name;
            } else channelName = "";

            if (info.banner_url != null && !info.banner_url.isEmpty()) {
                imageLoader.displayImage(info.banner_url, headerChannelBanner,
                        new ImageErrorLoadingListener(activity, rootView, info.service_id));
            }

            if (info.avatar_url != null && !info.avatar_url.isEmpty()) {
                headerAvatarView.setVisibility(View.VISIBLE);
                imageLoader.displayImage(info.avatar_url, headerAvatarView,
                        new ImageErrorLoadingListener(activity, rootView, info.service_id));
            }

            if (info.subscriberCount != -1) {
                headerSubscriberView.setText(buildSubscriberString(info.subscriberCount));
            }

            if ((info.feed_url != null && !info.feed_url.isEmpty()) || (info.subscriberCount != -1)) {
                headerSubscriberLayout.setVisibility(View.VISIBLE);
            }

            if (info.feed_url == null || info.feed_url.isEmpty()) {
                headerSubscriberButton.setVisibility(View.INVISIBLE);
            }

            infoListAdapter.showFooter(true);
        }
        hasNextPage = info.hasNextPage;
        if (!hasNextPage) infoListAdapter.showFooter(false);
        addVideos(info);
        isLoading = false;
    }

    @Override
    public void onError(int messageId) {
        Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onUnrecoverableError(Exception exception) {
        activity.finish();
    }
}
