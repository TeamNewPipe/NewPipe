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

import com.google.android.exoplayer.util.Util;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.util.ArrayList;
import java.util.Vector;


import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.AudioStream;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamExtractor;
import org.schabi.newpipe.extractor.StreamInfo;
import org.schabi.newpipe.extractor.StreamPreviewInfo;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.VideoStream;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
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

    private boolean autoPlayEnabled = false;
    private boolean showNextVideoItem = false;
    private Bitmap videoThumbnail;

    private View thumbnailWindowLayout;
    //this only remains due to downwards compatibility
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
            StreamInfo streamInfo = null;
            try {
                streamExtractor = service.getExtractorInstance(videoUrl, new Downloader());
                streamInfo = StreamInfo.getVideoInfo(streamExtractor, new Downloader());

                h.post(new VideoResultReturnedRunnable(streamInfo));

                // look for errors during extraction
                // this if statement only covers extra information.
                // if these are not available or caused an error, they are just not available
                // but don't render the stream information unusalbe.
                if(streamInfo != null &&
                        !streamInfo.errors.isEmpty()) {
                    Log.e(TAG, "OCCURRED ERRORS DURING EXTRACTION:");
                    for (Exception e : streamInfo.errors) {
                        e.printStackTrace();
                        Log.e(TAG, "------");
                    }

                    Activity a = getActivity();
                    View rootView = a != null ? a.findViewById(R.id.videoitem_detail) : null;
                    ErrorActivity.reportError(h, getActivity(),
                            streamInfo.errors, null, rootView,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                    service.getServiceInfo().name, videoUrl, 0 /* no message for the user */));
                }

                // These errors render the stream information unusable.
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
            } catch(YoutubeStreamExtractor.LiveStreamException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onNotSpecifiedContentErrorWithMessage(R.string.live_streams_not_supported);
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
            } catch(StreamInfo.StreamExctractException e) {
                if(!streamInfo.errors.isEmpty()) {
                    // !!! if this case ever kicks in someone gets kicked out !!!
                    ErrorActivity.reportError(h, getActivity(), e, VideoItemListActivity.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                    service.getServiceInfo().name, videoUrl, R.string.could_not_get_stream));
                } else {
                    ErrorActivity.reportError(h, getActivity(), streamInfo.errors, VideoItemListActivity.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                    service.getServiceInfo().name, videoUrl, R.string.could_not_get_stream));
                }
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().finish();
                    }
                });
                e.printStackTrace();
            } catch (ParsingException e) {
                ErrorActivity.reportError(h, getActivity(), e, VideoItemListActivity.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, R.string.parsing_error));
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                getActivity().finish();
                            }
                        });
                e.printStackTrace();
            } catch(Exception e) {
                ErrorActivity.reportError(h, getActivity(), e, VideoItemListActivity.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, R.string.general_error));
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                getActivity().finish();
                            }
                        });
                e.printStackTrace();
            }
        }
    }

    private class VideoResultReturnedRunnable implements Runnable {
        private final StreamInfo streamInfo;
        public VideoResultReturnedRunnable(StreamInfo streamInfo) {
            this.streamInfo = streamInfo;
        }
        @Override
        public void run() {
            Activity a = getActivity();
            if(a != null) {
                boolean showAgeRestrictedContent = PreferenceManager.getDefaultSharedPreferences(a)
                        .getBoolean(activity.getString(R.string.show_age_restricted_content), false);
                if (streamInfo.age_limit == 0 || showAgeRestrictedContent) {
                    updateInfo(streamInfo);
                } else {
                    onNotSpecifiedContentErrorWithMessage(R.string.video_is_age_restricted);
                }
            }
        }
    }

    private class ThumbnailLoadingListener implements ImageLoadingListener {
        @Override
        public void onLoadingStarted(String imageUri, View view) {}

        @Override
        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
            if(getContext() != null) {
                Toast.makeText(VideoItemDetailFragment.this.getActivity(),
                        R.string.could_not_load_thumbnails, Toast.LENGTH_LONG).show();
            }
            failReason.getCause().printStackTrace();
        }

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {}

        @Override
        public void onLoadingCancelled(String imageUri, View view) {}
    }

    private void updateInfo(final StreamInfo info) {
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
            View nextVideoView = null;
            if(info.next_video != null) {
                nextVideoView = videoItemViewCreator
                        .getViewFromVideoInfoItem(null, nextVideoFrame, info.next_video);
            } else {
                activity.findViewById(R.id.detailNextVidButtonAndContentLayout).setVisibility(View.GONE);
                activity.findViewById(R.id.detailNextVideoTitle).setVisibility(View.GONE);
                activity.findViewById(R.id.detailNextVideoButton).setVisibility(View.GONE);
            }

            progressBar.setVisibility(View.GONE);
            if(nextVideoView != null) {
                nextVideoFrame.addView(nextVideoView);
            }

            initThumbnailViews(info, nextVideoFrame);

            textContentLayout.setVisibility(View.VISIBLE);
            if (android.os.Build.VERSION.SDK_INT < 18) {
                playVideoButton.setVisibility(View.VISIBLE);
            } else {
                ImageView playArrowView = (ImageView) activity.findViewById(R.id.playArrowView);
                playArrowView.setVisibility(View.VISIBLE);
            }

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

            // Since newpipe is designed to work even if certain information is not available,
            // the UI has to react on missing information.
            videoTitleView.setText(info.title);
            if(!info.uploader.isEmpty()) {
                uploaderView.setText(info.uploader);
            } else {
                activity.findViewById(R.id.detailUploaderWrapView).setVisibility(View.GONE);
            }
            if(info.view_count >= 0) {
                viewCountView.setText(Localization.localizeViewCount(info.view_count, c));
            } else {
                viewCountView.setVisibility(View.GONE);
            }
            if(info.dislike_count >= 0) {
                thumbsDownView.setText(Localization.localizeNumber(info.dislike_count, c));
            } else {
                thumbsDownView.setVisibility(View.INVISIBLE);
                activity.findViewById(R.id.detailThumbsDownImgView).setVisibility(View.GONE);
            }
            if(info.like_count >= 0) {
                thumbsUpView.setText(Localization.localizeNumber(info.like_count, c));
            } else {
                thumbsUpView.setVisibility(View.GONE);
                activity.findViewById(R.id.detailThumbsUpImgView).setVisibility(View.GONE);
                thumbsDownView.setVisibility(View.GONE);
                activity.findViewById(R.id.detailThumbsDownImgView).setVisibility(View.GONE);
            }
            if(!info.upload_date.isEmpty()) {
                uploadDateView.setText(Localization.localizeDate(info.upload_date, c));
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

            if(info.related_videos != null && !info.related_videos.isEmpty()) {
                initSimilarVideos(info, videoItemViewCreator);
            } else {
                activity.findViewById(R.id.detailSimilarTitle).setVisibility(View.GONE);
                activity.findViewById(R.id.similarVideosView).setVisibility(View.GONE);
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

        } catch (java.lang.NullPointerException e) {
            Log.w(TAG, "updateInfo(): Fragment closed before thread ended work... or else");
            e.printStackTrace();
        }
    }

    private void initThumbnailViews(StreamInfo info, View nextVideoFrame) {
        ImageView videoThumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
        ImageView uploaderThumb
                = (ImageView) activity.findViewById(R.id.detailUploaderThumbnailView);
        ImageView nextVideoThumb =
                (ImageView) nextVideoFrame.findViewById(R.id.itemThumbnailView);

        if(info.thumbnail_url != null && !info.thumbnail_url.isEmpty()) {
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
        } else {
            videoThumbnailView.setImageResource(R.drawable.dummy_thumbnail_dark);
        }
        if(info.uploader_thumbnail_url != null && !info.uploader_thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.uploader_thumbnail_url,
                    uploaderThumb, displayImageOptions, new ThumbnailLoadingListener());
        }
        if(info.thumbnail_url != null && !info.thumbnail_url.isEmpty() && info.next_video != null) {
            imageLoader.displayImage(info.next_video.thumbnail_url,
                    nextVideoThumb, displayImageOptions, new ThumbnailLoadingListener());
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
                    DownloadDialog downloadDialog = new DownloadDialog();
                    downloadDialog.setArguments(args);
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
                        if (!BackgroundPlayer.isRunning && videoThumbnail != null) {
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

    private void initSimilarVideos(final StreamInfo info, VideoInfoItemViewCreator videoItemViewCreator) {
        LinearLayout similarLayout = (LinearLayout) activity.findViewById(R.id.similarVideosView);
        ArrayList<StreamPreviewInfo> similar = new ArrayList<>(info.related_videos);
        for (final StreamPreviewInfo item : similar) {
            View similarView = videoItemViewCreator
                    .getViewFromVideoInfoItem(null, similarLayout, item);

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

    private void onNotSpecifiedContentErrorWithMessage(int resourceId) {
        ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
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
        if (android.os.Build.VERSION.SDK_INT < 18) {
            playVideoButton = (FloatingActionButton) a.findViewById(R.id.playVideoButton);
        }
        thumbnailWindowLayout = a.findViewById(R.id.detailVideoThumbnailWindowLayout);
        Button backgroundButton = (Button)
                a.findViewById(R.id.detailVideoThumbnailWindowBackgroundButton);

        // Sometimes when this fragment is not visible it still gets initiated
        // then we must not try to access objects of this fragment.
        // Otherwise the applications would crash.
        if(backgroundButton != null) {
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

            if(Build.VERSION.SDK_INT >= 18) {
                ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
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