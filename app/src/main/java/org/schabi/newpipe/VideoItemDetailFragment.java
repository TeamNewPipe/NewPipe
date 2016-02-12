package org.schabi.newpipe;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.util.ArrayList;
import java.util.Vector;

import org.schabi.newpipe.crawler.MediaFormat;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.ServiceList;
import org.schabi.newpipe.crawler.StreamExtractor;
import org.schabi.newpipe.crawler.VideoPreviewInfo;
import org.schabi.newpipe.crawler.StreamingService;
import org.schabi.newpipe.crawler.VideoInfo;
import org.schabi.newpipe.crawler.services.youtube.YoutubeStreamExtractor;


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

    private boolean autoPlayEnabled = false;
    private boolean showNextVideoItem = false;
    private Bitmap videoThumbnail;

    private View thumbnailWindowLayout;
    private FloatingActionButton playVideoButton;
    private final Point initialThumbnailPos = new Point(0, 0);


    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions =
            new DisplayImageOptions.Builder().cacheInMemory(true).build();


    public interface OnInvokeCreateOptionsMenuListener {
        void createOptionsMenu();
    }

    private OnInvokeCreateOptionsMenuListener onInvokeCreateOptionsMenuListener = null;

    private class VideoExtractorRunnable implements Runnable {
        private final Handler h = new Handler();
        private StreamExtractor streamExtractor;
        private final StreamingService service;
        private final String videoUrl;

        public VideoExtractorRunnable(String videoUrl, StreamingService service) {
            this.service = service;
            this.videoUrl = videoUrl;
        }

        @Override
        public void run() {
            try {
                streamExtractor = service.getExtractorInstance(videoUrl, new Downloader());
                VideoInfo videoInfo = VideoInfo.getVideoInfo(streamExtractor, new Downloader());

                h.post(new VideoResultReturnedRunnable(videoInfo));
            } catch (IOException e) {
                postNewErrorToast(h, R.string.network_error);
                e.printStackTrace();
            }
            // custom service related exceptions
            catch (YoutubeStreamExtractor.DecryptException de) {
                postNewErrorToast(h, R.string.youtube_signature_decryption_error);
                de.printStackTrace();
            } catch (YoutubeStreamExtractor.GemaException ge) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onErrorBlockedByGema();
                    }
                });
            }
            // ----------------------------------------
            catch(StreamExtractor.ContentNotAvailableException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onNotSpecifiedContentError();
                    }
                });
                e.printStackTrace();
            } catch (ParsingException e) {
                postNewErrorToast(h, e.getMessage());
                e.printStackTrace();
            } catch(Exception e) {
                postNewErrorToast(h, R.string.general_error);
                e.printStackTrace();
            }
        }
    }

    private class VideoResultReturnedRunnable implements Runnable {
        private final VideoInfo videoInfo;
        public VideoResultReturnedRunnable(VideoInfo videoInfo) {
            this.videoInfo = videoInfo;
        }
        @Override
        public void run() {
            //todo: fix expired thread error:
            // If the thread calling this runnable is expired, the following function will crash.
            updateInfo(videoInfo);
        }
    }

    private class ThumbnailLoadingListener implements ImageLoadingListener {
        @Override
        public void onLoadingStarted(String imageUri, View view) {}

        @Override
        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
            Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                    R.string.could_not_load_thumbnails, Toast.LENGTH_LONG).show();
            failReason.getCause().printStackTrace();
        }

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {}

        @Override
        public void onLoadingCancelled(String imageUri, View view) {}
    }

    private void updateInfo(final VideoInfo info) {
        try {
            Context c = getContext();
            VideoInfoItemViewCreator videoItemViewCreator =
                    new VideoInfoItemViewCreator(LayoutInflater.from(getActivity()));

            RelativeLayout textContentLayout =
                    (RelativeLayout) activity.findViewById(R.id.detailTextContentLayout);
            final TextView videoTitleView =
                    (TextView) activity.findViewById(R.id.detailVideoTitleView);
            TextView uploaderView = (TextView) activity.findViewById(R.id.detailUploaderView);
            TextView viewCountView = (TextView) activity.findViewById(R.id.detailViewCountView);
            TextView thumbsUpView = (TextView) activity.findViewById(R.id.detailThumbsUpCountView);
            TextView thumbsDownView =
                    (TextView) activity.findViewById(R.id.detailThumbsDownCountView);
            TextView uploadDateView = (TextView) activity.findViewById(R.id.detailUploadDateView);
            TextView descriptionView = (TextView) activity.findViewById(R.id.detailDescriptionView);
            FrameLayout nextVideoFrame =
                    (FrameLayout) activity.findViewById(R.id.detailNextVideoFrame);
            RelativeLayout nextVideoRootFrame =
                    (RelativeLayout) activity.findViewById(R.id.detailNextVideoRootLayout);
            Button nextVideoButton = (Button) activity.findViewById(R.id.detailNextVideoButton);
            TextView similarTitle = (TextView) activity.findViewById(R.id.detailSimilarTitle);
            Button backgroundButton = (Button)
                    activity.findViewById(R.id.detailVideoThumbnailWindowBackgroundButton);
            View topView = activity.findViewById(R.id.detailTopView);
            View nextVideoView = videoItemViewCreator
                    .getViewFromVideoInfoItem(null, nextVideoFrame, info.next_video, getContext());

            progressBar.setVisibility(View.GONE);
            nextVideoFrame.addView(nextVideoView);

            initThumbnailViews(info, nextVideoFrame);

            textContentLayout.setVisibility(View.VISIBLE);
            playVideoButton.setVisibility(View.VISIBLE);
            if (!showNextVideoItem) {
                nextVideoRootFrame.setVisibility(View.GONE);
                similarTitle.setVisibility(View.GONE);
            }

            videoTitleView.setText(info.title);

            topView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        ImageView arrow = (ImageView) activity.findViewById(R.id.toggleDescriptionView);
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

            uploaderView.setText(info.uploader);
            videoTitleView.setText(info.title);
            uploaderView.setText(info.uploader);
            viewCountView.setText(Localization.localizeViewCount(info.view_count, c));
            thumbsUpView.setText(Localization.localizeNumber(info.like_count, c));
            thumbsDownView.setText(Localization.localizeNumber(info.dislike_count, c));
            uploadDateView.setText(Localization.localizeDate(info.upload_date, c));
            descriptionView.setText(Html.fromHtml(info.description));

            descriptionView.setMovementMethod(LinkMovementMethod.getInstance());

            // parse streams
            Vector<VideoInfo.VideoStream> streamsToUse = new Vector<>();
            for (VideoInfo.VideoStream i : info.video_streams) {
                if (useStream(i, streamsToUse)) {
                    streamsToUse.add(i);
                }
            }

            nextVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent detailIntent =
                            new Intent(getActivity(), VideoItemDetailActivity.class);
                        /*detailIntent.putExtra(
                                VideoItemDetailFragment.ARG_ITEM_ID, currentVideoInfo.nextVideo.id); */
                    detailIntent.putExtra(
                            VideoItemDetailFragment.VIDEO_URL, info.next_video.webpage_url);
                    detailIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, streamingServiceId);
                    startActivity(detailIntent);
                }
            });
            textContentLayout.setVisibility(View.VISIBLE);

            initSimilarVideos(info, videoItemViewCreator);

            if(autoPlayEnabled) {
                playVideo(info);
            }

            playVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playVideo(info);
                }
            });

            backgroundButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playVideo(info);
                }
            });

            setupActionBarHandler(info);
        } catch (java.lang.NullPointerException e) {
            Log.w(TAG, "updateInfo(): Fragment closed before thread ended work... or else");
            e.printStackTrace();
        }
    }

    private void initThumbnailViews(VideoInfo info, View nextVideoFrame) {
        ImageView videoThumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
        ImageView uploaderThumb
                = (ImageView) activity.findViewById(R.id.detailUploaderThumbnailView);
        ImageView nextVideoThumb =
                (ImageView) nextVideoFrame.findViewById(R.id.itemThumbnailView);

        imageLoader.displayImage(info.thumbnail_url, videoThumbnailView,
                displayImageOptions, new ImageLoadingListener() {
                    @Override
                    public void onLoadingStarted(String imageUri, View view) {
                    }

                    @Override
                    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                        Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                                R.string.could_not_load_thumbnails, Toast.LENGTH_LONG).show();
                        failReason.getCause().printStackTrace();
                    }

                    @Override
                    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                        videoThumbnail = loadedImage;
                    }

                    @Override
                    public void onLoadingCancelled(String imageUri, View view) {
                    }
                });
        imageLoader.displayImage(info.uploader_thumbnail_url,
                uploaderThumb, displayImageOptions, new ThumbnailLoadingListener());
        imageLoader.displayImage(info.next_video.thumbnail_url,
                nextVideoThumb, displayImageOptions, new ThumbnailLoadingListener());
    }

    private void setupActionBarHandler(final VideoInfo info) {
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
                //VideoInfo.VideoStream selectedStreamItem = videoStreams.get(selectedStream);
                VideoInfo.AudioStream audioStream =
                        info.audio_streams.get(getPreferredAudioStreamId(info));
                VideoInfo.VideoStream selectedStreamItem = info.video_streams.get(selectedStreamId);
                String videoSuffix = "." + MediaFormat.getSuffixById(selectedStreamItem.format);
                String audioSuffix = "." + MediaFormat.getSuffixById(audioStream.format);
                Bundle args = new Bundle();
                args.putString(DownloadDialog.FILE_SUFFIX_VIDEO, videoSuffix);
                args.putString(DownloadDialog.FILE_SUFFIX_AUDIO, audioSuffix);
                args.putString(DownloadDialog.TITLE, info.title);
                args.putString(DownloadDialog.VIDEO_URL, selectedStreamItem.url);
                args.putString(DownloadDialog.AUDIO_URL, audioStream.url);
                DownloadDialog downloadDialog = new DownloadDialog();
                downloadDialog.setArguments(args);
                downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
            }
        });

        actionBarHandler.setOnPlayAudioListener(new ActionBarHandler.OnActionListener() {
            @Override
            public void onActionSelected(int selectedStreamId) {
                boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                        .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);
                Intent intent;
                VideoInfo.AudioStream audioStream =
                        info.audio_streams.get(getPreferredAudioStreamId(info));
                if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 18) {
                    //internal music player: explicit intent
                    if (!BackgroundPlayer.isRunning  && videoThumbnail != null) {
                        ActivityCommunicator.getCommunicator()
                                .backgroundPlayerThumbnail = videoThumbnail;
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

    private int getPreferredAudioStreamId(final VideoInfo info) {
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

    private void initSimilarVideos(final VideoInfo info, VideoInfoItemViewCreator videoItemViewCreator) {
        LinearLayout similarLayout = (LinearLayout) activity.findViewById(R.id.similarVideosView);
        ArrayList<VideoPreviewInfo> similar = new ArrayList<>(info.related_videos);
        for (final VideoPreviewInfo item : similar) {
            View similarView = videoItemViewCreator
                    .getViewFromVideoInfoItem(null, similarLayout, item, getContext());

            similarView.setClickable(true);
            similarView.setFocusable(true);
            int[] attrs = new int[]{R.attr.selectableItemBackground};
            TypedArray typedArray = activity.obtainStyledAttributes(attrs);
            int backgroundResource = typedArray.getResourceId(0, 0);
            similarView.setBackgroundResource(backgroundResource);
            typedArray.recycle();

            similarView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        Intent detailIntent = new Intent(activity, VideoItemDetailActivity.class);
                        detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, item.webpage_url);
                        detailIntent.putExtra(
                                VideoItemDetailFragment.STREAMING_SERVICE, streamingServiceId);
                        startActivity(detailIntent);
                        return true;
                    }
                    return false;
                }
            });

            similarLayout.addView(similarView);
            ImageView rthumb = (ImageView)similarView.findViewById(R.id.itemThumbnailView);
            imageLoader.displayImage(item.thumbnail_url, rthumb,
                    displayImageOptions, new ThumbnailLoadingListener());
        }
    }

    private void onErrorBlockedByGema() {
        Button backgroundButton = (Button)
                activity.findViewById(R.id.detailVideoThumbnailWindowBackgroundButton);
        ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);

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
        ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
        progressBar.setVisibility(View.GONE);
        thumbnailView.setImageBitmap(BitmapFactory.decodeResource(
                getResources(), R.drawable.not_available_monkey));
        Toast.makeText(activity, R.string.content_not_available, Toast.LENGTH_LONG)
                .show();
    }

    private boolean useStream(VideoInfo.VideoStream stream, Vector<VideoInfo.VideoStream> streams) {
        for(VideoInfo.VideoStream i : streams) {
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
        showNextVideoItem = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(activity.getString(R.string.show_next_video_key), true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_videoitem_detail, container, false);
        progressBar = (ProgressBar) rootView.findViewById(R.id.detailProgressBar);

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
        playVideoButton = (FloatingActionButton) a.findViewById(R.id.playVideoButton);
        thumbnailWindowLayout = a.findViewById(R.id.detailVideoThumbnailWindowLayout);
        Button backgroundButton = (Button)
                a.findViewById(R.id.detailVideoThumbnailWindowBackgroundButton);

        // Sometimes when this fragment is not visible it still gets initiated
        // then we must not try to access objects of this fragment.
        // Otherwise the applications would crash.
        if(playVideoButton != null) {
            try {
                streamingServiceId = getArguments().getInt(STREAMING_SERVICE);
                StreamingService streamingService = ServiceList.getService(streamingServiceId);
                Thread videoExtractorThread = new Thread(new VideoExtractorRunnable(
                        getArguments().getString(VIDEO_URL), streamingService));

                autoPlayEnabled = getArguments().getBoolean(AUTO_PLAY);
                videoExtractorThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // todo: Fix this workaround (probably with a better design), so that older android
            // versions don't have problems rendering the thumbnail right.
            if(Build.VERSION.SDK_INT >= 18) {
                ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
                thumbnailView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    // This is used to synchronize the thumbnailWindowButton and the playVideoButton
                    // inside the ScrollView with the actual size of the thumbnail.
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

    public void playVideo(final VideoInfo info) {
        // ----------- THE MAGIC MOMENT ---------------
        VideoInfo.VideoStream selectedVideoStream =
                info.video_streams.get(actionBarHandler.getSelectedVideoStream());

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_video_player_key), false)) {

            // External Player
            Intent intent = new Intent();
            try {
                intent.setAction(Intent.ACTION_VIEW);

                intent.setDataAndType(Uri.parse(selectedVideoStream.url),
                        MediaFormat.getMimeById(selectedVideoStream.format));
                intent.putExtra(Intent.EXTRA_TITLE, info.title);
                intent.putExtra("title", info.title);

                activity.startActivity(intent);      // HERE !!!
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

                            }
                        });
                builder.create().show();
            }
        } else {
            // Internal Player
            Intent intent = new Intent(activity, PlayVideoActivity.class);
            intent.putExtra(PlayVideoActivity.VIDEO_TITLE, info.title);
            intent.putExtra(PlayVideoActivity.STREAM_URL, selectedVideoStream.url);
            intent.putExtra(PlayVideoActivity.VIDEO_URL, info.webpage_url);
            intent.putExtra(PlayVideoActivity.START_POSITION, info.start_position);
            activity.startActivity(intent);     //also HERE !!!
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

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                        stringResource, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void postNewErrorToast(Handler h, final String message) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                        message, Toast.LENGTH_LONG).show();
            }
        });
    }
}