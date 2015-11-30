package org.schabi.newpipe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.MenuItem;

import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import org.schabi.newpipe.services.VideoExtractor;
import org.schabi.newpipe.services.ServiceList;
import org.schabi.newpipe.services.StreamingService;


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

    private AppCompatActivity activity;
    private ActionBarHandler actionBarHandler;

    private boolean autoPlayEnabled = false;
    private VideoInfo currentVideoInfo = null;
    private boolean showNextVideoItem = false;

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
                this.videoExtractor = service.getExtractorInstance(videoUrl);
                VideoInfo videoInfo = videoExtractor.getVideoInfo();
                h.post(new VideoResultReturnedRunnable(videoInfo));
                if (videoInfo.videoAvailableStatus == VideoInfo.VIDEO_AVAILABLE) {
                    h.post(new SetThumbnailRunnable(
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
                    if(showNextVideoItem) {
                        h.post(new SetThumbnailRunnable(
                                BitmapFactory.decodeStream(
                                        new URL(videoInfo.nextVideo.thumbnail_url)
                                                .openConnection()
                                                .getInputStream()),
                                SetThumbnailRunnable.NEXT_VIDEO_THUMBNAIL));
                    }
                }
            } catch (Exception e) {
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
        Resources res = activity.getResources();
        try {
            VideoInfoItemViewCreator videoItemViewCreator =
                    new VideoInfoItemViewCreator(LayoutInflater.from(getActivity()));

            ScrollView contentMainView = (ScrollView) activity.findViewById(R.id.detailMainContent);
            ProgressBar progressBar = (ProgressBar) activity.findViewById(R.id.detailProgressBar);
            TextView videoTitleView = (TextView) activity.findViewById(R.id.detailVideoTitleView);
            TextView uploaderView = (TextView) activity.findViewById(R.id.detailUploaderView);
            TextView viewCountView = (TextView) activity.findViewById(R.id.detailViewCountView);
            TextView thumbsUpView = (TextView) activity.findViewById(R.id.detailThumbsUpCountView);
            TextView thumbsDownView = (TextView) activity.findViewById(R.id.detailThumbsDownCountView);
            TextView uploadDateView = (TextView) activity.findViewById(R.id.detailUploadDateView);
            TextView descriptionView = (TextView) activity.findViewById(R.id.detailDescriptionView);
            ImageView thumbnailView = (ImageView) activity.findViewById(R.id.detailThumbnailView);
            FrameLayout nextVideoFrame = (FrameLayout) activity.findViewById(R.id.detailNextVideoFrame);
            RelativeLayout nextVideoRootFrame =
                    (RelativeLayout) activity.findViewById(R.id.detailNextVideoRootLayout);
            View nextVideoView = videoItemViewCreator
                    .getViewByVideoInfoItem(null, nextVideoFrame, info.nextVideo);
            nextVideoFrame.addView(nextVideoView);
            Button nextVideoButton = (Button) activity.findViewById(R.id.detailNextVideoButton);
            Button similarVideosButton = (Button) activity.findViewById(R.id.detailShowSimilarButton);

            contentMainView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            if(!showNextVideoItem) {
                nextVideoRootFrame.setVisibility(View.GONE);
                similarVideosButton.setVisibility(View.GONE);
            }

            switch (info.videoAvailableStatus) {
                case VideoInfo.VIDEO_AVAILABLE: {
                    videoTitleView.setText(info.title);
                    uploaderView.setText(info.uploader);

                    Locale locale = getPreferredLocale();
                    NumberFormat nf = NumberFormat.getInstance(locale);
                    String localisedViewCount = nf.format(info.view_count);
                    viewCountView.setText(
                            String.format(
                                    res.getString(R.string.viewCountText), localisedViewCount));
                    /*viewCountView.setText(localisedViewCount
                            + " " + activity.getString(R.string.viewSufix)); */


                    thumbsUpView.setText(nf.format(info.like_count));
                    thumbsDownView.setText(nf.format(info.dislike_count));

                    @SuppressLint("SimpleDateFormat")
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                    Date datum = null;
                    try {
                        datum = formatter.parse(info.upload_date);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);

                    String localisedDate = df.format(datum);
                    uploadDateView.setText(
                            String.format(res.getString(R.string.uploadDateText), localisedDate));
                    descriptionView.setText(Html.fromHtml(info.description));
                    descriptionView.setMovementMethod(LinkMovementMethod.getInstance());

                    actionBarHandler.setVideoInfo(info.webpage_url, info.title);
                    actionBarHandler.setStartPosition(info.startPosition);

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
                    actionBarHandler.setStreams(streamList, info.audioStreams);
                }

                nextVideoButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent detailIntent =
                                new Intent(getActivity(), VideoItemDetailActivity.class);
                        detailIntent.putExtra(
                                VideoItemDetailFragment.ARG_ITEM_ID, currentVideoInfo.nextVideo.id);
                        detailIntent.putExtra(
                                VideoItemDetailFragment.VIDEO_URL, currentVideoInfo.nextVideo.webpage_url);
                        //todo: make id dynamic the following line is crap
                        detailIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, 0);
                        startActivity(detailIntent);
                    }
                });
                break;
                case VideoInfo.VIDEO_UNAVAILABLE_GEMA:
                    thumbnailView.setImageBitmap(BitmapFactory.decodeResource(
                            getResources(), R.drawable.gruese_die_gema_unangebracht));
                    break;
                case VideoInfo.VIDEO_UNAVAILABLE:
                    thumbnailView.setImageBitmap(BitmapFactory.decodeResource(
                            getResources(), R.drawable.not_available_monkey));
                    break;
                default:
                    Log.e(TAG, "Video Available Status not known.");
            }

            if(autoPlayEnabled) {
                actionBarHandler.playVideo();
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) getActivity();
        showNextVideoItem = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(activity.getString(R.string.showNextVideo), true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_videoitem_detail, container, false);
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
        FloatingActionButton playVideoButton =
                (FloatingActionButton) getActivity().findViewById(R.id.playVideoButton);

        // Sometimes when this fragment is not visible it still gets initiated
        // then we must not try to access objects of this fragment.
        // Otherwise the applications would crash.
        if(playVideoButton != null) {
            try {
                StreamingService streamingService = ServiceList.getService(
                        getArguments().getInt(STREAMING_SERVICE));
                Thread videoExtractorThread = new Thread(new VideoExtractorRunnable(
                        getArguments().getString(VIDEO_URL), streamingService));

                autoPlayEnabled = getArguments().getBoolean(AUTO_PLAY);
                videoExtractorThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getBoolean(getString(R.string.leftHandLayout), false) && checkIfLandscape()) {
                RelativeLayout.LayoutParams oldLayout =
                        (RelativeLayout.LayoutParams) playVideoButton.getLayoutParams();
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                layoutParams.setMargins(oldLayout.leftMargin, oldLayout.topMargin,
                        oldLayout.rightMargin, oldLayout.bottomMargin);
                playVideoButton.setLayoutParams(layoutParams);
            }

            playVideoButton.setOnClickListener(new View.OnClickListener() {
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
        }
    }

    /**Returns the java.util.Locale object which corresponds to the locale set in NewPipe's preferences.
     * Currently not affected by the device's locale.*/
    private Locale getPreferredLocale() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        String languageKey = getContext().getString(R.string.searchLanguage);
        //i know the following line defaults languageCode to "en", but java is picky about uninitialised values
        // Schabi: well lint tels me the value is redundant. I'll suppress it for now.
        @SuppressWarnings("UnusedAssignment") String languageCode = "en";
        languageCode = sp.getString(languageKey, "en");

        if(languageCode.length() == 2) {
            return new Locale(languageCode);
        }
        else if(languageCode.contains("_")) {
            String country = languageCode
                    .substring(languageCode.indexOf("_"), languageCode.length());
            return new Locale(languageCode.substring(0, 2), country);
        }
        return Locale.getDefault();
    }

    private boolean checkIfLandscape() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels < displayMetrics.widthPixels;
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
}