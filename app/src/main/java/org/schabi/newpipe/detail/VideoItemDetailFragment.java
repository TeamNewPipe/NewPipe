package org.schabi.newpipe.detail;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.exoplayer.util.Util;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.util.Vector;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.ChannelActivity;
import org.schabi.newpipe.extractor.StreamPreviewInfo;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.ImageErrorLoadingListener;
import org.schabi.newpipe.Localization;
import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.AudioStream;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamInfo;
import org.schabi.newpipe.extractor.VideoStream;
import org.schabi.newpipe.player.BackgroundPlayer;
import org.schabi.newpipe.player.PlayVideoActivity;
import org.schabi.newpipe.player.ExoPlayerActivity;


/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoItemDetailFragment.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class VideoItemDetailFragment extends Fragment {

    private static final String TAG = VideoItemDetailFragment.class.toString();
    private static final String KORE_PACKET = "org.xbmc.kore";

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String VIDEO_URL = "video_url";
    public static final String STREAMING_SERVICE = "streaming_service";
    public static final String AUTO_PLAY = "auto_play";

    private AppCompatActivity activity;
    private ActionBarHandler actionBarHandler;
    private ProgressBar progressBar;

    private int streamingServiceId = -1;

    private boolean autoPlayEnabled;
    private boolean showNextStreamItem;

    private View thumbnailWindowLayout;
    //this only remains due to downwards compatibility
    private FloatingActionButton playVideoButton;
    private final Point initialThumbnailPos = new Point(0, 0);
    private View rootView = null;
    private Bitmap streamThumbnail = null;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions =
            new DisplayImageOptions.Builder().cacheInMemory(true).build();

    private InfoItemBuilder infoItemBuilder = null;

    public interface OnInvokeCreateOptionsMenuListener {
        void createOptionsMenu();
    }

    private OnInvokeCreateOptionsMenuListener onInvokeCreateOptionsMenuListener;

    private void updateInfo(final StreamInfo info) {
        Activity a = getActivity();

        RelativeLayout textContentLayout =
                (RelativeLayout) activity.findViewById(R.id.detail_text_content_layout);
        final TextView videoTitleView =
                (TextView) activity.findViewById(R.id.detail_video_title_view);
        TextView uploaderView = (TextView) activity.findViewById(R.id.detail_uploader_view);
        TextView viewCountView = (TextView) activity.findViewById(R.id.detail_view_count_view);
        TextView thumbsUpView = (TextView) activity.findViewById(R.id.detail_thumbs_up_count_view);
        TextView thumbsDownView =
                (TextView) activity.findViewById(R.id.detail_thumbs_down_count_view);
        TextView uploadDateView = (TextView) activity.findViewById(R.id.detail_upload_date_view);
        TextView descriptionView = (TextView) activity.findViewById(R.id.detail_description_view);
        RecyclerView nextStreamView =
                (RecyclerView) activity.findViewById(R.id.detail_next_stream_content);
        RelativeLayout nextVideoRootFrame =
                (RelativeLayout) activity.findViewById(R.id.detail_next_stream_root_layout);
        TextView similarTitle = (TextView) activity.findViewById(R.id.detail_similar_title);
        Button backgroundButton = (Button)
                activity.findViewById(R.id.detail_stream_thumbnail_window_background_button);
        View topView = activity.findViewById(R.id.detailTopView);
        Button channelButton = (Button) activity.findViewById(R.id.channel_button);

        progressBar.setVisibility(View.GONE);
        if(info.next_video != null) {
            // todo: activate this function or remove it
            nextStreamView.setVisibility(View.GONE);
        } else {
            nextStreamView.setVisibility(View.GONE);
            activity.findViewById(R.id.detail_similar_title).setVisibility(View.GONE);
        }

        textContentLayout.setVisibility(View.VISIBLE);
        if (android.os.Build.VERSION.SDK_INT < 18) {
            playVideoButton.setVisibility(View.VISIBLE);
        } else {
            ImageView playArrowView = (ImageView) activity.findViewById(R.id.play_arrow_view);
            playArrowView.setVisibility(View.VISIBLE);
        }

        if (!showNextStreamItem) {
            nextVideoRootFrame.setVisibility(View.GONE);
            similarTitle.setVisibility(View.GONE);
        }

        videoTitleView.setText(info.title);

        topView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    ImageView arrow = (ImageView) activity.findViewById(R.id.toggle_description_view);
                    View extra = activity.findViewById(R.id.detailExtraView);
                    if (extra.getVisibility() == View.VISIBLE) {
                        extra.setVisibility(View.GONE);
                        arrow.setImageResource(R.drawable.arrow_down);
                    } else {
                        extra.setVisibility(View.VISIBLE);
                        arrow.setImageResource(R.drawable.arrow_up);
                    }
                }
                return true;
            }
        });

        // Since newpipe is designed to work even if certain information is not available,
        // the UI has to react on missing information.
        videoTitleView.setText(info.title);
        if(!info.uploader.isEmpty()) {
            uploaderView.setText(info.uploader);
        } else {
            activity.findViewById(R.id.detail_uploader_view).setVisibility(View.GONE);
        }
        if(info.view_count >= 0) {
            viewCountView.setText(Localization.localizeViewCount(info.view_count, a));
        } else {
            viewCountView.setVisibility(View.GONE);
        }
        if(info.dislike_count >= 0) {
            thumbsDownView.setText(Localization.localizeNumber(info.dislike_count, a));
        } else {
            thumbsDownView.setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.detail_thumbs_down_count_view).setVisibility(View.GONE);
        }
        if(info.like_count >= 0) {
            thumbsUpView.setText(Localization.localizeNumber(info.like_count, a));
        } else {
            thumbsUpView.setVisibility(View.GONE);
            activity.findViewById(R.id.detail_thumbs_up_img_view).setVisibility(View.GONE);
            thumbsDownView.setVisibility(View.GONE);
            activity.findViewById(R.id.detail_thumbs_down_img_view).setVisibility(View.GONE);
        }
        if(!info.upload_date.isEmpty()) {
            uploadDateView.setText(Localization.localizeDate(info.upload_date, a));
        } else {
            uploadDateView.setVisibility(View.GONE);
        }
        if(!info.description.isEmpty()) {
            descriptionView.setText(Html.fromHtml(info.description));
        } else {
            descriptionView.setVisibility(View.GONE);
        }

        descriptionView.setMovementMethod(LinkMovementMethod.getInstance());

        // parse streams
        Vector<VideoStream> streamsToUse = new Vector<>();
        for (VideoStream i : info.video_streams) {
            if (useStream(i, streamsToUse)) {
                streamsToUse.add(i);
            }
        }

        textContentLayout.setVisibility(View.VISIBLE);

        if(info.next_video == null) {
            activity.findViewById(R.id.detail_next_stream_title).setVisibility(View.GONE);
        }

        if(info.related_streams != null && !info.related_streams.isEmpty()) {
            initSimilarVideos(info);
        } else {
            activity.findViewById(R.id.detail_similar_title).setVisibility(View.GONE);
            activity.findViewById(R.id.similar_streams_view).setVisibility(View.GONE);
        }

        setupActionBarHandler(info);

        if(autoPlayEnabled) {
            playVideo(info);
        }

        if (android.os.Build.VERSION.SDK_INT < 18) {
            playVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playVideo(info);
                }
            });
        }

        backgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo(info);
            }
        });

        if(info.channel_url != null && info.channel_url != "") {
            channelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent i = new Intent(activity, ChannelActivity.class);
                    i.putExtra(ChannelActivity.CHANNEL_URL, info.channel_url);
                    i.putExtra(ChannelActivity.SERVICE_ID, info.service_id);
                    startActivity(i);
                }
            });
        } else {
            channelButton.setVisibility(Button.GONE);
        }

        initThumbnailViews(info);
    }

    private void initThumbnailViews(final StreamInfo info) {
        ImageView videoThumbnailView = (ImageView) activity.findViewById(R.id.detail_thumbnail_view);
        ImageView uploaderThumb
                = (ImageView) activity.findViewById(R.id.detail_uploader_thumbnail_view);

        if(info.thumbnail_url != null && !info.thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.thumbnail_url, videoThumbnailView,
                    displayImageOptions, new ImageLoadingListener() {
                        @Override
                        public void onLoadingStarted(String imageUri, View view) {
                        }

                        @Override
                        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                            ErrorActivity.reportError(getActivity(),
                                    failReason.getCause(), null, rootView,
                                    ErrorActivity.ErrorInfo.make(ErrorActivity.LOAD_IMAGE,
                                            ServiceList.getNameOfService(info.service_id), imageUri,
                                            R.string.could_not_load_thumbnails));
                        }

                        @Override
                        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                            streamThumbnail = loadedImage;
                        }

                        @Override
                        public void onLoadingCancelled(String imageUri, View view) {
                        }
                    });
        } else {
            videoThumbnailView.setImageResource(R.drawable.dummy_thumbnail_dark);
        }
        if(info.uploader_thumbnail_url != null && !info.uploader_thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.uploader_thumbnail_url,
                    uploaderThumb, displayImageOptions,
                    new ImageErrorLoadingListener(activity, rootView, info.service_id));
        }
    }

    private void setupActionBarHandler(final StreamInfo info) {
        actionBarHandler.setupStreamList(info.video_streams);

        actionBarHandler.setOnShareListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, info.webpage_url);
                intent.setType("text/plain");
                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_dialog_title)));
            }
        });

        actionBarHandler.setOnOpenInBrowserListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(info.webpage_url));

                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.choose_browser)));
            }
        });

        actionBarHandler.setOnPlayWithKodiListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
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
                    downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
                } catch (Exception e) {
                    Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                            R.string.could_not_setup_download_menu, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });

        if(info.audio_streams == null) {
            actionBarHandler.showAudioAction(false);
        } else {
            actionBarHandler.setOnPlayAudioListener(new ActionBarHandler.OnActionListener() {
                @Override
                public void onActionSelected(int selectedStreamId) {
                    boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                            .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);
                    Intent intent;
                    AudioStream audioStream =
                            info.audio_streams.get(getPreferredAudioStreamId(info));
                    if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 18) {
                        //internal music player: explicit intent
                        if (!BackgroundPlayer.isRunning && streamThumbnail != null) {
                            ActivityCommunicator.getCommunicator()
                                    .backgroundPlayerThumbnail = streamThumbnail;
                            intent = new Intent(activity, BackgroundPlayer.class);

                            intent.setAction(Intent.ACTION_VIEW);
                            Log.i(TAG, "audioStream is null:" + (audioStream == null));
                            Log.i(TAG, "audioStream.url is null:" + (audioStream.url == null));
                            intent.setDataAndType(Uri.parse(audioStream.url),
                                    MediaFormat.getMimeById(audioStream.format));
                            intent.putExtra(BackgroundPlayer.TITLE, info.title);
                            intent.putExtra(BackgroundPlayer.WEB_URL, info.webpage_url);
                            intent.putExtra(BackgroundPlayer.SERVICE_ID, streamingServiceId);
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

    private int getPreferredAudioStreamId(final StreamInfo info) {
        String preferredFormatString = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(activity.getString(R.string.default_audio_format_key), "webm");

        int preferredFormat = MediaFormat.WEBMA.id;
        switch(preferredFormatString) {
            case "webm":
                preferredFormat = MediaFormat.WEBMA.id;
                break;
            case "m4a":
                preferredFormat = MediaFormat.M4A.id;
                break;
            default:
                break;
        }

        for(int i = 0; i < info.audio_streams.size(); i++) {
            if(info.audio_streams.get(i).format == preferredFormat) {
                return i;
            }
        }

        //todo: make this a proper error
        Log.e(TAG, "FAILED to set audioStream value!");
        return 0;
    }

    private void initSimilarVideos(final StreamInfo info) {
        LinearLayout similarLayout = (LinearLayout) activity.findViewById(R.id.similar_streams_view);
        for (final StreamPreviewInfo item : info.related_streams) {
            similarLayout.addView(infoItemBuilder.buildView(similarLayout, item));
        }
        infoItemBuilder.setOnItemSelectedListener(new InfoItemBuilder.OnItemSelectedListener() {
            @Override
            public void selected(String url) {
                openStreamUrl(url);
            }
        });
    }

    private void onErrorBlockedByGema() {
        Button backgroundButton = (Button)
                activity.findViewById(R.id.detail_stream_thumbnail_window_background_button);
        ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detail_thumbnail_view);

        progressBar.setVisibility(View.GONE);
        thumbnailView.setImageBitmap(BitmapFactory.decodeResource(
                getResources(), R.drawable.gruese_die_gema));
        backgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(activity.getString(R.string.c3s_url)));
                activity.startActivity(intent);
            }
        });

        Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                R.string.blocked_by_gema, Toast.LENGTH_LONG).show();
    }

    private void onNotSpecifiedContentError() {
        ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detail_thumbnail_view);
        progressBar.setVisibility(View.GONE);
        thumbnailView.setImageBitmap(BitmapFactory.decodeResource(
                getResources(), R.drawable.not_available_monkey));
        Toast.makeText(activity, R.string.content_not_available, Toast.LENGTH_LONG)
                .show();
    }

    private void onNotSpecifiedContentErrorWithMessage(int resourceId) {
        ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detail_thumbnail_view);
        progressBar.setVisibility(View.GONE);
        thumbnailView.setImageBitmap(BitmapFactory.decodeResource(
                getResources(), R.drawable.not_available_monkey));
        Toast.makeText(activity, resourceId, Toast.LENGTH_LONG)
                .show();
    }

    private boolean useStream(VideoStream stream, Vector<VideoStream> streams) {
        for(VideoStream i : streams) {
            if(i.resolution.equals(stream.resolution)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) getActivity();
        showNextStreamItem = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(activity.getString(R.string.show_next_video_key), true);


        StreamInfoWorker siw = StreamInfoWorker.getInstance();
        siw.setOnStreamInfoReceivedListener(new StreamInfoWorker.OnStreamInfoReceivedListener() {
            @Override
            public void onReceive(StreamInfo info) {
                updateInfo(info);
            }

            @Override
            public void onError(int messageId) {
                postNewErrorToast(messageId);
            }

            @Override
            public void onBlockedByGemaError() {
                onErrorBlockedByGema();
            }

            @Override
            public void onContentErrorWithMessage(int messageId) {
                onNotSpecifiedContentErrorWithMessage(messageId);
            }

            @Override
            public void onContentError() {
                onNotSpecifiedContentError();
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_videoitem_detail, container, false);
        progressBar = (ProgressBar) rootView.findViewById(R.id.detail_progress_bar);

        actionBarHandler = new ActionBarHandler(activity);
        actionBarHandler.setupNavMenu(activity);
        if(onInvokeCreateOptionsMenuListener != null) {
            onInvokeCreateOptionsMenuListener.createOptionsMenu();
        }

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceBundle) {
        super.onActivityCreated(savedInstanceBundle);
        Activity a = getActivity();

        infoItemBuilder = new InfoItemBuilder(a, a.findViewById(android.R.id.content));

        if (android.os.Build.VERSION.SDK_INT < 18) {
            playVideoButton = (FloatingActionButton) a.findViewById(R.id.play_video_button);
        }
        thumbnailWindowLayout = a.findViewById(R.id.detail_stream_thumbnail_window_layout);
        Button backgroundButton = (Button)
                a.findViewById(R.id.detail_stream_thumbnail_window_background_button);

        // Sometimes when this fragment is not visible it still gets initiated
        // then we must not try to access objects of this fragment.
        // Otherwise the applications would crash.
        if(backgroundButton != null) {
            streamingServiceId = getArguments().getInt(STREAMING_SERVICE);
            String videoUrl = getArguments().getString(VIDEO_URL);
            StreamInfoWorker siw = StreamInfoWorker.getInstance();
            siw.search(streamingServiceId, videoUrl, getActivity());

            autoPlayEnabled = getArguments().getBoolean(AUTO_PLAY);

            if(Build.VERSION.SDK_INT >= 18) {
                ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detail_thumbnail_view);
                thumbnailView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    // This is used to synchronize the thumbnailWindowButton and the playVideoButton
                    // inside the ScrollView with the actual size of the thumbnail.
                    //todo: onLayoutChage sometimes not triggered
                    // background buttons area seem to overlap the thumbnail view
                    // So although you just clicked slightly beneath the thumbnail the action still
                    // gets triggered.
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        RelativeLayout.LayoutParams newWindowLayoutParams =
                                (RelativeLayout.LayoutParams) thumbnailWindowLayout.getLayoutParams();
                        newWindowLayoutParams.height = bottom - top;
                        thumbnailWindowLayout.setLayoutParams(newWindowLayoutParams);

                        //noinspection SuspiciousNameCombination
                        initialThumbnailPos.set(top, left);

                    }
                });
            }
        }
    }

    public void playVideo(final StreamInfo info) {
        // ----------- THE MAGIC MOMENT ---------------
        VideoStream selectedVideoStream =
                info.video_streams.get(actionBarHandler.getSelectedVideoStream());

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_video_player_key), false)) {

            // External Player
            Intent intent = new Intent();
            try {
                intent.setAction(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(selectedVideoStream.url),
                            MediaFormat.getMimeById(selectedVideoStream.format))
                        .putExtra(Intent.EXTRA_TITLE, info.title)
                        .putExtra("title", info.title);

                activity.startActivity(intent);      // HERE !!!
            } catch (Exception e) {
                e.printStackTrace();
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage(R.string.no_player_found)
                        .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent()
                                        .setAction(Intent.ACTION_VIEW)
                                        .setData(Uri.parse(activity.getString(R.string.fdroid_vlc_url)));
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
        } else {
            if (PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(activity.getString(R.string.use_exoplayer_key), false)) {

                // exo player

                if(info.dashMpdUrl != null && !info.dashMpdUrl.isEmpty()) {
                    // try dash
                    Intent intent = new Intent(activity, ExoPlayerActivity.class)
                            .setData(Uri.parse(info.dashMpdUrl))
                            .putExtra(ExoPlayerActivity.CONTENT_TYPE_EXTRA, Util.TYPE_DASH);
                    startActivity(intent);
                } else if((info.audio_streams != null  && !info.audio_streams.isEmpty()) &&
                        (info.video_only_streams != null && !info.video_only_streams.isEmpty())) {
                    // try smooth streaming

                } else {
                    //default streaming
                    Intent intent = new Intent(activity, ExoPlayerActivity.class)
                            .setDataAndType(Uri.parse(selectedVideoStream.url),
                                MediaFormat.getMimeById(selectedVideoStream.format))
                            .putExtra(ExoPlayerActivity.CONTENT_TYPE_EXTRA, Util.TYPE_OTHER);

                    activity.startActivity(intent);      // HERE !!!
                }
                //-------------

            } else {
                // Internal Player
                Intent intent = new Intent(activity, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, info.title)
                        .putExtra(PlayVideoActivity.STREAM_URL, selectedVideoStream.url)
                        .putExtra(PlayVideoActivity.VIDEO_URL, info.webpage_url)
                        .putExtra(PlayVideoActivity.START_POSITION, info.start_position);
                activity.startActivity(intent);     //also HERE !!!
            }
        }

        // --------------------------------------------
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        actionBarHandler.setupMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return actionBarHandler.onItemSelected(item);
    }

    public void setOnInvokeCreateOptionsMenuListener(OnInvokeCreateOptionsMenuListener listener) {
        this.onInvokeCreateOptionsMenuListener = listener;
    }

    private void postNewErrorToast(final int stringResource) {
        Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                stringResource, Toast.LENGTH_LONG).show();
    }

    private void openStreamUrl(String url) {
        Intent detailIntent = new Intent(activity, VideoItemDetailActivity.class);
        detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, url);
        detailIntent.putExtra(
                VideoItemDetailFragment.STREAMING_SERVICE, streamingServiceId);
        activity.startActivity(detailIntent);
    }
}