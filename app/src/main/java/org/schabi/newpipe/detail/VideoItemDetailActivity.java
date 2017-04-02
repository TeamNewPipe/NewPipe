package org.schabi.newpipe.detail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
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
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.player.AbstractPlayer;
import org.schabi.newpipe.player.BackgroundPlayer;
import org.schabi.newpipe.player.ExoPlayerActivity;
import org.schabi.newpipe.player.PlayVideoActivity;
import org.schabi.newpipe.player.PopupVideoPlayer;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.NavStack;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("FieldCanBeLocal")
public class VideoItemDetailActivity extends AppCompatActivity implements StreamExtractorWorker.OnStreamInfoReceivedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "VideoItemDetailActivity";
    private static final String KORE_PACKET = "org.xbmc.kore";

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String AUTO_PLAY = "auto_play";

    private ActionBarHandler actionBarHandler;

    private InfoItemBuilder infoItemBuilder = null;
    private StreamInfo currentStreamInfo = null;
    private StreamExtractorWorker curExtractorThread;
    private String videoUrl;
    private int serviceId = -1;

    private AtomicBoolean isLoading = new AtomicBoolean(false);
    private boolean needUpdate = false;

    private boolean autoPlayEnabled;
    private boolean showRelatedStreams;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions =
            new DisplayImageOptions.Builder().displayer(new FadeInBitmapDisplayer(400)).cacheInMemory(true).build();
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

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.show_next_video_key), true);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        ThemeHelper.setTheme(this, true);
        setContentView(R.layout.activity_videoitem_detail);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        else Log.e(TAG, "Could not get SupportActionBar");

        initViews();
        initListeners();
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Currently only used for enable/disable related videos
        // but can be extended for other live settings change
        if (needUpdate) {
            if (relatedStreamsView != null) initRelatedVideos(currentStreamInfo);
            needUpdate = false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        try {
            NavStack.getInstance().navBack(this);
        } catch (Exception e) {
            ErrorActivity.reportUiError(this, e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == RESULT_OK) {
                    String videoUrl = getIntent().getStringExtra(NavStack.URL);
                    NavStack.getInstance().openDetailActivity(this, videoUrl, serviceId);
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

    public void initViews() {
        loadingProgressBar = (ProgressBar) findViewById(R.id.detail_loading_progress_bar);

        parallaxScrollRootView = (ParallaxScrollView) findViewById(R.id.detail_main_content);

        //thumbnailRootLayout = (RelativeLayout) findViewById(R.id.detail_thumbnail_root_layout);
        thumbnailBackgroundButton = (Button) findViewById(R.id.detail_stream_thumbnail_background_button);
        thumbnailImageView = (ImageView) findViewById(R.id.detail_thumbnail_image_view);
        thumbnailPlayButton = (ImageView) findViewById(R.id.detail_thumbnail_play_button);

        contentRootLayout = (RelativeLayout) findViewById(R.id.detail_content_root_layout);

        videoTitleRoot = findViewById(R.id.detail_title_root_layout);
        videoTitleTextView = (TextView) findViewById(R.id.detail_video_title_view);
        videoTitleToggleArrow = (ImageView) findViewById(R.id.detail_toggle_description_view);
        videoCountView = (TextView) findViewById(R.id.detail_view_count_view);

        videoDescriptionRootLayout = (RelativeLayout) findViewById(R.id.detail_description_root_layout);
        videoUploadDateView = (TextView) findViewById(R.id.detail_upload_date_view);
        videoDescriptionView = (TextView) findViewById(R.id.detail_description_view);

        //thumbsRootLayout = (LinearLayout) findViewById(R.id.detail_thumbs_root_layout);
        thumbsUpTextView = (TextView) findViewById(R.id.detail_thumbs_up_count_view);
        thumbsUpImageView = (ImageView) findViewById(R.id.detail_thumbs_up_img_view);
        thumbsDownTextView = (TextView) findViewById(R.id.detail_thumbs_down_count_view);
        thumbsDownImageView = (ImageView) findViewById(R.id.detail_thumbs_down_img_view);
        thumbsDisabledTextView = (TextView) findViewById(R.id.detail_thumbs_disabled_view);

        //uploaderRootLayout = (FrameLayout) findViewById(R.id.detail_uploader_root_layout);
        uploaderButton = (Button) findViewById(R.id.detail_uploader_button);
        uploaderTextView = (TextView) findViewById(R.id.detail_uploader_text_view);
        uploaderThumb = (ImageView) findViewById(R.id.detail_uploader_thumbnail_view);

        relatedStreamRootLayout = (RelativeLayout) findViewById(R.id.detail_related_streams_root_layout);
        nextStreamTitle = (TextView) findViewById(R.id.detail_next_stream_title);
        relatedStreamsView = (LinearLayout) findViewById(R.id.detail_related_streams_view);

        actionBarHandler = new ActionBarHandler(this);
        actionBarHandler.setupNavMenu(this);
        videoDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());

        infoItemBuilder = new InfoItemBuilder(this, findViewById(android.R.id.content));

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
            public void selected(String url, int serviceId) {
                NavStack.getInstance().openDetailActivity(VideoItemDetailActivity.this, url, serviceId);
            }
        });

        uploaderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavStack.getInstance().openChannelActivity(VideoItemDetailActivity.this, currentStreamInfo.channel_url, currentStreamInfo.service_id);
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
                                sendBroadcast(intent);
                            }
                        }

                        @Override
                        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                            ErrorActivity.reportError(VideoItemDetailActivity.this,
                                    failReason.getCause(), null, findViewById(android.R.id.content),
                                    ErrorActivity.ErrorInfo.make(ErrorActivity.LOAD_IMAGE,
                                            NewPipe.getNameOfService(currentStreamInfo.service_id), imageUri,
                                            R.string.could_not_load_thumbnails));
                        }

                    });
        } else thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);

        if (info.uploader_thumbnail_url != null && !info.uploader_thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.uploader_thumbnail_url,
                    uploaderThumb, displayImageOptions,
                    new ImageErrorLoadingListener(this, findViewById(android.R.id.content), info.service_id));
        }
    }

    private void initRelatedVideos(StreamInfo info) {
        if (relatedStreamsView.getChildCount() > 0) relatedStreamsView.removeAllViews();

        if (info.next_video != null && showRelatedStreams) {
            nextStreamTitle.setVisibility(View.VISIBLE);
            relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, info.next_video));
            relatedStreamsView.addView(getSeparatorView());
            relatedStreamsView.setVisibility(View.VISIBLE);
        } else nextStreamTitle.setVisibility(View.GONE);

        if (info.related_streams != null && !info.related_streams.isEmpty() && showRelatedStreams) {
            for (InfoItem item : info.related_streams) {
                relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, item));
            }
            relatedStreamsView.setVisibility(View.VISIBLE);
        } else if (info.next_video == null) relatedStreamsView.setVisibility(View.GONE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        actionBarHandler.setupMenu(menu, getMenuInflater());
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavStack.getInstance().openMainActivity(this);
            return true;
        }
        return actionBarHandler.onItemSelected(item) || super.onOptionsItemSelected(item);
    }

    private void setupActionBarHandler(final StreamInfo info) {
        actionBarHandler.setupStreamList(info.video_streams);
        actionBarHandler.setOnShareListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, info.webpage_url);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, VideoItemDetailActivity.this.getString(R.string.share_dialog_title)));
            }
        });

        actionBarHandler.setOnOpenInBrowserListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(info.webpage_url));
                startActivity(Intent.createChooser(intent, VideoItemDetailActivity.this.getString(R.string.choose_browser)));
            }
        });

        actionBarHandler.setOnOpenInPopupListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                if (isLoading.get()) return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionHelper.checkSystemAlertWindowPermission(VideoItemDetailActivity.this)) {
                    Toast.makeText(VideoItemDetailActivity.this, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
                    return;
                }
                if (streamThumbnail != null) ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = streamThumbnail;

                Intent i = new Intent(VideoItemDetailActivity.this, PopupVideoPlayer.class);
                Toast.makeText(VideoItemDetailActivity.this, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show();
                i.putExtra(AbstractPlayer.VIDEO_TITLE, info.title)
                        .putExtra(AbstractPlayer.CHANNEL_NAME, info.uploader)
                        .putExtra(AbstractPlayer.VIDEO_URL, info.webpage_url)
                        .putExtra(AbstractPlayer.INDEX_SEL_VIDEO_STREAM, selectedStreamId)
                        .putExtra(AbstractPlayer.VIDEO_STREAMS_LIST, new ArrayList<>(info.video_streams));
                if (info.start_position > 0) i.putExtra(AbstractPlayer.START_POSITION, info.start_position * 1000);
                VideoItemDetailActivity.this.startService(i);
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
                    VideoItemDetailActivity.this.startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    AlertDialog.Builder builder = new AlertDialog.Builder(VideoItemDetailActivity.this);
                    builder.setMessage(R.string.kore_not_found)
                            .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse(VideoItemDetailActivity.this.getString(R.string.fdroid_kore_url)));
                                    VideoItemDetailActivity.this.startActivity(intent);
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

                if (isLoading.get() || !PermissionHelper.checkStoragePermissions(VideoItemDetailActivity.this)) {
                    return;
                }

                try {
                    Bundle args = new Bundle();

                    // Sometimes it may be that some information is not available due to changes fo the
                    // website which was crawled. Then the ui has to understand this and act right.

                    if (info.audio_streams != null) {
                        AudioStream audioStream =
                                info.audio_streams.get(getPreferredAudioStreamId(info));

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
                    downloadDialog.show(VideoItemDetailActivity.this.getSupportFragmentManager(), "downloadDialog");
                } catch (Exception e) {
                    Toast.makeText(VideoItemDetailActivity.this,
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

                    boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(VideoItemDetailActivity.this)
                            .getBoolean(VideoItemDetailActivity.this.getString(R.string.use_external_audio_player_key), false);
                    Intent intent;
                    AudioStream audioStream =
                            info.audio_streams.get(getPreferredAudioStreamId(info));
                    if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 18) {
                        //internal music player: explicit intent
                        if (!BackgroundPlayer.isRunning && streamThumbnail != null) {
                            ActivityCommunicator.getCommunicator()
                                    .backgroundPlayerThumbnail = streamThumbnail;
                            intent = new Intent(VideoItemDetailActivity.this, BackgroundPlayer.class);

                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(audioStream.url),
                                    MediaFormat.getMimeById(audioStream.format));
                            intent.putExtra(BackgroundPlayer.TITLE, info.title);
                            intent.putExtra(BackgroundPlayer.WEB_URL, info.webpage_url);
                            intent.putExtra(BackgroundPlayer.SERVICE_ID, serviceId);
                            intent.putExtra(BackgroundPlayer.CHANNEL_NAME, info.uploader);
                            VideoItemDetailActivity.this.startService(intent);
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
                            VideoItemDetailActivity.this.startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                            AlertDialog.Builder builder = new AlertDialog.Builder(VideoItemDetailActivity.this);
                            builder.setMessage(R.string.no_player_found)
                                    .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent();
                                            intent.setAction(Intent.ACTION_VIEW);
                                            intent.setData(Uri.parse(VideoItemDetailActivity.this.getString(R.string.fdroid_vlc_url)));
                                            VideoItemDetailActivity.this.startActivity(intent);
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
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        serviceId = intent.getIntExtra(NavStack.SERVICE_ID, 0);
        videoUrl = intent.getStringExtra(NavStack.URL);
        autoPlayEnabled = intent.getBooleanExtra(AUTO_PLAY, false);
        selectVideo(videoUrl, serviceId);
    }

    private void selectVideo(String url, int serviceId) {
        if (curExtractorThread != null && curExtractorThread.isRunning()) curExtractorThread.cancel();

        animateView(contentRootLayout, false, 200, null);

        thumbnailPlayButton.setVisibility(View.GONE);
        loadingProgressBar.setVisibility(View.VISIBLE);

        imageLoader.cancelDisplayTask(thumbnailImageView);
        imageLoader.cancelDisplayTask(uploaderThumb);
        thumbnailImageView.setImageDrawable(null);

        curExtractorThread = StreamExtractorWorker.startExtractorThread(serviceId, url, this, this);
        isLoading.set(true);
    }

    public void playVideo(StreamInfo info) {
        // ----------- THE MAGIC MOMENT ---------------
        VideoStream selectedVideoStream = info.video_streams.get(actionBarHandler.getSelectedVideoStream());

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(this.getString(R.string.use_external_video_player_key), false)) {

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
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
            boolean useOldPlayer = PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getBoolean(getString(R.string.use_old_player_key), false)
                    || (Build.VERSION.SDK_INT < 16);
            if (!useOldPlayer) {
                // ExoPlayer
                if (streamThumbnail != null) ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = streamThumbnail;
                intent = new Intent(this, ExoPlayerActivity.class)
                        .putExtra(AbstractPlayer.VIDEO_TITLE, info.title)
                        .putExtra(AbstractPlayer.VIDEO_URL, info.webpage_url)
                        .putExtra(AbstractPlayer.CHANNEL_NAME, info.uploader)
                        .putExtra(AbstractPlayer.INDEX_SEL_VIDEO_STREAM, actionBarHandler.getSelectedVideoStream())
                        .putExtra(AbstractPlayer.VIDEO_STREAMS_LIST, new ArrayList<>(info.video_streams));
                if (info.start_position > 0) intent.putExtra(AbstractPlayer.START_POSITION, info.start_position * 1000);
            } else {
                // Internal Player
                intent = new Intent(this, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, info.title)
                        .putExtra(PlayVideoActivity.STREAM_URL, selectedVideoStream.url)
                        .putExtra(PlayVideoActivity.VIDEO_URL, info.webpage_url)
                        .putExtra(PlayVideoActivity.START_POSITION, info.start_position);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private int getPreferredAudioStreamId(final StreamInfo info) {
        String preferredFormatString = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.default_audio_format_key), "webm");

        int preferredFormat = MediaFormat.WEBMA.id;
        switch (preferredFormatString) {
            case "webm":
                preferredFormat = MediaFormat.WEBMA.id;
                break;
            case "m4a":
                preferredFormat = MediaFormat.M4A.id;
                break;
            default:
                break;
        }

        for (int i = 0; i < info.audio_streams.size(); i++) {
            if (info.audio_streams.get(i).format == preferredFormat) {
                return i;
            }
        }

        //todo: make this a proper error
        Log.e(TAG, "FAILED to set audioStream value!");
        return 0;
    }

    private View getSeparatorView() {
        View separator = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        int m8 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        int m5 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        params.setMargins(m8, m5, m8, m5);
        separator.setLayoutParams(params);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.separatorColor, typedValue, true);
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
        currentStreamInfo = info;

        loadingProgressBar.setVisibility(View.GONE);
        thumbnailPlayButton.setVisibility(View.VISIBLE);
        relatedStreamRootLayout.setVisibility(showRelatedStreams ? View.VISIBLE : View.GONE);
        parallaxScrollRootView.scrollTo(0, 0);

        // Since newpipe is designed to work even if certain information is not available,
        // the UI has to react on missing information.
        videoTitleTextView.setText(info.title);
        if (!info.uploader.isEmpty()) uploaderTextView.setText(info.uploader);
        uploaderTextView.setVisibility(!info.uploader.isEmpty() ? View.VISIBLE : View.GONE);
        uploaderButton.setVisibility(!info.channel_url.isEmpty() ? View.VISIBLE : View.GONE);
        uploaderThumb.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.buddy));

        if (info.view_count >= 0) videoCountView.setText(Localization.localizeViewCount(info.view_count, this));
        videoCountView.setVisibility(info.view_count >= 0 ? View.VISIBLE : View.GONE);

        if (info.dislike_count == -1 && info.like_count == -1) {
            thumbsDownImageView.setVisibility(View.VISIBLE);
            thumbsUpImageView.setVisibility(View.VISIBLE);
            thumbsUpTextView.setVisibility(View.GONE);
            thumbsDownTextView.setVisibility(View.GONE);

            thumbsDisabledTextView.setVisibility(View.VISIBLE);
        } else {
            thumbsDisabledTextView.setVisibility(View.GONE);

            if (info.dislike_count >= 0) thumbsDownTextView.setText(Localization.localizeNumber(info.dislike_count, this));
            thumbsDownTextView.setVisibility(info.dislike_count >= 0 ? View.VISIBLE : View.GONE);
            thumbsDownImageView.setVisibility(info.dislike_count >= 0 ? View.VISIBLE : View.GONE);

            if (info.like_count >= 0) thumbsUpTextView.setText(Localization.localizeNumber(info.like_count, this));
            thumbsUpTextView.setVisibility(info.like_count >= 0 ? View.VISIBLE : View.GONE);
            thumbsUpImageView.setVisibility(info.like_count >= 0 ? View.VISIBLE : View.GONE);
        }

        if (!info.upload_date.isEmpty()) videoUploadDateView.setText(Localization.localizeDate(info.upload_date, this));
        videoUploadDateView.setVisibility(!info.upload_date.isEmpty() ? View.VISIBLE : View.GONE);

        if (!info.description.isEmpty()) videoDescriptionView.setText(
                Build.VERSION.SDK_INT >= 24 ? Html.fromHtml(info.description, 0) : Html.fromHtml(info.description)
        );
        videoDescriptionView.setVisibility(!info.description.isEmpty() ? View.VISIBLE : View.GONE);

        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);

        setupActionBarHandler(info);
        initRelatedVideos(info);
        initThumbnailViews(info);

        animateView(contentRootLayout, true, 200, null);

        isLoading.set(false);
        if (autoPlayEnabled) playVideo(info);
    }

    @Override
    public void onError(int messageId) {
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show();
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.not_available_monkey));
    }

    @Override
    public void onReCaptchaException() {
        Toast.makeText(this, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
        // Starting ReCaptcha Challenge Activity
        startActivityForResult(new Intent(this, ReCaptchaActivity.class), ReCaptchaActivity.RECAPTCHA_REQUEST);
    }

    @Override
    public void onBlockedByGemaError() {
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.gruese_die_gema));
        thumbnailBackgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.c3s_url)));
                startActivity(intent);
            }
        });

        Toast.makeText(this, R.string.blocked_by_gema, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onContentErrorWithMessage(int messageId) {
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.not_available_monkey));
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onContentError() {
        loadingProgressBar.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.not_available_monkey));
        Toast.makeText(this, R.string.content_not_available, Toast.LENGTH_LONG).show();
    }
}
