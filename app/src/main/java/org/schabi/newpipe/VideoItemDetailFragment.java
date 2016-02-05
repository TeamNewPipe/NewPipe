package org.schabi.newpipe;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
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
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.util.ArrayList;
import java.util.Vector;

import org.schabi.newpipe.crawler.CrawlingException;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.VideoPreviewInfo;
import org.schabi.newpipe.crawler.VideoExtractor;
import org.schabi.newpipe.crawler.ServiceList;
import org.schabi.newpipe.crawler.StreamingService;
import org.schabi.newpipe.crawler.VideoInfo;
import org.schabi.newpipe.crawler.services.youtube.YoutubeVideoExtractor;


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
    private VideoInfo currentVideoInfo = null;
    private boolean showNextVideoItem = false;

    private View thumbnailWindowLayout;
    private FloatingActionButton playVideoButton;
    private final Point initialThumbnailPos = new Point(0, 0);

    public interface OnInvokeCreateOptionsMenuListener {
        void createOptionsMenu();
    }

    private OnInvokeCreateOptionsMenuListener onInvokeCreateOptionsMenuListener = null;

    private class VideoExtractorRunnable implements Runnable {
        private final Handler h = new Handler();
        private VideoExtractor videoExtractor;
        private final StreamingService service;
        private final String videoUrl;

        public VideoExtractorRunnable(String videoUrl, StreamingService service) {
            this.service = service;
            this.videoUrl = videoUrl;
        }

        @Override
        public void run() {
            try {
                videoExtractor = service.getExtractorInstance(videoUrl, new Downloader());
                VideoInfo videoInfo = VideoInfo.getVideoInfo(videoExtractor, new Downloader());
                h.post(new VideoResultReturnedRunnable(videoInfo));
                h.post(new SetThumbnailRunnable(
                        //todo: make bitmaps not bypass tor
                        BitmapFactory.decodeStream(
                                new URL(videoInfo.thumbnail_url)
                                        .openConnection()
                                        .getInputStream()),
                        SetThumbnailRunnable.VIDEO_THUMBNAIL));
                h.post(new SetThumbnailRunnable(
                        BitmapFactory.decodeStream(
                                new URL(videoInfo.uploader_thumbnail_url)
                                        .openConnection()
                                        .getInputStream()),
                        SetThumbnailRunnable.CHANNEL_THUMBNAIL));
                if (showNextVideoItem) {
                    h.post(new SetThumbnailRunnable(
                            BitmapFactory.decodeStream(
                                    new URL(videoInfo.nextVideo.thumbnail_url)
                                            .openConnection()
                                            .getInputStream()),
                            SetThumbnailRunnable.NEXT_VIDEO_THUMBNAIL));
                }
            } catch (MalformedInputException e) {
                postNewErrorToast(h, R.string.could_not_load_thumbnails);
                e.printStackTrace();
            } catch (IOException e) {
                postNewErrorToast(h, R.string.network_error);
                e.printStackTrace();
            }
            // custom service related exceptions
            catch (YoutubeVideoExtractor.DecryptException de) {
                postNewErrorToast(h, R.string.youtube_signature_decryption_error);
                de.printStackTrace();
            } catch (YoutubeVideoExtractor.GemaException ge) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onErrorBlockedByGema();
                    }
                });
            }
            // ----------------------------------------
            catch(VideoExtractor.ContentNotAvailableException e) {
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

    private class SetThumbnailRunnable implements Runnable {
        public static final int VIDEO_THUMBNAIL = 1;
        public static final int CHANNEL_THUMBNAIL = 2;
        public static final int NEXT_VIDEO_THUMBNAIL = 3;
        private final Bitmap thumbnail;
        private final int thumbnailId;
        public SetThumbnailRunnable(Bitmap thumbnail, int id) {
            this.thumbnail = thumbnail;
            this.thumbnailId = id;
        }
        @Override
        public void run() {
            updateThumbnail(thumbnail, thumbnailId);
        }
    }

    private void updateThumbnail(Bitmap thumbnail, int id) {
        Activity a = getActivity();
        ImageView thumbnailView;
        try {
            switch (id) {
                case SetThumbnailRunnable.VIDEO_THUMBNAIL:
                    thumbnailView = (ImageView) a.findViewById(R.id.detailThumbnailView);
                    actionBarHandler.setSetVideoThumbnail(thumbnail);
                    break;
                case SetThumbnailRunnable.CHANNEL_THUMBNAIL:
                    thumbnailView = (ImageView) a.findViewById(R.id.detailUploaderThumbnailView);
                    break;
                case SetThumbnailRunnable.NEXT_VIDEO_THUMBNAIL:
                    FrameLayout nextVideoFrame = (FrameLayout) a.findViewById(R.id.detailNextVideoFrame);
                    thumbnailView = (ImageView) nextVideoFrame.findViewById(R.id.itemThumbnailView);
                    currentVideoInfo.nextVideo.thumbnail = thumbnail;
                    break;
                default:
                    Log.d(TAG, "Error: Thumbnail id not known");
                    return;
            }

            if (thumbnailView != null) {
                thumbnailView.setImageBitmap(thumbnail);
            }

        } catch (java.lang.NullPointerException e) {
            // Not good program design, I know. :/
            Log.w(TAG, "updateThumbnail(): Fragment closed before thread ended work");
        }
    }

    private void updateInfo(VideoInfo info) {
        currentVideoInfo = info;

        try {
            VideoInfoItemViewCreator videoItemViewCreator =
                    new VideoInfoItemViewCreator(LayoutInflater.from(getActivity()));

            RelativeLayout textContentLayout = (RelativeLayout) activity.findViewById(R.id.detailTextContentLayout);
            TextView videoTitleView = (TextView) activity.findViewById(R.id.detailVideoTitleView);
            TextView uploaderView = (TextView) activity.findViewById(R.id.detailUploaderView);
            TextView viewCountView = (TextView) activity.findViewById(R.id.detailViewCountView);
            TextView thumbsUpView = (TextView) activity.findViewById(R.id.detailThumbsUpCountView);
            TextView thumbsDownView = (TextView) activity.findViewById(R.id.detailThumbsDownCountView);
            TextView uploadDateView = (TextView) activity.findViewById(R.id.detailUploadDateView);
            TextView descriptionView = (TextView) activity.findViewById(R.id.detailDescriptionView);
            FrameLayout nextVideoFrame = (FrameLayout) activity.findViewById(R.id.detailNextVideoFrame);
            RelativeLayout nextVideoRootFrame =
                    (RelativeLayout) activity.findViewById(R.id.detailNextVideoRootLayout);

            progressBar.setVisibility(View.GONE);


            View nextVideoView = videoItemViewCreator
                    .getViewFromVideoInfoItem(null, nextVideoFrame, info.nextVideo, getContext());
            nextVideoFrame.addView(nextVideoView);


            Button nextVideoButton = (Button) activity.findViewById(R.id.detailNextVideoButton);
            Button similarVideosButton = (Button) activity.findViewById(R.id.detailShowSimilarButton);

            textContentLayout.setVisibility(View.VISIBLE);
            playVideoButton.setVisibility(View.VISIBLE);
            if (!showNextVideoItem) {
                nextVideoRootFrame.setVisibility(View.GONE);
                similarVideosButton.setVisibility(View.GONE);
            }

            videoTitleView.setText(info.title);
            uploaderView.setText(info.uploader);
            actionBarHandler.setChannelName(info.uploader);

            String localizedViewCount = Localization.localizeViewCount(info.view_count, getContext());
            viewCountView.setText(localizedViewCount);

            String localizedLikeCount = Localization.localizeNumber(info.like_count, getContext());
            thumbsUpView.setText(localizedLikeCount);

            String localizedDislikeCount = Localization.localizeNumber(info.dislike_count, getContext());
            thumbsDownView.setText(localizedDislikeCount);

            String localizedDate = Localization.localizeDate(info.upload_date, getContext());
            uploadDateView.setText(localizedDate);

            descriptionView.setText(Html.fromHtml(info.description));

            descriptionView.setMovementMethod(LinkMovementMethod.getInstance());

            actionBarHandler.setServiceId(streamingServiceId);
            actionBarHandler.setVideoInfo(info.webpage_url, info.title);
            actionBarHandler.setStartPosition(info.startPosition);

            // parse streams
            Vector<VideoInfo.VideoStream> streamsToUse = new Vector<>();
            for (VideoInfo.VideoStream i : info.videoStreams) {
                if (useStream(i, streamsToUse)) {
                    streamsToUse.add(i);
                }
            }

            actionBarHandler.setStreams(streamsToUse, info.audioStreams);

            nextVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent detailIntent =
                            new Intent(getActivity(), VideoItemDetailActivity.class);
                        /*detailIntent.putExtra(
                                VideoItemDetailFragment.ARG_ITEM_ID, currentVideoInfo.nextVideo.id); */
                    detailIntent.putExtra(
                            VideoItemDetailFragment.VIDEO_URL, currentVideoInfo.nextVideo.webpage_url);

                    detailIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, streamingServiceId);
                    startActivity(detailIntent);
                }
            });


            if(autoPlayEnabled) {
                actionBarHandler.playVideo();
            }
        } catch (java.lang.NullPointerException e) {
            Log.w(TAG, "updateInfo(): Fragment closed before thread ended work... or else");
            e.printStackTrace();
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

            playVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    actionBarHandler.playVideo();
                }
            });

            backgroundButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    actionBarHandler.playVideo();
                }
            });

            Button similarVideosButton = (Button) activity.findViewById(R.id.detailShowSimilarButton);
            similarVideosButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(activity, VideoItemListActivity.class);
                    //todo: find more elegant way to do this - converting from List to ArrayList sucks
                    ArrayList<VideoPreviewInfo> toParcel = new ArrayList<>(currentVideoInfo.relatedVideos);
                    //why oh why does the parcelable array put method have to be so damn specific
                    // about the class of its argument?
                    //why not a List<? extends Parcelable>?
                    intent.putParcelableArrayListExtra(VideoItemListActivity.VIDEO_INFO_ITEMS, toParcel);
                    activity.startActivity(intent);
                }
            });

            // todo: Fix this workaround (probably with a better design), so that older android
            // versions don't have problems rendering the thumbnail right.
            if(Build.VERSION.SDK_INT >= 18) {
                ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
                thumbnailView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    // This is used to synchronize the thumbnailWindowButton and the playVideoButton
                    // inside the ScrollView with the actual size of the thumbnail.
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
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