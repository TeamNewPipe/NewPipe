package org.schabi.newpipe.fragments.detail;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.fragments.list.videos.RelatedVideosFragment;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.MainVideoPlayer;
import org.schabi.newpipe.player.PopupVideoPlayer;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import icepick.State;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener,
        View.OnLongClickListener {
    public static final String AUTO_PLAY = "auto_play";

    private int updateFlags = 0;
    private static final int RELATED_STREAMS_UPDATE_FLAG = 0x1;
    private static final int RESOLUTIONS_MENU_UPDATE_FLAG = 0x2;
    private static final int TOOLBAR_ITEMS_UPDATE_FLAG = 0x4;
    private static final int COMMENTS_UPDATE_FLAG = 0x4;

    private boolean autoPlayEnabled;
    private boolean showRelatedStreams;
    private boolean showComments;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private StreamInfo currentInfo;
    private Disposable currentWorker;
    @NonNull
    private CompositeDisposable disposables = new CompositeDisposable();

    private List<VideoStream> sortedVideoStreams;
    private int selectedVideoStreamIndex = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private Menu menu;

    private Spinner spinnerToolbar;

    private LinearLayout contentRootLayoutHiding;

    private View thumbnailBackgroundButton;
    private ImageView thumbnailImageView;
    private ImageView thumbnailPlayButton;

    private View videoTitleRoot;
    private TextView videoTitleTextView;
    private ImageView videoTitleToggleArrow;
    private TextView videoCountView;

    private TextView detailControlsBackground;
    private TextView detailControlsPopup;
    private TextView detailControlsAddToPlaylist;
    private TextView detailControlsDownload;
    private TextView appendControlsDetail;
    private TextView detailDurationView;

    private LinearLayout videoDescriptionRootLayout;
    private TextView videoUploadDateView;
    private TextView videoDescriptionView;

    private View uploaderRootLayout;
    private TextView uploaderTextView;
    private ImageView uploaderThumb;

    private TextView thumbsUpTextView;
    private ImageView thumbsUpImageView;
    private TextView thumbsDownTextView;
    private ImageView thumbsDownImageView;
    private TextView thumbsDisabledTextView;

    private static final String COMMENTS_TAB_TAG = "COMMENTS";
    private static final String RELATED_TAB_TAG = "NEXT VIDEO";

    private AppBarLayout appBarLayout;
    private  ViewPager viewPager;
    private TabAdaptor pageAdapter;
    private TabLayout tabLayout;


    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(int serviceId, String videoUrl, String name) {
        VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, videoUrl, name);
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

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_next_video_key), true);

        showComments = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_comments_key), true);

        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_detail, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) currentWorker.dispose();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (updateFlags != 0) {
            if (!isLoading.get() && currentInfo != null) {
                if ((updateFlags & RELATED_STREAMS_UPDATE_FLAG) != 0) startLoading(false);
                if ((updateFlags & RESOLUTIONS_MENU_UPDATE_FLAG) != 0) setupActionBar(currentInfo);
                if ((updateFlags & COMMENTS_UPDATE_FLAG) != 0) startLoading(false);
            }

            if ((updateFlags & TOOLBAR_ITEMS_UPDATE_FLAG) != 0
                    && menu != null) {
                updateMenuItemVisibility();
            }

            updateFlags = 0;
        }

        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            selectAndLoadVideo(serviceId, url, name);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);

        if (currentWorker != null) currentWorker.dispose();
        if (disposables != null) disposables.clear();
        currentWorker = null;
        disposables = null;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        spinnerToolbar.setOnItemSelectedListener(null);
        spinnerToolbar.setAdapter(null);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetailFragment(getFragmentManager(), serviceId, url, name);
                } else Log.e(TAG, "ReCaptcha failed");
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.show_next_video_key))) {
            showRelatedStreams = sharedPreferences.getBoolean(key, true);
            updateFlags |= RELATED_STREAMS_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.default_video_format_key))
                || key.equals(getString(R.string.default_resolution_key))
                || key.equals(getString(R.string.show_higher_resolutions_key))
                || key.equals(getString(R.string.use_external_video_player_key))) {
            updateFlags |= RESOLUTIONS_MENU_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.show_play_with_kodi_key))) {
            updateFlags |= TOOLBAR_ITEMS_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.show_comments_key))) {
            showComments = sharedPreferences.getBoolean(key, true);
            updateFlags |= COMMENTS_UPDATE_FLAG;
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

        // Check if the next video label and video is visible,
        // if it is, include the two elements in the next check
        int nextCount = currentInfo != null && currentInfo.getNextVideo() != null ? 2 : 0;

        if (!isLoading.get() && currentInfo != null && isVisible()) {
            outState.putSerializable(INFO_KEY, currentInfo);
        }

        outState.putSerializable(STACK_KEY, stack);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof StreamInfo) {
            //noinspection unchecked
            currentInfo = (StreamInfo) serializable;
            InfoCache.getInstance().putInfo(serviceId, url, currentInfo, InfoItem.InfoType.STREAM);
        }

        serializable = savedState.getSerializable(STACK_KEY);
        if (serializable instanceof Collection) {
            //noinspection unchecked
            stack.addAll((Collection<? extends StackItem>) serializable);
        }

    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(View v) {
        if (isLoading.get() || currentInfo == null) return;

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(false);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(false);
                break;
            case R.id.detail_controls_playlist_append:
                if (getFragmentManager() != null && currentInfo != null) {
                    PlaylistAppendDialog.fromStreamInfo(currentInfo)
                            .show(getFragmentManager(), TAG);
                }
                break;
            case R.id.detail_controls_download:
                if (PermissionHelper.checkStoragePermissions(activity, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                    this.openDownloadDialog();
                }
                break;
            case R.id.detail_uploader_root_layout:
                if (TextUtils.isEmpty(currentInfo.getUploaderUrl())) {
                    Log.w(TAG, "Can't open channel because we got no channel URL");
                } else {
                    try {
                        NavigationHelper.openChannelFragment(
                                getFragmentManager(),
                                currentInfo.getServiceId(),
                                currentInfo.getUploaderUrl(),
                                currentInfo.getUploaderName());
                    } catch (Exception e) {
                        ErrorActivity.reportUiError((AppCompatActivity) getActivity(), e);
                    }
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                if (currentInfo.getVideoStreams().isEmpty()
                        && currentInfo.getVideoOnlyStreams().isEmpty()) {
                    openBackgroundPlayer(false);
                } else {
                    openVideoPlayer();
                }
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndDescription();
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (isLoading.get() || currentInfo == null) return false;

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(true);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(true);
                break;
            case R.id.detail_controls_download:
                NavigationHelper.openDownloads(getActivity());
                break;
        }

        return true;
    }

    private void toggleTitleAndDescription() {
        if (videoDescriptionRootLayout.getVisibility() == View.VISIBLE) {
            videoTitleTextView.setMaxLines(1);
            videoDescriptionRootLayout.setVisibility(View.GONE);
            videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        } else {
            videoTitleTextView.setMaxLines(10);
            videoDescriptionRootLayout.setVisibility(View.VISIBLE);
            videoTitleToggleArrow.setImageResource(R.drawable.arrow_up);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        spinnerToolbar = activity.findViewById(R.id.toolbar).findViewById(R.id.toolbar_spinner);

        thumbnailBackgroundButton = rootView.findViewById(R.id.detail_thumbnail_root_layout);
        thumbnailImageView = rootView.findViewById(R.id.detail_thumbnail_image_view);
        thumbnailPlayButton = rootView.findViewById(R.id.detail_thumbnail_play_button);

        contentRootLayoutHiding = rootView.findViewById(R.id.detail_content_root_hiding);

        videoTitleRoot = rootView.findViewById(R.id.detail_title_root_layout);
        videoTitleTextView = rootView.findViewById(R.id.detail_video_title_view);
        videoTitleToggleArrow = rootView.findViewById(R.id.detail_toggle_description_view);
        videoCountView = rootView.findViewById(R.id.detail_view_count_view);

        detailControlsBackground = rootView.findViewById(R.id.detail_controls_background);
        detailControlsPopup = rootView.findViewById(R.id.detail_controls_popup);
        detailControlsAddToPlaylist = rootView.findViewById(R.id.detail_controls_playlist_append);
        detailControlsDownload = rootView.findViewById(R.id.detail_controls_download);
        appendControlsDetail = rootView.findViewById(R.id.touch_append_detail);
        detailDurationView = rootView.findViewById(R.id.detail_duration_view);

        videoDescriptionRootLayout = rootView.findViewById(R.id.detail_description_root_layout);
        videoUploadDateView = rootView.findViewById(R.id.detail_upload_date_view);
        videoDescriptionView = rootView.findViewById(R.id.detail_description_view);
        videoDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());
        videoDescriptionView.setAutoLinkMask(Linkify.WEB_URLS);

        //thumbsRootLayout = rootView.findViewById(R.id.detail_thumbs_root_layout);
        thumbsUpTextView = rootView.findViewById(R.id.detail_thumbs_up_count_view);
        thumbsUpImageView = rootView.findViewById(R.id.detail_thumbs_up_img_view);
        thumbsDownTextView = rootView.findViewById(R.id.detail_thumbs_down_count_view);
        thumbsDownImageView = rootView.findViewById(R.id.detail_thumbs_down_img_view);
        thumbsDisabledTextView = rootView.findViewById(R.id.detail_thumbs_disabled_view);

        uploaderRootLayout = rootView.findViewById(R.id.detail_uploader_root_layout);
        uploaderTextView = rootView.findViewById(R.id.detail_uploader_text_view);
        uploaderThumb = rootView.findViewById(R.id.detail_uploader_thumbnail_view);

        appBarLayout = rootView.findViewById(R.id.appbarlayout);
        viewPager = rootView.findViewById(R.id.viewpager);
        pageAdapter = new TabAdaptor(getChildFragmentManager());
        viewPager.setAdapter(pageAdapter);
        tabLayout = rootView.findViewById(R.id.tablayout);
        tabLayout.setupWithViewPager(viewPager);

        setHeightThumbnail();


    }

    @Override
    protected void initListeners() {
        super.initListeners();

        videoTitleRoot.setOnClickListener(this);
        uploaderRootLayout.setOnClickListener(this);
        thumbnailBackgroundButton.setOnClickListener(this);
        detailControlsBackground.setOnClickListener(this);
        detailControlsPopup.setOnClickListener(this);
        detailControlsAddToPlaylist.setOnClickListener(this);
        detailControlsDownload.setOnClickListener(this);
        detailControlsDownload.setOnLongClickListener(this);

        detailControlsBackground.setLongClickable(true);
        detailControlsPopup.setLongClickable(true);
        detailControlsBackground.setOnLongClickListener(this);
        detailControlsPopup.setOnLongClickListener(this);
        detailControlsBackground.setOnTouchListener(getOnControlsTouchListener());
        detailControlsPopup.setOnTouchListener(getOnControlsTouchListener());
    }

    private void showStreamDialog(final StreamInfoItem item) {
        final Context context = getContext();
        if (context == null || context.getResources() == null || getActivity() == null) return;

        final String[] commands = new String[]{
                context.getResources().getString(R.string.enqueue_on_background),
                context.getResources().getString(R.string.enqueue_on_popup),
                context.getResources().getString(R.string.append_playlist),
                context.getResources().getString(R.string.share)
        };

        final DialogInterface.OnClickListener actions = (DialogInterface dialogInterface, int i) -> {
            switch (i) {
                case 0:
                    NavigationHelper.enqueueOnBackgroundPlayer(context, new SinglePlayQueue(item));
                    break;
                case 1:
                    NavigationHelper.enqueueOnPopupPlayer(getActivity(), new SinglePlayQueue(item));
                    break;
                case 2:
                    if (getFragmentManager() != null) {
                        PlaylistAppendDialog.fromStreamInfoItems(Collections.singletonList(item))
                                .show(getFragmentManager(), TAG);
                    }
                    break;
                case 3:
                    shareUrl(item.getName(), item.getUrl());
                    break;
                default:
                    break;
            }
        };

        new InfoItemDialog(getActivity(), item, commands, actions).show();
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return (View view, MotionEvent motionEvent) -> {
            if (!PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)) {
                return false;
            }

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                animateView(appendControlsDetail, true, 250, 0, () ->
                        animateView(appendControlsDetail, false, 1500, 1000));
            }
            return false;
        };
    }

    private void initThumbnailViews(@NonNull StreamInfo info) {
        thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);
        if (!TextUtils.isEmpty(info.getThumbnailUrl())) {
            final String infoServiceName = NewPipe.getNameOfService(info.getServiceId());
            final ImageLoadingListener onFailListener = new SimpleImageLoadingListener() {
                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    showSnackBarError(failReason.getCause(), UserAction.LOAD_IMAGE,
                            infoServiceName, imageUri, R.string.could_not_load_thumbnails);
                }
            };

            imageLoader.displayImage(info.getThumbnailUrl(), thumbnailImageView,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, onFailListener);
        }

        if (!TextUtils.isEmpty(info.getUploaderAvatarUrl())) {
            imageLoader.displayImage(info.getUploaderAvatarUrl(), uploaderThumb,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        inflater.inflate(R.menu.video_detail_menu, menu);

        updateMenuItemVisibility();

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void updateMenuItemVisibility() {

        // show kodi if set in settings
        menu.findItem(R.id.action_play_with_kodi).setVisible(
                PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                        activity.getString(R.string.show_play_with_kodi_key), false));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isLoading.get()) {
            // if is still loading block menu
            return true;
        }

        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_share: {
                if (currentInfo != null) {
                    shareUrl(currentInfo.getName(), currentInfo.getUrl());
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                if (currentInfo != null) {
                    openUrlInBrowser(currentInfo.getUrl());
                }
                return true;
            }
            case R.id.action_play_with_kodi:
                try {
                    NavigationHelper.playWithKore(activity, Uri.parse(
                            url.replace("https", "http")));
                } catch (Exception e) {
                    if (DEBUG) Log.i(TAG, "Failed to start kore", e);
                    showInstallKoreDialog(activity);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (DialogInterface dialog, int which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> {
                });
        builder.create().show();
    }

    private void setupActionBarOnError(final String url) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandlerOnError() called with: url = [" + url + "]");
        Log.e("-----", "missing code");
    }

    private void setupActionBar(final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandler() called with: info = [" + info + "]");
        boolean isExternalPlayerEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_video_player_key), false);

        sortedVideoStreams = ListHelper.getSortedStreamVideosList(activity, info.getVideoStreams(), info.getVideoOnlyStreams(), false);
        selectedVideoStreamIndex = ListHelper.getDefaultResolutionIndex(activity, sortedVideoStreams);

        final StreamItemAdapter<VideoStream> streamsAdapter = new StreamItemAdapter<>(activity, new StreamSizeWrapper<>(sortedVideoStreams), isExternalPlayerEnabled);
        spinnerToolbar.setAdapter(streamsAdapter);
        spinnerToolbar.setSelection(selectedVideoStreamIndex);
        spinnerToolbar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVideoStreamIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    protected final LinkedList<StackItem> stack = new LinkedList<>();

    public void clearHistory() {
        stack.clear();
    }

    public void pushToStack(int serviceId, String videoUrl, String name) {
        if (DEBUG) {
            Log.d(TAG, "pushToStack() called with: serviceId = [" + serviceId + "], videoUrl = [" + videoUrl + "], name = [" + name + "]");
        }

        if (stack.size() > 0 && stack.peek().getServiceId() == serviceId && stack.peek().getUrl().equals(videoUrl)) {
            Log.d(TAG, "pushToStack() called with: serviceId == peek.serviceId = [" + serviceId + "], videoUrl == peek.getUrl = [" + videoUrl + "]");
            return;
        } else {
            Log.d(TAG, "pushToStack() wasn't equal");
        }

        stack.push(new StackItem(serviceId, videoUrl, name));
    }

    public void setTitleToUrl(int serviceId, String videoUrl, String name) {
        if (name != null && !name.isEmpty()) {
            for (StackItem stackItem : stack) {
                if (stack.peek().getServiceId() == serviceId
                        && stackItem.getUrl().equals(videoUrl)) {
                    stackItem.setTitle(name);
                }
            }
        }
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

        selectAndLoadVideo(peek.getServiceId(), peek.getUrl(), !TextUtils.isEmpty(peek.getTitle()) ? peek.getTitle() : "");
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (currentInfo == null) prepareAndLoadInfo();
        else prepareAndHandleInfo(currentInfo, false);
    }

    public void selectAndLoadVideo(int serviceId, String videoUrl, String name) {
        setInitialData(serviceId, videoUrl, name);
        prepareAndLoadInfo();
    }

    public void prepareAndHandleInfo(final StreamInfo info, boolean scrollToTop) {
        if (DEBUG)
            Log.d(TAG, "prepareAndHandleInfo() called with: info = [" + info + "], scrollToTop = [" + scrollToTop + "]");

        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName());
        pushToStack(serviceId, url, name);
        showLoading();
        initTabs();

        if (scrollToTop) appBarLayout.setExpanded(true, true);
        animateView(contentRootLayoutHiding,
                false, 0, 0, () -> {
                    handleResult(info);
                    showContentWithAnimation(120, 0, .01f);
                });
    }

    protected void prepareAndLoadInfo() {
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

        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull StreamInfo result) -> {
                    isLoading.set(false);
                    currentInfo = result;
                    showContentWithAnimation(120, 0, 0);
                    handleResult(result);
                }, (@NonNull Throwable throwable) -> {
                    isLoading.set(false);
                    onError(throwable);
                });

    }

    private void initTabs() {
        pageAdapter.clearAllItems();

        if(shouldShowComments()){
            pageAdapter.addFragment(CommentsFragment.getInstance(serviceId, url, name), COMMENTS_TAB_TAG);
        }

        if(showRelatedStreams){
            //temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(new Fragment(), RELATED_TAB_TAG);
        }

        pageAdapter.notifyDataSetUpdate();

        if(pageAdapter.getCount() < 2){
            tabLayout.setVisibility(View.GONE);
        }else{
            tabLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean shouldShowComments() {
        try {
            return showComments && NewPipe.getService(serviceId).isCommentsSupported();
        } catch (ExtractionException e) {
            return false;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void openBackgroundPlayer(final boolean append) {
        AudioStream audioStream = currentInfo.getAudioStreams()
                .get(ListHelper.getDefaultAudioFormat(activity, currentInfo.getAudioStreams()));

        boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 16) {
            openNormalBackgroundPlayer(append);
        } else {
            startOnExternalPlayer(activity, currentInfo, audioStream);
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (!PermissionHelper.isPopupEnabled(activity)) {
            PermissionHelper.showPopupEnablementToast(activity);
            return;
        }

        final PlayQueue itemQueue = new SinglePlayQueue(currentInfo);
        if (append) {
            NavigationHelper.enqueueOnPopupPlayer(activity, itemQueue);
        } else {
            Toast.makeText(activity, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    activity, PopupVideoPlayer.class, itemQueue, getSelectedVideoStream().resolution
            );
            activity.startService(intent);
        }
    }

    private void openVideoPlayer() {
        VideoStream selectedVideoStream = getSelectedVideoStream();

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            startOnExternalPlayer(activity, currentInfo, selectedVideoStream);
        } else {
            openNormalPlayer(selectedVideoStream);
        }
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        final PlayQueue itemQueue = new SinglePlayQueue(currentInfo);
        if (append) {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, itemQueue);
        } else {
            NavigationHelper.playOnBackgroundPlayer(activity, itemQueue);
        }
    }

    private void openNormalPlayer(VideoStream selectedVideoStream) {
        Intent mIntent;
        boolean useOldPlayer = PlayerHelper.isUsingOldPlayer(activity) || (Build.VERSION.SDK_INT < 16);
        if (!useOldPlayer) {
            // ExoPlayer
            final PlayQueue playQueue = new SinglePlayQueue(currentInfo);
            mIntent = NavigationHelper.getPlayerIntent(activity,
                    MainVideoPlayer.class,
                    playQueue,
                    getSelectedVideoStream().getResolution());
        } else {
            // Internal Player
            mIntent = new Intent(activity, PlayVideoActivity.class)
                    .putExtra(PlayVideoActivity.VIDEO_TITLE, currentInfo.getName())
                    .putExtra(PlayVideoActivity.STREAM_URL, selectedVideoStream.getUrl())
                    .putExtra(PlayVideoActivity.VIDEO_URL, currentInfo.getUrl())
                    .putExtra(PlayVideoActivity.START_POSITION, currentInfo.getStartPosition());
        }
        startActivity(mIntent);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoplay(boolean autoplay) {
        this.autoPlayEnabled = autoplay;
    }

    private void startOnExternalPlayer(@NonNull final Context context,
                                       @NonNull final StreamInfo info,
                                       @NonNull final Stream selectedStream) {
        NavigationHelper.playOnExternalPlayer(context, currentInfo.getName(),
                currentInfo.getUploaderName(), selectedStream);

        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        disposables.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        ignored -> {/* successful */},
                        error -> Log.e(TAG, "Register view failure: ", error)
                ));
    }

    @Nullable
    private VideoStream getSelectedVideoStream() {
        return sortedVideoStreams != null ? sortedVideoStreams.get(selectedVideoStreamIndex) : null;
    }

    private void prepareDescription(final String descriptionHtml) {
        if (TextUtils.isEmpty(descriptionHtml)) {
            return;
        }

        disposables.add(Single.just(descriptionHtml)
                .map((@io.reactivex.annotations.NonNull String description) -> {
                    Spanned parsedDescription;
                    if (Build.VERSION.SDK_INT >= 24) {
                        parsedDescription = Html.fromHtml(description, 0);
                    } else {
                        //noinspection deprecation
                        parsedDescription = Html.fromHtml(description);
                    }
                    return parsedDescription;
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@io.reactivex.annotations.NonNull Spanned spanned) -> {
                    videoDescriptionView.setText(spanned);
                    videoDescriptionView.setVisibility(View.VISIBLE);
                }));
    }

    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        int height = isPortrait
                ? (int) (metrics.widthPixels / (16.0f / 9.0f))
                : (int) (metrics.heightPixels / 2f);
        thumbnailImageView.setLayoutParams(
                new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailImageView.setMinimumHeight(height);
    }

    private void showContentWithAnimation(long duration,
                                          long delay,
                                          @FloatRange(from = 0.0f, to = 1.0f) float translationPercent) {
        int translationY = (int) (getResources().getDisplayMetrics().heightPixels *
                (translationPercent > 0.0f ? translationPercent : .06f));

        contentRootLayoutHiding.animate().setListener(null).cancel();
        contentRootLayoutHiding.setAlpha(0f);
        contentRootLayoutHiding.setTranslationY(translationY);
        contentRootLayoutHiding.setVisibility(View.VISIBLE);
        contentRootLayoutHiding.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(delay)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();

        uploaderRootLayout.animate().setListener(null).cancel();
        uploaderRootLayout.setAlpha(0f);
        uploaderRootLayout.setTranslationY(translationY);
        uploaderRootLayout.setVisibility(View.VISIBLE);
        uploaderRootLayout.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay((long) (duration * .5f) + delay)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();

    }

    protected void setInitialData(int serviceId, String url, String name) {
        this.serviceId = serviceId;
        this.url = url;
        this.name = !TextUtils.isEmpty(name) ? name : "";
    }

    private void setErrorImage(final int imageResource) {
        if (thumbnailImageView == null || activity == null) return;

        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, imageResource));
        animateView(thumbnailImageView, false, 0, 0,
                () -> animateView(thumbnailImageView, true, 500));
    }

    @Override
    public void showError(String message, boolean showRetryButton) {
        showError(message, showRetryButton, R.drawable.not_available_monkey);
    }

    protected void showError(String message, boolean showRetryButton, @DrawableRes int imageError) {
        super.showError(message, showRetryButton);
        setErrorImage(imageError);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();

        animateView(contentRootLayoutHiding, false, 200);
        animateView(spinnerToolbar, false, 200);
        animateView(thumbnailPlayButton, false, 50);
        animateView(detailDurationView, false, 100);

        videoTitleTextView.setText(name != null ? name : "");
        videoTitleTextView.setMaxLines(1);
        animateView(videoTitleTextView, true, 0);

        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoTitleToggleArrow.setVisibility(View.GONE);
        videoTitleRoot.setClickable(false);

        imageLoader.cancelDisplayTask(thumbnailImageView);
        imageLoader.cancelDisplayTask(uploaderThumb);
        thumbnailImageView.setImageBitmap(null);
        uploaderThumb.setImageBitmap(null);
    }

    @Override
    public void handleResult(@NonNull StreamInfo info) {
        super.handleResult(info);

        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName());

        if(showRelatedStreams){
            pageAdapter.updateItem(RELATED_TAB_TAG, RelatedVideosFragment.getInstance(currentInfo));
            pageAdapter.notifyDataSetUpdate();
        }

        pushToStack(serviceId, url, name);

        animateView(thumbnailPlayButton, true, 200);
        videoTitleTextView.setText(name);

        if (!TextUtils.isEmpty(info.getUploaderName())) {
            uploaderTextView.setText(info.getUploaderName());
            uploaderTextView.setVisibility(View.VISIBLE);
            uploaderTextView.setSelected(true);
        } else {
            uploaderTextView.setVisibility(View.GONE);
        }
        uploaderThumb.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));

        if (info.getViewCount() >= 0) {
            videoCountView.setText(Localization.localizeViewCount(activity, info.getViewCount()));
            videoCountView.setVisibility(View.VISIBLE);
        } else {
            videoCountView.setVisibility(View.GONE);
        }

        if (info.getDislikeCount() == -1 && info.getLikeCount() == -1) {
            thumbsDownImageView.setVisibility(View.VISIBLE);
            thumbsUpImageView.setVisibility(View.VISIBLE);
            thumbsUpTextView.setVisibility(View.GONE);
            thumbsDownTextView.setVisibility(View.GONE);

            thumbsDisabledTextView.setVisibility(View.VISIBLE);
        } else {
            if (info.getDislikeCount() >= 0) {
                thumbsDownTextView.setText(Localization.shortCount(activity, info.getDislikeCount()));
                thumbsDownTextView.setVisibility(View.VISIBLE);
                thumbsDownImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsDownTextView.setVisibility(View.GONE);
                thumbsDownImageView.setVisibility(View.GONE);
            }

            if (info.getLikeCount() >= 0) {
                thumbsUpTextView.setText(Localization.shortCount(activity, info.getLikeCount()));
                thumbsUpTextView.setVisibility(View.VISIBLE);
                thumbsUpImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsUpTextView.setVisibility(View.GONE);
                thumbsUpImageView.setVisibility(View.GONE);
            }
            thumbsDisabledTextView.setVisibility(View.GONE);
        }

        if (info.getDuration() > 0) {
            detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            detailDurationView.setBackgroundColor(ContextCompat.getColor(activity, R.color.duration_background_color));
            animateView(detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            detailDurationView.setText(R.string.duration_live);
            detailDurationView.setBackgroundColor(ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animateView(detailDurationView, true, 100);
        } else {
            detailDurationView.setVisibility(View.GONE);
        }

        videoTitleRoot.setClickable(true);
        videoTitleToggleArrow.setVisibility(View.VISIBLE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoDescriptionView.setVisibility(View.GONE);
        videoDescriptionRootLayout.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(info.getUploadDate())) {
            videoUploadDateView.setText(Localization.localizeDate(activity, info.getUploadDate()));
        }
        prepareDescription(info.getDescription());

        animateView(spinnerToolbar, true, 500);
        setupActionBar(info);
        initThumbnailViews(info);

        setTitleToUrl(info.getServiceId(), info.getUrl(), info.getName());
        setTitleToUrl(info.getServiceId(), info.getOriginalUrl(), info.getName());

        if (!info.getErrors().isEmpty()) {
            showSnackBarError(info.getErrors(),
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(info.getServiceId()),
                    info.getUrl(),
                    0);
        }

        switch (info.getStreamType()) {
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
                detailControlsDownload.setVisibility(View.GONE);
                spinnerToolbar.setVisibility(View.GONE);
                break;
            default:
                if(info.getAudioStreams().isEmpty()) detailControlsBackground.setVisibility(View.GONE);
                if (!info.getVideoStreams().isEmpty()
                        || !info.getVideoOnlyStreams().isEmpty()) break;

                detailControlsPopup.setVisibility(View.GONE);
                spinnerToolbar.setVisibility(View.GONE);
                thumbnailPlayButton.setImageResource(R.drawable.ic_headset_white_24dp);
                break;
        }

        if (autoPlayEnabled) {
            openVideoPlayer();
            // Only auto play in the first open
            autoPlayEnabled = false;
        }
    }


    public void openDownloadDialog() {
        try {
            DownloadDialog downloadDialog = DownloadDialog.newInstance(currentInfo);
            downloadDialog.setVideoStreams(sortedVideoStreams);
            downloadDialog.setAudioStreams(currentInfo.getAudioStreams());
            downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex);

            downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
        } catch (Exception e) {
            Toast.makeText(activity,
                    R.string.could_not_setup_download_menu,
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        if (exception instanceof YoutubeStreamExtractor.GemaException) {
            onBlockedByGemaError();
        } else if (exception instanceof ContentNotAvailableException) {
            showError(getString(R.string.content_not_available), false);
        } else {
            int errorId = exception instanceof YoutubeStreamExtractor.DecryptException
                    ? R.string.youtube_signature_decryption_error
                    : exception instanceof ParsingException
                    ? R.string.parsing_error
                    : R.string.general_error;
            onUnrecoverableError(exception,
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(serviceId),
                    url,
                    errorId);
        }

        return true;
    }

    public void onBlockedByGemaError() {
        thumbnailBackgroundButton.setOnClickListener((View v) -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(getString(R.string.c3s_url)));
            startActivity(intent);
        });

        showError(getString(R.string.blocked_by_gema), false, R.drawable.gruese_die_gema);
    }
}