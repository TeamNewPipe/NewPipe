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
import android.support.v4.content.ContextCompat;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nirhart.parallaxscroll.views.ParallaxScrollView;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.history.HistoryListener;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.player.MainVideoPlayer;
import org.schabi.newpipe.player.PopupVideoPlayer;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import icepick.State;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class VideoDetailFragment extends BaseStateFragment<StreamInfo> implements BackPressable, SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener, View.OnLongClickListener {
    public static final String AUTO_PLAY = "auto_play";

    // Amount of videos to show on start
    private static final int INITIAL_RELATED_VIDEOS = 8;

    private ActionBarHandler actionBarHandler;
    private ArrayList<VideoStream> sortedStreamVideosList;

    private InfoItemBuilder infoItemBuilder = null;

    private int updateFlags = 0;
    private static final int RELATED_STREAMS_UPDATE_FLAG = 0x1;
    private static final int RESOLUTIONS_MENU_UPDATE_FLAG = 0x2;
    private static final int TOOLBAR_ITEMS_UPDATE_FLAG = 0x4;

    private boolean autoPlayEnabled;
    private boolean showRelatedStreams;
    private boolean wasRelatedStreamsExpanded = false;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private StreamInfo currentInfo;
    private Disposable currentWorker;
    private CompositeDisposable disposables = new CompositeDisposable();

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private Spinner spinnerToolbar;

    private ParallaxScrollView parallaxScrollRootView;
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
    private TextView appendControlsDetail;

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

    private TextView nextStreamTitle;
    private LinearLayout relatedStreamRootLayout;
    private LinearLayout relatedStreamsView;
    private ImageButton relatedStreamExpandButton;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(getString(R.string.show_next_video_key), true);
        PreferenceManager.getDefaultSharedPreferences(activity).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
                if ((updateFlags & RELATED_STREAMS_UPDATE_FLAG) != 0) initRelatedVideos(currentInfo);
                if ((updateFlags & RESOLUTIONS_MENU_UPDATE_FLAG) != 0) setupActionBarHandler(currentInfo);
            }

            if ((updateFlags & TOOLBAR_ITEMS_UPDATE_FLAG) != 0 && actionBarHandler != null) actionBarHandler.updateItemsVisibility();
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
        PreferenceManager.getDefaultSharedPreferences(activity).unregisterOnSharedPreferenceChangeListener(this);

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
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    private static final String INFO_KEY = "info_key";
    private static final String STACK_KEY = "stack_key";
    private static final String WAS_RELATED_EXPANDED_KEY = "was_related_expanded_key";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Check if the next video label and video is visible,
        // if it is, include the two elements in the next check
        int nextCount = currentInfo != null && currentInfo.getNextVideo() != null ? 2 : 0;
        if (relatedStreamsView != null && relatedStreamsView.getChildCount() > INITIAL_RELATED_VIDEOS + nextCount) {
            outState.putSerializable(WAS_RELATED_EXPANDED_KEY, true);
        }

        if (!isLoading.get() && currentInfo != null && isVisible()) {
            outState.putSerializable(INFO_KEY, currentInfo);
        }

        outState.putSerializable(STACK_KEY, stack);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        wasRelatedStreamsExpanded = savedState.getBoolean(WAS_RELATED_EXPANDED_KEY, false);
        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof StreamInfo) {
            //noinspection unchecked
            currentInfo = (StreamInfo) serializable;
            InfoCache.getInstance().putInfo(currentInfo);
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
            case R.id.detail_uploader_root_layout:
                if (TextUtils.isEmpty(currentInfo.getUploaderUrl())) {
                    Log.w(TAG, "Can't open channel because we got no channel URL");
                } else {
                    NavigationHelper.openChannelFragment(
                            getFragmentManager(),
                            currentInfo.getServiceId(),
                            currentInfo.getUploaderUrl(),
                            currentInfo.getUploaderName());
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                openVideoPlayer();
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndDescription();
                break;
            case R.id.detail_related_streams_expand:
                toggleExpandRelatedVideos(currentInfo);
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

    private void toggleExpandRelatedVideos(StreamInfo info) {
        if (DEBUG) Log.d(TAG, "toggleExpandRelatedVideos() called with: info = [" + info + "]");
        if (!showRelatedStreams) return;

        int nextCount = info.getNextVideo() != null ? 2 : 0;
        int initialCount = INITIAL_RELATED_VIDEOS + nextCount;

        if (relatedStreamsView.getChildCount() > initialCount) {
            relatedStreamsView.removeViews(initialCount, relatedStreamsView.getChildCount() - (initialCount));
            relatedStreamExpandButton.setImageDrawable(ContextCompat.getDrawable(activity, resolveResourceIdFromAttr(R.attr.expand)));
            return;
        }

        //Log.d(TAG, "toggleExpandRelatedVideos() called with: info = [" + info + "], from = [" + INITIAL_RELATED_VIDEOS + "]");
        for (int i = INITIAL_RELATED_VIDEOS; i < info.getRelatedStreams().size(); i++) {
            InfoItem item = info.getRelatedStreams().get(i);
            //Log.d(TAG, "i = " + i);
            relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, item));
        }
        relatedStreamExpandButton.setImageDrawable(ContextCompat.getDrawable(activity, resolveResourceIdFromAttr(R.attr.collapse)));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        spinnerToolbar = activity.findViewById(R.id.toolbar).findViewById(R.id.toolbar_spinner);

        parallaxScrollRootView = rootView.findViewById(R.id.detail_main_content);

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
        appendControlsDetail = rootView.findViewById(R.id.touch_append_detail);

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

        relatedStreamRootLayout = rootView.findViewById(R.id.detail_related_streams_root_layout);
        nextStreamTitle = rootView.findViewById(R.id.detail_next_stream_title);
        relatedStreamsView = rootView.findViewById(R.id.detail_related_streams_view);

        relatedStreamExpandButton = rootView.findViewById(R.id.detail_related_streams_expand);

        actionBarHandler = new ActionBarHandler(activity);
        infoItemBuilder = new InfoItemBuilder(activity);
        setHeightThumbnail();
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        infoItemBuilder.setOnStreamSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener<StreamInfoItem>() {
            @Override
            public void selected(StreamInfoItem selectedItem) {
                selectAndLoadVideo(selectedItem.getServiceId(), selectedItem.getUrl(), selectedItem.getName());
            }

            @Override
            public void held(StreamInfoItem selectedItem) {
                showStreamDialog(selectedItem);
            }
        });

        videoTitleRoot.setOnClickListener(this);
        uploaderRootLayout.setOnClickListener(this);
        thumbnailBackgroundButton.setOnClickListener(this);
        detailControlsBackground.setOnClickListener(this);
        detailControlsPopup.setOnClickListener(this);
        relatedStreamExpandButton.setOnClickListener(this);

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
                context.getResources().getString(R.string.enqueue_on_popup)
        };

        final DialogInterface.OnClickListener actions = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                switch (i) {
                    case 0:
                        NavigationHelper.enqueueOnBackgroundPlayer(context, new SinglePlayQueue(item));
                        break;
                    case 1:
                        NavigationHelper.enqueueOnPopupPlayer(getActivity(), new SinglePlayQueue(item));
                        break;
                    default:
                        break;
                }
            }
        };

        new InfoItemDialog(getActivity(), item, commands, actions).show();
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (!PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(getString(R.string.show_hold_to_append_key), true)) return false;

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    animateView(appendControlsDetail, true, 250, 0, new Runnable() {
                        @Override
                        public void run() {
                            animateView(appendControlsDetail, false, 1500, 1000);
                        }
                    });
                }
                return false;
            }
        };
    }

    private void initThumbnailViews(StreamInfo info) {
        thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);
        if (!TextUtils.isEmpty(info.getThumbnailUrl())) {
            imageLoader.displayImage(info.getThumbnailUrl(), thumbnailImageView, DISPLAY_THUMBNAIL_OPTIONS, new SimpleImageLoadingListener() {
                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    ErrorActivity.reportError(activity, failReason.getCause(), null, activity.findViewById(android.R.id.content), ErrorActivity.ErrorInfo.make(UserAction.LOAD_IMAGE, NewPipe.getNameOfService(currentInfo.getServiceId()), imageUri, R.string.could_not_load_thumbnails));
                }
            });
        }
        
        if (!TextUtils.isEmpty(info.getUploaderAvatarUrl())) {
            imageLoader.displayImage(info.getUploaderAvatarUrl(), uploaderThumb, DISPLAY_AVATAR_OPTIONS);
        }
    }

    private void initRelatedVideos(StreamInfo info) {
        if (relatedStreamsView.getChildCount() > 0) relatedStreamsView.removeAllViews();

        if (info.getNextVideo() != null && showRelatedStreams) {
            nextStreamTitle.setVisibility(View.VISIBLE);
            relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, info.getNextVideo()));
            relatedStreamsView.addView(getSeparatorView());
            relatedStreamRootLayout.setVisibility(View.VISIBLE);
        } else nextStreamTitle.setVisibility(View.GONE);

        if (info.related_streams != null && !info.related_streams.isEmpty() && showRelatedStreams) {
            //long first = System.nanoTime(), each;
            int to = info.getRelatedStreams().size() >= INITIAL_RELATED_VIDEOS ? INITIAL_RELATED_VIDEOS : info.getRelatedStreams().size();
            for (int i = 0; i < to; i++) {
                InfoItem item = info.getRelatedStreams().get(i);
                //each = System.nanoTime();
                relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, item));
                //if (DEBUG) Log.d(TAG, "each took " + ((System.nanoTime() - each) / 1000000L) + "ms");
            }
            //if (DEBUG) Log.d(TAG, "Total time " + ((System.nanoTime() - first) / 1000000L) + "ms");

            relatedStreamRootLayout.setVisibility(View.VISIBLE);
            relatedStreamExpandButton.setVisibility(View.VISIBLE);

            relatedStreamExpandButton.setImageDrawable(ContextCompat.getDrawable(activity, resolveResourceIdFromAttr(R.attr.expand)));
        } else {
            if (info.getNextVideo() == null) relatedStreamRootLayout.setVisibility(View.GONE);
            relatedStreamExpandButton.setVisibility(View.GONE);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        actionBarHandler.setupMenu(menu, inflater);
        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return (!isLoading.get() && actionBarHandler.onItemSelected(item)) || super.onOptionsItemSelected(item);
    }

    private static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        NavigationHelper.installKore(context);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        builder.create().show();
    }

    private void setupActionBarHandler(final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandler() called with: info = [" + info + "]");
        sortedStreamVideosList = new ArrayList<>(ListHelper.getSortedStreamVideosList(activity, info.getVideoStreams(), info.getVideoOnlyStreams(), false));
        actionBarHandler.setupStreamList(sortedStreamVideosList, spinnerToolbar);
        actionBarHandler.setOnShareListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                shareUrl(info.getUrl());
            }
        });

        actionBarHandler.setOnOpenInBrowserListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                openUrlInBrowser(info.getUrl());
            }
        });

        actionBarHandler.setOnPlayWithKodiListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                try {
                    NavigationHelper.playWithKore(activity, Uri.parse(info.getUrl().replace("https", "http")));
                    if(activity instanceof HistoryListener) {
                        ((HistoryListener) activity).onVideoPlayed(info, null);
                    }
                } catch (Exception e) {
                    if(DEBUG) Log.i(TAG, "Failed to start kore", e);
                    showInstallKoreDialog(activity);
                }
            }
        });

        actionBarHandler.setOnDownloadListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (!PermissionHelper.checkStoragePermissions(activity)) {
                    return;
                }

                try {
                    DownloadDialog downloadDialog = DownloadDialog.newInstance(info, sortedStreamVideosList, selectedStreamId);
                    downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
                } catch (Exception e) {
                    Toast.makeText(activity, R.string.could_not_setup_download_menu, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
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
    protected LinkedList<StackItem> stack = new LinkedList<>();

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
                if (stack.peek().getServiceId() == serviceId && stackItem.getUrl().equals(videoUrl)) stackItem.setTitle(name);
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
        if (DEBUG) Log.d(TAG, "prepareAndHandleInfo() called with: info = [" + info + "], scrollToTop = [" + scrollToTop + "]");

        setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        pushToStack(serviceId, url, name);
        showLoading();

        Log.d(TAG, "prepareAndHandleInfo() called parallaxScrollRootView.getScrollY(): " + parallaxScrollRootView.getScrollY());
        final boolean greaterThanThreshold = parallaxScrollRootView.getScrollY() > (int)
                (getResources().getDisplayMetrics().heightPixels * .1f);

        if (scrollToTop) parallaxScrollRootView.smoothScrollTo(0, 0);
        animateView(contentRootLayoutHiding, false, greaterThanThreshold ? 250 : 0, 0, new Runnable() {
            @Override
            public void run() {
                handleResult(info);
                showContentWithAnimation(120, 0, .01f);
            }
        });
    }

    protected void prepareAndLoadInfo() {
        parallaxScrollRootView.smoothScrollTo(0, 0);
        pushToStack(serviceId, url, name);
        startLoading(false);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);

        currentInfo = null;
        if (currentWorker != null) currentWorker.dispose();

        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<StreamInfo>() {
                    @Override
                    public void accept(@NonNull StreamInfo result) throws Exception {
                        isLoading.set(false);
                        currentInfo = result;
                        showContentWithAnimation(120, 0, 0);
                        handleResult(result);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        isLoading.set(false);
                        onError(throwable);
                    }
                });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void openBackgroundPlayer(final boolean append) {
        AudioStream audioStream = currentInfo.getAudioStreams().get(ListHelper.getDefaultAudioFormat(activity, currentInfo.getAudioStreams()));

        if (activity instanceof HistoryListener) {
            ((HistoryListener) activity).onAudioPlayed(currentInfo, audioStream);
        }

        boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 16) {
            openNormalBackgroundPlayer(append);
        } else {
            openExternalBackgroundPlayer(audioStream);
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (!PermissionHelper.isPopupEnabled(activity)) {
            PermissionHelper.showPopupEnablementToast(activity);
            return;
        }

        if (activity instanceof HistoryListener) {
            ((HistoryListener) activity).onVideoPlayed(currentInfo, getSelectedVideoStream());
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

        if (activity instanceof HistoryListener) {
            ((HistoryListener) activity).onVideoPlayed(currentInfo, selectedVideoStream);
        }

        if (PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            openExternalVideoPlayer(selectedVideoStream);
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

    private void openExternalBackgroundPlayer(AudioStream audioStream) {
        Intent intent;
        intent = new Intent();
        try {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(audioStream.getUrl()), audioStream.getFormat().getMimeType());
            intent.putExtra(Intent.EXTRA_TITLE, currentInfo.getName());
            intent.putExtra("title", currentInfo.getName());
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(R.string.no_player_found)
                    .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(activity.getString(R.string.fdroid_vlc_url)));
                            activity.startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "You unlocked a secret unicorn.");
                        }
                    });
            builder.create().show();
            Log.e(TAG, "Either no Streaming player for audio was installed, or something important crashed:");
            e.printStackTrace();
        }
    }

    private void openNormalPlayer(VideoStream selectedVideoStream) {
        Intent mIntent;
        boolean useOldPlayer = PlayerHelper.isUsingOldPlayer(activity) || (Build.VERSION.SDK_INT < 16);
        if (!useOldPlayer) {
            // ExoPlayer
            final PlayQueue playQueue = new SinglePlayQueue(currentInfo);
            mIntent = NavigationHelper.getPlayerIntent(activity, MainVideoPlayer.class, playQueue, getSelectedVideoStream().getResolution());
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

    private void openExternalVideoPlayer(VideoStream selectedVideoStream) {
        // External Player
        Intent intent = new Intent();
        try {
            intent.setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(selectedVideoStream.getUrl()), selectedVideoStream.getFormat().getMimeType())
                    .putExtra(Intent.EXTRA_TITLE, currentInfo.getName())
                    .putExtra("title", currentInfo.getName());
            this.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(R.string.no_player_found)
                    .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent()
                                    .setAction(Intent.ACTION_VIEW)
                                    .setData(Uri.parse(getString(R.string.fdroid_vlc_url)));
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);
            builder.create().show();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoplay(boolean autoplay) {
        this.autoPlayEnabled = autoplay;
    }

    private VideoStream getSelectedVideoStream() {
        return sortedStreamVideosList.get(actionBarHandler.getSelectedVideoStream());
    }

    private void prepareDescription(final String descriptionHtml) {
        if (TextUtils.isEmpty(descriptionHtml)) {
            return;
        }

        disposables.add(Single.just(descriptionHtml)
                .map(new Function<String, Spanned>() {
                    @Override
                    public Spanned apply(@io.reactivex.annotations.NonNull String description) throws Exception {
                        Spanned parsedDescription;
                        if (Build.VERSION.SDK_INT >= 24) {
                            parsedDescription = Html.fromHtml(description, 0);
                        } else {
                            //noinspection deprecation
                            parsedDescription = Html.fromHtml(description);
                        }
                        return parsedDescription;
                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Spanned>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Spanned spanned) throws Exception {
                        videoDescriptionView.setText(spanned);
                        videoDescriptionView.setVisibility(View.VISIBLE);
                    }
                }));
    }

    private View getSeparatorView() {
        View separator = new View(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        int m8 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        int m5 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        params.setMargins(m8, m5, m8, m5);
        separator.setLayoutParams(params);

        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(R.attr.separator_color, typedValue, true);
        separator.setBackgroundColor(typedValue.data);

        return separator;
    }

    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        int height = isPortrait ? (int) (metrics.widthPixels / (16.0f / 9.0f)) : (int) (metrics.heightPixels / 2f);
        thumbnailImageView.setScaleType(isPortrait ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        thumbnailImageView.setLayoutParams(new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailImageView.setMinimumHeight(height);
    }

    private void showContentWithAnimation(long duration, long delay, @FloatRange(from = 0.0f, to = 1.0f) float translationPercent) {
        int translationY = (int) (getResources().getDisplayMetrics().heightPixels *
                (translationPercent > 0.0f ? translationPercent : .06f));

        contentRootLayoutHiding.animate().setListener(null).cancel();
        contentRootLayoutHiding.setAlpha(0f);
        contentRootLayoutHiding.setTranslationY(translationY);
        contentRootLayoutHiding.setVisibility(View.VISIBLE);
        contentRootLayoutHiding.animate().alpha(1f).translationY(0)
                .setStartDelay(delay).setDuration(duration).setInterpolator(new FastOutSlowInInterpolator()).start();

        uploaderRootLayout.animate().setListener(null).cancel();
        uploaderRootLayout.setAlpha(0f);
        uploaderRootLayout.setTranslationY(translationY);
        uploaderRootLayout.setVisibility(View.VISIBLE);
        uploaderRootLayout.animate().alpha(1f).translationY(0)
                .setStartDelay((long) (duration * .5f) + delay).setDuration(duration).setInterpolator(new FastOutSlowInInterpolator()).start();

        if (showRelatedStreams) {
            relatedStreamRootLayout.animate().setListener(null).cancel();
            relatedStreamRootLayout.setAlpha(0f);
            relatedStreamRootLayout.setTranslationY(translationY);
            relatedStreamRootLayout.setVisibility(View.VISIBLE);
            relatedStreamRootLayout.animate().alpha(1f).translationY(0)
                    .setStartDelay((long) (duration * .8f) + delay).setDuration(duration).setInterpolator(new FastOutSlowInInterpolator()).start();
        }
    }

    protected void setInitialData(int serviceId, String url, String name) {
        this.serviceId = serviceId;
        this.url = url;
        this.name = !TextUtils.isEmpty(name) ? name : "";
    }

    private void setErrorImage(final int imageResource) {
        if (thumbnailImageView == null || activity == null) return;

        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, imageResource));
        animateView(thumbnailImageView, false, 0, 0, new Runnable() {
            @Override
            public void run() {
                animateView(thumbnailImageView, true, 500);
            }
        });
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

        setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        pushToStack(serviceId, url, name);

        animateView(thumbnailPlayButton, true, 200);
        videoTitleTextView.setText(name);

        if (!TextUtils.isEmpty(info.getUploaderName())) {
            uploaderTextView.setText(info.getUploaderName());
            uploaderTextView.setVisibility(View.VISIBLE);
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
        setupActionBarHandler(info);
        initThumbnailViews(info);
        initRelatedVideos(info);
        if (wasRelatedStreamsExpanded) {
            toggleExpandRelatedVideos(currentInfo);
            wasRelatedStreamsExpanded = false;
        }
        setTitleToUrl(info.getServiceId(), info.getUrl(), info.getName());

        if (!info.getErrors().isEmpty()) {
            showSnackBarError(info.getErrors(), UserAction.REQUESTED_STREAM, NewPipe.getNameOfService(info.getServiceId()), info.getUrl(), 0);
        }

        if (autoPlayEnabled) {
            openVideoPlayer();
            // Only auto play in the first open
            autoPlayEnabled = false;
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
        } else if (exception instanceof YoutubeStreamExtractor.LiveStreamException) {
            showError(getString(R.string.live_streams_not_supported), false);
        } else if (exception instanceof ContentNotAvailableException) {
            showError(getString(R.string.content_not_available), false);
        } else {
            int errorId = exception instanceof YoutubeStreamExtractor.DecryptException ? R.string.youtube_signature_decryption_error :
                    exception instanceof ParsingException ? R.string.parsing_error : R.string.general_error;
            onUnrecoverableError(exception, UserAction.REQUESTED_STREAM, NewPipe.getNameOfService(serviceId), url, errorId);
        }

        return true;
    }

    public void onBlockedByGemaError() {
        thumbnailBackgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.c3s_url)));
                startActivity(intent);
            }
        });

        showError(getString(R.string.blocked_by_gema), false, R.drawable.gruese_die_gema);
    }
}