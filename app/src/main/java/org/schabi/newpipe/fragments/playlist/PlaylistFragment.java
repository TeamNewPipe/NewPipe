package org.schabi.newpipe.fragments.playlist;

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
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.ImageErrorLoadingListener;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlayListExtractor;
import org.schabi.newpipe.extractor.playlist.PlayListInfo;
import org.schabi.newpipe.fragments.BaseFragment;
import org.schabi.newpipe.fragments.search.OnScrollBelowItemsListener;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class PlaylistFragment extends BaseFragment {
    private final String TAG = "PlaylistFragment@" + Integer.toHexString(hashCode());

    private static final String INFO_LIST_KEY = "info_list_key";
    private static final String PLAYLIST_INFO_KEY = "playlist_info_key";
    private static final String PAGE_NUMBER_KEY = "page_number_key";

    private InfoListAdapter infoListAdapter;

    private PlayListInfo currentPlaylistInfo;
    private int serviceId = -1;
    private String playlistTitle = "";
    private String playlistUrl = "";
    private int pageNumber = 0;
    private boolean hasNextPage = true;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private RecyclerView playlistStreams;

    private View headerRootLayout;
    private ImageView headerBannerView;
    private ImageView headerAvatarView;
    private TextView headerTitleView;

    /*////////////////////////////////////////////////////////////////////////*/

    public PlaylistFragment() {
    }

    public static Fragment getInstance(int serviceId, String playlistUrl, String title) {
        PlaylistFragment instance = new PlaylistFragment();
        instance.setPlaylist(serviceId, playlistUrl, title);
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
            playlistUrl = savedInstanceState.getString(Constants.KEY_URL);
            playlistTitle = savedInstanceState.getString(Constants.KEY_TITLE);
            serviceId = savedInstanceState.getInt(Constants.KEY_SERVICE_ID, -1);

            pageNumber = savedInstanceState.getInt(PAGE_NUMBER_KEY, 0);
            Serializable serializable = savedInstanceState.getSerializable(PLAYLIST_INFO_KEY);
            if (serializable instanceof PlayListInfo) currentPlaylistInfo = (PlayListInfo) serializable;
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

        if (currentPlaylistInfo == null) loadPage(0);
        else handlePlayListInfo(currentPlaylistInfo, false, false);
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        headerAvatarView.setImageBitmap(null);
        headerBannerView.setImageBitmap(null);
        playlistStreams.removeAllViews();

        playlistStreams = null;
        headerRootLayout = null;
        headerBannerView = null;
        headerAvatarView = null;
        headerTitleView = null;

        super.onDestroyView();
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.d(TAG, "onResume() called");
        super.onResume();
        if (wasLoading.getAndSet(false)) {
            loadPage(pageNumber);
        }
    }

    @Override
    public void onStop() {
        if (DEBUG) Log.d(TAG, "onStop() called");

        disposable.dispose();
        disposable = null;

        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (DEBUG) Log.d(TAG, "onSaveInstanceState() called with: outState = [" + outState + "]");
        super.onSaveInstanceState(outState);
        outState.putString(Constants.KEY_URL, playlistUrl);
        outState.putString(Constants.KEY_TITLE, playlistTitle);
        outState.putInt(Constants.KEY_SERVICE_ID, serviceId);

        outState.putSerializable(INFO_LIST_KEY, infoListAdapter.getItemsList());
        outState.putSerializable(PLAYLIST_INFO_KEY, currentPlaylistInfo);
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
                intent.setData(Uri.parse(playlistUrl));
                startActivity(Intent.createChooser(intent, getString(R.string.choose_browser)));
                return true;
            }
            case R.id.menu_item_share: {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, playlistUrl);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
                return true;
            }
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

        playlistStreams = (RecyclerView) rootView.findViewById(R.id.channel_streams_view);

        playlistStreams.setLayoutManager(new LinearLayoutManager(activity));
        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(activity);
            if (savedInstanceState != null) {
                //noinspection unchecked
                ArrayList<InfoItem> serializable = (ArrayList<InfoItem>) savedInstanceState.getSerializable(INFO_LIST_KEY);
                infoListAdapter.addInfoItemList(serializable);
            }
        }

        playlistStreams.setAdapter(infoListAdapter);
        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.playlist_header, playlistStreams, false);
        infoListAdapter.setHeader(headerRootLayout);
        infoListAdapter.setFooter(activity.getLayoutInflater().inflate(R.layout.pignate_footer, playlistStreams, false));

        headerBannerView = (ImageView) headerRootLayout.findViewById(R.id.playlist_banner_image);
        headerAvatarView = (ImageView) headerRootLayout.findViewById(R.id.playlist_avatar_view);
        headerTitleView = (TextView) headerRootLayout.findViewById(R.id.playlist_title_view);
    }

    protected void initListeners() {
        super.initListeners();

        infoListAdapter.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(int serviceId, String url, String title) {
                if (DEBUG) Log.d(TAG, "selected() called with: serviceId = [" + serviceId + "], url = [" + url + "], title = [" + title + "]");
                NavigationHelper.openVideoDetailFragment(getFragmentManager(), serviceId, url, title);
            }
        });

        playlistStreams.clearOnScrollListeners();
        playlistStreams.addOnScrollListener(new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(RecyclerView recyclerView) {
                loadMore(true);
            }
        });
    }


    @Override
    protected void reloadContent() {
        if (DEBUG) Log.d(TAG, "reloadContent() called");
        currentPlaylistInfo = null;
        infoListAdapter.clearStreamItemList();
        loadPage(0);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Loader
    //////////////////////////////////////////////////////////////////////////*/

    private StreamingService getService(final int serviceId) throws ExtractionException {
        return NewPipe.getService(serviceId);
    }

    Disposable disposable;

    private void loadMore(final boolean onlyVideos) {
        final Callable<PlayListInfo> task = new Callable<PlayListInfo>() {
            @Override
            public PlayListInfo call() throws Exception {
                final PlayListExtractor extractor = getService(serviceId)
                        .getPlayListExtractorInstance(playlistUrl, pageNumber);

                return PlayListInfo.getInfo(extractor);
            }
        };


        Observable.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<PlayListInfo>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        if (disposable == null || disposable.isDisposed()) {
                            disposable = d;
                            isLoading.set(true);
                        } else {
                            d.dispose();
                        }
                    }

                    @Override
                    public void onNext(@NonNull PlayListInfo playListInfo) {
                        if (DEBUG) Log.d(TAG, "onReceive() called with: info = [" + playListInfo + "]");
                        if (playListInfo == null || isRemoving() || !isVisible()) return;

                        handlePlayListInfo(playListInfo, onlyVideos, true);
                        isLoading.set(false);
                        pageNumber++;
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        onRxError(e, "Observer failure");
                    }

                    @Override
                    public void onComplete() {
                        if (disposable != null) {
                            disposable.dispose();
                            disposable = null;
                        }
                    }
                });
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void loadPage(int page) {
        if (DEBUG) Log.d(TAG, "loadPage() called with: page = [" + page + "]");
        isLoading.set(true);
        pageNumber = page;
        infoListAdapter.showFooter(false);

        animateView(loadingProgressBar, true, 200);
        animateView(errorPanel, false, 200);

        imageLoader.cancelDisplayTask(headerBannerView);
        imageLoader.cancelDisplayTask(headerAvatarView);

        headerTitleView.setText(playlistTitle != null ? playlistTitle : "");
        headerBannerView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.channel_banner));
        headerAvatarView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(playlistTitle != null ? playlistTitle : "");

        loadMore(true);
    }

    private void setPlaylist(int serviceId, String playlistUrl, String title) {
        this.serviceId = serviceId;
        this.playlistUrl = playlistUrl;
        this.playlistTitle = title;
    }

    private void handlePlayListInfo(PlayListInfo info, boolean onlyVideos, boolean addVideos) {
        currentPlaylistInfo = info;

        animateView(errorPanel, false, 300);
        animateView(playlistStreams, true, 200);
        animateView(loadingProgressBar, false, 200);

        if (!onlyVideos) {
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().invalidateOptionsMenu();

            headerRootLayout.setVisibility(View.VISIBLE);
            //animateView(loadingProgressBar, false, 200, null);

            if (!TextUtils.isEmpty(info.playList_name)) {
                if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(info.playList_name);
                headerTitleView.setText(info.playList_name);
                playlistTitle = info.playList_name;
            } else playlistTitle = "";

            if (!TextUtils.isEmpty(info.banner_url)) {
                imageLoader.displayImage(info.banner_url, headerBannerView, displayImageOptions, new ImageErrorLoadingListener(activity, getView(), info.service_id));
            }

            if (!TextUtils.isEmpty(info.avatar_url)) {
                headerAvatarView.setVisibility(View.VISIBLE);
                imageLoader.displayImage(info.avatar_url, headerAvatarView, displayImageOptions, new ImageErrorLoadingListener(activity, getView(), info.service_id));
            }

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

        animateView(playlistStreams, false, 200);
        currentPlaylistInfo = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error Handlers
    //////////////////////////////////////////////////////////////////////////*/

    private void onRxError(final Throwable exception, final String tag) {
        if (exception instanceof IOException) {
            onRecoverableError(R.string.network_error);
        } else {
            onUnrecoverableError(exception, tag);
        }
    }

    private void onRecoverableError(int messageId) {
        if (!this.isAdded()) return;

        if (DEBUG) Log.d(TAG, "onError() called with: messageId = [" + messageId + "]");
        setErrorMessage(getString(messageId), true);
    }

    private void onUnrecoverableError(Throwable exception, final String tag) {
        if (DEBUG) Log.d(TAG, "onUnrecoverableError() called with: exception = [" + exception + "]");
        ErrorActivity.reportError(
                getContext(),
                exception,
                MainActivity.class,
                null,
                ErrorActivity.ErrorInfo.make(UserAction.REQUESTED_PLAYLIST, "Feed", tag, R.string.general_error)
        );

        activity.finish();
    }
}
