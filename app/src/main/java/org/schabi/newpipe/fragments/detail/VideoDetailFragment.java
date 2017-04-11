package org.schabi.newpipe.fragments.detail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nirhart.parallaxscroll.views.ParallaxScrollView;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.ImageErrorLoadingListener;
import org.schabi.newpipe.Localization;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.VideoStream;
import org.schabi.newpipe.fragments.OnItemSelectedListener;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.player.AbstractPlayer;
import org.schabi.newpipe.player.BackgroundPlayer;
import org.schabi.newpipe.player.ExoPlayerActivity;
import org.schabi.newpipe.player.PlayVideoActivity;
import org.schabi.newpipe.player.PopupVideoPlayer;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.Utils;
import org.schabi.newpipe.workers.StreamExtractorWorker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("FieldCanBeLocal")
public class VideoDetailFragment extends Fragment implements StreamExtractorWorker.OnStreamInfoReceivedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private final String TAG = "VideoDetailFragment@" + Integer.toHexString(hashCode());

    private static final String KORE_PACKET = "org.xbmc.kore";
    private static final String SERVICE_ID_KEY = "service_id_key";
    private static final String VIDEO_URL_KEY = "video_url_key";
    private static final String VIDEO_TITLE_KEY = "video_title_key";
    private static final String STACK_KEY = "stack_key";

    public static final String AUTO_PLAY = "auto_play";

    private AppCompatActivity activity;
    private OnItemSelectedListener onItemSelectedListener;
    private ActionBarHandler actionBarHandler;

    private InfoItemBuilder infoItemBuilder = null;
    private StreamInfo currentStreamInfo = null;
    private StreamExtractorWorker curExtractorWorker;

    private String videoTitle;
    private String videoUrl;
    private int serviceId = -1;

    private AtomicBoolean isLoading = new AtomicBoolean(false);
    private boolean needUpdate = false;

    private boolean autoPlayEnabled;
    private boolean showRelatedStreams;

    private static final ImageLoader imageLoader = ImageLoader.getInstance();
    private static final DisplayImageOptions displayImageOptions =
            new DisplayImageOptions.Builder().displayer(new FadeInBitmapDisplayer(400)).cacheInMemory(false).build();
    private Bitmap streamThumbnail = null;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private ProgressBar loadingProgressBar;

    private ParallaxScrollView parallaxScrollRootView;
    private RelativeLayout contentRootLayout;

    private Button thumbnailBackgroundButton;
    private ImageView thumbnailImageView;
    private ImageView thumbnailPlayButton;

    private View videoTitleRoot;
    private TextView videoTitleTextView;
    private ImageView videoTitleToggleArrow;
    private TextView videoCountView;

    private RelativeLayout videoDescriptionRootLayout;
    private TextView videoUploadDateView;
    private TextView videoDescriptionView;

    private Button uploaderButton;
    private TextView uploaderTextView;
    private ImageView uploaderThumb;

    private TextView thumbsUpTextView;
    private ImageView thumbsUpImageView;
    private TextView thumbsDownTextView;
    private ImageView thumbsDownImageView;
    private TextView thumbsDisabledTextView;

    private TextView nextStreamTitle;
    private RelativeLayout relatedStreamRootLayout;
    private LinearLayout relatedStreamsView;

    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(int serviceId, String url) {
        return getInstance(serviceId, url, "");
    }

    public static VideoDetailFragment getInstance(int serviceId, String videoUrl, String videoTitle) {
        VideoDetailFragment instance = getInstance();
        instance.selectVideo(serviceId, videoUrl, videoTitle);
        return instance;
    }

    public static VideoDetailFragment getInstance() {
        return new VideoDetailFragment();
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (AppCompatActivity) context;
        onItemSelectedListener = (OnItemSelectedListener) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            videoTitle = savedInstanceState.getString(VIDEO_TITLE_KEY);
            videoUrl = savedInstanceState.getString(VIDEO_URL_KEY);
            serviceId = savedInstanceState.getInt(SERVICE_ID_KEY);
            Serializable serializable = savedInstanceState.getSerializable(STACK_KEY);
            if (serializable instanceof Stack) {
                //noinspection unchecked
                Stack<StackItem> list = (Stack<StackItem>) serializable;
                stack.clear();
                stack.addAll(list);
            }
        }

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(getString(R.string.show_next_video_key), true);
        PreferenceManager.getDefaultSharedPreferences(activity).registerOnSharedPreferenceChangeListener(this);
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        isLoading.set(false);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_detail, container, false);
    }

    @Override
    public void onViewCreated(View rootView, Bundle savedInstanceState) {
        initViews(rootView);
        initListeners();
        isLoading.set(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        thumbnailImageView.setImageBitmap(null);
        relatedStreamsView.removeAllViews();

        loadingProgressBar = null;

        parallaxScrollRootView = null;
        contentRootLayout = null;

        thumbnailBackgroundButton = null;
        thumbnailImageView = null;
        thumbnailPlayButton = null;

        videoTitleRoot = null;
        videoTitleTextView = null;
        videoTitleToggleArrow = null;
        videoCountView = null;

        videoDescriptionRootLayout = null;
        videoUploadDateView = null;
        videoDescriptionView = null;

        uploaderButton = null;
        uploaderTextView = null;
        uploaderThumb = null;

        thumbsUpTextView = null;
        thumbsUpImageView = null;
        thumbsDownTextView = null;
        thumbsDownImageView = null;
        thumbsDisabledTextView = null;

        nextStreamTitle = null;
        relatedStreamRootLayout = null;
        relatedStreamsView = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Currently only used for enable/disable related videos
        // but can be extended for other live settings changes
        if (needUpdate) {
            if (relatedStreamsView != null) initRelatedVideos(currentStreamInfo);
            needUpdate = false;
        }

        // Check if it was loading when the activity was stopped/paused,
        // because when this happen, the curExtractorWorker is cancelled
        if (isLoading.get()) selectAndLoadVideo(serviceId, videoUrl, videoTitle);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (curExtractorWorker != null && curExtractorWorker.isRunning()) curExtractorWorker.cancel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(activity).unregisterOnSharedPreferenceChangeListener(this);
        imageLoader.clearMemoryCache();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(VIDEO_URL_KEY, videoUrl);
        outState.putString(VIDEO_TITLE_KEY, videoTitle);
        outState.putInt(SERVICE_ID_KEY, serviceId);
        outState.putSerializable(STACK_KEY, stack);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetail(onItemSelectedListener, serviceId, videoUrl, videoTitle);
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
            needUpdate = true;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initViews(View rootView) {
        loadingProgressBar = (ProgressBar) rootView.findViewById(R.id.detail_loading_progress_bar);

        parallaxScrollRootView = (ParallaxScrollView) rootView.findViewById(R.id.detail_main_content);

        //thumbnailRootLayout = (RelativeLayout) rootView.findViewById(R.id.detail_thumbnail_root_layout);
        thumbnailBackgroundButton = (Button) rootView.findViewById(R.id.detail_stream_thumbnail_background_button);
        thumbnailImageView = (ImageView) rootView.findViewById(R.id.detail_thumbnail_image_view);
        thumbnailPlayButton = (ImageView) rootView.findViewById(R.id.detail_thumbnail_play_button);

        contentRootLayout = (RelativeLayout) rootView.findViewById(R.id.detail_content_root_layout);

        videoTitleRoot = rootView.findViewById(R.id.detail_title_root_layout);
        videoTitleTextView = (TextView) rootView.findViewById(R.id.detail_video_title_view);
        videoTitleToggleArrow = (ImageView) rootView.findViewById(R.id.detail_toggle_description_view);
        videoCountView = (TextView) rootView.findViewById(R.id.detail_view_count_view);

        videoDescriptionRootLayout = (RelativeLayout) rootView.findViewById(R.id.detail_description_root_layout);
        videoUploadDateView = (TextView) rootView.findViewById(R.id.detail_upload_date_view);
        videoDescriptionView = (TextView) rootView.findViewById(R.id.detail_description_view);

        //thumbsRootLayout = (LinearLayout) rootView.findViewById(R.id.detail_thumbs_root_layout);
        thumbsUpTextView = (TextView) rootView.findViewById(R.id.detail_thumbs_up_count_view);
        thumbsUpImageView = (ImageView) rootView.findViewById(R.id.detail_thumbs_up_img_view);
        thumbsDownTextView = (TextView) rootView.findViewById(R.id.detail_thumbs_down_count_view);
        thumbsDownImageView = (ImageView) rootView.findViewById(R.id.detail_thumbs_down_img_view);
        thumbsDisabledTextView = (TextView) rootView.findViewById(R.id.detail_thumbs_disabled_view);

        //uploaderRootLayout = (FrameLayout) rootView.findViewById(R.id.detail_uploader_root_layout);
        uploaderButton = (Button) rootView.findViewById(R.id.detail_uploader_button);
        uploaderTextView = (TextView) rootView.findViewById(R.id.detail_uploader_text_view);
        uploaderThumb = (ImageView) rootView.findViewById(R.id.detail_uploader_thumbnail_view);

        relatedStreamRootLayout = (RelativeLayout) rootView.findViewById(R.id.detail_related_streams_root_layout);
        nextStreamTitle = (TextView) rootView.findViewById(R.id.detail_next_stream_title);
        relatedStreamsView = (LinearLayout) rootView.findViewById(R.id.detail_related_streams_view);

        actionBarHandler = new ActionBarHandler(activity);
        videoDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());

        infoItemBuilder = new InfoItemBuilder(activity, rootView.findViewById(android.R.id.content));

        setHeightThumbnail();
    }

    private void initListeners() {
        videoTitleRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        });
        thumbnailBackgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLoading.get() && currentStreamInfo != null) playVideo(currentStreamInfo);
            }
        });

        infoItemBuilder.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(int serviceId, String url, String title) {
                //NavigationHelper.openVideoDetail(activity, url, serviceId);
                selectAndLoadVideo(serviceId, url, title);
            }
        });

        uploaderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavigationHelper.openChannel(onItemSelectedListener, currentStreamInfo.service_id, currentStreamInfo.channel_url, currentStreamInfo.uploader);
            }
        });
    }

    private void initThumbnailViews(StreamInfo info) {
        if (info.thumbnail_url != null && !info.thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.thumbnail_url, thumbnailImageView,
                    displayImageOptions, new SimpleImageLoadingListener() {

                        @Override
                        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                            streamThumbnail = loadedImage;

                            if (streamThumbnail != null) {
                                // TODO: Change the thumbnail implementation

                                // When the thumbnail is not loaded yet, it not passes to the service in time
                                // so, I can notify the service through a broadcast, but the problem is
                                // when I click in another video, another thumbnail will be load, and will
                                // notify again, so I send the videoUrl and compare with the service's url
                                ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = streamThumbnail;
                                Intent intent = new Intent(AbstractPlayer.ACTION_UPDATE_THUMB);
                                intent.putExtra(AbstractPlayer.VIDEO_URL, currentStreamInfo.webpage_url);
                                activity.sendBroadcast(intent);
                            }
                        }

                        @Override
                        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                            ErrorActivity.reportError(activity,
                                    failReason.getCause(), null, activity.findViewById(android.R.id.content),
                                    ErrorActivity.ErrorInfo.make(ErrorActivity.LOAD_IMAGE,
                                            NewPipe.getNameOfService(currentStreamInfo.service_id), imageUri,
                                            R.string.could_not_load_thumbnails));
                        }

                    });
        } else thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);

        if (info.uploader_thumbnail_url != null && !info.uploader_thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.uploader_thumbnail_url,
                    uploaderThumb, displayImageOptions,
                    new ImageErrorLoadingListener(activity, activity.findViewById(android.R.id.content), info.service_id));
        }
    }

    private void initRelatedVideos(StreamInfo info) {
        if (relatedStreamsView.getChildCount() > 0) relatedStreamsView.removeAllViews();

        if (info.next_video != null && showRelatedStreams) {
            nextStreamTitle.setVisibility(View.VISIBLE);
            relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, info.next_video));
            relatedStreamsView.addView(getSeparatorView());
            relatedStreamRootLayout.setVisibility(View.VISIBLE);
        } else nextStreamTitle.setVisibility(View.GONE);

        if (info.related_streams != null && !info.related_streams.isEmpty() && showRelatedStreams) {
            for (InfoItem item : info.related_streams) {
                relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, item));
            }
            relatedStreamRootLayout.setVisibility(View.VISIBLE);
        } else if (info.next_video == null) relatedStreamRootLayout.setVisibility(View.GONE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        actionBarHandler.setupMenu(menu, inflater);
        actionBarHandler.setupNavMenu(activity);
        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowTitleEnabled(false);
            //noinspection deprecation
            supportActionBar.setNavigationMode(0);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return actionBarHandler.onItemSelected(item) || super.onOptionsItemSelected(item);
    }

    private void setupActionBarHandler(final StreamInfo info) {
        if (activity.getSupportActionBar() != null) {
            //noinspection deprecation
            activity.getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        }

        actionBarHandler.setupStreamList(info.video_streams);
        actionBarHandler.setOnShareListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, info.webpage_url);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, activity.getString(R.string.share_dialog_title)));
            }
        });

        actionBarHandler.setOnOpenInBrowserListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(info.webpage_url));
                startActivity(Intent.createChooser(intent, activity.getString(R.string.choose_browser)));
            }
        });

        actionBarHandler.setOnOpenInPopupListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionHelper.checkSystemAlertWindowPermission(activity)) {
                    Toast.makeText(activity, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
                    return;
                }
                if (streamThumbnail != null) ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = streamThumbnail;

                Intent i = new Intent(activity, PopupVideoPlayer.class);
                Toast.makeText(activity, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show();
                i.putExtra(AbstractPlayer.VIDEO_TITLE, info.title)
                        .putExtra(AbstractPlayer.CHANNEL_NAME, info.uploader)
                        .putExtra(AbstractPlayer.VIDEO_URL, info.webpage_url)
                        .putExtra(AbstractPlayer.INDEX_SEL_VIDEO_STREAM, selectedStreamId)
                        .putExtra(AbstractPlayer.VIDEO_STREAMS_LIST, new ArrayList<>(info.video_streams));
                if (info.start_position > 0) i.putExtra(AbstractPlayer.START_POSITION, info.start_position * 1000);
                activity.startService(i);
            }
        });

        actionBarHandler.setOnPlayWithKodiListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setPackage(KORE_PACKET);
                    intent.setData(Uri.parse(info.webpage_url.replace("https", "http")));
                    activity.startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setMessage(R.string.kore_not_found)
                            .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse(activity.getString(R.string.fdroid_kore_url)));
                                    activity.startActivity(intent);
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            });
                    builder.create().show();
                }
            }
        });

        actionBarHandler.setOnDownloadListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {

                if (isLoading.get() || !PermissionHelper.checkStoragePermissions(activity)) {
                    return;
                }

                try {
                    Bundle args = new Bundle();

                    // Sometimes it may be that some information is not available due to changes fo the
                    // website which was crawled. Then the ui has to understand this and act right.

                    if (info.audio_streams != null) {
                        AudioStream audioStream =
                                info.audio_streams.get(Utils.getPreferredAudioFormat(activity, info.audio_streams));

                        String audioSuffix = "." + MediaFormat.getSuffixById(audioStream.format);
                        args.putString(DownloadDialog.AUDIO_URL, audioStream.url);
                        args.putString(DownloadDialog.FILE_SUFFIX_AUDIO, audioSuffix);
                    }

                    if (info.video_streams != null) {
                        VideoStream selectedStreamItem = info.video_streams.get(selectedStreamId);
                        String videoSuffix = "." + MediaFormat.getSuffixById(selectedStreamItem.format);
                        args.putString(DownloadDialog.FILE_SUFFIX_VIDEO, videoSuffix);
                        args.putString(DownloadDialog.VIDEO_URL, selectedStreamItem.url);
                    }

                    args.putString(DownloadDialog.TITLE, info.title);
                    DownloadDialog downloadDialog = DownloadDialog.newInstance(args);
                    downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
                } catch (Exception e) {
                    Toast.makeText(activity,
                            R.string.could_not_setup_download_menu, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });

        if (info.audio_streams == null) {
            actionBarHandler.showAudioAction(false);
        } else {
            actionBarHandler.setOnPlayAudioListener(new ActionBarHandler.OnActionListener() {
                @Override
                public void onActionSelected(int selectedStreamId) {
                    if (isLoading.get()) return;

                    boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                            .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);
                    Intent intent;
                    AudioStream audioStream =
                            info.audio_streams.get(Utils.getPreferredAudioFormat(activity, info.audio_streams));
                    if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 18) {
                        //internal music player: explicit intent
                        if (!BackgroundPlayer.isRunning && streamThumbnail != null) {
                            ActivityCommunicator.getCommunicator()
                                    .backgroundPlayerThumbnail = streamThumbnail;
                            intent = new Intent(activity, BackgroundPlayer.class);

                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(audioStream.url),
                                    MediaFormat.getMimeById(audioStream.format));
                            intent.putExtra(BackgroundPlayer.TITLE, info.title);
                            intent.putExtra(BackgroundPlayer.WEB_URL, info.webpage_url);
                            intent.putExtra(BackgroundPlayer.SERVICE_ID, serviceId);
                            intent.putExtra(BackgroundPlayer.CHANNEL_NAME, info.uploader);
                            activity.startService(intent);
                        }
                    } else {
                        intent = new Intent();
                        try {
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(audioStream.url),
                                    MediaFormat.getMimeById(audioStream.format));
                            intent.putExtra(Intent.EXTRA_TITLE, info.title);
                            intent.putExtra("title", info.title);
                            // HERE !!!
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
                }
            });
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    private final Stack<StackItem> stack = new Stack<>();

    public void clearHistory() {
        stack.clear();
    }

    public void pushToStack(String videoUrl, String videoTitle) {

        if (stack.size() > 0 && stack.peek().getUrl().equals(videoUrl)) return;
        stack.push(new StackItem(videoUrl, videoTitle));

    }

    public void setTitleToUrl(String videoUrl, String videoTitle) {
        if (videoTitle != null && !videoTitle.isEmpty()) {
            for (StackItem stackItem : stack) {
                if (stackItem.getUrl().equals(videoUrl)) stackItem.setTitle(videoTitle);
            }
        }
    }

    public boolean onActivityBackPressed() {
        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size() == 1) return false;
        // Remove top
        stack.pop();
        // Get url from the new top
        StackItem peek = stack.peek();
        selectAndLoadVideo(0, peek.getUrl(),
                peek.getTitle() != null && !peek.getTitle().isEmpty() ? peek.getTitle() : ""
        );
        return true;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/


    public void setAutoplay(boolean autoplay) {
        this.autoPlayEnabled = autoplay;
    }

    public void selectVideo(int serviceId, String videoUrl, String videoTitle) {
        this.videoUrl = videoUrl;
        this.videoTitle = videoTitle;
        this.serviceId = serviceId;
    }

    public void selectAndLoadVideo(int serviceId, String videoUrl, String videoTitle) {
        selectVideo(serviceId, videoUrl, videoTitle);
        loadSelectedVideo();
    }

    public void loadSelectedVideo() {
        pushToStack(videoUrl, videoTitle);

        if (curExtractorWorker != null && curExtractorWorker.isRunning()) curExtractorWorker.cancel();

        if (activity.getSupportActionBar() != null) {
            //noinspection deprecation
            activity.getSupportActionBar().setNavigationMode(0);
        }

        animateView(contentRootLayout, false, 50, null);

        videoTitleTextView.setMaxLines(1);
        int scrollY = parallaxScrollRootView.getScrollY();
        if (scrollY < 30) animateView(videoTitleTextView, false, 200, new Runnable() {
            @Override
            public void run() {
                videoTitleTextView.setText(videoTitle != null ? videoTitle : "");
                animateView(videoTitleTextView, true, 400, null);
            }
        });
        else videoTitleTextView.setText(videoTitle != null ? videoTitle : "");
        //videoTitleTextView.setText(videoTitle != null ? videoTitle : "");
        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoTitleToggleArrow.setVisibility(View.GONE);
        videoTitleRoot.setClickable(false);

        //thumbnailPlayButton.setVisibility(View.GONE);
        animateView(thumbnailPlayButton, false, 50, null);
        loadingProgressBar.setVisibility(View.VISIBLE);

        imageLoader.cancelDisplayTask(thumbnailImageView);
        imageLoader.cancelDisplayTask(uploaderThumb);
        thumbnailImageView.setImageBitmap(null);
        uploaderThumb.setImageBitmap(null);

        curExtractorWorker = new StreamExtractorWorker(activity, serviceId, videoUrl, this);
        curExtractorWorker.start();
        isLoading.set(true);
    }

    public void playVideo(StreamInfo info) {
        // ----------- THE MAGIC MOMENT ---------------
        VideoStream selectedVideoStream = info.video_streams.get(actionBarHandler.getSelectedVideoStream());

        if (PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(this.getString(R.string.use_external_video_player_key), false)) {

            // External Player
            Intent intent = new Intent();
            try {
                intent.setAction(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(selectedVideoStream.url), MediaFormat.getMimeById(selectedVideoStream.format))
                        .putExtra(Intent.EXTRA_TITLE, info.title)
                        .putExtra("title", info.title);
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
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                builder.create().show();
            }
        } else {
            Intent intent;
            boolean useOldPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.use_old_player_key), false)
                    || (Build.VERSION.SDK_INT < 16);
            if (!useOldPlayer) {
                // ExoPlayer
                if (streamThumbnail != null) ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = streamThumbnail;
                intent = new Intent(activity, ExoPlayerActivity.class)
                        .putExtra(AbstractPlayer.VIDEO_TITLE, info.title)
                        .putExtra(AbstractPlayer.VIDEO_URL, info.webpage_url)
                        .putExtra(AbstractPlayer.CHANNEL_NAME, info.uploader)
                        .putExtra(AbstractPlayer.INDEX_SEL_VIDEO_STREAM, actionBarHandler.getSelectedVideoStream())
                        .putExtra(AbstractPlayer.VIDEO_STREAMS_LIST, new ArrayList<>(info.video_streams));
                if (info.start_position > 0) intent.putExtra(AbstractPlayer.START_POSITION, info.start_position * 1000);
            } else {
                // Internal Player
                intent = new Intent(activity, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, info.title)
                        .putExtra(PlayVideoActivity.STREAM_URL, selectedVideoStream.url)
                        .putExtra(PlayVideoActivity.VIDEO_URL, info.webpage_url)
                        .putExtra(PlayVideoActivity.START_POSITION, info.start_position);
            }
            //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private View getSeparatorView() {
        View separator = new View(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        int m8 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        int m5 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        params.setMargins(m8, m5, m8, m5);
        separator.setLayoutParams(params);

        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(R.attr.separatorColor, typedValue, true);
        separator.setBackgroundColor(typedValue.data);
        return separator;
    }

    private void setHeightThumbnail() {
        boolean isPortrait = getResources().getDisplayMetrics().heightPixels > getResources().getDisplayMetrics().widthPixels;
        int height = isPortrait ? (int) (getResources().getDisplayMetrics().widthPixels / (16.0f / 9.0f))
                : (int) (getResources().getDisplayMetrics().heightPixels / 2f);
        thumbnailImageView.setScaleType(isPortrait ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        thumbnailImageView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailImageView.setMinimumHeight(height);
        thumbnailBackgroundButton.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailBackgroundButton.setMinimumHeight(height);
    }

    /**
     * Animate the view
     *
     * @param view        view that will be animated
     * @param enterOrExit true to enter, false to exit
     * @param duration    how long the animation will take, in milliseconds
     * @param execOnEnd   runnable that will be executed when the animation ends
     */
    public void animateView(final View view, final boolean enterOrExit, long duration, final Runnable execOnEnd) {
        if (view.getVisibility() == View.VISIBLE && enterOrExit) {
            view.animate().setListener(null).cancel();
            view.setVisibility(View.VISIBLE);
            view.setAlpha(1f);
            if (execOnEnd != null) execOnEnd.run();
            return;
        } else if ((view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE) && !enterOrExit) {
            view.animate().setListener(null).cancel();
            view.setVisibility(View.GONE);
            view.setAlpha(0f);
            if (execOnEnd != null) execOnEnd.run();
            return;
        }

        view.animate().setListener(null).cancel();
        view.setVisibility(View.VISIBLE);

        if (enterOrExit) {
            view.animate().alpha(1f).setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (execOnEnd != null) execOnEnd.run();
                        }
                    }).start();
        } else {
            view.animate().alpha(0f)
                    .setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setVisibility(View.GONE);
                            if (execOnEnd != null) execOnEnd.run();
                        }
                    })
                    .start();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnStreamInfoReceivedListener callbacks
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onReceive(StreamInfo info) {
        if (info == null || isRemoving() || !isVisible()) return;

        currentStreamInfo = info;
        loadingProgressBar.setVisibility(View.GONE);
        animateView(thumbnailPlayButton, true, 200, null);
        parallaxScrollRootView.scrollTo(0, 0);

        // Since newpipe is designed to work even if certain information is not available,
        // the UI has to react on missing information.
        videoTitleTextView.setText(info.title);
        if (!info.uploader.isEmpty()) uploaderTextView.setText(info.uploader);
        uploaderTextView.setVisibility(!info.uploader.isEmpty() ? View.VISIBLE : View.GONE);
        uploaderButton.setVisibility(!info.channel_url.isEmpty() ? View.VISIBLE : View.GONE);
        uploaderThumb.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));

        if (info.view_count >= 0) videoCountView.setText(Localization.localizeViewCount(info.view_count, activity));
        videoCountView.setVisibility(info.view_count >= 0 ? View.VISIBLE : View.GONE);

        if (info.dislike_count == -1 && info.like_count == -1) {
            thumbsDownImageView.setVisibility(View.VISIBLE);
            thumbsUpImageView.setVisibility(View.VISIBLE);
            thumbsUpTextView.setVisibility(View.GONE);
            thumbsDownTextView.setVisibility(View.GONE);

            thumbsDisabledTextView.setVisibility(View.VISIBLE);
        } else {
            thumbsDisabledTextView.setVisibility(View.GONE);

            if (info.dislike_count >= 0) thumbsDownTextView.setText(Localization.localizeNumber(info.dislike_count, activity));
            thumbsDownTextView.setVisibility(info.dislike_count >= 0 ? View.VISIBLE : View.GONE);
            thumbsDownImageView.setVisibility(info.dislike_count >= 0 ? View.VISIBLE : View.GONE);

            if (info.like_count >= 0) thumbsUpTextView.setText(Localization.localizeNumber(info.like_count, activity));
            thumbsUpTextView.setVisibility(info.like_count >= 0 ? View.VISIBLE : View.GONE);
            thumbsUpImageView.setVisibility(info.like_count >= 0 ? View.VISIBLE : View.GONE);
        }

        if (!info.upload_date.isEmpty()) videoUploadDateView.setText(Localization.localizeDate(info.upload_date, activity));
        videoUploadDateView.setVisibility(!info.upload_date.isEmpty() ? View.VISIBLE : View.GONE);

        if (!info.description.isEmpty()) { //noinspection deprecation
            videoDescriptionView.setText(Build.VERSION.SDK_INT >= 24 ? Html.fromHtml(info.description, 0) : Html.fromHtml(info.description));
        }
        videoDescriptionView.setVisibility(!info.description.isEmpty() ? View.VISIBLE : View.GONE);

        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoTitleToggleArrow.setVisibility(View.VISIBLE);
        videoTitleRoot.setClickable(true);

        setupActionBarHandler(info);
        initRelatedVideos(info);
        initThumbnailViews(info);

        setTitleToUrl(info.webpage_url, info.title);

        animateView(contentRootLayout, true, 200, null);

        if (autoPlayEnabled) {
            playVideo(info);
            // Only auto play in the first open
            autoPlayEnabled = false;
        }

        isLoading.set(false);
    }

    @Override
    public void onError(int messageId) {
        Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show();
        loadingProgressBar.setVisibility(View.GONE);
        videoTitleTextView.setText(getString(messageId));
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.not_available_monkey));
    }

    @Override
    public void onReCaptchaException() {
        Toast.makeText(activity, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
        // Starting ReCaptcha Challenge Activity
        startActivityForResult(new Intent(activity, ReCaptchaActivity.class), ReCaptchaActivity.RECAPTCHA_REQUEST);
    }

    @Override
    public void onBlockedByGemaError() {
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.gruese_die_gema));
        thumbnailBackgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.c3s_url)));
                startActivity(intent);
            }
        });

        Toast.makeText(activity, R.string.blocked_by_gema, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onContentErrorWithMessage(int messageId) {
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.not_available_monkey));
        Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onContentError() {
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.not_available_monkey));
        Toast.makeText(activity, R.string.content_not_available, Toast.LENGTH_LONG).show();
    }
}
