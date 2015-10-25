package org.schabi.newpipe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.URL;
import java.util.Vector;


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
    public static final String ARG_ITEM_ID = "item_id";
    public static final String VIDEO_URL = "video_url";
    public static final String STREAMING_SERVICE = "streaming_service";
    public static final String AUTO_PLAY = "auto_play";

    private boolean autoPlayEnabled = false;
    private Thread extractorThread = null;
    private VideoInfo currentVideoInfo = null;

    private class ExtractorRunnable implements Runnable {
        private Handler h = new Handler();
        private Class extractorClass;
        private String videoUrl;
        public ExtractorRunnable(String videoUrl, Class extractorClass, VideoItemDetailFragment f) {
            this.extractorClass = extractorClass;
            this.videoUrl = videoUrl;
        }
        @Override
        public void run() {
            try {
                Extractor extractor = (Extractor) extractorClass.newInstance();
                VideoInfo videoInfo = extractor.getVideoInfo(videoUrl);
                h.post(new VideoResultReturnedRunnable(videoInfo));
                if (videoInfo.videoAvailableStatus == VideoInfo.VIDEO_AVAILABLE) {
                    h.post(new SetThumbnailRunnable(
                            BitmapFactory.decodeStream(
                                    new URL(videoInfo.thumbnail_url)
                                            .openConnection()
                                            .getInputStream()), SetThumbnailRunnable.VIDEO_THUMBNAIL));
                    h.post(new SetThumbnailRunnable(
                            BitmapFactory.decodeStream(
                                    new URL(videoInfo.uploader_thumbnail_url)
                                            .openConnection()
                                            .getInputStream()), SetThumbnailRunnable.CHANNEL_THUMBNAIL));
                    h.post(new SetThumbnailRunnable(
                            BitmapFactory.decodeStream(
                                    new URL(videoInfo.nextVideo.thumbnail_url)
                                            .openConnection()
                                            .getInputStream()), SetThumbnailRunnable.NEXT_VIDEO_THUMBNAIL));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private class VideoResultReturnedRunnable implements Runnable {
        private VideoInfo videoInfo;
        public VideoResultReturnedRunnable(VideoInfo videoInfo) {
            this.videoInfo = videoInfo;
        }
        @Override
        public void run() {
            updateInfo(videoInfo);
        }
    }

    private class SetThumbnailRunnable implements Runnable {
        public static final int VIDEO_THUMBNAIL = 1;
        public static final int CHANNEL_THUMBNAIL = 2;
        public static final int NEXT_VIDEO_THUMBNAIL = 3;
        private Bitmap thumbnail;
        private int thumbnailId;
        public SetThumbnailRunnable(Bitmap thumbnail, int id) {
            this.thumbnail = thumbnail;
            this.thumbnailId = id;
        }
        @Override
        public void run() {
            updateThumbnail(thumbnail, thumbnailId);
        }
    }

    public void updateThumbnail(Bitmap thumbnail, int id) {
        Activity a = getActivity();
        ImageView thumbnailView = null;
        try {
            switch (id) {
                case SetThumbnailRunnable.VIDEO_THUMBNAIL:
                    thumbnailView = (ImageView) a.findViewById(R.id.detailThumbnailView);
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
            // No god programm design i know. :/
            Log.w(TAG, "updateThumbnail(): Fragment closed before thread ended work");
        }
    }

    public void updateInfo(VideoInfo info) {
        Activity a = getActivity();
        currentVideoInfo = info;
        try {
            VideoInfoItemViewCreator videoItemViewCreator =
                    new VideoInfoItemViewCreator(LayoutInflater.from(getActivity()));

            ScrollView contentMainView = (ScrollView) a.findViewById(R.id.detailMainContent);
            ProgressBar progressBar = (ProgressBar) a.findViewById(R.id.detailProgressBar);
            TextView videoTitleView = (TextView) a.findViewById(R.id.detailVideoTitleView);
            TextView uploaderView = (TextView) a.findViewById(R.id.detailUploaderView);
            TextView viewCountView = (TextView) a.findViewById(R.id.detailViewCountView);
            TextView thumbsUpView = (TextView) a.findViewById(R.id.detailThumbsUpCountView);
            TextView thumbsDownView = (TextView) a.findViewById(R.id.detailThumbsDownCountView);
            TextView uploadDateView = (TextView) a.findViewById(R.id.detailUploadDateView);
            TextView descriptionView = (TextView) a.findViewById(R.id.detailDescriptionView);
            ImageView thumbnailView = (ImageView) a.findViewById(R.id.detailThumbnailView);
            FrameLayout nextVideoFrame = (FrameLayout) a.findViewById(R.id.detailNextVideoFrame);
            View nextVideoView = videoItemViewCreator
                    .getViewByVideoInfoItem(null, nextVideoFrame, info.nextVideo);
            nextVideoFrame.addView(nextVideoView);
            Button nextVideoButton = (Button) a.findViewById(R.id.detailNextVideoButton);

            contentMainView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);

            switch (info.videoAvailableStatus) {
                case VideoInfo.VIDEO_AVAILABLE: {
                    videoTitleView.setText(info.title);
                    uploaderView.setText(info.uploader);
                    viewCountView.setText(info.view_count + " " + a.getString(R.string.viewSufix));
                    thumbsUpView.setText(info.like_count);
                    thumbsDownView.setText(info.dislike_count);
                    uploadDateView.setText(a.getString(R.string.uploadDatePrefix) + " " + info.upload_date);
                    descriptionView.setText(Html.fromHtml(info.description));
                    descriptionView.setMovementMethod(LinkMovementMethod.getInstance());

                    ActionBarHandler.getHandler().setVideoInfo(info.webpage_url, info.title);

                    // parse streams
                    Vector<VideoInfo.VideoStream> streamsToUse = new Vector<>();
                    for (VideoInfo.VideoStream i : info.videoStreams) {
                        if (useStream(i, streamsToUse)) {
                            streamsToUse.add(i);
                        }
                    }
                    VideoInfo.VideoStream[] streamList = new VideoInfo.VideoStream[streamsToUse.size()];
                    for (int i = 0; i < streamList.length; i++) {
                        streamList[i] = streamsToUse.get(i);
                    }
                    ActionBarHandler.getHandler().setStreams(streamList, info.audioStreams);
                }

                nextVideoButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent detailIntent = new Intent(getActivity(), VideoItemDetailActivity.class);
                        detailIntent.putExtra(VideoItemDetailFragment.ARG_ITEM_ID, currentVideoInfo.nextVideo.id);
                        detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, currentVideoInfo.nextVideo.webpage_url);
                        //todo: make id dynamic the following line is crap
                        detailIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, 0);
                        startActivity(detailIntent);
                    }
                });
                break;
                case VideoInfo.VIDEO_UNAVAILABLE_GEMA:
                    thumbnailView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.gruese_die_gema_unangebracht));
                    break;
                case VideoInfo.VIDEO_UNAVAILABLE:
                    thumbnailView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.not_available_monkey));
                    break;
                default:
                    Log.e(TAG, "Video Available Status not known.");
            }

            if(autoPlayEnabled) {
                ActionBarHandler.getHandler().playVideo();
            }
        } catch (java.lang.NullPointerException e) {
            Log.w(TAG, "updateInfo(): Fragment closed before thread ended work... or else");
            e.printStackTrace();
        }
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
    public VideoItemDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_videoitem_detail, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceBundle) {
        super.onActivityCreated(savedInstanceBundle);
        FloatingActionButton playVideoButton = (FloatingActionButton) getActivity().findViewById(R.id.playVideoButton);

        if(playVideoButton != null) {

            try {
                StreamingService streamingService = ServiceList.getService(
                        getArguments().getInt(STREAMING_SERVICE));
                extractorThread = new Thread(new ExtractorRunnable(
                        getArguments().getString(VIDEO_URL), streamingService.getExtractorClass(), this));
                autoPlayEnabled = getArguments().getBoolean(AUTO_PLAY);
                extractorThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getBoolean(getString(R.string.leftHandLayout), false) && checkIfLandscape()) {
                RelativeLayout.LayoutParams oldLayout = (RelativeLayout.LayoutParams) playVideoButton.getLayoutParams();
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                layoutParams.setMargins(oldLayout.leftMargin, oldLayout.topMargin, oldLayout.rightMargin, oldLayout.bottomMargin);
                playVideoButton.setLayoutParams(layoutParams);
            }

            playVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActionBarHandler.getHandler().playVideo();
                }
            });
        }
    }

    public boolean checkIfLandscape() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels < displayMetrics.widthPixels;
    }
}